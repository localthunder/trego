package com.splitter.splittr.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.splitter.splittr.MyApplication
import com.splitter.splittr.ui.components.GlobalTopAppBar
import com.splitter.splittr.data.model.Group
import com.splitter.splittr.ui.viewmodels.GroupViewModel
import com.splitter.splittr.utils.getUserIdFromPreferences
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AddGroupScreen(navController: NavController, context: Context) {
    val myApplication = context.applicationContext as MyApplication
    val groupViewModel: GroupViewModel = viewModel(factory = myApplication.viewModelFactory)

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val groupCreationStatus by groupViewModel.groupCreationStatus.observeAsState()

    LaunchedEffect(groupCreationStatus) {
        groupCreationStatus?.let { result ->
            loading = false
            when {
                result.isSuccess -> {
                    val (group, member) = result.getOrNull()!!
                    Toast.makeText(context, "Group created and joined successfully", Toast.LENGTH_SHORT).show()
                    // Navigate to the new group's details screen
                    navController.navigate("groupDetails/${group.id}") {
                        // Clear back stack up to user groups screen
                        popUpTo("addGroup") { inclusive = true }
                    }
                }
                result.isFailure -> {
                    error = result.exceptionOrNull()?.message ?: "An unknown error occurred"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            GlobalTopAppBar(title = { Text("Add New Group") })
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group Name") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
                TextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Group Description") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )

                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                Toast.makeText(context, "Group name cannot be empty", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            loading = true
                            val userId = getUserIdFromPreferences(context) ?: return@Button
                            val newGroup = Group(
                                id = 0, // This will be replaced by Room
                                name = name,
                                description = description,
                                groupImg = null,
                                createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Date()),
                                updatedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Date()),
                                inviteLink = null
                            )
                            groupViewModel.createGroup(newGroup, userId)
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp)
                    ) {
                        Text("Create Group")
                    }
                }

                error?.let {
                    Text("Error: $it", color = MaterialTheme.colors.error)
                }
            }
        }
    )
}