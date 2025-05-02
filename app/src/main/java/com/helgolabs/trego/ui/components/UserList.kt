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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val displayName = if (isCurrentUser) "Me" else username
    val userInitial = initial ?: displayName.firstOrNull()?.toString()?.uppercase() ?: "?"

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
                Spacer(modifier = Modifier.width(24.dp))
                // Smaller chip with compact padding
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text(
                        text = "Provisional",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Trailing content - either invite button or custom content
        if (isProvisional && canInvite && onInviteClick != null) {
            Button(
                onClick = { onInviteClick(userId) },
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary

                ),            ) {
                Text("Invite", style = MaterialTheme.typography.labelMedium)
            }
        } else if (trailingContent != null) {
            trailingContent()
        }
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
                    onInviteClick = onInviteClick
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