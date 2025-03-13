package com.helgolabs.trego.ui.screens

import android.content.Context
import android.icu.text.NumberFormat
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.model.Transaction
import com.helgolabs.trego.ui.components.ConvertCurrencyButton
import com.helgolabs.trego.ui.components.CurrencySelectionBottomSheet
import com.helgolabs.trego.ui.components.GlobalDatePickerDialog
import com.helgolabs.trego.ui.components.GlobalFAB
import com.helgolabs.trego.ui.components.GlobalTopAppBar
import com.helgolabs.trego.ui.components.GroupMemberSplits
import com.helgolabs.trego.ui.components.PayerDropdown
import com.helgolabs.trego.ui.components.PaymentTypeDropdown
import com.helgolabs.trego.ui.components.RecipientDropdown
import com.helgolabs.trego.ui.components.SplitModeDropdown
import com.helgolabs.trego.ui.theme.GlobalTheme
import com.helgolabs.trego.ui.viewmodels.PaymentsViewModel
import com.helgolabs.trego.ui.viewmodels.PaymentsViewModel.PaymentAction
import com.helgolabs.trego.ui.viewmodels.UserViewModel
import com.helgolabs.trego.utils.CurrencyUtils
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.getCurrencySymbol
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                             CurrencyButton(
                                 currencyCode = editablePayment?.currency ?: "GBP",
                                 onClick = { showCurrencySheet = true }
                             )

                            PaymentAmountField(
                                amount = editablePayment?.amount ?: 0.0,
                                onAmountChange = { newAmount ->
                                    paymentsViewModel.processAction(
                                        PaymentsViewModel.PaymentAction.UpdateAmount(newAmount)
                                    )
                                },
                                enabled = !screenState.shouldLockUI,
                                focusManager = focusManager
                            )
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = editablePayment?.description ?: "",
                                onValueChange = {
                                    paymentsViewModel.processAction(
                                        PaymentAction.UpdateDescription(it)
                                    )
                                },
                                label = { Text("Description") },
                                modifier = Modifier
                                    .weight(3f)
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

                            Spacer(modifier = Modifier.width(8.dp))

                            GlobalDatePickerDialog(
                                date = editablePayment?.paymentDate.toString() ?: "",
                                enabled = !screenState.isTransaction,
                                onDateChange = { newDate ->
                                    paymentsViewModel.processAction(
                                        PaymentAction.UpdatePaymentDate(newDate)
                                    )
                                },
                                modifier = Modifier.weight(2f)
                            )
                        }
                    }

                    item {
                        TextField(
                            value = editablePayment?.notes ?: "",
                            onValueChange = {
                                paymentsViewModel.processAction(
                                    PaymentAction.UpdateNotes(
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
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (editablePayment?.paymentType == "transferred") {

                                PaymentTypeDropdown(paymentsViewModel)

                                Text("out of app by")

                                PayerDropdown(paymentsViewModel, userId, users)

                                Text("to")

                                RecipientDropdown(paymentsViewModel, userId, users)


                            } else {

                                PaymentTypeDropdown(paymentsViewModel)

                                Text("by")

                                PayerDropdown(paymentsViewModel, userId, users)

                                Text("and split")

                                SplitModeDropdown(paymentsViewModel)
                            }
                        }
                    }

                    item {
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

                            PaymentSplitSection(
                                paymentsViewModel,
                                userViewModel,
                                context
                            )
                        }

                        if (screenState.showDeleteDialog) {
                            AlertDialog(
                                onDismissRequest = {
                                    paymentsViewModel.processAction(
                                        PaymentAction.HideDeleteDialog
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

@Composable
fun CurrencyButton(
    currencyCode: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
            .clickable(onClick = onClick)
    ) {
        Text(
            text = getCurrencySymbol(currencyCode),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun PaymentAmountField(
    amount: Double,
    onAmountChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusManager: FocusManager,
    textStyle: TextStyle = LocalTextStyle.current.copy(
        fontSize = 40.sp,
        fontWeight = FontWeight.Normal
    )
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused = interactionSource.collectIsFocusedAsState().value

    // Use TextFieldValue to track both text content and cursor position
    var textFieldValue by remember { mutableStateOf(TextFieldValue()) }

    // Initialize from provided amount
    LaunchedEffect(amount) {
        if (!isFocused) {
            // Only update when not focused to prevent cursor jumping
            if (amount != 0.0) {
                // Format with 2 decimal places and include commas
                val formattedText = NumberFormat.getNumberInstance(Locale.US).apply {
                    maximumFractionDigits = 2
                    minimumFractionDigits = 2
                }.format(amount)

                textFieldValue = TextFieldValue(
                    text = formattedText,
                    selection = TextRange(formattedText.length) // Place cursor at end
                )
            } else {
                textFieldValue = TextFieldValue(text = "")
            }
        }
    }

    // The main text field
    BasicTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            // Store original cursor position and text
            val originalPosition = newValue.selection.start
            val originalText = newValue.text

            // Only allow digits, decimal point and commas in the input
            val cleanInput = originalText.filter { it.isDigit() || it == '.' || it == ',' }

            // Split by decimal point for validation
            val parts = cleanInput.split(".")

            if (parts.size <= 2) { // Ensure only one decimal point
                val integerPartWithCommas = parts[0]
                val decimalPart = if (parts.size > 1) parts[1].take(2) else ""

                // Extract just digits from integer part
                val integerDigitsOnly = integerPartWithCommas.filter { it.isDigit() }

                // Format integer part with proper commas
                val formattedInteger = if (integerDigitsOnly.isEmpty()) "" else {
                    try {
                        NumberFormat.getNumberInstance(Locale.US).format(integerDigitsOnly.toLong())
                    } catch (e: Exception) {
                        integerPartWithCommas // Keep existing value on error
                    }
                }

                // Construct the new formatted input
                val validInput = if (decimalPart.isEmpty()) {
                    if (cleanInput.endsWith(".")) {
                        "$formattedInteger."
                    } else {
                        formattedInteger
                    }
                } else {
                    "$formattedInteger.$decimalPart"
                }

                // Calculate cursor adjustment based on comma differences
                val beforeCursorOriginal = originalText.take(originalPosition)
                val beforeCursorValidated = validInput.take(minOf(originalPosition, validInput.length))

                // Count commas in both before-cursor segments
                val originalCommas = beforeCursorOriginal.count { it == ',' }
                val newCommas = beforeCursorValidated.count { it == ',' }
                val commaDifference = newCommas - originalCommas

                // Adjust cursor position for added/removed commas
                val adjustedPosition = minOf(
                    originalPosition + commaDifference,
                    validInput.length
                )

                // Update with new text and adjusted cursor position
                textFieldValue = TextFieldValue(
                    text = validInput,
                    selection = TextRange(adjustedPosition)
                )

                // Extract numeric value without commas for the callback
                val numericString = validInput.replace(",", "")
                val numericValue = if (numericString.isEmpty() || numericString == ".") 0.0 else {
                    try {
                        numericString.toDouble()
                    } catch (e: NumberFormatException) {
                        0.0
                    }
                }

                onAmountChange(numericValue)
            }
        },
        textStyle = textStyle.copy(
            color = Color.Transparent // Make the actual input invisible
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions.Default.copy(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(
            onNext = {
                focusManager.moveFocus(FocusDirection.Down)
            }
        ),
        interactionSource = interactionSource,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                // Display formatted text based on state
                when {
                    // Empty and focused/unfocused - show placeholder
                    textFieldValue.text.isEmpty() -> {
                        Text(
                            text = "0.00",
                            style = textStyle,
                            color = if (isFocused) Color.LightGray else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    // Has value - show formatted input with placeholders for decimals
                    else -> {
                        // Parse the current input
                        val hasDecimalPoint = textFieldValue.text.contains(".")
                        val parts = textFieldValue.text.split(".")
                        val integerPart = parts[0]
                        val decimalPart = if (parts.size > 1) parts[1] else ""

                        // Complex layout to properly show placeholders with real input
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Integer part (with commas already included)
                            Text(
                                text = integerPart,
                                style = textStyle,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Decimal point
                            Text(
                                text = ".",
                                style = textStyle,
                                color = if (hasDecimalPoint) MaterialTheme.colorScheme.onSurface else Color.LightGray
                            )

                            // First decimal place
                            if (decimalPart.isNotEmpty()) {
                                Text(
                                    text = decimalPart.first().toString(),
                                    style = textStyle,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                Text(
                                    text = "0",
                                    style = textStyle,
                                    color = Color.LightGray
                                )
                            }

                            // Second decimal place
                            if (decimalPart.length > 1) {
                                Text(
                                    text = decimalPart[1].toString(),
                                    style = textStyle,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                Text(
                                    text = "0",
                                    style = textStyle,
                                    color = Color.LightGray
                                )
                            }
                        }
                    }
                }

                // The actual invisible input field
                Box {
                    innerTextField()
                }
            }
        }
    )

    // Request focus on initial display
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
fun PaymentSplitSection(
    paymentsViewModel: PaymentsViewModel,
    userViewModel: UserViewModel,
    context: Context
) {
    // Get ViewModels and user ID
    val userId = getUserIdFromPreferences(context)
    val screenState by paymentsViewModel.paymentScreenState.collectAsState()

    // Only show the splits UI for appropriate payment types
    if (screenState.shouldShowSplitUI) {
        Card {
            Column {
                Text("Split With")

                // Group member splits component
                GroupMemberSplits(
                    paymentViewModel = paymentsViewModel,
                    userViewModel = userViewModel,
                    currentUserId = userId
                )
            }
        }
    }
}