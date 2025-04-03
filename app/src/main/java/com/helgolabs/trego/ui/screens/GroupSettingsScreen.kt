package com.helgolabs.trego.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.entities.GroupDefaultSplitEntity
import com.helgolabs.trego.data.local.entities.GroupEntity
import com.helgolabs.trego.ui.components.AddMembersBottomSheet
import com.helgolabs.trego.ui.components.CurrencySelectionBottomSheet
import com.helgolabs.trego.ui.components.EditableField
import com.helgolabs.trego.ui.components.GlobalTopAppBar
import com.helgolabs.trego.ui.components.SectionHeader
import com.helgolabs.trego.ui.components.SelectableField
import com.helgolabs.trego.ui.viewmodels.GroupViewModel
import com.helgolabs.trego.utils.CurrencyUtils
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsScreen(
    navController: NavController,
    groupId: Int
) {
    val context = LocalContext.current
    val myApplication = context.applicationContext as MyApplication
    val groupViewModel: GroupViewModel = viewModel(factory = myApplication.viewModelFactory)
    val scope = rememberCoroutineScope()
    val userId = getUserIdFromPreferences(context)

    // Observe group details state
    val groupDetailsState by groupViewModel.groupDetailsState.collectAsState()
    val group = groupDetailsState.group
    val members = groupDetailsState.groupMembers
    val usernames = groupDetailsState.usernames
    val users = groupDetailsState.users

    // State for default splits
    val defaultSplits by groupViewModel.groupDefaultSplits.collectAsState()
    val operationState by groupViewModel.defaultSplitOperationState.collectAsState()

    // UI state variables
    var editingGroupName by remember { mutableStateOf(false) }
    var groupName by remember(group) { mutableStateOf(group?.name ?: "") }
    var showCurrencySheet by remember { mutableStateOf(false) }
    var showAddMembersSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showArchiveConfirmDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showLeaveGroupDialog by remember { mutableStateOf(false) }
    var expandedSplitSection by remember { mutableStateOf(false) }

    // Toast state
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var showToast by remember { mutableStateOf(false) }

    // State for selected currency and split mode
    var selectedCurrency by remember(group) { mutableStateOf(group?.defaultCurrency ?: "GBP") }
    var selectedSplitMode by remember(group) { mutableStateOf(group?.defaultSplitMode ?: "equally") }
    var showSuccessMessage by remember { mutableStateOf(false) }

    // State for percentage splits
    val memberPercentages = remember { mutableStateMapOf<Int, String>() }

    LaunchedEffect(group) {
        group?.let {
            groupName = it.name
            selectedCurrency = it.defaultCurrency
            selectedSplitMode = it.defaultSplitMode
        }
    }

    // Initialize data
    LaunchedEffect(groupId) {
        Log.d("GroupSettings", "Loading group details for groupId: $groupId")
        groupViewModel.initializeGroupDetails(groupId)
        groupViewModel.loadGroupDefaultSplits(groupId)
    }

    // Initialize percentages when members and splits are loaded
    LaunchedEffect(members, defaultSplits) {
        memberPercentages.clear()

        if (defaultSplits.isNotEmpty()) {
            defaultSplits.forEach { split ->
                memberPercentages[split.userId] = split.percentage?.toString() ?: "0.0"
            }
        } else {
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
                showSuccessMessage = true
                delay(2000)
                showSuccessMessage = false
                groupViewModel.resetDefaultSplitOperationState()
            }
            is GroupViewModel.OperationState.Error -> {
                delay(3000)
                groupViewModel.resetDefaultSplitOperationState()
            }
            else -> {}
        }
    }

    // Toast handling
    LaunchedEffect(showToast) {
        if (showToast && toastMessage != null) {
            Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
            delay(2000)
            showToast = false
            toastMessage = null
        }
    }

    // Function to show toast
    fun showToast(message: String) {
        toastMessage = message
        showToast = true
    }

    // Save group name
    fun saveGroupName() {
        if (group == null || groupName.isBlank()) return

        val updatedGroup = group.copy(
            name = groupName,
            updatedAt = DateUtils.getCurrentTimestamp()
        )
        groupViewModel.updateGroup(updatedGroup)
        editingGroupName = false
    }

    // Save split settings
    fun saveSplitSettings() {
        if (group == null) return

        val updatedGroup = group.copy(
            defaultSplitMode = selectedSplitMode,
            updatedAt = DateUtils.getCurrentTimestamp()
        )
        scope.launch {
            groupViewModel.updateGroup(updatedGroup)
            showToast("Split mode updated")

            // If percentage mode, save all percentages
            if (selectedSplitMode == "percentage") {
                val currentTime = DateUtils.getCurrentTimestamp()
                val splits = members.mapNotNull { member ->
                    val percentageStr = memberPercentages[member.userId] ?: return@mapNotNull null
                    val percentage = percentageStr.toDoubleOrNull() ?: return@mapNotNull null

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
                showToast("Percentage splits updated")
            } else if (defaultSplits.isNotEmpty()) {
                groupViewModel.deleteAllGroupDefaultSplits(groupId)
                showToast("Percentage splits removed")
            }
        }
    }

    // Save currency change
    fun saveCurrencyChange() {
        if (group == null) return

        val updatedGroup = group.copy(
            defaultCurrency = selectedCurrency,
            updatedAt = DateUtils.getCurrentTimestamp()
        )
        scope.launch {
            groupViewModel.updateGroup(updatedGroup)
            showToast("Currency updated to $selectedCurrency")
        }
    }

    // Delete all splits
    fun deleteAllSplits() {
        scope.launch {
            groupViewModel.deleteAllGroupDefaultSplits(groupId)
            showDeleteConfirmation = false
            showToast("Splits reset to equal values")

            // Reset percentages to equal
            val equalPercentage = if (members.isNotEmpty()) (100.0 / members.size) else 0.0
            members.forEach { member ->
                memberPercentages[member.userId] = String.format("%.1f", equalPercentage)
            }
        }
    }

    // Main scaffold
    Scaffold(
        topBar = {
            GlobalTopAppBar(
                title = { Text("Group Settings") }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Show success message at the top
//            AnimatedVisibility(
//                visible = showSuccessMessage,
//                enter = fadeIn() + expandVertically(),
//                exit = fadeOut() + shrinkVertically(),
//                modifier = Modifier
//                    .align(Alignment.TopCenter)
//                    .fillMaxWidth()
//                    .padding(horizontal = 16.dp)
//            ) {
//                Surface(
//                    color = MaterialTheme.colorScheme.primaryContainer,
//                    modifier = Modifier.padding(top = 8.dp)
//                ) {
//                    Text(
//                        text = "Settings saved successfully!",
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(16.dp),
//                        textAlign = TextAlign.Center,
//                        color = MaterialTheme.colorScheme.onPrimaryContainer
//                    )
//                }
//            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Group Name Section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SectionHeader("Group details")

                        // Group name with inline editing
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EditableField(
                                label = "Name",
                                value = group?.name ?: "Loading...",
                                isEditing = editingGroupName,
                                editedValue = groupName,
                                onEditStart = {
                                    editingGroupName = true
                                    groupName = group?.name ?: ""
                                },
                                onValueChange = { groupName = it },
                                onSave = { saveGroupName() },
                                onCancel = { editingGroupName = false }
                            )
                        }

                        val symbol = CurrencyUtils.currencySymbols[selectedCurrency] ?: selectedCurrency

                        // Currency selector
                        SelectableField(
                            label = "Default Currency",
                            value = "$selectedCurrency ($symbol)",
                            onSelectClick = { showCurrencySheet = true },
                            icon = Icons.Default.Edit,  // You can use different icons
                        )
                    }
                }

                // Members section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SectionHeader("Members (${members.size})")

                            IconButton(
                                onClick = { showAddMembersSheet = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = "Add Member",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Member cards with avatars
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            members.forEach { member ->
                                val memberName = when {
                                    member.userId == userId -> "Me"
                                    usernames.containsKey(member.userId) -> usernames[member.userId] ?: "Unknown"
                                    users.any { it.userId == member.userId } -> users.find { it.userId == member.userId }?.username ?: "Unknown"
                                    else -> "User ${member.userId}"
                                }

                                // Initial for avatar
                                val initial = memberName.firstOrNull()?.uppercase() ?: "?"

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 1.dp,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Avatar circle
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.secondaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = initial,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Text(
                                            text = memberName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.weight(1f)
                                        )

//                                        // Don't show remove button for current user
//                                        if (member.userId != userId) {
//                                            IconButton(
//                                                onClick = {
//                                                    // Show confirmation dialog
//                                                }
//                                            ) {
//                                                Icon(
//                                                    imageVector = Icons.Default.PersonRemove,
//                                                    contentDescription = "Remove Member",
//                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
//                                                )
//                                            }
//                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Default Split Settings section
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SectionHeader("Default Split Settings")

                            IconButton(onClick = { expandedSplitSection = !expandedSplitSection }) {
                                Icon(
                                    imageVector = if (expandedSplitSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (expandedSplitSection) "Collapse" else "Expand"
                                )
                            }
                        }

                        // Split mode toggles
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Method",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Split type selection buttons
                                Row(
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedSplitMode == "equally",
                                        onClick = {
                                            selectedSplitMode = "equally"
                                            expandedSplitSection = false
                                            saveSplitSettings()
                                        },
                                        label = { Text("Equal Split") },
                                        leadingIcon = if (selectedSplitMode == "equally") {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else null,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )

                                    FilterChip(
                                        selected = selectedSplitMode == "percentage",
                                        onClick = {
                                            selectedSplitMode = "percentage"
                                            expandedSplitSection = true
                                            saveSplitSettings()
                                        },
                                        label = { Text("Percentage") },
                                        leadingIcon = if (selectedSplitMode == "percentage") {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else null
                                    )
                                }
                            }
                        }

                        // Expanded split percentage section
                        AnimatedVisibility(
                            visible = expandedSplitSection && selectedSplitMode == "percentage",
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                // Total percentage display
                                val totalPercentage = memberPercentages.values
                                    .mapNotNull { it.toDoubleOrNull() }
                                    .sum()

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Total: ${String.format("%.1f", totalPercentage)}%",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (totalPercentage == 100.0)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.error
                                    )

                                    if (selectedSplitMode == "percentage" && defaultSplits.isNotEmpty()) {
                                        IconButton(onClick = { showDeleteConfirmation = true }) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Reset percentages",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                if (totalPercentage != 100.0) {
                                    Text(
                                        text = "Total must equal 100%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Member percentage inputs
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    ) {
                                        // Header row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Member",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(2f)
                                            )
                                            Text(
                                                text = "Percentage",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f),
                                                textAlign = TextAlign.Center
                                            )
                                        }

                                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                                        members.forEach { member ->
                                            val username = when {
                                                member.userId == userId -> "Me"
                                                usernames.containsKey(member.userId) -> usernames[member.userId] ?: "Unknown"
                                                users.any { it.userId == member.userId } -> users.find { it.userId == member.userId }?.username ?: "Unknown"
                                                else -> "User ${member.userId}"
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = username,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.weight(2f)
                                                )

                                                OutlinedTextField(
                                                    value = memberPercentages[member.userId] ?: "0.0",
                                                    onValueChange = { value ->
                                                        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                                                            memberPercentages[member.userId] = value
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(56.dp),
                                                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                    singleLine = true,
                                                    suffix = { Text("%") },
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                                    )
                                                )
                                            }

                                            if (member != members.last()) {
                                                Divider(
                                                    modifier = Modifier.padding(vertical = 4.dp),
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Equal distribution button
                                    OutlinedButton(
                                        onClick = {
                                            val equalPercentage = 100.0 / members.size
                                            members.forEach { member ->
                                                memberPercentages[member.userId] =
                                                    String.format("%.1f", equalPercentage)
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Equal Split")
                                    }

                                    Button(
                                        onClick = { saveSplitSettings() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Save Changes")
                                    }
                                }
                            }
                        }
                    }
                }

                // Danger zone
                item {
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Archive/Unarchive Button
                        OutlinedButton(
                            onClick = {
                                if (groupDetailsState.isArchived) {
                                    showRestoreConfirmDialog = true
                                } else {
                                    showArchiveConfirmDialog = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
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

                        // Leave Group Button
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

                // Space at the bottom
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            // Status indicator during async operations
            if (operationState is GroupViewModel.OperationState.Loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }

            // Error message
            if (operationState is GroupViewModel.OperationState.Error) {
                val errorMessage = (operationState as GroupViewModel.OperationState.Error).message
                Snackbar(
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.BottomCenter),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Text(errorMessage)
                }
            }
        }

        // Currency selection bottom sheet
        if (showCurrencySheet) {
            CurrencySelectionBottomSheet(
                onDismiss = { showCurrencySheet = false },
                onCurrencySelected = { currencyCode ->
                    selectedCurrency = currencyCode
                    saveCurrencyChange()
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
                title = { Text("Reset Percentage Splits") },
                text = { Text("This will reset all custom percentage splits to equal values. Continue?") },
                confirmButton = {
                    Button(
                        onClick = { deleteAllSplits() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
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
                icon = { Icon(Icons.Default.Archive, contentDescription = null) },
                title = { Text("Archive Group") },
                text = {
                    Text("This group will be moved to your archived groups. You can restore it later if needed.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            groupViewModel.archiveGroup(groupId)
                            showArchiveConfirmDialog = false
                            showToast("Group archived")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
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
                icon = { Icon(Icons.Default.Unarchive, contentDescription = null) },
                title = { Text("Unarchive Group") },
                text = {
                    Text("This will restore the group to your active groups list.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            groupViewModel.restoreGroup(groupId)
                            showRestoreConfirmDialog = false
                            showToast("Group restored")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
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
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
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