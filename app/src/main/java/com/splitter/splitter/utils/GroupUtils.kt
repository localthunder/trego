package com.splitter.splitter.utils

import android.content.Context
import android.util.Log
import com.splitter.splitter.model.Group
import com.splitter.splitter.model.GroupMember
import com.splitter.splitter.model.User
import com.splitter.splitter.data.network.ApiService
import com.splitter.splitter.data.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object GroupUtils {
    fun fetchUsernames(apiService: ApiService, userIds: List<Int>, onResult: (Map<Int, String>) -> Unit) {
        val usernames = mutableMapOf<Int, String>()
        Log.d("InviteMembersScreen", "Fetching usernames for userIds: $userIds")

        userIds.forEach { userId ->
            apiService.getUserById(userId).enqueue(object : Callback<User> {
                override fun onResponse(call: Call<User>, response: Response<User>) {
                    if (response.isSuccessful) {
                        response.body()?.let { user ->
                            usernames[userId] = user.username
                            Log.d("InviteMembersScreen", "Fetched username for userId $userId: ${user.username}")
                            onResult(usernames)
                        }
                    } else {
                        Log.e("InviteMembersScreen", "Error fetching username for userId $userId: ${response.message()}")
                    }
                }

                override fun onFailure(call: Call<User>, t: Throwable) {
                    Log.e("InviteMembersScreen", "Failed to fetch username for userId $userId: ${t.message}")
                }
            })
        }
    }
}
