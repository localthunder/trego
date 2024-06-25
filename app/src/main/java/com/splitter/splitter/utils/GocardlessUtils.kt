package com.splitter.splitter.utils

import android.content.Context
import android.util.Log
import com.splitter.splitter.model.Transaction
import com.splitter.splitter.network.ApiService
import com.splitter.splitter.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object GocardlessUtils {

    fun fetchTransactions(context: Context, userId: Int, callback: (List<Transaction>) -> Unit) {
        val apiService = RetrofitClient.getInstance(context).create(ApiService::class.java)

        // Fetch recent transactions
        val recentTransactionsCall = apiService.getRecentTransactions(userId, LocalDate.now().minusDays(7).format(DateTimeFormatter.ISO_DATE))

        recentTransactionsCall.enqueue(object : Callback<List<Transaction>> {
            override fun onResponse(call: Call<List<Transaction>>, response: Response<List<Transaction>>) {
                if (response.isSuccessful) {
                    val recentTransactions = response.body() ?: emptyList()
                    callback(recentTransactions)

                    // Fetch non-recent transactions and append to the recent transactions
                    val nonRecentTransactionsCall = apiService.getNonRecentTransactions(userId, LocalDate.now().minusDays(7).format(DateTimeFormatter.ISO_DATE))

                    nonRecentTransactionsCall.enqueue(object : Callback<List<Transaction>> {
                        override fun onResponse(call: Call<List<Transaction>>, response: Response<List<Transaction>>) {
                            if (response.isSuccessful) {
                                val nonRecentTransactions = response.body() ?: emptyList()
                                val combinedTransactions = recentTransactions + nonRecentTransactions
                                callback(combinedTransactions)
                            } else {
                                Log.e("fetchTransactions", "Failed to fetch non-recent transactions: ${response.errorBody()?.string()}")
                            }
                        }

                        override fun onFailure(call: Call<List<Transaction>>, t: Throwable) {
                            Log.e("fetchTransactions", "Error fetching non-recent transactions: ${t.message}")
                        }
                    })
                } else {
                    Log.e("fetchTransactions", "Failed to fetch recent transactions: ${response.errorBody()?.string()}")
                    callback(emptyList())
                }
            }

            override fun onFailure(call: Call<List<Transaction>>, t: Throwable) {
                Log.e("fetchTransactions", "Error fetching recent transactions: ${t.message}")
                callback(emptyList())
            }
        })
    }
}
