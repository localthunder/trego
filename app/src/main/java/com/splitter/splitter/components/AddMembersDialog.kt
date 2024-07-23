package com.splitter.splitter.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AddMembersDialog(
    onDismissRequest: () -> Unit,
    onCreateInviteLinkClick: () -> Unit,
    onInviteMembersClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Add Members") },
        text = {
            Column {
                Button(onClick = onCreateInviteLinkClick) {
                    Text("Create Invite Link")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onInviteMembersClick) {
                    Text("Invite Members")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    )
}