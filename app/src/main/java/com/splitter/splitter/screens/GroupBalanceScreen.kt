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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.splitter.splitter.model.UserBalance
import com.splitter.splitter.network.ApiService
import com.splitter.splitter.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@Composable
fun GroupBalancesScreen(navController: NavController, context: Context, groupId: Int) {
    val apiService = RetrofitClient.getInstance(context).create(ApiService::class.java)
    var balances by remember { mutableStateOf<List<UserBalance>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(groupId) {
        apiService.getGroupBalances(groupId).enqueue(object : Callback<List<UserBalance>> {
            override fun onResponse(call: Call<List<UserBalance>>, response: Response<List<UserBalance>>) {
                if (response.isSuccessful) {
                    balances = response.body() ?: emptyList()
                    loading = false
                } else {
                    error = response.message()
                    loading = false
                }
            }

            override fun onFailure(call: Call<List<UserBalance>>, t: Throwable) {
                error = t.message
                loading = false
            }
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Group Balances") })
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                if (loading) {
                    CircularProgressIndicator()
                } else if (error != null) {
                    Text("Error: $error", color = MaterialTheme.colors.error)
                } else {
                    LazyColumn {
                        items(balances) { balance ->
                            BalanceItem(balance)
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun BalanceItem(balance: UserBalance) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("User: ${balance.username}", fontSize = 18.sp, color = MaterialTheme.colors.onSurface)
                Text("Balance: ${balance.balance}", fontSize = 18.sp, color = if (balance.balance >= 0) MaterialTheme.colors.primary else MaterialTheme.colors.error)
            }
        }
    }
}