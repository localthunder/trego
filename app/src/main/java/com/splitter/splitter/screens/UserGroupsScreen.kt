package com.splitter.splitter.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.rememberImagePainter
import com.splitter.splitter.components.GlobalFAB
import com.splitter.splitter.components.GlobalTopAppBar
import com.splitter.splitter.model.Group
import com.splitter.splitter.data.network.ApiService
import com.splitter.splitter.data.network.RetrofitClient
import com.splitter.splitter.ui.theme.GlobalTheme
import com.splitter.splitter.utils.getUserIdFromPreferences
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.text.Typography

@Composable
fun UserGroupsScreen(navController: NavController, context: Context) {
    val apiService = RetrofitClient.getInstance(context).create(ApiService::class.java)
    val userId = getUserIdFromPreferences(context)
    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        Log.d("UserGroupsScreen", "LaunchedEffect triggered with userId: $userId")
        if (userId != null && userId > 0) {
            apiService.getGroupsByUserId(userId).enqueue(object : Callback<List<Group>> {
                override fun onResponse(call: Call<List<Group>>, response: Response<List<Group>>) {
                    if (response.isSuccessful) {
                        groups = response.body() ?: emptyList()
                        loading = false
                        Log.d("UserGroupsScreen", "Received groups: $groups")
                    } else {
                        error = response.message()
                        loading = false
                        Log.e("UserGroupsScreen", "Error response: ${response.message()}")
                    }
                }

                override fun onFailure(call: Call<List<Group>>, t: Throwable) {
                    error = t.message
                    loading = false
                    Log.e("UserGroupsScreen", "API call failed: ${t.message}")
                }
            })
        } else {
            error = "Invalid user ID"
            loading = false
            Log.e("UserGroupsScreen", "Invalid user ID: $userId")
        }
    }

    GlobalTheme {
        Scaffold(
            topBar = {
                GlobalTopAppBar(title = { Text("Your Groups", style = MaterialTheme.typography.headlineSmall) })
            },
            floatingActionButton = {
                GlobalFAB(onClick = {
                    navController.navigate("addGroup")
                },
                    icon = {
                        androidx.compose.material3.Icon(
                            Icons.Filled.Add,
                            contentDescription = "Add"
                        )
                    },
                    text = "Add Group"
                )
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
                    } else if (groups.isEmpty()) {
                        Text("You are not part of any groups.")
                    } else {
                        LazyColumn {
                            items(groups) { group ->
                                Log.d("UserGroupsScreen", "Displaying group with id: ${group.id}")
                                GroupItem(context, group.id, navController, group)
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun GroupItem(context: Context, groupId: Int, navController: NavController, group: Group) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { navController.navigate("groupDetails/${groupId}") }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberImagePainter(group.groupImg),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .padding(end = 8.dp)
            )
            Column {
                Text(group.name, fontSize = 20.sp, color = Color.Black)
                group.description?.let {
                    Text(it, fontSize = 16.sp, color = Color.Gray)
                }
            }
        }
    }
}
