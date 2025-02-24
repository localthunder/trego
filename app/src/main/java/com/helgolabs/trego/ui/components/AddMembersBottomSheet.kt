package com.helgolabs.trego.ui.components

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.entities.GroupMemberEntity
import com.helgolabs.trego.ui.viewmodels.GroupViewModel
import com.helgolabs.trego.ui.viewmodels.UserViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    // Add new state for provisional users and email dialog
    var selectedProvisionalUser by remember { mutableStateOf<GroupMemberEntity?>(null) }
    var showEmailDialog by remember { mutableStateOf(false) }
    var editableEmail by remember { mutableStateOf("") }

    val groupMembers by groupViewModel.groupDetailsState.collectAsState()
    val users by userViewModel.users.collectAsState()

    // Filter provisional users
    val provisionalMembers = groupDetailsState.groupMembers.mapNotNull { member ->
        val user = groupDetailsState.users.find { it.userId == member.userId }
        if (user?.isProvisional == true) {
            member to user
        } else null
    }

    LaunchedEffect(Unit) {
        groupViewModel.fetchUsernamesForInvitation(groupId)
        groupViewModel.loadGroupMembersWithUsers(groupId)
    }

    LaunchedEffect(addMemberResult) {
        addMemberResult?.let { result ->
            result.onSuccess { member ->
                Toast.makeText(context, "${usernames[member.userId]} added to group", Toast.LENGTH_SHORT).show()
            }.onFailure { exception ->
                Toast.makeText(context, "Failed to add member: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
            groupViewModel.resetAddMemberResult()
        }
    }

    // Observe generated link and share when available
    LaunchedEffect(groupDetailsState.generatedInviteLink) {
        groupDetailsState.generatedInviteLink?.let { link ->
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "Join my group on Splittr: $link")
                type = "text/plain"
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share invite"))
        }
    }

    // Show email dialog if needed
    if (showEmailDialog && selectedProvisionalUser != null) {
        AlertDialog(
            onDismissRequest = {
                showEmailDialog = false
                selectedProvisionalUser = null
                editableEmail = ""
            },
            title = { Text("Update Invite Email") },
            text = {
                Column {
                    Text("Enter email address for invitation:")
                    OutlinedTextField(
                        value = editableEmail,
                        onValueChange = { editableEmail = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        selectedProvisionalUser?.let { member ->
                            groupViewModel.generateProvisionalUserInviteLink(
                                provisionalUserId = member.userId,
                            )
                        }
                    }
                    showEmailDialog = false
                    selectedProvisionalUser = null
                }) {
                    Text("Send Invite")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showEmailDialog = false
                    selectedProvisionalUser = null
                    editableEmail = ""
                }) {
                    Text("Cancel")
                }
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
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Join my group: $link")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share invite link"))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create Invite Link")
            }

            if (isAddingNew) {
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
                                    groupViewModel.generateProvisionalUserInviteLink(userId)
                                    onDismissRequest()
                                }.onFailure { error ->
                                    Log.e("AddMembersBottomSheet", "Failed to create user and add to group", error)
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

                // Add Provisional Users Section
                if (provisionalMembers.isNotEmpty()) {
                    Text(
                        "Provisional Members",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )

                    LazyColumn {
                        items(provisionalMembers) { (member, user) ->
                            ListItem(
                                headlineContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column {
                                            Text(user.username)
                                            AssistChip(
                                                onClick = { },
                                                label = { Text("Provisional") },
                                                colors = AssistChipDefaults.assistChipColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                                )
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                selectedProvisionalUser = member
                                                editableEmail = user.invitationEmail ?: ""
                                                showEmailDialog = true
                                            }
                                        ) {
                                            Text("Invite")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                } else {
                    LazyColumn {
                        val sortedUsernames = usernames.values.sorted()
                        items(sortedUsernames) { username ->
                            val userId = usernames.filterValues { it == username }.keys.first()
                            ListItem(
                                headlineContent = { Text(username) },
                                modifier = Modifier.clickable {
                                    groupViewModel.addMemberToGroup(groupId, userId)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}