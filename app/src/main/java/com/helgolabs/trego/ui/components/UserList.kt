package com.helgolabs.trego.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.helgolabs.trego.data.local.entities.UserEntity

@Composable
fun UserListItem(
    userId: Int,
    username: String,
    initial: String? = null,
    isProvisional: Boolean = false,
    isCurrentUser: Boolean = false,
    canInvite: Boolean = false,
    onInviteClick: ((Int) -> Unit)? = null,
    onRemoveClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val displayName = if (isCurrentUser) "Me" else username
    val userInitial = initial ?: displayName.firstOrNull()?.toString()?.uppercase() ?: "?"

    // State for showing provisional info dialog
    var showProvisionalInfoDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = userInitial,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        // Username and provisional chip in a row
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyLarge
            )

            if (isProvisional) {
                Spacer(modifier = Modifier.width(8.dp))
                // Clickable chip with info icon
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier
                        .height(20.dp)
                        .clickable { showProvisionalInfoDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Provisional",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Provisional user info",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        // Action buttons - This section has been reorganized for better layout
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Invite button for provisional users
            if (isProvisional && canInvite && onInviteClick != null) {
                Button(
                    onClick = { onInviteClick(userId) },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                ) {
                    Text("Invite", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Remove button (not shown for current user)
            if (!isCurrentUser && onRemoveClick != null) {
                IconButton(
                    onClick = onRemoveClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Remove,
                        contentDescription = "Remove Member",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Always show custom trailing content if provided
            if (trailingContent != null) {
                trailingContent()
            }
        }
    }

    // Provisional user info dialog
    if (showProvisionalInfoDialog) {
        AlertDialog(
            onDismissRequest = { showProvisionalInfoDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Provisional Users") },
            text = {
                Text(
                    "A provisional user is a placeholder created for someone who hasn't signed up for Trego yet.\n\n" +
                            "When you invite them using the 'Invite' button, they'll receive an invitation to join. " +
                            "Once they sign up, their provisional account will automatically merge with their real account, " +
                            "including all expenses and splits already assigned to them."
                )
            },
            confirmButton = {
                TextButton(onClick = { showProvisionalInfoDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

// Example of a composable that shows a list of users with sections
@Composable
fun UserList(
    groupMembers: List<Pair<UserEntity, Boolean>>, // Pair of (user, isMember)
    currentUserId: Int?,
    onInviteClick: (Int) -> Unit,
    onAddClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        // Group members section
        val currentMembers = groupMembers.filter { it.second }

        if (currentMembers.isNotEmpty()) {
            item {
                Text(
                    "Group Members",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }

            items(currentMembers) { (user, _) ->
                UserListItem(
                    userId = user.userId,
                    username = user.username,
                    isProvisional = user.isProvisional,
                    isCurrentUser = user.userId == currentUserId,
                    canInvite = user.isProvisional,
                    onInviteClick = onInviteClick,
                    // If we need to add remove functionality here, you would pass the onRemoveClick
                    // onRemoveClick = { /* handle remove */ }
                )
            }
        }

        // Available users section
        val availableUsers = groupMembers.filter { !it.second }

        if (availableUsers.isNotEmpty()) {
            item {
                Text(
                    "Users from your groups",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }

            items(availableUsers) { (user, _) ->
                UserListItem(
                    userId = user.userId,
                    username = user.username,
                    isProvisional = user.isProvisional,
                    isCurrentUser = user.userId == currentUserId,
                    canInvite = user.isProvisional, // Allow invitation for provisional users
                    onInviteClick = onInviteClick, // Pass the invite click handler
                    onClick = { onAddClick(user.userId) }
                )
            }
        }
    }
}