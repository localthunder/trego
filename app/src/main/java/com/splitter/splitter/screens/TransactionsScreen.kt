package com.splitter.splitter.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.splitter.splitter.components.GlobalTopAppBar
import com.splitter.splitter.components.TransactionItem
import com.splitter.splitter.model.Transaction
import com.splitter.splitter.data.network.ApiService
import com.splitter.splitter.ui.theme.GlobalTheme
import com.splitter.splitter.utils.GocardlessUtils.fetchRecentTransactions
import com.splitter.splitter.utils.GocardlessUtils.fetchNonRecentTransactions

@Composable
fun TransactionsScreen(navController: NavController, context: Context, userId: Int, apiService: ApiService) {
    var recentTransactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var nonRecentTransactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var loadingRecent by remember { mutableStateOf(true) }
    var loadingNonRecent by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        if (userId != null) {
            // Fetch recent transactions
            fetchRecentTransactions(context, userId, onRecentTransactionsFetched = { transactions ->
                recentTransactions = transactions
                loadingRecent = false
            }, onError = { errorMessage ->
                error = errorMessage
                loadingRecent = false
            })

            // Fetch non-recent transactions
            fetchNonRecentTransactions(context, userId, onNonRecentTransactionsFetched = { transactions ->
                nonRecentTransactions = transactions
                loadingNonRecent = false
            }, onError = { errorMessage ->
                error = errorMessage
                loadingNonRecent = false
            })
        }
    }

    GlobalTheme {
        Scaffold(
            topBar = {
                GlobalTopAppBar(
                    title = { Text("Transactions") }
                )
            },
            content = { padding ->
                if (loadingRecent && loadingNonRecent) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (error != null) {
                    Text("Error: $error", color = MaterialTheme.colorScheme.error)
                } else {
                    val combinedTransactions = (recentTransactions + nonRecentTransactions).distinct().sortedByDescending { it.bookingDateTime }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp)
                    ) {
                        items(combinedTransactions) { transaction ->
                            TransactionItem(transaction, context, apiService) {
                                val description =
                                    if (!transaction.creditorName.isNullOrEmpty()) {
                                        transaction.creditorName
                                    } else {
                                        transaction.remittanceInformationUnstructured
                                    }
                                Log.d("AddExpenseScreen", "Navigating to paymentDetails with transactionId=${transaction.transactionId}, amount=${transaction.transactionAmount.amount}, description=${description}, creditorName=${transaction.creditorName}, currency=${transaction.transactionAmount.currency}, bookingDateTime=${transaction.bookingDateTime}, remittanceInfo=${transaction.remittanceInformationUnstructured}")
                                navController.navigate(
                                    "paymentDetails/1/0?transactionId=${transaction.transactionId}&amount=${transaction.transactionAmount.amount}&description=${description}&creditorName=${transaction.creditorName}&currency=${transaction.transactionAmount.currency}&bookingDateTime=${transaction.bookingDateTime}&remittanceInfo=${transaction.remittanceInformationUnstructured}"
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}
