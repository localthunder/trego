package com.helgolabs.trego.ui.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.entities.GroupDefaultSplitEntity
import com.helgolabs.trego.data.local.entities.GroupEntity
import com.helgolabs.trego.data.local.entities.GroupMemberEntity
import com.helgolabs.trego.data.local.entities.UserEntity
import com.helgolabs.trego.ui.components.AddMembersBottomSheet
import com.helgolabs.trego.ui.components.CurrencySelectionBottomSheet
import com.helgolabs.trego.ui.components.GlobalFAB
import com.helgolabs.trego.ui.components.GlobalTopAppBar
import com.helgolabs.trego.ui.viewmodels.GroupViewModel
import com.helgolabs.trego.utils.CurrencyUtils
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GroupSettingsScreen(
    navController: NavController,
    groupId: Int
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val myApplication = context.applicationContext as MyApplication
    val groupViewModel: GroupViewModel = viewModel(factory = myApplication.viewModelFactory)
    val scope = rememberCoroutineScope()
    val userId = getUserIdFromPreferences(context)
    val groupDao = myApplication.database.groupDao()

    // Observe group details state
    val groupDetailsState by groupViewModel.groupDetailsState.collectAsState()
    val group = groupDetailsState.group
    val members = groupDetailsState.groupMembers
    val usernames = groupDetailsState.usernames
    val users = groupDetailsState.users

    // Add this to load and log the actual group data
    var actualGroup by remember { mutableStateOf<GroupEntity?>(null) }

    // State for default splits
    val defaultSplits by groupViewModel.groupDefaultSplits.collectAsState()
    val operationState by groupViewModel.defaultSplitOperationState.collectAsState()

    // Local state for editing
    var selectedSplitMode by remember(group) { mutableStateOf(group?.defaultSplitMode ?: "equally") }
    var editMode by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showArchiveConfirmDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showLeaveGroupDialog by remember { mutableStateOf(false) }
    var showCurrencySheet by remember { mutableStateOf(false) }
    var showAddMembersSheet by remember { mutableStateOf(false) }


    // State for selected currency
    var selectedCurrency by remember(group) { mutableStateOf(group?.defaultCurrency ?: "GBP") }

    // State for percentage splits
    val memberPercentages = remember { mutableStateMapOf<Int, String>() }

    // Load data when screen opens
    LaunchedEffect(groupId) {
        Log.d("GroupSettings", "Loading group details for groupId: $groupId")
        groupViewModel.initializeGroupDetails(groupId)
        groupViewModel.loadGroupDefaultSplits(groupId)

        // Properly load group from database in a coroutine
        myApplication.database.groupDao().getGroupById(groupId).collect { dbGroup ->
            actualGroup = dbGroup
            Log.d("GroupSettings", "Loaded group from DB: $dbGroup")
        }
    }

    // Initialize percentages when members and splits are loaded
    LaunchedEffect(members, defaultSplits) {
        // Reset percentages map
        memberPercentages.clear()

        if (defaultSplits.isNotEmpty()) {
            // Use existing default splits
            defaultSplits.forEach { split ->
                memberPercentages[split.userId] = split.percentage?.toString() ?: "0.0"
            }
        } else {
            // Initialize with equal percentages
            val equalPercentage = if (members.isNotEmpty()) (100.0 / members.size) else 0.0
            members.forEach { member ->
                memberPercentages[member.userId] = String.format("%.1f", equalPercentage)
            }
        }
    }

    // Handle operation state changes
    LaunchedEffect(operationState) {
        when (operationState) {
            is GroupViewModel.OperationState.Success -> {
                editMode = false
                // Reset operation state after a delay
                scope.launch {
                    delay(1000)
                    groupViewModel.resetDefaultSplitOperationState()
                }
            }

            is GroupViewModel.OperationState.Error -> {
                // Show error message
                // Reset operation state after user dismisses
            }

            else -> {
                // Handle loading or idle state
            }
        }
    }

    // Function to save changes
    fun saveChanges() {
        if (group == null) return

        Log.d("Group Settings Screen", "group is: $group with id=$groupId")

        // Save group split mode using the explicit groupId parameter
        val updatedGroup = group.copy(
            id = groupId,  // Use the parameter groupId here instead of group.id
            defaultSplitMode = selectedSplitMode,
            defaultCurrency = selectedCurrency,
            updatedAt = DateUtils.getCurrentTimestamp()
        )
        groupViewModel.updateGroup(updatedGroup)

        // If percentage mode, save all percentages
        if (selectedSplitMode == "percentage") {
            val currentTime = DateUtils.getCurrentTimestamp()
            val splits = members.mapNotNull { member ->
                val percentageStr = memberPercentages[member.userId] ?: return@mapNotNull null
                val percentage = percentageStr.toDoubleOrNull() ?: return@mapNotNull null

                // Find existing split or create new one
                val existingSplit = defaultSplits.find { it.userId == member.userId }

                GroupDefaultSplitEntity(
                    id = existingSplit?.id ?: 0,
                    serverId = existingSplit?.serverId,
                    groupId = groupId,
                    userId = member.userId,
                    percentage = percentage,
                    createdAt = existingSplit?.createdAt ?: currentTime,
                    updatedAt = currentTime
                )
            }

            groupViewModel.updateGroupDefaultSplits(groupId, splits)
        } else {
            // If switching to equally mode, delete all percentage splits
            if (defaultSplits.isNotEmpty()) {
                groupViewModel.deleteAllGroupDefaultSplits(groupId)
            }
        }
    }

    // Function to delete all splits
    fun deleteAllSplits() {
        groupViewModel.deleteAllGroupDefaultSplits(groupId)
        showDeleteConfirmation = false
    }

    // UI
    Scaffold(
        topBar = {
            GlobalTopAppBar(
                title = { Text("Group Settings") },
                actions = {
                    if (editMode) {
                        IconButton(onClick = { saveChanges() }) {
                            Icon(Icons.Default.Check, "Save Changes")
                        }
                    } else {
                        IconButton(onClick = { editMode = true }) {
                            Icon(Icons.Default.Edit, "Edit Settings")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (editMode) {
                GlobalFAB(
                    onClick = { saveChanges() },
                    text = "Save Changes",
                    icon = { Icon(Icons.Default.Check, "Save Changes") }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Group Information section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Group Information",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Text(
                            text = "Name: ${group?.name ?: "Loading..."}",
                            style = MaterialTheme.typography.bodyLarge
                        )

                        // Default currency with edit option
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Default currency: ",
                                style = MaterialTheme.typography.bodyLarge
                            )

                            val symbol = CurrencyUtils.currencySymbols[selectedCurrency] ?: selectedCurrency

                            if (editMode) {
                                OutlinedButton(
                                    onClick = { showCurrencySheet = true },
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Text("$selectedCurrency ($symbol)")
                                }
                            } else {
                                Text(
                                    text = "$selectedCurrency ($symbol)",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Members list section
                        Text(
                            text = "Members (${members.size}):",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Column(
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            members.forEach { member ->
                                val memberName = when {
                                    member.userId == userId -> "Me"
                                    usernames.containsKey(member.userId) -> usernames[member.userId] ?: "Unknown"
                                    users.any { it.userId == member.userId } -> users.find { it.userId == member.userId }?.username ?: "Unknown"
                                    else -> "User ${member.userId}"
                                }

                                Text(
                                    text = "• $memberName",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }

                            OutlinedButton(
                                onClick = { showAddMembersSheet = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add Someone")
                            }
                        }
                    }
                }
            }

            // Split Settings section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Default Split Settings",
                                style = MaterialTheme.typography.titleLarge
                            )

                            if (editMode && selectedSplitMode == "percentage" && defaultSplits.isNotEmpty()) {
                                IconButton(onClick = { showDeleteConfirmation = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Reset split settings",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Default split mode:",
                            style = MaterialTheme.typography.bodyLarge
                        )

                        // Split mode selection
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedSplitMode == "equally",
                                onClick = {
                                    if (editMode) selectedSplitMode = "equally"
                                },
                                enabled = editMode
                            )
                            Text(
                                text = "Split equally",
                                modifier = Modifier.clickable(enabled = editMode) {
                                    selectedSplitMode = "equally"
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedSplitMode == "percentage",
                                onClick = {
                                    if (editMode) selectedSplitMode = "percentage"
                                },
                                enabled = editMode
                            )
                            Text(
                                text = "Split by percentage",
                                modifier = Modifier.clickable(enabled = editMode) {
                                    selectedSplitMode = "percentage"
                                }
                            )
                        }

                        // Percentage settings (only visible when percentage is selected)
                        if (selectedSplitMode == "percentage") {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Total percentage display
                            val totalPercentage = memberPercentages.values
                                .mapNotNull { it.toDoubleOrNull() }
                                .sum()

                            Text(
                                text = "Total: ${String.format("%.1f", totalPercentage)}%",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (totalPercentage == 100.0)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )

                            if (totalPercentage != 100.0) {
                                Text(
                                    text = "Total must equal 100%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Members percentage inputs
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Member",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(2f)
                                    )
                                    Text(
                                        text = "Percentage",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Divider()

                                members.forEach { member ->
                                    val username = when {
                                        member.userId == userId -> "Me"
                                        usernames.containsKey(member.userId) -> usernames[member.userId]
                                            ?: "Unknown"

                                        users.any { it.userId == member.userId } -> users.find { it.userId == member.userId }?.username
                                            ?: "Unknown"

                                        else -> "User ${member.userId}"
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = username,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.weight(2f)
                                        )

                                        if (editMode) {
                                            TextField(
                                                value = memberPercentages[member.userId] ?: "0.0",
                                                onValueChange = { value ->
                                                    // Only allow numbers and decimal point
                                                    if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                                                        memberPercentages[member.userId] = value
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                singleLine = true,
                                                suffix = { Text("%") }
                                            )
                                        } else {
                                            Text(
                                                text = "${memberPercentages[member.userId] ?: "0.0"}%",
                                                style = MaterialTheme.typography.bodyLarge,
                                                modifier = Modifier.weight(1f),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                // Equal distribution button
                                if (editMode) {
                                    Button(
                                        onClick = {
                                            // Set equal percentages for all members
                                            val equalPercentage = 100.0 / members.size
                                            members.forEach { member ->
                                                memberPercentages[member.userId] =
                                                    String.format("%.1f", equalPercentage)
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                    ) {
                                        Text("Set Equal Percentages")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Operation status message
            item {
                when (operationState) {
                    is GroupViewModel.OperationState.Loading -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    is GroupViewModel.OperationState.Success -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Text(
                                text = "Settings saved successfully!",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    is GroupViewModel.OperationState.Error -> {
                        val errorMessage =
                            (operationState as GroupViewModel.OperationState.Error).message
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text(
                                text = errorMessage,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    else -> { /* No message for Idle state */
                    }
                }
            }

            // Archive/Leave Group section
            item {
                Spacer(modifier = Modifier.height(32.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Danger Zone",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error
                        )

                        // Archive/Unarchive Group
                        Button(
                            onClick = {
                                if (groupDetailsState.isArchived) {
                                    showRestoreConfirmDialog = true
                                } else {
                                    showArchiveConfirmDialog = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Icon(
                                imageVector = if (groupDetailsState.isArchived)
                                    Icons.Default.Unarchive
                                else
                                    Icons.Default.Archive,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                if (groupDetailsState.isArchived) "Unarchive Group"
                                else "Archive Group"
                            )
                        }

                        // Leave Group
                        Button(
                            onClick = { showLeaveGroupDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Leave Group")
                        }
                    }
                }
            }
        }

        // Currency selection bottom sheet
        if (showCurrencySheet) {
            CurrencySelectionBottomSheet(
                onDismiss = { showCurrencySheet = false },
                onCurrencySelected = { currencyCode ->
                    selectedCurrency = currencyCode
                }
            )
        }

        // Add members bottom sheet
        if (showAddMembersSheet) {
            AddMembersBottomSheet(
                groupId = groupId,
                onDismissRequest = {
                    showAddMembersSheet = false
                    // Refresh members list
                    groupViewModel.loadGroupMembersWithUsers(groupId)
                }
            )
        }

        // Confirmation dialog for deleting splits
        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text("Reset Split Settings") },
                text = { Text("This will reset all custom percentage splits. Are you sure?") },
                confirmButton = {
                    Button(
                        onClick = { deleteAllSplits() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Reset")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Archive confirmation dialog
        if (showArchiveConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showArchiveConfirmDialog = false },
                title = { Text("Archive Group") },
                text = {
                    Text("This will archive the group. The group will still exist but will be hidden from your main list. You can unarchive it later.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            groupViewModel.archiveGroup(groupId)
                            showArchiveConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Archive")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showArchiveConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Restore confirmation dialog
        if (showRestoreConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showRestoreConfirmDialog = false },
                title = { Text("Unarchive Group") },
                text = {
                    Text("This will unarchive the group and restore it to your main list.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            groupViewModel.restoreGroup(groupId)
                            showRestoreConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Unarchive")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Leave group confirmation dialog
        if (showLeaveGroupDialog) {
            AlertDialog(
                onDismissRequest = { showLeaveGroupDialog = false },
                title = { Text("Leave Group") },
                text = {
                    Text("Are you sure you want to leave this group? You will no longer have access to the group's expenses and activities.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val memberId = groupViewModel.getCurrentMemberId()
                            if (memberId != null) {
                                groupViewModel.removeMember(memberId)
                                // Navigate back to home screen
                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = false }
                                }
                            }
                            showLeaveGroupDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Leave Group")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLeaveGroupDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}