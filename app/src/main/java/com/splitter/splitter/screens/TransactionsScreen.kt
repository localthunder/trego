package com.splitter.splitter.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.splitter.splitter.model.Transaction
import com.splitter.splitter.network.ApiService
import com.splitter.splitter.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@Composable
fun TransactionsScreen(navController: NavController, context: Context, userId: Int) {
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        fetchTransactions(context, userId) { fetchedTransactions ->
            transactions = fetchedTransactions
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions") }
            )
        },
        content = { padding ->
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                ) {
                    items(transactions) { transaction ->
                        TransactionItem(transaction)
                    }
                }
            }
        }
    )
}

@Composable
fun TransactionItem(transaction: Transaction) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Transaction ID: ${transaction.transactionId ?: "N/A"}")
            Text("Booking Date: ${transaction.bookingDate ?: "N/A"}")
            Text("Booking DateTime: ${transaction.bookingDateTime ?: "N/A"}")
            Text("Amount: ${transaction.transactionAmount?.amount ?: "N/A"}")
            Text("Currency: ${transaction.transactionAmount?.currency ?: "N/A"}")
            Text("Creditor Name: ${transaction.creditorName ?: "N/A"}")
            Text("Creditor Account BBAN: ${transaction.creditorAccount?.bban ?: "N/A"}")
            Text("Remittance Info: ${transaction.remittanceInformationUnstructured ?: "N/A"}")
            Text("Proprietary Bank Transaction Code: ${transaction.proprietaryBankTransactionCode ?: "N/A"}")
            Text("Internal Transaction ID: ${transaction.internalTransactionId ?: "N/A"}")
        }
    }
}

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
