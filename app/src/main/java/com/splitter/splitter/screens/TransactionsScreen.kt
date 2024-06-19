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
import com.splitter.splitter.components.GlobalTopAppBar
import com.splitter.splitter.components.TransactionItem
import com.splitter.splitter.model.Transaction
import com.splitter.splitter.ui.theme.GlobalTheme
import com.splitter.splitter.utils.GocardlessUtils.fetchTransactions


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

    GlobalTheme {
        Scaffold(
            topBar = {
                GlobalTopAppBar(
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
                            TransactionItem(transaction) {
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
