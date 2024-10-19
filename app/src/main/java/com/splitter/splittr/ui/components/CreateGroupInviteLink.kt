package com.splitter.splitter.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.splitter.splittr.MyApplication
import com.splitter.splittr.ui.viewmodels.GroupViewModel
import kotlinx.coroutines.launch

@Composable
fun CreateGroupInviteLink(navController: NavController, context: Context, groupId: Int) {
    val myApplication = context.applicationContext as MyApplication
    val groupViewModel: GroupViewModel = viewModel(factory = myApplication.viewModelFactory)
    var inviteLink by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()


    Column(
        modifier = Modifier
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.padding(vertical = 16.dp))
        } else {
            Button(
                onClick = {
                    scope.launch {
                        groupViewModel.getGroupInviteLink(groupId)
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 16.dp)
            ) {
                Text("Create Invite Link")
            }

            inviteLink?.let {
                Text("Invite Link: $it", style = MaterialTheme.typography.h6, modifier = Modifier.padding(vertical = 8.dp))
            }

            error?.let {
                Text("Error: $it", color = MaterialTheme.colors.error)
            }
        }
    }
}
