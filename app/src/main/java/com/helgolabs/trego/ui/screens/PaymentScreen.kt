package com.helgolabs.trego.ui.screens

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.entities.TransactionEntity
import com.helgolabs.trego.data.model.Transaction
import com.helgolabs.trego.ui.components.ConvertCurrencyButton
import com.helgolabs.trego.ui.components.CurrencySelectionBottomSheet
import com.helgolabs.trego.ui.components.GlobalDatePickerDialog
import com.helgolabs.trego.ui.components.GlobalFAB
import com.helgolabs.trego.ui.components.GlobalTopAppBar
import com.helgolabs.trego.ui.theme.GlobalTheme
import com.helgolabs.trego.ui.viewmodels.PaymentsViewModel
import com.helgolabs.trego.ui.viewmodels.PaymentsViewModel.PaymentAction
import com.helgolabs.trego.ui.viewmodels.UserViewModel
import com.helgolabs.trego.utils.CurrencyUtils
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.FormattingUtils.formatPaymentAmount
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.launch
import java.util.*

@Composable
fun PaymentScreen(
    navController: NavController,
    groupId: Int,
    paymentId: Int,
    context: Context
) {
    val myApplication = context.applicationContext as MyApplication
    val paymentsViewModel: PaymentsViewModel = viewModel(factory = myApplication.viewModelFactory)
    val userViewModel: UserViewModel = viewModel(factory = myApplication.viewModelFactory)
    val paymentRepository = myApplication.paymentRepository
    val transactionRepository = myApplication.transactionRepository
    val reviewManager = myApplication.reviewManager
    val coroutineScope = rememberCoroutineScope()

    // Get Activity context safely
    val activity = LocalContext.current as? ComponentActivity

    val userId = getUserIdFromPreferences(context)
    val screenState by paymentsViewModel.paymentScreenState.collectAsState()
    val users by userViewModel.users.collectAsState(emptyList())
    val navigationState by paymentsViewModel.navigationState.collectAsState()
    var showCurrencySheet by remember { mutableStateOf(false) }

    val editablePayment = screenState.editablePayment
    val editableSplits = screenState.editableSplits
    val groupMembers = screenState.groupMembers
    val paymentOperationStatus = screenState.paymentOperationStatus

    // Retrieve transaction details from previous screen
    val transactionDetails = navController.currentBackStackEntry?.arguments?.let { args ->
        PaymentsViewModel.TransactionDetails(
            transactionId = args.getString("transactionId"),
            amount = args.getString("amount")?.toDoubleOrNull(),
            description = args.getString("description"),
            creditorName = args.getString("creditorName"),
            currency = args.getString("currency"),
            bookingDateTime = args.getString("bookingDateTime"),
            institutionId = args.getString("institutionId")
        )
    }

    LaunchedEffect(paymentId) {
        if (userId != null) {
            // Check TransactionCache first for the transaction
            transactionDetails?.transactionId?.let { transactionId ->
                val cachedTransactions = TransactionCache.getTransactions()
                val cachedTransaction = cachedTransactions?.find { it.transactionId == transactionId }

                if (cachedTransaction != null) {
                    Log.d("PaymentScreen", "Found transaction in cache: $transactionId")
                    try {
                        // Save cached transaction to local database
                        transactionRepository.saveTransaction(cachedTransaction)
                            .onSuccess {
                                Log.d("PaymentScreen", "Successfully saved cached transaction $transactionId")
                            }
                            .onFailure { error ->
                                Log.e("PaymentScreen", "Failed to save cached transaction", error)
                            }
                    } catch (e: Exception) {
                        Log.e("PaymentScreen", "Error saving cached transaction", e)
                    }
                } else {
                    Log.d("PaymentScreen", "Transaction not found in cache, will create new")
                    // Create new transaction if not found in cache
                    val newTransaction = Transaction(
                        transactionId = transactionId,
                        userId = userId,
                        description = transactionDetails.description,
                        amount = transactionDetails.amount ?: 0.0,
                        currency = transactionDetails.currency,
                        bookingDateTime = transactionDetails.bookingDateTime,
                        creditorName = transactionDetails.creditorName,
                        institutionId = transactionDetails.institutionId,
                        createdAt = DateUtils.getCurrentTimestamp(),
                        updatedAt = DateUtils.getCurrentTimestamp(),
                        accountId = null,
                        bookingDate = null,
                        debtorName = null,
                        creditorAccount = null,
                        institutionName = null,
                        internalTransactionId = null,
                        proprietaryBankTransactionCode = null,
                        remittanceInformationUnstructured = null,
                        valueDate = null
                    )

                    try {
                        transactionRepository.saveTransaction(newTransaction)
                            .onSuccess {
                                Log.d("PaymentScreen", "Successfully saved new transaction $transactionId")
                            }
                            .onFailure { error ->
                                Log.e("PaymentScreen", "Failed to save new transaction", error)
                            }
                    } catch (e: Exception) {
                        Log.e("PaymentScreen", "Error saving new transaction", e)
                    }
                }
            }

            // Then initialize the payment screen
            paymentsViewModel.initializePaymentScreen(paymentId, groupId, transactionDetails)
        }
    }

    LaunchedEffect(groupMembers) {
        // Load users for all group members
        if (groupMembers.isNotEmpty()) {
            userViewModel.loadUsers(groupMembers.map { it.userId })
        }
    }

    // Handle navigation and review prompt
    LaunchedEffect(navigationState) {
        when (navigationState) {
            is PaymentsViewModel.NavigationState.NavigateBack -> {
                // If payment was successful (check paymentOperationStatus)
                if (screenState.paymentOperationStatus is PaymentsViewModel.PaymentOperationStatus.Success) {
                    // Show review prompt
                    activity?.let {
                        coroutineScope.launch {
                            reviewManager.maybeAskForReview(it)
                        }
                    }
                }
                navController.popBackStack()
                paymentsViewModel.resetNavigationState()
            }
            else -> {}
        }
    }

    val focusRequesterDescription = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Handle result from currency selection screen
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    savedStateHandle?.getLiveData<String>("currency")?.observe(navController.currentBackStackEntry!!) { selectedCurrency ->
        paymentsViewModel.processAction(PaymentAction.UpdateCurrency(selectedCurrency))
    }

    fun getCurrencySymbol(currencyCode: String): String {
        return CurrencyUtils.currencySymbols[currencyCode] ?: currencyCode
    }


    GlobalTheme {
        Scaffold(
            topBar = {
                GlobalTopAppBar(
                    title = { Text(if (paymentId == 0) "Add Payment" else "Edit Payment") },
                    actions = {
                        if (paymentId == 0) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.Filled.Close, contentDescription = "Discard Payment", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        } else {
                            IconButton(onClick = { paymentsViewModel.processAction(PaymentAction.ShowDeleteDialog) }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Archive Payment",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                GlobalFAB(
                    onClick = { paymentsViewModel.savePayment() },
                    icon = { Icon(Icons.Filled.Add, contentDescription = "Save") },
                    text = if (paymentId == 0) "Add Payment" else "Save Changes"
                )
            },
            content = { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    item {
                        // Top row with payer's name, payment type, and amount
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (editablePayment?.paidByUserId == userId) "I" else users.find { it.userId == editablePayment?.paidByUserId }?.username
                                    ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .clickable { paymentsViewModel.processAction(PaymentsViewModel.PaymentAction.ToggleExpandedPaidByUserList) }
                                    .padding(end = 8.dp)
                            )

                            DropdownMenu(
                                expanded = screenState.expandedPaidByUserList,
                                onDismissRequest = {
                                    paymentsViewModel.processAction(
                                        PaymentAction.ToggleExpandedPaidByUserList
                                    )
                                }
                            ) {
                                groupMembers.forEach { member ->
                                    val user = users.find { it.userId == member.userId }
                                    val username = user?.username ?: member.userId.toString()
                                    DropdownMenuItem(
                                        text = { Text(text = if (member.userId == userId) "I" else username) },
                                        onClick = {
                                            paymentsViewModel.processAction(
                                                PaymentsViewModel.PaymentAction.UpdatePaidByUser(
                                                    member.userId
                                                )
                                            )
                                            paymentsViewModel.processAction(PaymentsViewModel.PaymentAction.ToggleExpandedPaidByUserList)
                                        }
                                    )
                                }
                            }

                            TextButton(
                                onClick = { paymentsViewModel.processAction(PaymentsViewModel.PaymentAction.ToggleExpandedPaymentTypeList) },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    editablePayment?.paymentType ?: "",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            PaymentTypeDropdownMenu(viewModel = paymentsViewModel)

                            // Currency Selector
                            Text(
                                text = getCurrencySymbol(editablePayment?.currency ?: "USD"),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .clickable { showCurrencySheet = true }
                                    .padding(end = 8.dp)
                            )

                            OutlinedTextField(
                                value = formatPaymentAmount(
                                    editablePayment?.amount?.toString() ?: ""
                                ),
                                onValueChange = { newValue ->
                                    val sanitizedInput =
                                        newValue.filter { char -> char.isDigit() || char == '.' }
                                    if (sanitizedInput.count { it == '.' } <= 1 && sanitizedInput.length <= 10) {
                                        paymentsViewModel.processAction(
                                            PaymentsViewModel.PaymentAction.UpdateAmount(
                                                sanitizedInput.toDoubleOrNull() ?: 0.0
                                            )
                                        )
                                    }
                                },
                                label = { Text("Amount") },
                                modifier = Modifier.weight(1f),
                                enabled = !screenState.shouldLockUI,
                                keyboardOptions = KeyboardOptions.Default.copy(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = {
                                        focusManager.moveFocus(FocusDirection.Down)
                                    }
                                ),
                            )
                        }

                        //Convert currency button
                        if (editablePayment != null && editablePayment.id != 0) {  // Check if this is an existing payment
                            val group = paymentsViewModel.getGroup()
                            val groupDefaultCurrency = group?.defaultCurrency ?: "GBP"

                            if (editablePayment.currency != groupDefaultCurrency) {
                                ConvertCurrencyButton(
                                    currentCurrency = editablePayment.currency ?: "GBP",
                                    targetCurrency = groupDefaultCurrency,
                                    amount = editablePayment.amount,
                                    isConverting = screenState.isConverting,
                                    conversionError = screenState.conversionError,
                                    onConvertClicked = { confirmed, customRate ->
                                        if (confirmed) {
                                            paymentsViewModel.convertPaymentCurrency(
                                                useCustomRate = customRate != null,
                                                customRate = customRate
                                            )
                                        }
                                    },
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }

                        if (editablePayment?.paymentType == "transferred") {
                            Text(
                                text = if (screenState.paidToUser == userId) {
                                    "me"
                                } else {
                                    users.find { it.userId == screenState.paidToUser }?.username
                                        ?: users.find { it.userId != userId }?.username ?: ""
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .clickable { paymentsViewModel.processAction(PaymentsViewModel.PaymentAction.ToggleExpandedPaidToUserList) }
                                    .padding(end = 8.dp)
                            )
                            DropdownMenu(
                                expanded = screenState.expandedPaidToUserList,
                                onDismissRequest = {
                                    paymentsViewModel.processAction(
                                        PaymentsViewModel.PaymentAction.ToggleExpandedPaidToUserList
                                    )
                                }
                            ) {
                                groupMembers.forEach { member ->
                                    val user = users.find { it.userId == member.userId }
                                    val username = user?.username ?: member.userId.toString()
                                    DropdownMenuItem(
                                        text = { Text(text = if (member.userId == userId) "I" else username) },
                                        onClick = {
                                            paymentsViewModel.processAction(
                                                PaymentsViewModel.PaymentAction.UpdatePaidToUser(
                                                    member.userId
                                                )
                                            )
                                            paymentsViewModel.processAction(PaymentsViewModel.PaymentAction.ToggleExpandedPaidToUserList)
                                        }
                                    )
                                }
                            }
                        } else {
                            TextField(
                                value = editablePayment?.description ?: "",
                                onValueChange = {
                                    paymentsViewModel.processAction(
                                        PaymentsViewModel.PaymentAction.UpdateDescription(
                                            it
                                        )
                                    )
                                },
                                label = { Text("Description") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequesterDescription),
                                enabled = !screenState.isTransaction,
                                keyboardOptions = KeyboardOptions.Default.copy(
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                    }
                                )
                            )
                        }

                        TextField(
                            value = editablePayment?.notes ?: "",
                            onValueChange = {
                                paymentsViewModel.processAction(
                                    PaymentsViewModel.PaymentAction.UpdateNotes(
                                        it
                                    )
                                )
                            },
                            label = { Text("Notes") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions.Default.copy(
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                }
                            )
                        )
                        GlobalDatePickerDialog(
                            date = editablePayment?.paymentDate.toString() ?: "",
                            enabled = !screenState.isTransaction,
                            onDateChange = { newDate ->
                                paymentsViewModel.processAction(
                                    PaymentsViewModel.PaymentAction.UpdatePaymentDate(
                                        newDate
                                    )
                                )
                            },
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (screenState.hasBeenConverted) {
                            screenState.originalCurrency?.let { currency ->
                                CurrencyConvertedCard(
                                    originalCurrency = currency,
                                    onUndo = {
                                        paymentsViewModel.undoCurrencyConversion()
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }

                        if (screenState.shouldShowSplitUI) {

                            // Split Mode Dropdown
                            Text(
                                "Split Mode:",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            var expanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { expanded = true }) {
                                    Text(
                                        editablePayment?.splitMode ?: "",
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    DropdownMenuItem(
                                        onClick = {
                                            paymentsViewModel.processAction(
                                                PaymentsViewModel.PaymentAction.UpdateSplitMode(
                                                    "equally"
                                                )
                                            )
                                            expanded = false
                                        },
                                        text = { Text("equally") }
                                    )
                                    DropdownMenuItem(
                                        onClick = {
                                            paymentsViewModel.processAction(
                                                PaymentsViewModel.PaymentAction.UpdateSplitMode(
                                                    "unequally"
                                                )
                                            )
                                            expanded = false
                                        },
                                        text = { Text("unequally") }
                                    )
                                }
                            }


                            // NEW SECTION: Member Selection
                            val selectedMembers = screenState.selectedMembers

                            Text(
                                "Select Members to Split With:",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            Column(
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                groupMembers.forEach { member ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val username = when (member.userId) {
                                            userId -> "Me"
                                            else -> users.find { it.userId == member.userId }?.username
                                                ?: "Loading..."
                                        }
                                        Text(
                                            text = username,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Checkbox(
                                            checked = selectedMembers.contains(member),
                                            onCheckedChange = { checked ->
                                                val newSelection = if (checked) {
                                                    selectedMembers + member
                                                } else {
                                                    selectedMembers - member
                                                }
                                                paymentsViewModel.processAction(
                                                    PaymentsViewModel.PaymentAction.UpdateSelectedMembers(
                                                        newSelection
                                                    )
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            // Modified splits display - only show for selected members
                            editableSplits
                                .filter { split -> selectedMembers.any { it.userId == split.userId } }
                                .forEach { split ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = when (split.userId) {
                                                userId -> "Me"
                                                else -> users.find { it.userId == split.userId }?.username
                                                    ?: "Loading..."
                                            },
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        TextField(
                                            value = formatPaymentAmount(split.amount.toString()),
                                            onValueChange = { input ->
                                                val newAmount =
                                                    input.filter { char -> char.isDigit() || char == '.' }
                                                        .toDoubleOrNull() ?: 0.0
                                                paymentsViewModel.processAction(
                                                    PaymentsViewModel.PaymentAction.UpdateSplit(
                                                        split.userId,
                                                        newAmount
                                                    )
                                                )
                                            },
                                            modifier = Modifier.width(100.dp),
                                            enabled = editablePayment?.splitMode == "unequally",
                                            leadingIcon = {
                                                Text(
                                                    getCurrencySymbol(
                                                        editablePayment?.currency ?: "GBP"
                                                    )
                                                )
                                            },
                                            keyboardOptions = KeyboardOptions.Default.copy(
                                                keyboardType = KeyboardType.Number
                                            )
                                        )
                                    }
                                }
                        }

                        if (screenState.showDeleteDialog) {
                            AlertDialog(
                                onDismissRequest = {
                                    paymentsViewModel.processAction(
                                        PaymentsViewModel.PaymentAction.HideDeleteDialog
                                    )
                                },
                                title = { Text("Delete Payment") },
                                text = { Text("Are you sure you want to delete this payment?") },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            paymentsViewModel.archivePayment()
                                        }
                                    ) {
                                        Text("Delete")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = {
                                        paymentsViewModel.processAction(
                                            PaymentsViewModel.PaymentAction.HideDeleteDialog
                                        )
                                    }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }
                }
                if (showCurrencySheet) {
                    CurrencySelectionBottomSheet(
                        onDismiss = { showCurrencySheet = false },
                        onCurrencySelected = { selectedCurrency ->
                            paymentsViewModel.processAction(PaymentAction.UpdateCurrency(selectedCurrency))
                        }
                    )
                }
            }
        )
    }
}

@Composable
fun PaymentTypeDropdownMenu(viewModel: PaymentsViewModel) {
    val screenState by viewModel.paymentScreenState.collectAsState()

    DropdownMenu(
        expanded = screenState.expandedPaymentTypeList,
        onDismissRequest = { viewModel.processAction(PaymentAction.ToggleExpandedPaymentTypeList) }
    ) {
        listOf("spent", "received", "transferred").forEach { type ->
            DropdownMenuItem(
                text = { Text(type) },
                onClick = {
                    when (type) {
                        "spent" -> {
                            viewModel.processAction(PaymentAction.UpdatePaymentType("spent"))
                            viewModel.processAction(PaymentAction.UpdateAmount(
                                screenState.payment?.amount?.let { kotlin.math.abs(it) } ?: 0.0
                            ))
                        }
                        "received" -> {
                            viewModel.processAction(PaymentAction.UpdatePaymentType("received"))
                            viewModel.processAction(PaymentAction.UpdateAmount(
                                screenState.payment?.amount?.let { kotlin.math.abs(it) * -1 } ?: 0.0
                            ))
                        }
                        "transferred" -> {
                            // Find first available user who isn't the payer to be the recipient
                            val currentPayer = screenState.editablePayment?.paidByUserId
                            val defaultRecipient = screenState.groupMembers
                                .firstOrNull { it.userId != currentPayer }
                                ?.userId

                            // Set the recipient first
                            if (defaultRecipient != null) {
                                viewModel.processAction(PaymentAction.UpdatePaidToUser(defaultRecipient))
                            }

                            // Then update the payment type
                            viewModel.processAction(PaymentAction.UpdatePaymentType("transferred"))

                        }
                    }
                    viewModel.processAction(PaymentAction.ToggleExpandedPaymentTypeList)
                }
            )
        }
    }
}

@Composable
fun CurrencyConvertedCard(
    originalCurrency: String,
    onUndo: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "This payment cannot be edited because it was converted from $originalCurrency",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Button(
                onClick = onUndo,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onErrorContainer,
                    contentColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text("Undo Conversion to Edit")
            }
        }
    }
}
