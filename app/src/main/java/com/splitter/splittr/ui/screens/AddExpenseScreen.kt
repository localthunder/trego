package com.splitter.splittr.ui.screens

import BankAccountViewModel
import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.splitter.splittr.MyApplication
import com.splitter.splittr.data.repositories.TransactionRepository
import com.splitter.splittr.ui.components.GlobalTopAppBar
import com.splitter.splittr.ui.components.TransactionItem
import com.splitter.splittr.data.model.Transaction
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.ui.components.MultipleReauthorizationCard
import com.splitter.splittr.ui.components.ReauthorizeBankAccountCard
import com.splitter.splittr.ui.viewmodels.GroupViewModel
import com.splitter.splittr.ui.viewmodels.InstitutionViewModel
import com.splitter.splittr.ui.viewmodels.TransactionViewModel
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
@Composable
fun AddExpenseScreen(
    navController: NavController,
    context: Context,
    groupId: Int,
    transactionRepository: TransactionRepository
) {
    val myApplication = context.applicationContext as MyApplication
    val transactionViewModel: TransactionViewModel = viewModel(factory = myApplication.viewModelFactory)
    val institutionViewModel: InstitutionViewModel = viewModel(factory = myApplication.viewModelFactory)
    val bankAccountViewModel: BankAccountViewModel = viewModel(factory = myApplication.viewModelFactory)
    val coroutineScope = rememberCoroutineScope()
    val userId = getUserIdFromPreferences(context)

    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var accountsNeedingReauth by remember { mutableStateOf<List<TransactionViewModel.AccountReauthState>>(emptyList()) }

    // Institution-related states
    val institutionLoading by institutionViewModel.loading.collectAsState()
    val institutionError by institutionViewModel.error.collectAsState()
    val requisitionLink by institutionViewModel.requisitionLink.collectAsState()

    // Effect to load transactions and reauth states
    LaunchedEffect(userId) {
        isLoading = true
        error = null

        try {
            userId?.let { id ->
                // Load transactions and reauth states concurrently
                val transactionsDeferred = async { transactionViewModel.fetchTransactions(id) }
                val reauthDeferred = async { transactionViewModel.getAccountsNeedingReauth(id) }

                transactions = transactionsDeferred.await() ?: emptyList()
                accountsNeedingReauth = reauthDeferred.await()
            }
        } catch (e: Exception) {
            error = e.message
            Log.e("AddExpenseScreen", "Error loading data", e)
        } finally {
            isLoading = false
        }
    }

    // Observe transaction error state
    LaunchedEffect(Unit) {
        transactionViewModel.error.collect { errorMessage ->
            error = errorMessage
        }
    }

    Scaffold(
        topBar = {
            GlobalTopAppBar(
                title = { Text("Add Expense", style = MaterialTheme.typography.headlineSmall) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            // Action Buttons
            ActionButtons(
                onAddCustom = { navController.navigate("paymentDetails/$groupId/0") },
                onAddRecurring = { /* TODO */ },
                onConnectBank = { navController.navigate("institutions") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Reauthorization Cards
            if (accountsNeedingReauth.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                if (accountsNeedingReauth.size == 1) {
                    val account = accountsNeedingReauth.first()
                    ReauthorizeBankAccountCard(
                        institutionId = account.institutionId ?: "Unknown Bank",
                        onReconnectClick = {
                            account.institutionId?.let { id ->
                                institutionViewModel.createRequisitionLink(
                                    institutionId = id,
                                    baseUrl = "splitter://bankaccounts"
                                )
                            }
                        }
                    )
                } else {
                    MultipleReauthorizationCard(
                        institutions = accountsNeedingReauth.mapNotNull { it.institutionId }
                            .ifEmpty { listOf("Unknown Bank") },
                        onReconnectClick = { institutionId ->
                            institutionViewModel.createRequisitionLink(
                                institutionId = institutionId,
                                baseUrl = "splitter://bankaccounts"
                            )
                        }
                    )
                }
            }

            // Handle requisition link
            requisitionLink?.getOrNull()?.let { response ->
                LaunchedEffect(response) {
                    response.link?.let { url ->
                        try {
                            // Extract the requisition ID from the URL
                            // The URL format is: https://ob.gocardless.com/ob-psd2/start/{requisitionId}/{institutionId}
                            val requisitionId = url.split("/").dropLast(1).last()

                            // First update the account's reauthentication status and requisition ID
                            accountsNeedingReauth.firstOrNull()?.let { account ->
                                account.accountId?.let { accountId ->
                                    bankAccountViewModel.updateAccountAfterReauth(
                                        accountId = accountId,
                                        newRequisitionId = requisitionId // Now passing the extracted requisitionId
                                    )
                                    // Refresh the accounts needing reauth
                                    userId?.let { id ->
                                        accountsNeedingReauth = transactionViewModel.getAccountsNeedingReauth(id)
                                    }
                                }
                            }

                            // Then launch the bank's authentication page
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)

                            // Clear the requisition link after use
                            institutionViewModel.clearRequisitionLink()
                        } catch (e: Exception) {
                            Log.e("AddExpenseScreen", "Error updating reauth status or launching URL", e)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Transactions Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    error != null -> {
                        Text(
                            text = "Error: $error",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .padding(16.dp)
                                .align(Alignment.Center)
                        )
                    }
                    transactions.isEmpty() -> {
                        Text(
                            text = "No transactions available",
                            modifier = Modifier
                                .padding(16.dp)
                                .align(Alignment.Center)
                        )
                    }
                    else -> {
                        LazyColumn {
                            items(
                                items = transactions,
                                key = { it.transactionId }
                            ) { transaction ->
                                TransactionItem(
                                    transaction = transaction,
                                    context = context,
                                    onClick = {
                                        handleTransactionClick(
                                            transaction,
                                            groupId,
                                            navController,
                                            transactionViewModel,
                                            coroutineScope
                                        )
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButtons(
    onAddCustom: () -> Unit,
    onAddRecurring: () -> Unit,
    onConnectBank: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton(
            icon = Icons.Default.Add,
            text = "Add custom expense",
            onClick = onAddCustom
        )
        ActionButton(
            icon = Icons.Default.EventRepeat,
            text = "Add recurring expense",
            onClick = onAddRecurring
        )
        ActionButton(
            icon = Icons.Default.AccountBalance,
            text = "Connect another bank account",
            onClick = onConnectBank
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = text)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, fontSize = 18.sp)
    }
}

private fun handleTransactionClick(
    transaction: Transaction,
    groupId: Int,
    navController: NavController,
    transactionViewModel: TransactionViewModel,
    coroutineScope: CoroutineScope
) {
    coroutineScope.launch {
        val savedTransaction = transactionViewModel.saveTransaction(transaction)
        if (savedTransaction != null) {
            val description = transaction.creditorName.takeIf { !it.isNullOrEmpty() }
                ?: transaction.remittanceInformationUnstructured
                ?: ""

            navController.navigate(
                "paymentDetails/$groupId/0?" +
                        "transactionId=${transaction.transactionId}&" +
                        "amount=${transaction.getEffectiveAmount()}&" +
                        "description=${Uri.encode(description)}&" +
                        "creditorName=${Uri.encode(transaction.creditorName ?: "")}&" +
                        "currency=${transaction.getEffectiveCurrency()}&" +
                        "bookingDateTime=${transaction.bookingDateTime}&" +
                        "remittanceInfo=${Uri.encode(transaction.remittanceInformationUnstructured ?: "")}" +
                        "institutionId=${transaction.institutionId}"
            ) {
                popUpTo("groupDetails/${groupId}")
            }
        }
    }
}