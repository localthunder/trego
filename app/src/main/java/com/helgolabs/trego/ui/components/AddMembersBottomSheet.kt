package com.helgolabs.trego.ui.components

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.entities.GroupMemberEntity
import com.helgolabs.trego.data.local.entities.UserEntity
import com.helgolabs.trego.ui.viewmodels.GroupViewModel
import com.helgolabs.trego.ui.viewmodels.UserViewModel
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMembersBottomSheet(
    groupId: Int,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val myApplication = context.applicationContext as MyApplication
    val groupViewModel: GroupViewModel = viewModel(factory = myApplication.viewModelFactory)
    val userViewModel: UserViewModel = viewModel(factory = myApplication.viewModelFactory)

    var isAddingNew by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var inviteLater by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val usernames by groupViewModel.usernames.observeAsState(emptyMap())
    val loading by groupViewModel.loading.collectAsState(false)
    val error by groupViewModel.error.collectAsState(null)
    val addMemberResult by groupViewModel.addMemberResult.observeAsState()
    val groupDetailsState by groupViewModel.groupDetailsState.collectAsState()

    var selectedProvisionalUser by remember { mutableStateOf<UserEntity?>(null) }
    var showEmailDialog by remember { mutableStateOf(false) }
    var editableEmail by remember { mutableStateOf("") }

    val groupMembers = groupDetailsState.groupMembers
    val currentUserId = getUserIdFromPreferences(context)

    var showArchivedMembers by remember { mutableStateOf(false) }

    // Get archived members separately
    val archivedMembers = remember(groupDetailsState.groupMembers) {
        groupDetailsState.groupMembers.filter { it.removedAt != null }
    }

    // Combine members and available users (excluding archived)
    val combinedUserList = remember(groupDetailsState.users, groupMembers, usernames) {
        // First get current active members
        val activeMemberUserIds = groupMembers
            .filter { it.removedAt == null }
            .map { it.userId }
            .toSet()

        val activeMembers = groupDetailsState.users
            .filter { it.userId in activeMemberUserIds }
            .map { it to true } // true = is a member

        // Create a full map of users for lookup
        val userMap = groupDetailsState.users.associateBy { it.userId }

        // Get archived member user IDs
        val archivedMemberUserIds = archivedMembers.map { it.userId }.toSet()

        // Then get available users from usernames (excluding active and archived members)
        val availableUsers = usernames.entries
            .filter { (userId, _) ->
                userId !in activeMemberUserIds &&
                        userId !in archivedMemberUserIds &&
                        userId != 0
            }
            .map { (userId, username) ->
                val foundUser = userMap[userId]

                if (foundUser != null) {
                    foundUser to false // false = not a member
                } else {
                    val isProvisionalUser = groupDetailsState.users
                        .any { it.userId == userId && it.isProvisional }

                    UserEntity(
                        userId = userId,
                        username = username,
                        isProvisional = isProvisionalUser,
                        serverId = null,
                        createdAt = DateUtils.getCurrentTimestamp(),
                        updatedAt = DateUtils.getCurrentTimestamp()
                    ) to false
                }
            }

        activeMembers + availableUsers
    }

    LaunchedEffect(Unit) {
        groupViewModel.fetchUsernamesForInvitation(groupId)
        groupViewModel.loadGroupMembersWithUsers(groupId)
    }

    LaunchedEffect(addMemberResult) {
        addMemberResult?.let { result ->
            result.onSuccess { member ->
                Toast.makeText(context, "${usernames[member.userId]} added to group", Toast.LENGTH_SHORT).show()
                // Refresh the user list after adding a member
                groupViewModel.fetchUsernamesForInvitation(groupId)
                groupViewModel.loadGroupMembersWithUsers(groupId)
            }.onFailure { exception ->
                Toast.makeText(context, "Failed to add member: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
            groupViewModel.resetAddMemberResult()
        }
    }

    // Show email dialog if needed
    if (showEmailDialog && selectedProvisionalUser != null) {
        InviteProvisionalUserDialog(
            user = selectedProvisionalUser!!,
            groupViewModel = groupViewModel,
            onDismiss = {
                showEmailDialog = false
                selectedProvisionalUser = null
                editableEmail = ""
            }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text("Add Members", style = MaterialTheme.typography.headlineMedium)

            Button(
                onClick = {
                    scope.launch {
                        val inviteLink = groupViewModel.getGroupInviteLink(groupId)
                        inviteLink.onSuccess { link ->
                            if (link.isNotBlank()) {
                                // Create a share message
                                val playStoreUrl = "https://play.google.com/store/apps/details?id=com.helgolabs.trego"
                                val shareText = """
                        Join my group on Trego!
                        
                        👉 If you have the app: $link
                        
                        📱 Don't have the app? Get it here: $playStoreUrl
                    """.trimIndent()

                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share invite link"))
                            } else {
                                Toast.makeText(
                                    context, "Couldn't generate invite link", Toast.LENGTH_SHORT
                                ).show()
                            }
                        }.onFailure { error ->
                            Toast.makeText(
                                context, "Failed to generate invite link: ${error.message}", Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create Invite Link")
            }

            if (isAddingNew) {
                // New user form
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = inviteLater,
                        onCheckedChange = { inviteLater = it }
                    )
                    Text("Invite Later", modifier = Modifier.padding(start = 8.dp))
                }

                AnimatedVisibility(!inviteLater) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                val result = userViewModel.createProvisionalUser(name, email, inviteLater, groupId)
                                result.onSuccess { userId ->
                                    Log.d("AddMembersBottomSheet", "User created and added to group successfully")
                                    if (!inviteLater && email.isNotBlank()) {
                                        groupViewModel.generateProvisionalUserInviteLink(userId)
                                    }
                                    // Reset form fields
                                    name = ""
                                    email = ""
                                    inviteLater = false
                                    isAddingNew = false

                                    // Refresh members list
                                    groupViewModel.loadGroupMembersWithUsers(groupId)
                                }.onFailure { error ->
                                    Log.e("AddMembersBottomSheet", "Failed to create user and add to group", error)
                                    Toast.makeText(context, "Failed to add user: ${error.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = name.isNotBlank() && (inviteLater || email.isNotBlank())
                    ) {
                        Text("Add and Invite User")
                    }
                    OutlinedButton(onClick = { isAddingNew = false }) {
                        Text("Cancel")
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { isAddingNew = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Someone New")
                }

                // Use our new user list component
                if (loading) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                } else if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                } else {
                    UserList(
                        groupMembers = combinedUserList,
                        currentUserId = currentUserId,
                        onInviteClick = { userId ->
                            val user = combinedUserList.find { it.first.userId == userId }?.first
                            if (user != null && user.isProvisional) {
                                selectedProvisionalUser = user
                                editableEmail = user.invitationEmail ?: ""
                                showEmailDialog = true
                            }
                        },
                        onAddClick = { userId ->
                            // Add debugging
                            Log.d("AddMembersBottomSheet", "Attempting to add user with ID: $userId")

                            // Verify user exists
                            val userExists = usernames.containsKey(userId)
                            if (!userExists) {
                                Log.e("AddMembersBottomSheet", "User with ID $userId not found in usernames map: $usernames")
                                Toast.makeText(context, "Can't add user: Not found in available users", Toast.LENGTH_SHORT).show()
                                return@UserList
                            }

                            groupViewModel.addMemberToGroup(groupId, userId)
                            groupViewModel.loadGroupMembersWithUsers(groupId)
                        }
                    )
                    // Archived members section
                    if (archivedMembers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = { showArchivedMembers = !showArchivedMembers },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    if (showArchivedMembers) "Hide Archived Members"
                                    else "Show Archived Members (${archivedMembers.size})"
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    if (showArchivedMembers) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null
                                )
                            }
                        }

                        AnimatedVisibility(visible = showArchivedMembers) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Archived Members",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                archivedMembers.forEach { member ->
                                    val user = groupDetailsState.users.find { it.userId == member.userId }
                                    val username = user?.username ?: "Unknown User"

                                    ListItem(
                                        headlineContent = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(username)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                AssistChip(
                                                    onClick = { },
                                                    label = { Text("Archived", fontSize = 10.sp) },
                                                    colors = AssistChipDefaults.assistChipColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                                    ),
                                                    modifier = Modifier.height(20.dp)
                                                )
                                            }
                                        },
                                        trailingContent = {
                                            IconButton(
                                                onClick = {
                                                    groupViewModel.restoreArchivedMember(member.id)
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Filled.Restore,
                                                    contentDescription = "Restore Member",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InviteProvisionalUserDialog(
    user: UserEntity,
    groupViewModel: GroupViewModel,
    onDismiss: () -> Unit
) {
    var editableEmail by remember { mutableStateOf(user.invitationEmail ?: "") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State for showing invitation progress
    var isInviting by remember { mutableStateOf(false) }

    // Monitor generated link
    val groupDetailsState by groupViewModel.groupDetailsState.collectAsState()
    val generatedInviteLink = groupDetailsState.generatedInviteLink

    // Check for generated link
    LaunchedEffect(generatedInviteLink) {
        if (generatedInviteLink != null && isInviting) {
            // Create share intent with the invite link
            val shareText = """
                Hi ${user.username},
                
                You've been invited to join a group on Trego!
                
                Click this link to join:
                $generatedInviteLink
                
                If you don't have the Trego app yet, you can download it here:
                https://play.google.com/store/apps/details?id=com.helgolabs.trego
            """.trimIndent()

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_SUBJECT, "Invitation to join Trego")
                if (editableEmail.isNotBlank()) {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(editableEmail))
                }
                type = "text/plain"
            }

            context.startActivity(Intent.createChooser(shareIntent, "Send invite via"))

            // Clear the generated link after using it
            groupViewModel.clearGeneratedInviteLink()

            // Show success message
            Toast.makeText(
                context,
                "Invite link generated for ${user.username}",
                Toast.LENGTH_SHORT
            ).show()

            // Dismiss the dialog
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Invite to ${user.username}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Enter the email address where you'd like to send the invitation:")

                OutlinedTextField(
                    value = editableEmail,
                    onValueChange = { editableEmail = it },
                    label = { Text("Email (optional)") },
                    placeholder = { Text("user@example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    )
                )

                if (isInviting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Text(
                    text = "They'll receive a link to join Trego and will be merged with the provisional user ${user.username} and added to this group.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        isInviting = true

                        // Generate the invite link
                        groupViewModel.generateProvisionalUserInviteLink(
                            provisionalUserId = user.userId,
                            emailOverride = if (editableEmail.isNotBlank()) editableEmail else null
                        )
                    }
                },
                enabled = !isInviting && (editableEmail.isBlank() ||
                        android.util.Patterns.EMAIL_ADDRESS.matcher(editableEmail).matches())
            ) {
                Text(if (isInviting) "Generating..." else "Send Invite")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}