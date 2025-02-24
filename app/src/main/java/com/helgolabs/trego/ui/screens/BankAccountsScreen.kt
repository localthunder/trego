package com.helgolabs.trego.ui.screens

import BankAccountViewModel
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.model.BankAccount
import java.util.Collections.emptyList

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

    val bankAccounts by bankAccountViewModel.bankAccounts.collectAsState(emptyList())
    val loading by bankAccountViewModel.loading.collectAsState(false)
    val error by bankAccountViewModel.error.collectAsState(null)

    val coroutineScope = rememberCoroutineScope()

    // Launch the account loading when the screen is created
    LaunchedEffect(requisitionId) {
        Log.d("BankAccountsScreen", "LaunchedEffect triggered for requisitionId: $requisitionId")
        bankAccountViewModel.loadAccountsForRequisition(requisitionId, userId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (loading) {
            Log.d("BankAccountsScreen", "Showing loading indicator")
            CircularProgressIndicator()
        } else if (error != null) {
            Text(
                text = error ?: "Unknown error occurred",
                color = Color.Red,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            Text("Available Bank Accounts", style = MaterialTheme.typography.displaySmall)
            Spacer(modifier = Modifier.height(16.dp))

            if (bankAccounts.isEmpty()) {
                Text(
                    "No bank accounts found",
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn {
                    items(bankAccounts) { account ->
                        BankAccountCard(
                            account = account,
                            context = context,
                            navController = navController,
                            addedAccounts = bankAccounts.map { it.accountId }.toSet(),
                            onClick = { accountId ->
                                // Your existing onClick logic
                            }
                        )
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
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )    ) {
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
