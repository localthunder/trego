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

    fun fetchRecentTransactions(context: Context, userId: Int, onRecentTransactionsFetched: (List<Transaction>) -> Unit, onError: (String) -> Unit) {
        val apiService = RetrofitClient.getInstance(context).create(ApiService::class.java)

        // Fetch recent transactions
        val recentTransactionsCall = apiService.getRecentTransactions(
            userId,
            LocalDate.now().minusDays(7).format(DateTimeFormatter.ISO_DATE)
        )

        recentTransactionsCall.enqueue(object : Callback<List<Transaction>> {
            override fun onResponse(
                call: Call<List<Transaction>>,
                response: Response<List<Transaction>>
            ) {
                if (response.isSuccessful) {
                    val recentTransactions = response.body() ?: emptyList()

                    // Log the recent transactions
                    recentTransactions.forEach { transaction ->
                        Log.d("fetchTransactions", "Recent Transaction: $transaction")
                    }

                    onRecentTransactionsFetched(recentTransactions)
                } else {
                    Log.e(
                        "fetchTransactions",
                        "Failed to fetch recent transactions: ${response.errorBody()?.string()}"
                    )
                    onError("Failed to fetch recent transactions: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<List<Transaction>>, t: Throwable) {
                Log.e("fetchTransactions", "Error fetching recent transactions: ${t.message}")
                onError("Error fetching recent transactions: ${t.message}")
            }
        })
    }

    fun fetchNonRecentTransactions(context: Context, userId: Int, onNonRecentTransactionsFetched: (List<Transaction>) -> Unit, onError: (String) -> Unit) {
        val apiService = RetrofitClient.getInstance(context).create(ApiService::class.java)

        // Fetch non-recent transactions
        val nonRecentTransactionsCall = apiService.getNonRecentTransactions(
            userId,
            LocalDate.now().minusDays(7).format(DateTimeFormatter.ISO_DATE)
        )

        nonRecentTransactionsCall.enqueue(object : Callback<List<Transaction>> {
            override fun onResponse(
                call: Call<List<Transaction>>,
                response: Response<List<Transaction>>
            ) {
                if (response.isSuccessful) {
                    val nonRecentTransactions = response.body() ?: emptyList()

                    // Log the non-recent transactions
                    nonRecentTransactions.forEach { transaction ->
                        Log.d("fetchTransactions", "Non-Recent Transaction: $transaction")
                    }

                    onNonRecentTransactionsFetched(nonRecentTransactions)
                } else {
                    Log.e(
                        "fetchTransactions",
                        "Failed to fetch non-recent transactions: ${response.errorBody()?.string()}"
                    )
                    onError("Failed to fetch non-recent transactions: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<List<Transaction>>, t: Throwable) {
                Log.e("fetchTransactions", "Error fetching non-recent transactions: ${t.message}")
                onError("Error fetching non-recent transactions: ${t.message}")
            }
        })
    }
    suspend fun getInstitutionLogoUrl(apiService: ApiService, institutionId: String): String? {
            return try {
                val response = apiService.getInstitutionById(institutionId).execute()
                if (response.isSuccessful) {
                    response.body()?.logo
                } else {
                    Log.e("getInstitutionLogoUrl", "Failed to fetch institution logo: ${response.errorBody()?.string()}")
                    null
                }
            } catch (e: Exception) {
                Log.e("getInstitutionLogoUrl", "Exception while fetching institution logo: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }
