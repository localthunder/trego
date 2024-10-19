package com.splitter.splittr.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.splitter.splittr.data.local.repositories.TransactionRepository
import com.splitter.splittr.ui.components.GlobalTopAppBar
import com.splitter.splittr.ui.components.TransactionItem
import com.splitter.splittr.model.Transaction
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.launch

@Composable
fun AddExpenseScreen(
    navController: NavController,
    context: Context,
    groupId: Int,
    transactionRepository: TransactionRepository
) {
    val userId = getUserIdFromPreferences(context)
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(userId) {
        if (userId != null) {
            try {
                val recentTransactions = transactionRepository.fetchRecentTransactions(userId)
                val nonRecentTransactions = transactionRepository.fetchNonRecentTransactions(userId)

                transactions = (recentTransactions ?: emptyList()) + (nonRecentTransactions ?: emptyList())
                transactions = transactions.distinct().sortedByDescending { it.bookingDateTime }

                loading = false
            } catch (e: Exception) {
                error = e.message
                loading = false
            }
        }
    }

    Scaffold(
        topBar = {
            GlobalTopAppBar(title = { Text("Add Expense", style = MaterialTheme.typography.headlineSmall) })
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Add Custom Expense Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            navController.navigate("paymentDetails/$groupId/0")
                        }
                        .padding(vertical = 16.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add custom expense")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add custom expense", fontSize = 18.sp, color = Color.Black)
                }

                // Add Recurring Expense Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Navigate to Recurring Expense Screen (Not yet implemented)
                        }
                        .padding(vertical = 16.dp)
                ) {
                    Icon(imageVector = Icons.Default.EventRepeat, contentDescription = "Add recurring expense")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add recurring expense", fontSize = 18.sp, color = Color.Black)
                }

                // Connect Another Bank Account Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            navController.navigate("institutions")
                        }
                        .padding(vertical = 16.dp)
                ) {
                    Icon(imageVector = Icons.Default.AccountBalance, contentDescription = "Connect another bank account")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect another bank account", fontSize = 18.sp, color = Color.Black)
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (loading) {
                    CircularProgressIndicator()
                } else if (error != null) {
                    Text("Error: $error", color = MaterialTheme.colorScheme.error)
                } else {
                    LazyColumn {
                        items(transactions) { transaction ->
                            TransactionItem(transaction, context) {
                                val description = transaction.creditorName.takeIf { !it.isNullOrEmpty() }
                                    ?: transaction.remittanceInformationUnstructured ?: ""

                                val transactionAmount = transaction.transactionAmount?.amount?.toString() ?: "N/A"
                                val currency = transaction.transactionAmount?.currency ?: "N/A"

                                Log.d(
                                    "AddExpenseScreen",
                                    "Navigating to paymentDetails with transactionId=${transaction.transactionId}, amount=${transactionAmount}, description=${description}, creditorName=${transaction.creditorName}, currency=${currency}, bookingDateTime=${transaction.bookingDateTime}, remittanceInfo=${transaction.remittanceInformationUnstructured}"
                                )

                                coroutineScope.launch {
                                    val savedTransaction = transactionRepository.saveTransaction(transaction)
                                    if (savedTransaction != null) {
                                        navController.navigate(
                                            "paymentDetails/$groupId/0?transactionId=${transaction.transactionId}&amount=${transactionAmount}&description=${description}&creditorName=${transaction.creditorName}&currency=${currency}&bookingDateTime=${transaction.bookingDateTime}&remittanceInfo=${transaction.remittanceInformationUnstructured}"
                                        ) {
                                            popUpTo("groupDetails/${groupId}")
                                        }
                                    } else {
                                        Log.e("AddExpenseScreen", "Failed to save transaction")
                                        // You might want to show an error message to the user here
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}