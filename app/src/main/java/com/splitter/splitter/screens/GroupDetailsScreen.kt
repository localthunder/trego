package com.splitter.splitter.screens

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.rememberImagePainter
import com.splitter.splitter.model.Group
import com.splitter.splitter.model.GroupMember
import com.splitter.splitter.network.ApiService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@Composable
fun GroupDetailsScreen(navController: NavController, groupId: Int, apiService: ApiService) {
    var group by remember { mutableStateOf<Group?>(null) }
    var groupMembers by remember { mutableStateOf<List<GroupMember>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(groupId) {
        apiService.getGroupById(groupId).enqueue(object : Callback<Group> {
            override fun onResponse(call: Call<Group>, response: Response<Group>) {
                if (response.isSuccessful) {
                    group = response.body()
                } else {
                    error = response.message()
                }
            }

            override fun onFailure(call: Call<Group>, t: Throwable) {
                error = t.message
            }
        })

        apiService.getMembersOfGroup(groupId).enqueue(object : Callback<List<GroupMember>> {
            override fun onResponse(call: Call<List<GroupMember>>, response: Response<List<GroupMember>>) {
                if (response.isSuccessful) {
                    groupMembers = response.body() ?: emptyList()
                } else {
                    error = response.message()
                }
                loading = false
            }

            override fun onFailure(call: Call<List<GroupMember>>, t: Throwable) {
                error = t.message
                loading = false
            }
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(group?.name ?: "Group Details") })
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
                    Text("Error: $error", color = MaterialTheme.colors.error)
                } else {
                    group?.let { groupDetails ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = rememberImagePainter(groupDetails.groupImg),
                                contentDescription = null,
                                modifier = Modifier.size(80.dp).padding(bottom = 16.dp)
                            )
                            Text(groupDetails.name, fontSize = 24.sp, color = Color.Black)
                            groupDetails.description?.let {
                                Text(it, fontSize = 16.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 16.dp))
                            }
                            Text("Members", fontSize = 20.sp, color = Color.Black, modifier = Modifier.padding(vertical = 8.dp))
                            LazyColumn {
                                items(groupMembers) { member ->
                                    GroupMemberItem(member)
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun GroupMemberItem(groupMember: GroupMember) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("User ID: ${groupMember.userId}", fontSize = 18.sp, color = Color.Black)
                Text("Joined at: ${groupMember.createdAt}", fontSize = 14.sp, color = Color.Gray)
                groupMember.updatedAt?.let {
                    Text("Updated at: $it", fontSize = 14.sp, color = Color.Gray)
                }
            }
        }
    }
}
