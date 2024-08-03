package com.splitter.splitter.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.splitter.splitter.components.GlobalTopAppBar
import com.splitter.splitter.model.Group
import com.splitter.splitter.model.GroupMember
import com.splitter.splitter.data.network.ApiService
import com.splitter.splitter.data.network.RetrofitClient
import com.splitter.splitter.utils.getUserIdFromPreferences
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AddGroupScreen(navController: NavController, context: Context) {
    val apiService = RetrofitClient.getInstance(context).create(ApiService::class.java)
    val userId = getUserIdFromPreferences(context)
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

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
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                            val newGroup = Group(
                                id = 0,
                                name = name,
                                description = description,
                                groupImg = null,
                                createdAt = dateFormat.format(Date()),
                                updatedAt = dateFormat.format(Date())
                            )

                            apiService.createGroup(newGroup).enqueue(object : Callback<Group> {
                                override fun onResponse(call: Call<Group>, response: Response<Group>) {
                                    if (response.isSuccessful) {
                                        val createdGroup = response.body()!!
                                        val groupMember = GroupMember(
                                            id = 0,
                                            userId = userId ?: return,
                                            groupId = createdGroup.id,
                                            createdAt = dateFormat.format(Date()),
                                            updatedAt = dateFormat.format(Date()),
                                            removedAt = null
                                        )

                                        // Add the creator as a member of the group
                                        apiService.addMemberToGroup(createdGroup.id, groupMember).enqueue(object : Callback<GroupMember> {
                                            override fun onResponse(call: Call<GroupMember>, response: Response<GroupMember>) {
                                                loading = false
                                                if (response.isSuccessful) {
                                                    navController.popBackStack()
                                                } else {
                                                    error = response.message()
                                                }
                                            }

                                            override fun onFailure(call: Call<GroupMember>, t: Throwable) {
                                                loading = false
                                                error = t.message
                                            }
                                        })
                                    } else {
                                        loading = false
                                        error = response.message()
                                    }
                                }

                                override fun onFailure(call: Call<Group>, t: Throwable) {
                                    loading = false
                                    error = t.message
                                }
                            })
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
