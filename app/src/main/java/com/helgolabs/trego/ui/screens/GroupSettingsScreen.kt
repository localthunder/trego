package com.helgolabs.trego.ui.screens

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.dataClasses.PreferenceKeys
import com.helgolabs.trego.data.local.entities.GroupDefaultSplitEntity
import com.helgolabs.trego.data.local.entities.GroupEntity
import com.helgolabs.trego.data.local.entities.GroupMemberEntity
import com.helgolabs.trego.data.local.entities.UserEntity
import com.helgolabs.trego.ui.components.AddMembersBottomSheet
import com.helgolabs.trego.ui.components.CurrencySelectionBottomSheet
import com.helgolabs.trego.ui.components.EditableField
import com.helgolabs.trego.ui.components.GlobalTopAppBar
import com.helgolabs.trego.ui.components.InviteProvisionalUserDialog
import com.helgolabs.trego.ui.components.SectionHeader
import com.helgolabs.trego.ui.components.SelectableField
import com.helgolabs.trego.ui.components.UserListItem
import com.helgolabs.trego.ui.theme.AnimatedDynamicThemeProvider
import com.helgolabs.trego.ui.viewmodels.GroupViewModel
import com.helgolabs.trego.ui.viewmodels.UserPreferencesViewModel
import com.helgolabs.trego.utils.CurrencyUtils
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GroupSettingsScreen(
    navController: NavController,
    groupId: Int,
    groupViewModel: GroupViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userId = getUserIdFromPreferences(context)

    val myApplication = context.applicationContext as MyApplication
    val userPreferencesViewModel: UserPreferencesViewModel = viewModel(factory = myApplication.viewModelFactory)
    val themeMode by userPreferencesViewModel.themeMode.collectAsState(initial = PreferenceKeys.ThemeMode.SYSTEM)

    // Observe group details state
    val groupDetailsState by groupViewModel.groupDetailsState.collectAsState()
    val group = groupDetailsState.group
    val groupColorScheme = groupDetailsState.groupColorScheme

    // State for default splits
    val defaultSplits by groupViewModel.groupDefaultSplits.collectAsState()
    val operationState by groupViewModel.defaultSplitOperationState.collectAsState()

    // UI state variables
    var showCurrencySheet by remember { mutableStateOf(false) }
    var showAddMembersSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showArchiveConfirmDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showLeaveGroupDialog by remember { mutableStateOf(false) }

    // Dialog state
    var showRemoveConfirmDialog by remember { mutableStateOf(false) }
    var removeDialogMessage by remember { mutableStateOf("") }
    var removeDialogTitle by remember { mutableStateOf("") }
    var selectedMemberId by remember { mutableStateOf<Int?>(null) }

    // Toast state
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var showToast by remember { mutableStateOf(false) }

    // Initialize data
    LaunchedEffect(groupId) {
        // Load group details
        groupViewModel.loadGroupDetails(groupId)

        // Load group members with their user data
        groupViewModel.loadAllGroupMembersWithUsers(groupId)

        // Load default splits if needed
        groupViewModel.loadGroupDefaultSplits(groupId)
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

    AnimatedDynamicThemeProvider(groupId, groupColorScheme, themeMode) {
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Group details section
                    item {
                        GroupDetailsSection(
                            group = group,
                            groupViewModel = groupViewModel,
                            onCurrencySelect = { showCurrencySheet = true }
                        )
                    }

                    // Members section
                    item {
                        MembersSection(
                            groupId = groupId,
                            groupViewModel = groupViewModel,
                            userId = userId,
                            onAddMemberClick = { showAddMembersSheet = true },
                            onShowRemoveDialog = { memberId, title, message ->
                                selectedMemberId = memberId
                                removeDialogTitle = title
                                removeDialogMessage = message
                                showRemoveConfirmDialog = true
                            }
                        )
                    }

                    // Default Split Settings section
                    item {
                        SplitSettingsSection(
                            groupId = groupId,
                            groupViewModel = groupViewModel,
                            defaultSplits = defaultSplits,
                            onResetSplits = { showDeleteConfirmation = true }
                        )
                    }

                    // Danger zone
                    item {
                        DangerZoneSection(
                            isArchived = groupDetailsState.isArchived,
                            onArchiveClick = { showArchiveConfirmDialog = true },
                            onUnarchiveClick = { showRestoreConfirmDialog = true },
                            onLeaveClick = { showLeaveGroupDialog = true }
                        )
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
                    val errorMessage =
                        (operationState as GroupViewModel.OperationState.Error).message
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
                        scope.launch {
                            if (group != null) {
                                val updatedGroup = group.copy(
                                    defaultCurrency = currencyCode,
                                    updatedAt = DateUtils.getCurrentTimestamp()
                                )
                                groupViewModel.updateGroup(updatedGroup)
                                showToast("Currency updated to $currencyCode")
                            }
                        }
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

            // Remove member confirmation dialog
            if (showRemoveConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showRemoveConfirmDialog = false },
                    title = { Text(removeDialogTitle) },
                    text = { Text(removeDialogMessage) },
                    confirmButton = {
                        Button(
                            onClick = {
                                showRemoveConfirmDialog = false
                                selectedMemberId?.let { memberId ->
                                    groupViewModel.removeMember(memberId)
                                }
                                selectedMemberId = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Remove")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showRemoveConfirmDialog = false
                            selectedMemberId = null
                        }) {
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
}

/**
 * Section for showing and editing general group details (name, description, currency)
 */
@Composable
fun GroupDetailsSection(
    group: GroupEntity?,
    groupViewModel: GroupViewModel,
    onCurrencySelect: () -> Unit
) {
    var editingGroupName by remember { mutableStateOf(false) }
    var editingGroupDescription by remember { mutableStateOf(false) }
    var groupName by remember(group) { mutableStateOf(group?.name ?: "") }
    var groupDescription by remember(group) { mutableStateOf(group?.description ?: "") }
    val scope = rememberCoroutineScope()

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

    // Save group description
    fun saveGroupDescription() {
        if (group == null || groupDescription.isBlank()) return

        val updatedGroup = group.copy(
            description = groupDescription,
            updatedAt = DateUtils.getCurrentTimestamp()
        )
        groupViewModel.updateGroup(updatedGroup)
        editingGroupDescription = false
    }

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

        // Group description
        EditableField(
            label = "Description",
            value = group?.description ?: "Loading...",
            isEditing = editingGroupDescription,
            editedValue = groupDescription,
            onEditStart = {
                editingGroupDescription = true
                groupDescription = group?.description ?: ""
            },
            onValueChange = { groupDescription = it },
            onSave = { saveGroupDescription() },
            onCancel = { editingGroupDescription = false }
        )

        val currencyCode = group?.defaultCurrency ?: "GBP"
        val symbol = CurrencyUtils.currencySymbols[currencyCode] ?: currencyCode

        // Currency selector
        SelectableField(
            label = "Default Currency",
            value = "$currencyCode ($symbol)",
            onSelectClick = onCurrencySelect,
            icon = Icons.Default.Edit,
        )
    }
}

/**
 * Section for managing group members (active and archived)
 */
@Composable
fun MembersSection(
    groupId: Int,
    groupViewModel: GroupViewModel,
    userId: Int?,
    onAddMemberClick: () -> Unit,
    onShowRemoveDialog: (Int, String, String) -> Unit
) {
    val groupDetailsState by groupViewModel.groupDetailsState.collectAsState()
    val members = groupDetailsState.groupMembers
    val usernames = groupDetailsState.usernames
    val users = groupDetailsState.users
    val scope = rememberCoroutineScope()

    var showMemberMenu by remember { mutableStateOf(false) }
    var selectedMemberId by remember { mutableStateOf<Int?>(null) }
    var isCheckingMember by remember { mutableStateOf(false) }
    var showArchivedMembers by remember { mutableStateOf(false) }

    // Filter members in the screen
    val activeMembers = groupDetailsState.activeMembers
    val archivedMembers = groupDetailsState.archivedMembers

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            SectionHeader("Active Members (${activeMembers.size})")

            IconButton(
                onClick = onAddMemberClick
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = "Add Member",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Show active members
            ActiveMembersList(
                activeMembers = activeMembers,
                users = users,
                usernames = usernames,
                userId = userId,
                groupId = groupId,
                groupViewModel = groupViewModel,
                onShowRemoveDialog = onShowRemoveDialog
            )

            // Archived members section
            if (archivedMembers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                // Title with accordion button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Archived Members (${archivedMembers.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { showArchivedMembers = !showArchivedMembers }
                    ) {
                        Icon(
                            if (showArchivedMembers) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (showArchivedMembers) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedVisibility(visible = showArchivedMembers) {
                    ArchivedMembersList(
                        archivedMembers = archivedMembers,
                        users = users,
                        usernames = usernames,
                        userId = userId,
                        groupViewModel = groupViewModel
                    )
                }
            }
        }
    }
}

/**
 * List of active members in the group with improved menu functionality
 */
@Composable
fun ActiveMembersList(
    activeMembers: List<GroupMemberEntity>,
    users: List<UserEntity>,
    usernames: Map<Int, String>,
    userId: Int?,
    groupId: Int,
    groupViewModel: GroupViewModel,
    onShowRemoveDialog: (Int, String, String) -> Unit
) {
    val scope = rememberCoroutineScope()

    // State for email dialog
    var showEmailDialog by remember { mutableStateOf(false) }
    var selectedProvisionalUser by remember { mutableStateOf<UserEntity?>(null) }
    var editableEmail by remember { mutableStateOf("") }

    activeMembers.forEach { member ->
        // Get user details
        val user = users.find { it.userId == member.userId }
        val isProvisional = user?.isProvisional == true
        val memberUsername = when {
            member.userId == userId -> "Me"
            usernames.containsKey(member.userId) -> usernames[member.userId] ?: "Unknown"
            user != null -> user.username
            else -> "User ${member.userId}"
        }

        // State for member menu
        var showMemberMenu by remember { mutableStateOf(false) }
        var isCheckingMember by remember { mutableStateOf(false) }

        UserListItem(
            userId = member.userId,
            username = memberUsername,
            isProvisional = isProvisional,
            isCurrentUser = member.userId == userId,
            canInvite = isProvisional,
            onInviteClick = { provisionalUserId ->
                val provisionalUser = users.find { it.userId == provisionalUserId }
                if (provisionalUser != null) {
                    selectedProvisionalUser = provisionalUser
                    editableEmail = provisionalUser.invitationEmail ?: ""
                    showEmailDialog = true
                }
            },
            trailingContent = {
                // Show menu button ONLY for provisional members and not the current user
                if (isProvisional && member.userId != userId) {
                    Box(modifier = Modifier.size(32.dp)) {  // Fixed size to prevent overflow
                        IconButton(
                            onClick = { showMemberMenu = true },
                            modifier = Modifier.size(32.dp)  // Smaller icon button
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Member Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)  // Smaller icon
                            )
                        }

                        DropdownMenu(
                            expanded = showMemberMenu,
                            onDismissRequest = { showMemberMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    if (isCheckingMember) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Checking...")
                                        }
                                    } else {
                                        Text("Remove Member")
                                    }
                                },
                                onClick = {
                                    showMemberMenu = false
                                    isCheckingMember = true

                                    // Use the ViewModel function
                                    groupViewModel.checkMemberHasPaymentsOrSplits(
                                        member.userId,
                                        groupId
                                    ) { hasPayments ->
                                        isCheckingMember = false
                                        val title = "Remove Member"
                                        val message = if (hasPayments) {
                                            "This member has payments or splits in the group. They will be archived rather than removed completely. They won't appear in future split options but their payment history will be preserved."
                                        } else {
                                            "This member will be completely removed from the group. Are you sure you want to continue?"
                                        }
                                        onShowRemoveDialog(member.id, title, message)
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Remove,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                enabled = !isCheckingMember
                            )
                        }
                    }
                }
            }
        )
    }
    // Show invite bottom sheet when a provisional user is selected
    selectedProvisionalUser?.let { user ->
        InviteProvisionalUserDialog(
            user = user,
            groupViewModel = groupViewModel,
            onDismiss = { selectedProvisionalUser = null }
        )
    }
}

/**
 * List of archived members in the group with improved menu functionality
 */
@Composable
fun ArchivedMembersList(
    archivedMembers: List<GroupMemberEntity>,
    users: List<UserEntity>,
    usernames: Map<Int, String>,
    userId: Int?,
    groupViewModel: GroupViewModel
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(8.dp))

        archivedMembers.forEach { member ->
            // Get user details
            val user = users.find { it.userId == member.userId }
            val isProvisional = user?.isProvisional == true
            val memberUsername = when {
                member.userId == userId -> "Me"
                usernames.containsKey(member.userId) -> usernames[member.userId] ?: "Unknown"
                user != null -> user.username
                else -> "User ${member.userId}"
            }

            // State for member menu
            var showMemberMenu by remember { mutableStateOf(false) }

            UserListItem(
                userId = member.userId,
                username = memberUsername,
                isProvisional = isProvisional,
                isCurrentUser = member.userId == userId,
                canInvite = false, // Don't show invite for archived members
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)  // Reduced spacing
                    ) {
                        // Show archived chip with smaller text
                        AssistChip(
                            onClick = { },
                            label = { Text("Archived", fontSize = 10.sp) },  // Smaller text
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier.height(24.dp)  // Smaller height
                        )

                        // Three-dot menu for archived members with fixed size
                        Box(modifier = Modifier.size(28.dp)) {  // Fixed size container
                            IconButton(
                                onClick = { showMemberMenu = true },
                                modifier = Modifier.size(28.dp)  // Smaller button
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Member Options",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)  // Smaller icon
                                )
                            }

                            DropdownMenu(
                                expanded = showMemberMenu,
                                onDismissRequest = { showMemberMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Restore Member") },
                                    onClick = {
                                        showMemberMenu = false
                                        groupViewModel.restoreArchivedMember(member.id)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Undo,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

/**
 * Section for configuring default split settings
 */
@Composable
fun SplitSettingsSection(
    groupId: Int,
    groupViewModel: GroupViewModel,
    defaultSplits: List<GroupDefaultSplitEntity>,
    onResetSplits: () -> Unit
) {
    val groupDetailsState by groupViewModel.groupDetailsState.collectAsState()
    val scope = rememberCoroutineScope()
    val group = groupDetailsState.group
    val members = groupDetailsState.groupMembers
    val usernames = groupDetailsState.usernames
    val users = groupDetailsState.users
    val context = LocalContext.current

    // State for modal dialogs
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // State for selected split mode
    var selectedSplitMode by remember(group) { mutableStateOf(group?.defaultSplitMode ?: "equally") }

    // State for expanded section - make sure it defaults to true when in percentage mode
    var expandedSplitSection by remember(selectedSplitMode) {
        mutableStateOf(selectedSplitMode == "percentage")
    }

    // State for percentage splits
    val memberPercentages = remember { mutableStateMapOf<Int, String>() }

    // Force expand the section whenever split mode is percentage
    LaunchedEffect(selectedSplitMode) {
        if (selectedSplitMode == "percentage") {
            expandedSplitSection = true
        }
    }

    // Delete all splits function
    fun deleteAllSplits() {
        scope.launch {
            // First delete all splits from the database
            groupViewModel.deleteAllGroupDefaultSplits(groupId)
            showDeleteConfirmation = false

            // Show confirmation toast
            Toast.makeText(context, "Splits reset to equal values", Toast.LENGTH_SHORT).show()

            // Clear all existing percentages in the UI
            memberPercentages.clear()

            // Get only active members
            val activeMembers = members.filter { it.removedAt == null }

            // Reset percentages to equal values ONLY for active members
            val equalPercentage = if (activeMembers.isNotEmpty()) (100.0 / activeMembers.size) else 0.0
            activeMembers.forEach { member ->
                memberPercentages[member.userId] = String.format("%.1f", equalPercentage)
            }
        }
    }

    // Initialize percentages when members and splits are loaded
    LaunchedEffect(members, defaultSplits) {
        memberPercentages.clear()

        if (defaultSplits.isNotEmpty()) {
            defaultSplits.forEach { split ->
                memberPercentages[split.userId] = split.percentage?.toString() ?: "0.0"
            }
        } else {
            val activeMembers = members.filter { it.removedAt == null }
            val equalPercentage = if (activeMembers.isNotEmpty()) (100.0 / activeMembers.size) else 0.0
            activeMembers.forEach { member ->
                memberPercentages[member.userId] = String.format("%.1f", equalPercentage)
            }
        }
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

            // If percentage mode, save all percentages
            if (selectedSplitMode == "percentage") {
                val currentTime = DateUtils.getCurrentTimestamp()

                // Only use active members for splits
                val activeMembers = members.filter { it.removedAt == null }
                val splits = activeMembers.mapNotNull { member ->
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
            } else if (defaultSplits.isNotEmpty()) {
                groupViewModel.deleteAllGroupDefaultSplits(groupId)
            }

            // Don't collapse if we're in percentage mode
            if (selectedSplitMode != "percentage") {
                expandedSplitSection = false
            }
        }
    }

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

            IconButton(onClick = {
                expandedSplitSection = !expandedSplitSection
            }) {
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
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null
                                )
                            }
                        } else null,
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    FilterChip(
                        selected = selectedSplitMode == "percentage",
                        onClick = {
                            selectedSplitMode = "percentage"
                            // Force the section to expand
                            expandedSplitSection = true
                            saveSplitSettings()
                        },
                        label = { Text("Percentage") },
                        leadingIcon = if (selectedSplitMode == "percentage") {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null
                                )
                            }
                        } else null
                    )
                }
            }
        }

        // Add a spacer that's visible only when in percentage mode but not expanded
        if (selectedSplitMode == "percentage" && !expandedSplitSection) {
            // This is a fallback case that shouldn't happen but helps debug
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap percentage chip again to view settings",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Expanded split percentage section - modify to always show when in percentage mode
        AnimatedVisibility(
            visible = expandedSplitSection && selectedSplitMode == "percentage",
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            PercentageSplitSection(
                members = members,
                usernames = usernames,
                users = users,
                memberPercentages = memberPercentages,
                defaultSplits = defaultSplits,
                selectedSplitMode = selectedSplitMode,
                onSaveSettings = { saveSplitSettings() },
                onResetClick = { showDeleteConfirmation = true }
            )
        }
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
}

/**
 * Section for configuring percentage splits
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun PercentageSplitSection(
    members: List<GroupMemberEntity>,
    usernames: Map<Int, String>,
    users: List<UserEntity>,
    memberPercentages: MutableMap<Int, String>,
    defaultSplits: List<GroupDefaultSplitEntity>,
    selectedSplitMode: String,
    onSaveSettings: () -> Unit,
    onResetClick: () -> Unit
) {
    // Filter to only include active members
    val activeMembers = members.filter { it.removedAt == null }

    // Clear percentages for archived members and only calculate total for active members
    LaunchedEffect(activeMembers) {
        // Remove percentages for any archived members
        val allMemberIds = members.map { it.userId }.toSet()
        val activeMemberIds = activeMembers.map { it.userId }.toSet()
        val archivedMemberIds = allMemberIds - activeMemberIds

        // Remove percentages for archived members
        archivedMemberIds.forEach { memberPercentages.remove(it) }
    }

    // Automatically expand when "percentage" mode is selected
    LaunchedEffect(selectedSplitMode) {
        if (selectedSplitMode == "percentage") {
            // If there are no percentages set yet, initialize with equal values
            if (memberPercentages.isEmpty() && activeMembers.isNotEmpty()) {
                val equalPercentage = 100.0 / activeMembers.size
                activeMembers.forEach { member ->
                    // Format with no decimal if it's a whole number
                    val formattedPercentage = if (equalPercentage % 1 == 0.0) {
                        equalPercentage.toInt().toString()
                    } else {
                        String.format("%.1f", equalPercentage)
                    }
                    memberPercentages[member.userId] = formattedPercentage
                }
            }
        }
    }

    // Calculate total percentage (only from active members)
    val totalPercentage = activeMembers
        .mapNotNull { member -> memberPercentages[member.userId]?.toDoubleOrNull() }
        .sum()

    // Format total percentage to hide decimal if it's a whole number
    val formattedTotalPercentage = if (totalPercentage % 1 == 0.0) {
        totalPercentage.toInt().toString()
    } else {
        String.format("%.1f", totalPercentage)
    }

    // Wrap entire content in a BoxWithConstraints to handle keyboard
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Use Modifier.imePadding() at the Column level instead of individual items
                .imePadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total: $formattedTotalPercentage%",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (totalPercentage == 100.0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
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
                            modifier = Modifier.weight(0.6f)
                        )
                        Text(
                            text = "Percentage",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(0.4f),
                            textAlign = TextAlign.Center
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // Only iterate through active members
                    activeMembers.forEachIndexed { index, member ->
                        val username = when {
                            users.any { it.userId == member.userId } -> users.find { it.userId == member.userId }?.username ?: "Unknown"
                            usernames.containsKey(member.userId) -> usernames[member.userId] ?: "Unknown"
                            else -> "User ${member.userId}"
                        }

                        // Get the raw percentage value
                        val percentValue = memberPercentages[member.userId] ?: "0.0"

                        // Format it for display (but keep the raw value in the state)
                        val displayPercentage = percentValue.toDoubleOrNull()?.let { value ->
                            if (value % 1 == 0.0) {
                                value.toInt().toString()
                            } else {
                                String.format("%.1f", value)
                            }
                        } ?: percentValue

                        // Modified to avoid excessive spacing
                        MemberPercentageRow(
                            username = username,
                            percentage = displayPercentage,
                            onPercentageChange = { value ->
                                if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                                    memberPercentages[member.userId] = value
                                }
                            }
                        )

                        if (index < activeMembers.size - 1) {
                            Divider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(
                                    alpha = 0.3f
                                )
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
                        // First clear all existing percentages (including for archived members)
                        memberPercentages.clear()

                        // Then set equal percentage ONLY for active members
                        val equalPercentage = 100.0 / activeMembers.size
                        activeMembers.forEach { member ->
                            // Format with no decimal if it's a whole number
                            val formattedPercentage = if (equalPercentage % 1 == 0.0) {
                                equalPercentage.toInt().toString()
                            } else {
                                String.format("%.1f", equalPercentage)
                            }
                            memberPercentages[member.userId] = formattedPercentage
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Equal Split")
                }

                Button(
                    onClick = onSaveSettings,
                    modifier = Modifier.weight(1f),
                    enabled = totalPercentage == 100.0
                ) {
                    Text("Save Changes")
                }
            }
        }
    }
}

/**
 * Row for a single member's percentage input
 * This is separated to help with keyboard focusing
 */
@Composable
fun MemberPercentageRow(
    username: String,
    percentage: String,
    onPercentageChange: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Username takes up 60% of the space
        Text(
            text = username,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(0.6f)
                .padding(end = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Text field takes up 40% of the space
        OutlinedTextField(
            value = percentage,
            onValueChange = onPercentageChange,
            modifier = Modifier
                .weight(0.4f)
                .heightIn(min = 56.dp),  // Use heightIn instead of fixed height
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    // Clear focus to dismiss keyboard
                    focusManager.clearFocus()
                }
            ),
            singleLine = true,
            suffix = { Text("%") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(
                    alpha = 0.5f
                )
            )
        )
    }
}

/**
 * Section for dangerous group actions (archive, leave)
 */
@Composable
fun DangerZoneSection(
    isArchived: Boolean,
    onArchiveClick: () -> Unit,
    onUnarchiveClick: () -> Unit,
    onLeaveClick: () -> Unit
) {
    Spacer(modifier = Modifier.height(16.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Archive/Unarchive Button
        OutlinedButton(
            onClick = {
                if (isArchived) {
                    onUnarchiveClick()
                } else {
                    onArchiveClick()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        ) {
            Icon(
                imageVector = if (isArchived)
                    Icons.Default.Unarchive
                else
                    Icons.Default.Archive,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                if (isArchived) "Unarchive Group"
                else "Archive Group"
            )
        }

        // Leave Group Button
        Button(
            onClick = onLeaveClick,
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