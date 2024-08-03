package com.splitter.splitter.screens

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.splitter.splitter.components.GlobalTopAppBar
import com.splitter.splitter.model.Group
import com.splitter.splitter.model.GroupMember
import com.splitter.splitter.data.network.ApiService
import com.splitter.splitter.data.network.RetrofitClient
import com.splitter.splitter.ui.theme.GlobalTheme
import com.splitter.splitter.utils.GroupUtils.fetchUsernames
import com.splitter.splitter.utils.getUserIdFromPreferences
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@Composable
fun InviteMembersScreen(navController: NavController, context: Context, groupId: Int) {
    val apiService = RetrofitClient.getInstance(context).create(ApiService::class.java)
    val userId = getUserIdFromPreferences(context)
    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var groupMembers by remember { mutableStateOf<Map<Int, List<GroupMember>>>(emptyMap()) }
    var usernames by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        if (userId != null && userId > 0) {
            apiService.getGroupsByUserId(userId).enqueue(object : Callback<List<Group>> {
                override fun onResponse(call: Call<List<Group>>, response: Response<List<Group>>) {
                    if (response.isSuccessful) {
                        groups = response.body() ?: emptyList()
                        Log.d("InviteMembersScreen", "Fetched groups: $groups")
                        fetchGroupMembers(apiService, groups.map { it.id }) { membersMap ->
                            groupMembers = membersMap
                            val uniqueUserIds = membersMap.values.flatten().map { it.userId }.distinct()
                            fetchUsernames(apiService, uniqueUserIds) { usernamesResult ->
                                usernames = usernamesResult
                                loading = false
                            }
                        }
                    } else {
                        error = response.message()
                        loading = false
                        Log.e("InviteMembersScreen", "Error fetching groups: $error")
                    }
                }

                override fun onFailure(call: Call<List<Group>>, t: Throwable) {
                    error = t.message
                    loading = false
                    Log.e("InviteMembersScreen", "API call failed: ${t.message}")
                }
            })
        } else {
            error = "Invalid user ID"
            loading = false
        }
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
                                            // Handle member click
                                            apiService.addMemberToGroup(groupId, GroupMember(0, groupId, userId, "", "", null)).enqueue(object : Callback<GroupMember> {
                                                override fun onResponse(call: Call<GroupMember>, response: Response<GroupMember>) {
                                                    if (response.isSuccessful) {
                                                        Toast.makeText(context, "$username added to group", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Error: ${response.message()}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }

                                                override fun onFailure(call: Call<GroupMember>, t: Throwable) {
                                                    Toast.makeText(context, "Failed to add member: ${t.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            })
                                        }
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}

private fun fetchGroupMembers(apiService: ApiService, groupIds: List<Int>, onResult: (Map<Int, List<GroupMember>>) -> Unit) {
    val groupMembers = mutableMapOf<Int, List<GroupMember>>()
    var groupsFetched = 0

    groupIds.forEach { groupId ->
        apiService.getMembersOfGroup(groupId).enqueue(object : Callback<List<GroupMember>> {
            override fun onResponse(call: Call<List<GroupMember>>, response: Response<List<GroupMember>>) {
                if (response.isSuccessful) {
                    groupMembers[groupId] = response.body() ?: emptyList()
                    groupsFetched++
                    if (groupsFetched == groupIds.size) {
                        onResult(groupMembers)
                    }
                } else {
                    Log.e("InviteMembersScreen", "Error fetching members for group $groupId: ${response.message()}")
                }
            }

            override fun onFailure(call: Call<List<GroupMember>>, t: Throwable) {
                Log.e("InviteMembersScreen", "Failed to fetch members for group $groupId: ${t.message}")
            }
        })
    }
}
