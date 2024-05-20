package com.splitter.splitter.utils

import android.content.Context
import com.splitter.splitter.model.Group
import com.splitter.splitter.model.GroupMember
import com.splitter.splitter.network.ApiService
import com.splitter.splitter.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object GroupUtils {

    fun addMemberToGroup(context: Context, groupId: Int, groupMember: GroupMember, callback: (GroupMember?) -> Unit) {
        val apiService = RetrofitClient.getInstance(context).create(ApiService::class.java)
        apiService.addMemberToGroup(groupId, groupMember).enqueue(object : Callback<GroupMember> {
            override fun onResponse(call: Call<GroupMember>, response: Response<GroupMember>) {
                if (response.isSuccessful) {
                    callback(response.body())
                } else {
                    callback(null)
                }
            }

            override fun onFailure(call: Call<GroupMember>, t: Throwable) {
                callback(null)
            }
        })
    }

    fun getMembersOfGroup(context: Context, groupId: Int, callback: (List<GroupMember>?) -> Unit) {
        val apiService = RetrofitClient.getInstance(context).create(ApiService::class.java)
        apiService.getMembersOfGroup(groupId).enqueue(object : Callback<List<GroupMember>> {
            override fun onResponse(call: Call<List<GroupMember>>, response: Response<List<GroupMember>>) {
                if (response.isSuccessful) {
                    callback(response.body())
                } else {
                    callback(null)
                }
            }

            override fun onFailure(call: Call<List<GroupMember>>, t: Throwable) {
                callback(null)
            }
        })
    }

    fun removeMemberFromGroup(context: Context, groupId: Int, memberId: Int, callback: (GroupMember?) -> Unit) {
        val apiService = RetrofitClient.getInstance(context).create(ApiService::class.java)
        apiService.removeMemberFromGroup(groupId, memberId).enqueue(object : Callback<GroupMember> {
            override fun onResponse(call: Call<GroupMember>, response: Response<GroupMember>) {
                if (response.isSuccessful) {
                    callback(response.body())
                } else {
                    callback(null)
                }
            }

            override fun onFailure(call: Call<GroupMember>, t: Throwable) {
                callback(null)
            }
        })
    }

    fun createGroup(context: Context, group: Group, callback: (Group?) -> Unit) {
        val apiService = RetrofitClient.getInstance(context).create(ApiService::class.java)
        apiService.createGroup(group).enqueue(object : Callback<Group> {
            override fun onResponse(call: Call<Group>, response: Response<Group>) {
                if (response.isSuccessful) {
                    callback(response.body())
                } else {
                    callback(null)
                }
            }

            override fun onFailure(call: Call<Group>, t: Throwable) {
                callback(null)
            }
        })
    }

    fun getGroupById(context: Context, id: Int, callback: (Group?) -> Unit) {
        val apiService = RetrofitClient.getInstance(context).create(ApiService::class.java)
        apiService.getGroupById(id).enqueue(object : Callback<Group> {
            override fun onResponse(call: Call<Group>, response: Response<Group>) {
                if (response.isSuccessful) {
                    callback(response.body())
                } else {
                    callback(null)
                }
            }

            override fun onFailure(call: Call<Group>, t: Throwable) {
                callback(null)
            }
        })
    }

    fun updateGroup(context: Context, id: Int, group: Group, callback: (Group?) -> Unit) {
        val apiService = RetrofitClient.getInstance(context).create(ApiService::class.java)
        apiService.updateGroup(id, group).enqueue(object : Callback<Group> {
            override fun onResponse(call: Call<Group>, response: Response<Group>) {
                if (response.isSuccessful) {
                    callback(response.body())
                } else {
                    callback(null)
                }
            }

            override fun onFailure(call: Call<Group>, t: Throwable) {
                callback(null)
            }
        })
    }
}
