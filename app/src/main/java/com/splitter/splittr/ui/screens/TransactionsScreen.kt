package com.splitter.splittr.ui.screens

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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.splitter.splittr.MyApplication
import com.splitter.splittr.ui.components.GlobalTopAppBar
import com.splitter.splittr.ui.components.TransactionItem
import com.splitter.splittr.data.model.Transaction
import com.splitter.splittr.ui.theme.GlobalTheme
import com.splitter.splittr.ui.viewmodels.TransactionViewModel
import com.splitter.splittr.ui.viewmodels.InstitutionViewModel

@Composable
fun TransactionsScreen(
    navController: NavController,
    context: Context,
    userId: Int
) {
    val myApplication = context.applicationContext as MyApplication
    val transactionViewModel: TransactionViewModel = viewModel(factory = myApplication.viewModelFactory)
    val institutionViewModel: InstitutionViewModel = viewModel(factory = myApplication.viewModelFactory)

    val transactions by transactionViewModel.transactions.collectAsState(initial = emptyList())
    val loading by transactionViewModel.loading.collectAsState(initial = true)
    val error by transactionViewModel.error.collectAsState(initial = null)

    LaunchedEffect(userId) {
        transactionViewModel.fetchRecentTransactions(userId)
        transactionViewModel.fetchNonRecentTransactions(userId)
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
                } else if (error != null) {
                    Text("Error: $error", color = MaterialTheme.colorScheme.error)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp)
                    ) {
                        items(transactions) { transaction ->
                            TransactionItem(
                                transaction = transaction,
                                context = context,
                            ) {
                                navigateToPaymentDetails(navController, transaction)
                            }
                        }
                    }
                }
            }
        )
    }
}

private fun navigateToPaymentDetails(navController: NavController, transaction: Transaction) {
    val description = transaction.creditorName.takeIf { !it.isNullOrEmpty() }
        ?: transaction.remittanceInformationUnstructured

    Log.d("TransactionsScreen", "Navigating to paymentDetails with transactionId=${transaction.transactionId}, " +
            "amount=${transaction.transactionAmount.amount}, description=$description, " +
            "creditorName=${transaction.creditorName}, currency=${transaction.transactionAmount.currency}, " +
            "bookingDateTime=${transaction.bookingDateTime}, " +
            "remittanceInfo=${transaction.remittanceInformationUnstructured}")

    navController.navigate(
        "paymentDetails/1/0?transactionId=${transaction.transactionId}" +
                "&amount=${transaction.transactionAmount.amount}" +
                "&description=${description}" +
                "&creditorName=${transaction.creditorName}" +
                "&currency=${transaction.transactionAmount.currency}" +
                "&bookingDateTime=${transaction.bookingDateTime}" +
                "&remittanceInfo=${transaction.remittanceInformationUnstructured}"
    )
}