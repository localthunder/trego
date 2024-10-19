package com.splitter.splittr.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.splitter.splittr.MyApplication
import com.splitter.splittr.ui.components.GlobalTopAppBar
import com.splitter.splittr.ui.theme.GlobalTheme
import com.splitter.splittr.ui.viewmodels.GroupViewModel


@Composable
fun InviteMembersScreen(
    navController: NavController,
    groupId: Int
) {
    val context = LocalContext.current
    val myApplication = context.applicationContext as MyApplication
    val groupViewModel: GroupViewModel = viewModel(factory = myApplication.viewModelFactory)

    val usernames by groupViewModel.usernames.observeAsState(emptyMap())
    val loading by groupViewModel.loading.collectAsState(true)
    val error by groupViewModel.error.collectAsState(null)

    LaunchedEffect(Unit) {
        groupViewModel.fetchUsernamesForInvitation(groupId)
    }

    GlobalTheme {
        Scaffold(
            topBar = {
                GlobalTopAppBar(title = { Text("Invite Members") })
            },
            content = { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (loading) {
                        CircularProgressIndicator()
                    } else if (error != null) {
                        Text("Error: $error", color = MaterialTheme.colorScheme.error)
                    } else {
                        LazyColumn {
                            val sortedUsernames = usernames.values.sorted()
                            items(sortedUsernames) { username ->
                                val userId = usernames.filterValues { it == username }.keys.first()
                                Text(
                                    text = username,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                        .clickable {
                                            groupViewModel.addMemberToGroup(groupId, userId)
                                        }
                                )
                            }
                        }
                    }
                }
            }
        )
    }

    // Observe add member result
    val addMemberResult by groupViewModel.addMemberResult.observeAsState()
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
}