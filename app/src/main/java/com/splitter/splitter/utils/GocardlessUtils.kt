package com.splitter.splitter.utils

import android.content.Context
import android.util.Log
import com.splitter.splitter.model.Transaction
import com.splitter.splitter.network.ApiService
import com.splitter.splitter.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object GocardlessUtils {

    fun fetchTransactions(context: Context, userId: Int, callback: (List<Transaction>) -> Unit) {
        val apiService = RetrofitClient.getInstance(context).create(ApiService::class.java)
        apiService.getTransactions(userId).enqueue(object : Callback<List<Transaction>> {
            override fun onResponse(call: Call<List<Transaction>>, response: Response<List<Transaction>>) {
                if (response.isSuccessful) {
                    Log.d("fetchTransactions", "Transactions fetched successfully: ${response.body()}")
                    callback(response.body() ?: emptyList())
                } else {
                    Log.e("fetchTransactions", "Failed to fetch transactions: ${response.errorBody()?.string()}")
                    callback(emptyList())
                }
            }

            override fun onFailure(call: Call<List<Transaction>>, t: Throwable) {
                Log.e("fetchTransactions", "Error fetching transactions: ${t.message}")
                callback(emptyList())
            }
        })
    }
}