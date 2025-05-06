package com.helgolabs.trego.ui.screens

import BankAccountViewModel
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.helgolabs.trego.MainActivity
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.extensions.toEntity
import com.helgolabs.trego.data.local.dataClasses.PreferenceKeys
import com.helgolabs.trego.data.model.BankAccount
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.ui.theme.GlobalTheme
import com.helgolabs.trego.ui.viewmodels.UserPreferencesViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSelectionScreen(
    navController: NavController,
    requisitionId: String,
    userId: Int
) {
    val context = LocalContext.current
    val myApplication = context.applicationContext as MyApplication
    val bankAccountViewModel: BankAccountViewModel = viewModel(factory = myApplication.viewModelFactory)
    val userPreferencesViewModel: UserPreferencesViewModel = viewModel(factory = myApplication.viewModelFactory)
    val themeMode by userPreferencesViewModel.themeMode.collectAsState(initial = PreferenceKeys.ThemeMode.SYSTEM)

    val accounts by bankAccountViewModel.bankAccounts.collectAsState(emptyList())
    val loading by bankAccountViewModel.loading.collectAsState(false)
    val coroutineScope = rememberCoroutineScope()

    // Ensure accounts are loaded
    LaunchedEffect(requisitionId) {
        bankAccountViewModel.loadAccountsForRequisition(requisitionId, userId)
    }

    // State for selected accounts with pre-selection logic
    val selectedAccounts = remember(accounts) {
        mutableStateListOf<BankAccount>().apply {
            // Add main accounts but not pots
            val mainAccounts = accounts.filter { account ->
                !account.name.orEmpty().contains("pot", ignoreCase = true) &&
                        !account.name.orEmpty().contains("savings", ignoreCase = true) &&
                        !account.name.orEmpty().contains("joint", ignoreCase = true)
            }

            addAll(mainAccounts)

            // If no main accounts were found, select the first account
            if (isEmpty() && accounts.isNotEmpty()) {
                add(accounts[0])
            }
        }
    }

    // Organize accounts into categories
    val mainAccounts = accounts.filter { account ->
        !account.name.orEmpty().contains("pot", ignoreCase = true) &&
                !account.name.orEmpty().contains("savings", ignoreCase = true)
    }

    val potAccounts = accounts.filter { account ->
        account.name.orEmpty().contains("pot", ignoreCase = true) ||
                account.name.orEmpty().contains("savings", ignoreCase = true)
    }

    GlobalTheme(themeMode = themeMode) {
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
                BottomAppBar {
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                // Save each selected account, but check first if it exists
                                for (account in selectedAccounts) {
                                    // Check if account already exists
                                    val exists = bankAccountViewModel.checkIfAccountExists(account.accountId)
                                    if (!exists) {
                                        val accountEntity = account.toEntity(SyncStatus.PENDING_SYNC)
                                        bankAccountViewModel.addBankAccount(accountEntity)
                                        Log.d("AccountSelection", "Added account: ${account.name ?: account.accountId}")
                                    } else {
                                        Log.d("AccountSelection", "Account ${account.accountId} already exists, skipping")
                                        // No need to add it again
                                    }
                                }

                                // Get return route
                                val returnRoute = (context as? MainActivity)?.intent?.data?.getQueryParameter("returnRoute")

                                // Navigate back
                                if (!returnRoute.isNullOrEmpty()) {
                                    navController.navigate(returnRoute) {
                                        popUpTo("accountSelection/$requisitionId") { inclusive = true }
                                    }
                                } else {
                                    navController.navigate("home") {
                                        popUpTo("accountSelection/$requisitionId") { inclusive = true }
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
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Text(
                    "Choose which accounts to add:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Main accounts section
                if (mainAccounts.isNotEmpty()) {
                    Text(
                        text = "Main Accounts",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.weight(
                            if (potAccounts.isNotEmpty()) 0.5f else 1f
                        )
                    ) {
                        items(mainAccounts) { account ->
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

                // Add spacing between sections
                if (mainAccounts.isNotEmpty() && potAccounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Pots section
                if (potAccounts.isNotEmpty()) {
                    Text(
                        text = "Savings & Pots",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.weight(
                            if (mainAccounts.isNotEmpty()) 0.5f else 1f
                        )
                    ) {
                        items(potAccounts) { account ->
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

@Composable
fun AccountItem(
    account: BankAccount,
    isSelected: Boolean,
    onSelectionChanged: (Boolean) -> Unit
) {
    // Generate a more descriptive account name if the original is missing or generic
    val displayName = remember(account) {
        when {
            // If account has a meaningful name, use it
            !account.name.isNullOrBlank() && account.name != "Account" -> account.name!!
            // If it has an IBAN, use a formatted version
            !account.iban.isNullOrBlank() -> "Account (${account.iban!!.takeLast(4)})"
            // For Monzo accounts, try to identify account types
            account.institutionId == "MONZO_MONZGB2L" -> {
                // For Monzo, the first account is usually the main account
                if (account.cashAccountType == "CACC") "Monzo Current Account"
                else "Monzo Account"
            }
            // Default fallback
            else -> "Account ${account.accountId.takeLast(4)}"
        }
    }

    // Generate a more descriptive account type
    val accountType = remember(account) {
        when (account.cashAccountType) {
            "CACC" -> "Current Account"
            "SVGS" -> "Savings Account"
            "LOAN" -> "Loan Account"
            "CARD" -> "Card Account"
            else -> account.product ?: "Account"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSelectionChanged(!isSelected) },
        border = BorderStroke(
            width = 2.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelectionChanged(it) }
            )
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${account.currency ?: "GBP"} • ${accountType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}