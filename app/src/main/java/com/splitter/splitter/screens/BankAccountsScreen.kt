package com.splitter.splitter.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.rememberImagePainter
import com.splitter.splitter.model.BankAccount
import com.splitter.splitter.network.ApiService
import com.splitter.splitter.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@Composable
fun BankAccountsScreen(navController: NavController, context: Context, requisitionId: String, userId: Int) {
    var bankAccounts by remember { mutableStateOf(listOf<BankAccount>()) }
    var existingAccounts by remember { mutableStateOf(listOf<BankAccount>()) }
    var loading by remember { mutableStateOf(true) }
    var addedAccounts by remember { mutableStateOf(setOf<String>()) }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        fetchBankAccounts(context, requisitionId) { accounts ->
            bankAccounts = accounts
            loading = false
        }
        fetchExistingAccounts(context, userId) { accounts ->
            existingAccounts = accounts
            addedAccounts = accounts.map { it.accountId }.toSet()
        }
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
                            navController.navigate("transactions")
                        } else {
                            handleAddAccount(context, navController, account) { success ->
                                if (success) {
                                    addedAccounts = addedAccounts + accountId
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

fun handleAddAccount(context: Context, navController: NavController, account: BankAccount, onResult: (Boolean) -> Unit) {
    val apiService = RetrofitClient.getInstance(context).create(ApiService::class.java)
    apiService.addAccount(account).enqueue(object : Callback<Void> {
        override fun onResponse(call: Call<Void>, response: Response<Void>) {
            if (response.isSuccessful) {
                Toast.makeText(context, "Account added successfully", Toast.LENGTH_SHORT).show()
                onResult(true)
            } else if (response.code() == 409) {
                Toast.makeText(context, "Account already exists", Toast.LENGTH_SHORT).show()
                onResult(false)
            } else {
                Toast.makeText(context, "Failed to add account", Toast.LENGTH_SHORT).show()
                onResult(false)
            }
        }

        override fun onFailure(call: Call<Void>, t: Throwable) {
            Toast.makeText(context, "Error adding account", Toast.LENGTH_SHORT).show()
            onResult(false)
        }
    })
}

fun fetchBankAccounts(context: Context, requisitionId: String, callback: (List<BankAccount>) -> Unit) {
    val apiService = RetrofitClient.getInstance(context).create(ApiService::class.java)
    apiService.getBankAccounts(requisitionId).enqueue(object : Callback<List<BankAccount>> {
        override fun onResponse(call: Call<List<BankAccount>>, response: Response<List<BankAccount>>) {
            if (response.isSuccessful) {
                callback(response.body() ?: listOf())
            } else {
                callback(listOf())
            }
        }

        override fun onFailure(call: Call<List<BankAccount>>, t: Throwable) {
            callback(listOf())
        }
    })
}

fun fetchExistingAccounts(context: Context, userId: Int, callback: (List<BankAccount>) -> Unit) {
    val apiService = RetrofitClient.getInstance(context).create(ApiService::class.java)
    apiService.getUserAccounts(userId).enqueue(object : Callback<List<BankAccount>> {
        override fun onResponse(call: Call<List<BankAccount>>, response: Response<List<BankAccount>>) {
            if (response.isSuccessful) {
                callback(response.body() ?: listOf())
            } else {
                callback(listOf())
            }
        }

        override fun onFailure(call: Call<List<BankAccount>>, t: Throwable) {
            callback(listOf())
        }
    })
}
