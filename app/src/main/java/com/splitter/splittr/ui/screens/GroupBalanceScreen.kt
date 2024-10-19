package com.splitter.splittr.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.splitter.splittr.MyApplication
import com.splitter.splittr.ui.components.GlobalTopAppBar
import com.splitter.splittr.ui.viewmodels.GroupViewModel

data class UserBalanceWithCurrency(
    val userId: Int,
    val username: String,
    val balances: Map<String, Double> // Currency code to balance
)

@Composable
fun GroupBalancesScreen(
    navController: NavController,
    context: Context,
    groupId: Int
) {
    val myApplication = context.applicationContext as MyApplication
    val groupViewModel: GroupViewModel = viewModel(factory = myApplication.viewModelFactory)
    val balances by groupViewModel.groupBalances.collectAsStateWithLifecycle()
    val loading by groupViewModel.loading.collectAsStateWithLifecycle()
    val error by groupViewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(groupId) {
        groupViewModel.fetchGroupBalances(groupId)
    }

    Scaffold(
        topBar = {
            GlobalTopAppBar(title = { Text("Group Balances") })
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
                when {
                    loading -> CircularProgressIndicator()
                    error != null -> Text("Error: $error", color = MaterialTheme.colorScheme.error)
                    else -> {
                        LazyColumn {
                            items(balances) { balance ->
                                BalanceItem(balance)
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun BalanceItem(balance: UserBalanceWithCurrency) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("User: ${balance.username}", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                balance.balances.forEach { (currency, amount) ->
                    Text(
                        "Balance: $amount $currency",
                        fontSize = 18.sp,
                        color = if (amount >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}