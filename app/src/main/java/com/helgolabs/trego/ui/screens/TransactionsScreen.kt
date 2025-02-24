package com.helgolabs.trego.ui.screens

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
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.ui.components.GlobalTopAppBar
import com.helgolabs.trego.ui.components.TransactionItem
import com.helgolabs.trego.data.model.Transaction
import com.helgolabs.trego.ui.theme.GlobalTheme
import com.helgolabs.trego.ui.viewmodels.TransactionViewModel
import com.helgolabs.trego.ui.viewmodels.InstitutionViewModel

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
            "amount=${transaction.getEffectiveAmount()}, description=$description, " +
            "creditorName=${transaction.creditorName}, currency=${transaction.getEffectiveCurrency()}, " +
            "bookingDateTime=${transaction.bookingDateTime}, " +
            "remittanceInfo=${transaction.remittanceInformationUnstructured}")

    navController.navigate(
        "paymentDetails/1/0?transactionId=${transaction.transactionId}" +
                "&amount=${transaction.getEffectiveAmount()}" +
                "&description=${description}" +
                "&creditorName=${transaction.creditorName}" +
                "&currency=${transaction.getEffectiveCurrency()}" +
                "&bookingDateTime=${transaction.bookingDateTime}" +
                "&remittanceInfo=${transaction.remittanceInformationUnstructured}"
    )
}