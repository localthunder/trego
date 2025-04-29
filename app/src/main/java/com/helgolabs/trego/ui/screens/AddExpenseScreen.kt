package com.helgolabs.trego.ui.screens

import BankAccountViewModel
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.model.Transaction
import com.helgolabs.trego.data.repositories.TransactionRepository
import com.helgolabs.trego.ui.components.GlobalTopAppBar
import com.helgolabs.trego.ui.components.MultipleReauthorizationCard
import com.helgolabs.trego.ui.components.ReauthorizeBankAccountCard
import com.helgolabs.trego.ui.components.ScrollToTopButton
import com.helgolabs.trego.ui.components.TransactionItem
import com.helgolabs.trego.ui.viewmodels.InstitutionViewModel
import com.helgolabs.trego.ui.viewmodels.PaymentsViewModel
import com.helgolabs.trego.ui.viewmodels.TransactionViewModel
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

// Object to store scroll positions for different groups
object AddExpenseScrollPositions {
    private val scrollPositions = mutableMapOf<Int, ScrollInfo>()

    data class ScrollInfo(
        val index: Int = 0,
        val offset: Int = 0
    )

    fun savePosition(groupId: Int, index: Int, offset: Int) {
        scrollPositions[groupId] = ScrollInfo(index, offset)
    }

    fun getPosition(groupId: Int): ScrollInfo {
        return scrollPositions[groupId] ?: ScrollInfo(0, 0)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    navController: NavController,
    context: Context,
    groupId: Int,
) {
    val myApplication = context.applicationContext as MyApplication
    val transactionViewModel: TransactionViewModel = viewModel(factory = myApplication.viewModelFactory)
    val institutionViewModel: InstitutionViewModel = viewModel(factory = myApplication.viewModelFactory)
    val bankAccountViewModel: BankAccountViewModel = viewModel(factory = myApplication.viewModelFactory)
    val paymentsViewModel: PaymentsViewModel = viewModel(factory = myApplication.viewModelFactory)
    val coroutineScope = rememberCoroutineScope()
    val userId = getUserIdFromPreferences(context)
    val hapticFeedback = LocalHapticFeedback.current
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val addedTransactionIds by transactionViewModel.addedTransactionIds.collectAsState(emptySet())
    val showAlreadyAdded by transactionViewModel.showAlreadyAdded.collectAsState(true)
    val filteredTransactions by transactionViewModel.filteredTransactions.collectAsState(emptyList())

    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var accountsNeedingReauth by remember { mutableStateOf<List<TransactionViewModel.AccountReauthState>>(emptyList()) }

    // Multi-select state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedTransactions by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Get the remembered scroll position for this group
    val savedScrollInfo = remember(groupId) { AddExpenseScrollPositions.getPosition(groupId) }

    // Create the scroll state and restore the position
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedScrollInfo.index,
        initialFirstVisibleItemScrollOffset = savedScrollInfo.offset
    )

    // Debugging launched effect
    LaunchedEffect(transactions, addedTransactionIds, showAlreadyAdded) {
        Log.d("AddExpenseScreen", "Transactions: ${transactions.size}, Added IDs: ${addedTransactionIds.size}, Showing Added: $showAlreadyAdded")
    }

    // Save scroll position when the user scrolls or leaves the screen
    DisposableEffect(listState, groupId) {
        onDispose {
            val firstVisibleItemIndex = listState.firstVisibleItemIndex
            val firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
            AddExpenseScrollPositions.savePosition(
                groupId,
                firstVisibleItemIndex,
                firstVisibleItemScrollOffset
            )
        }
    }

    // Handle back button to exit selection mode when in selection mode
    DisposableEffect(isSelectionMode, backDispatcher) {
        // Define the exit function that the callback will use
        val exitSelection = {
            isSelectionMode = false
            selectedTransactions = emptySet()
        }

        val callback = object : OnBackPressedCallback(isSelectionMode) {
            override fun handleOnBackPressed() {
                // Exit selection mode instead of navigating back
                exitSelection()
            }
        }

        backDispatcher?.addCallback(callback)

        onDispose {
            callback.remove()
        }
    }

    // Institution-related states
    val institutionLoading by institutionViewModel.loading.collectAsState()
    val institutionError by institutionViewModel.error.collectAsState()
    val requisitionLink by institutionViewModel.requisitionLink.collectAsState()

    // Effect to load transactions and reauth states
    LaunchedEffect(userId, groupId) {
        try {
            isLoading = true
            error = null

            userId?.let { id ->
                // Load transactions and reauth states concurrently
                val transactionsDeferred = async { transactionViewModel.fetchTransactions(id) }
                val reauthDeferred = async { transactionViewModel.getAccountsNeedingReauth(id) }

                // Load already added transaction IDs for this group
                transactionViewModel.loadAddedTransactionIds(groupId)

                // Await other operations
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

    // Function to toggle item selection
    fun toggleSelection(transactionId: String) {
        selectedTransactions = if (selectedTransactions.contains(transactionId)) {
            selectedTransactions - transactionId
        } else {
            selectedTransactions + transactionId
        }

        // Exit selection mode if no items are selected
        if (selectedTransactions.isEmpty()) {
            isSelectionMode = false
        }
    }

    // Function to exit selection mode
    fun exitSelectionMode() {
        isSelectionMode = false
        selectedTransactions = emptySet()
    }

    // Function to select all transactions
    fun selectAll() {
        selectedTransactions = transactions.map { it.transactionId }.toSet()
    }

    // Function to deselect all transactions
    fun deselectAll() {
        selectedTransactions = emptySet()
        isSelectionMode = false
    }

    fun addSelectedTransactions() {
        if (selectedTransactions.isEmpty()) return

        // Show loading indicator
        isLoading = true

        if (selectedTransactions.size == 1) {
            // For a single selection, use the regular flow
            val transaction = transactions.first { it.transactionId in selectedTransactions }
            handleTransactionClick(
                transaction = transaction,
                groupId = groupId,
                navController = navController,
                transactionViewModel = transactionViewModel,
                coroutineScope = coroutineScope
            )
        } else {
            // For multiple selections, use batch processing
            val selectedItems = transactions.filter { it.transactionId in selectedTransactions }

            coroutineScope.launch {
                try {
                    // Process all selected transactions
                    paymentsViewModel.addMultipleTransactions(
                        transactions = selectedItems,
                        groupId = groupId,
                        onComplete = { result ->
                            result.fold(
                                onSuccess = { count ->
                                    // Pass result back through navigation
                                    navController.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set("batchAddCount", count)

                                    // Navigate back
                                    navController.popBackStack()
                                },
                                onFailure = { failureError ->
                                    // Show error - correctly reference the error var
                                    isLoading = false
                                    error = failureError.message

                                    Log.e("AddExpenseScreen", "Failed to process transactions: ${failureError.message}")
                                }
                            )
                        }
                    )
                } catch (e: Exception) {
                    // Handle errors - correctly reference the error var
                    isLoading = false
                    error = e.message
                    Log.e("AddExpenseScreen", "Error processing transactions", e)
                }
            }
        }
    }

    // Custom top app bar based on selection mode
    val topBar: @Composable () -> Unit = {
        if (isSelectionMode) {
            TopAppBar(
                title = {
                    Text(
                        "${selectedTransactions.size} selected",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { exitSelectionMode() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit selection mode"
                        )
                    }
                },
                actions = {
                    // Toggle between select all and deselect all
                    IconButton(
                        onClick = {
                            if (selectedTransactions.size < transactions.size) {
                                selectAll()
                            } else {
                                deselectAll()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SelectAll,
                            contentDescription = if (selectedTransactions.size < transactions.size)
                                "Select all" else "Deselect all"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        } else {
            GlobalTopAppBar(
                title = { Text("Add Expense", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    // Add filter toggle
                    IconButton(
                        onClick = { transactionViewModel.toggleShowAlreadyAdded() }
                    ) {
                        Icon(
                            imageVector = if (showAlreadyAdded)
                                Icons.Default.FilterAlt
                            else
                                Icons.Default.FilterAltOff,
                            contentDescription = if (showAlreadyAdded)
                                "Filter out already added transactions"
                            else
                                "Show all transactions"
                        )
                    }
                }
            )
        }
    }

    Scaffold(
        topBar = topBar,
        floatingActionButton = {
            // Only show FAB in selection mode when items are selected
            AnimatedVisibility(
                visible = isSelectionMode && selectedTransactions.isNotEmpty(),
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                FloatingActionButton(
                    onClick = { addSelectedTransactions() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add selected expenses"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Selected")
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Top
            ) {
                // Always show action buttons regardless of selection mode
                ActionButtons(
                    onAddCustom = { navController.navigate("paymentDetails/$groupId/0") },
                    groupId = groupId,
                    navController = navController
                )

                // Show filter indicator when filter is active
                if (!showAlreadyAdded) {
                    FilterChip(
                        selected = true,
                        onClick = { transactionViewModel.toggleShowAlreadyAdded() },
                        label = { Text("Hiding already added") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.FilterAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

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
                                        baseUrl = "trego://bankaccounts",
                                        currentRoute = "addExpense/$groupId"
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
                                    baseUrl = "trego://bankaccounts",
                                    currentRoute = "addExpense/$groupId"
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Handle requisition link processing
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

                val displayedTransactions = remember(transactions, addedTransactionIds, showAlreadyAdded) {
                    if (showAlreadyAdded) {
                        transactions
                    } else {
                        transactions.filter { it.transactionId !in addedTransactionIds }
                    }
                }

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
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (displayedTransactions.isEmpty() && !isLoading) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (transactions.isEmpty())
                                                    "No transactions available"
                                                else
                                                    "No matching transactions found",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    items(
                                        items = displayedTransactions,
                                        key = { it.transactionId }
                                    ) { transaction ->
                                        val isSelected = selectedTransactions.contains(transaction.transactionId)
                                        val isAlreadyAdded = transaction.transactionId in addedTransactionIds

                                        TransactionItem(
                                            transaction = transaction,
                                            context = context,
                                            isSelected = isSelected,
                                            isAlreadyAdded = isAlreadyAdded,
                                            onClick = {
                                                if (isSelectionMode) {
                                                    toggleSelection(transaction.transactionId)
                                                } else {
                                                    handleTransactionClick(
                                                        transaction,
                                                        groupId,
                                                        navController,
                                                        transactionViewModel,
                                                        coroutineScope
                                                    )
                                                }
                                            },
                                            onLongClick = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (!isSelectionMode) {
                                                    isSelectionMode = true
                                                    selectedTransactions = setOf(transaction.transactionId)
                                                } else {
                                                    toggleSelection(transaction.transactionId)
                                                }
                                            }
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }
                            }

                            // Add scroll to top button in the top-right corner
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .wrapContentSize(align = Alignment.TopEnd)
                            ) {
                                ScrollToTopButton(
                                    listState = listState,
                                    modifier = Modifier.padding(top = 4.dp, end = 4.dp)
                                )
                            }

                            // If in selection mode, show a help text at the bottom
                            if (isSelectionMode) {
                                Card(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                ) {
                                    Text(
                                        text = "Tap to select/deselect • Long-press to add more • Back to exit selection",
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                    )
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
private fun ActionButtons(
    onAddCustom: () -> Unit,
    groupId: Int,
    navController: NavController
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton(
            icon = Icons.Default.Add,
            text = "Add custom expense",
            onClick = onAddCustom
        )
        ActionButton(
            icon = Icons.Default.AccountBalance,
            text = "Connect another card or bank account",
            onClick = {
                val currentRoute = "addExpense/$groupId"
                navController.navigate("institutions?returnRoute=${Uri.encode(currentRoute)}")
            }
        )
    }
}

@Composable
fun ActionButton(
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