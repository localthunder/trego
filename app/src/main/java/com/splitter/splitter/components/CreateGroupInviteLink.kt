package com.splitter.splitter.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.splitter.splitter.network.ApiService
import com.splitter.splitter.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@Composable
fun CreateGroupInviteLink(navController: NavController, context: Context, groupId: Int) {
    val apiService = RetrofitClient.getInstance(context).create(ApiService::class.java)
    var inviteLink by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

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
                    loading = true
                    apiService.getGroupInviteLink(groupId).enqueue(object : Callback<Map<String, String>> {
                        override fun onResponse(call: Call<Map<String, String>>, response: Response<Map<String, String>>) {
                            loading = false
                            if (response.isSuccessful) {
                                inviteLink = response.body()?.get("inviteLink")
                            } else {
                                error = response.message()
                            }
                        }

                        override fun onFailure(call: Call<Map<String, String>>, t: Throwable) {
                            loading = false
                            error = t.message
                        }
                    })
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
