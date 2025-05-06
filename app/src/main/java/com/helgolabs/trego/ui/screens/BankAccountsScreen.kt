package com.helgolabs.trego.ui.screens

import BankAccountViewModel
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.helgolabs.trego.MainActivity
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.extensions.toEntity
import com.helgolabs.trego.data.model.BankAccount
import com.helgolabs.trego.data.sync.SyncStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankAccountsScreen(
    navController: NavController,
    context: Context,
    requisitionId: String,
    userId: Int
) {
    Log.d("BankAccountsScreen", "Screen initialized with requisitionId: $requisitionId")

    val myApplication = context.applicationContext as MyApplication
    val bankAccountViewModel: BankAccountViewModel = viewModel(factory = myApplication.viewModelFactory)
    val coroutineScope = rememberCoroutineScope()

    // Loading state and data
    var isLoading by remember { mutableStateOf(true) }
    var accounts by remember { mutableStateOf<List<BankAccount>>(emptyList()) }
    val selectedAccounts = remember { mutableStateListOf<BankAccount>() }

    // Load accounts on initialization
    LaunchedEffect(requisitionId) {
        Log.d("BankAccountsScreen", "LaunchedEffect triggered for requisitionId: $requisitionId")
        bankAccountViewModel.loadAccountsForRequisition(requisitionId, userId)

        // Wait briefly to ensure accounts have time to load
        delay(5000)

        // Check if accounts loaded
        var loadedAccounts = bankAccountViewModel.bankAccounts.value
        Log.d("BankAccountsScreen", "Raw accounts received: ${loadedAccounts.size}")

        if (loadedAccounts.isEmpty()) {
            // Try to wait a bit longer for accounts to come through the flow
            val timeoutJob = launch {
                delay(5000) // Additional 5 second timeout
                if (accounts.isEmpty()) {
                    Log.d("BankAccountsScreen", "No accounts received after timeout")
                }
            }

            // Collect in parallel with timeout
            try {
                withTimeoutOrNull(5000) {
                    bankAccountViewModel.bankAccounts
                        .filter { it.isNotEmpty() }
                        .collect { newAccounts ->
                            Log.d("BankAccountsScreen", "Flow update with ${newAccounts.size} accounts")
                            accounts = newAccounts
                            isLoading = false
                            timeoutJob.cancel() // Cancel timeout job since we got accounts
                        }
                }
            } catch (e: Exception) {
                Log.e("BankAccountsScreen", "Error collecting accounts", e)
            }
        } else {
            accounts = loadedAccounts
            isLoading = false
        }
    }

    // Collect account updates
    LaunchedEffect(Unit) {
        bankAccountViewModel.bankAccounts.collect { newAccounts ->
            if (newAccounts.isNotEmpty()) {
                Log.d("BankAccountsScreen", "New accounts received: ${newAccounts.size}")
                accounts = newAccounts

                // Only set pre-selections if this is the first time getting accounts
                if (selectedAccounts.isEmpty() && newAccounts.isNotEmpty()) {
                    val mainAccount = newAccounts.find { it.iban != null || it.ownerName != null }
                    if (mainAccount != null) {
                        selectedAccounts.add(mainAccount)
                    } else {
                        selectedAccounts.add(newAccounts[0])
                    }
                }

                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Accounts") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (accounts.isNotEmpty() && selectedAccounts.isNotEmpty()) {
                BottomAppBar {
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                Log.d("BankAccountsScreen", "Adding ${selectedAccounts.size} selected accounts")

                                // Save the selected accounts
                                for (account in selectedAccounts) {
                                    val accountEntity = account.toEntity(SyncStatus.PENDING_SYNC)
                                    bankAccountViewModel.addBankAccount(accountEntity)
                                    Log.d("BankAccountsScreen", "Added account: ${account.name ?: account.accountId}")
                                }

                                // Navigate to return route if provided
                                val returnRoute = (context as? MainActivity)?.intent?.data?.getQueryParameter("returnRoute")
                                if (!returnRoute.isNullOrEmpty()) {
                                    val decodedRoute = Uri.decode(returnRoute)
                                    Log.d("BankAccountsScreen", "Navigating to return route: $decodedRoute")
                                    delay(500) // Brief delay to ensure accounts are saved
                                    navController.navigate(decodedRoute) {
                                        popUpTo("bankaccounts/$requisitionId") { inclusive = true }
                                    }
                                } else {
                                    Log.d("BankAccountsScreen", "No return route, navigating to home")
                                    delay(500)
                                    navController.navigate("home") {
                                        popUpTo("bankaccounts/$requisitionId") { inclusive = true }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("Add Selected Accounts (${selectedAccounts.size})")
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loading accounts...")
                    }
                }
                accounts.isEmpty() -> {
                    Text("No bank accounts found")
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            "Choose which accounts to add:",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.weight(1f)
                        ) {
                            items(accounts) { account ->
                                AccountItem(
                                    account = account,
                                    isSelected = selectedAccounts.contains(account),
                                    onSelectionChanged = { selected ->
                                        if (selected) {
                                            if (!selectedAccounts.contains(account)) {
                                                selectedAccounts.add(account)
                                            }
                                        } else {
                                            selectedAccounts.remove(account)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}