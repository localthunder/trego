package com.splitter.splitter.screens

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
import com.splitter.splitter.components.GlobalTopAppBar
import com.splitter.splitter.components.TransactionItem
import com.splitter.splitter.data.TransactionRepository
import com.splitter.splitter.model.Transaction
import com.splitter.splitter.network.ApiService
import com.splitter.splitter.utils.getUserIdFromPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@Composable
fun AddExpenseScreen(
    navController: NavController,
    context: Context,
    groupId: Int,
    repository: TransactionRepository,
    apiService: ApiService
) {
    val userId = getUserIdFromPreferences(context)
    var recentTransactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var nonRecentTransactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var loadingRecent by remember { mutableStateOf(true) }
    var loadingNonRecent by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        if (userId != null) {
            // Launch both API calls simultaneously
            val recentTransactionsDeferred = async { repository.fetchRecentTransactions(userId) }
            val nonRecentTransactionsDeferred = async { repository.fetchNonRecentTransactions(userId) }

            try {
                // Handle recent transactions
                val recentTransactionsResponse = recentTransactionsDeferred.await()
                recentTransactions = recentTransactionsResponse ?: emptyList()
                loadingRecent = false

                // Handle non-recent transactions
                val nonRecentTransactionsResponse = nonRecentTransactionsDeferred.await()
                nonRecentTransactions = nonRecentTransactionsResponse ?: emptyList()
                loadingNonRecent = false
            } catch (e: Exception) {
                error = e.message
                loadingRecent = false
                loadingNonRecent = false
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

                if (loadingRecent || loadingNonRecent) {
                    CircularProgressIndicator()
                } else if (error != null) {
                    Text("Error: $error", color = MaterialTheme.colorScheme.error)
                } else {
                    val combinedTransactions = (recentTransactions + nonRecentTransactions).distinct().sortedByDescending { it.bookingDateTime }
                    LazyColumn {
                        items(combinedTransactions) { transaction ->
                            TransactionItem(transaction, context, apiService) {
                                val description =
                                    if (!transaction.creditorName.isNullOrEmpty()) {
                                        transaction.creditorName
                                    } else {
                                        transaction.remittanceInformationUnstructured
                                    }

                                val transactionAmount = transaction.transactionAmount?.amount?.toString() ?: "N/A"
                                val currency = transaction.transactionAmount?.currency ?: "N/A"

                                Log.d(
                                    "AddExpenseScreen",
                                    "Navigating to paymentDetails with transactionId=${transaction.transactionId}, amount=${transactionAmount}, description=${description}, creditorName=${transaction.creditorName}, currency=${currency}, bookingDateTime=${transaction.bookingDateTime}, remittanceInfo=${transaction.remittanceInformationUnstructured}"
                                )

                                val transactionToSave = Transaction(
                                    transactionId = transaction.transactionId,
                                    userId = userId!!,
                                    description = description,
                                    createdAt = transaction.bookingDateTime ?: "",
                                    updatedAt = transaction.bookingDateTime ?: "",
                                    accountId = transaction.accountId,
                                    currency = currency,
                                    bookingDate = transaction.bookingDate,
                                    valueDate = transaction.bookingDate,  // Assuming valueDate is the same as bookingDate for simplicity
                                    bookingDateTime = transaction.bookingDateTime,
                                    transactionAmount = transaction.transactionAmount,
                                    creditorName = transaction.creditorName,
                                    debtorName = transaction.debtorName,
                                    creditorAccount = transaction.creditorAccount,
                                    remittanceInformationUnstructured = transaction.remittanceInformationUnstructured,
                                    proprietaryBankTransactionCode = transaction.proprietaryBankTransactionCode,
                                    internalTransactionId = transaction.internalTransactionId,
                                    institutionName = transaction.institutionName,
                                    institutionId = transaction.institutionId
                                )

                                // Save the transaction first
                                apiService.createTransaction(transactionToSave).enqueue(object : Callback<Transaction> {
                                    override fun onResponse(call: Call<Transaction>, response: Response<Transaction>) {
                                        if (response.isSuccessful) {
                                            val savedTransaction = response.body()!!
                                            navController.navigate(
                                                "paymentDetails/$groupId/0?transactionId=${transaction.transactionId}&amount=${transactionAmount}&description=${description}&creditorName=${transaction.creditorName}&currency=${currency}&bookingDateTime=${transaction.bookingDateTime}&remittanceInfo=${transaction.remittanceInformationUnstructured}"
                                            ) {
                                                popUpTo("groupDetails/${groupId}")
                                            }
                                        } else {
                                            Log.e("AddExpenseScreen", "Failed to save transaction: ${response.message()}")
                                        }
                                    }

                                    override fun onFailure(call: Call<Transaction>, t: Throwable) {
                                        Log.e("AddExpenseScreen", "Error saving transaction: ${t.message}")
                                    }
                                })
                            }
                        }
                    }
                }
            }
        }
    )
}
