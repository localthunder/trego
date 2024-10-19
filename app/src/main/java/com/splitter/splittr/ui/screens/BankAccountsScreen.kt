package com.splitter.splittr.ui.screens

import BankAccountViewModel
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.splitter.splittr.MyApplication
import com.splitter.splittr.model.BankAccount
import kotlinx.coroutines.launch

@Composable
fun BankAccountsScreen(navController: NavController, context: Context, requisitionId: String, userId: Int) {
    val myApplication = context.applicationContext as MyApplication
    val bankAccountViewModel: BankAccountViewModel = viewModel(factory = myApplication.viewModelFactory)

    // Observe the state from the ViewModel
    val bankAccounts by bankAccountViewModel.bankAccounts.collectAsState(emptyList())
    val existingAccounts by bankAccountViewModel.bankAccounts.collectAsState(emptyList())
    val loading by bankAccountViewModel.loading.collectAsState(false)
    val error by bankAccountViewModel.error.collectAsState(null)

    val coroutineScope = rememberCoroutineScope()
    val addedAccounts = remember { existingAccounts.map { it.accountId }.toSet() }

    val context = LocalContext.current


    // Trigger loading bank accounts and existing accounts when the screen is launched
    LaunchedEffect(requisitionId, userId) {
        bankAccountViewModel.loadBankAccounts(userId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (loading) {
            CircularProgressIndicator()
        } else {
            Text("Available Bank Accounts", style = MaterialTheme.typography.h6)
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn {
                items(bankAccounts) { account ->
                    BankAccountCard(account, context, navController, addedAccounts) { accountId ->
                        if (addedAccounts.contains(accountId)) {
                            navController.navigate("transactions/$userId")
                        } else {
                            coroutineScope.launch {
                                val success = bankAccountViewModel.addBankAccount(account)
                                if (success) {
                                    Toast.makeText(context, "Account added successfully", Toast.LENGTH_SHORT).show()
                                    // Refetch accounts to include newly added one
                                    bankAccountViewModel.loadBankAccounts(userId)
                                } else {
                                    Toast.makeText(context, "Failed to add account", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BankAccountCard(account: BankAccount, context: Context, navController: NavController, addedAccounts: Set<String>, onClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color.White),
        elevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                account.name?.let { Text(it, fontSize = 20.sp, color = Color.Black) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onClick(account.accountId) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(if (addedAccounts.contains(account.accountId)) "View" else "Add Account")
            }
        }
    }
}
