package com.helgolabs.trego.ui.screens

import android.content.Context
import android.icu.text.NumberFormat
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.dataClasses.PreferenceKeys
import com.helgolabs.trego.data.model.GroupMember
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
import com.helgolabs.trego.ui.viewmodels.UserPreferencesViewModel
import com.helgolabs.trego.ui.viewmodels.UserViewModel
import com.helgolabs.trego.utils.ConfigureForNumericInput
import com.helgolabs.trego.utils.CurrencyUtils
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.getCurrencySymbol
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun PaymentScreen(
    navController: NavController,
    groupId: Int,
    paymentId: Int,
    context: Context
) {
    ConfigureForNumericInput()

    val myApplication = context.applicationContext as MyApplication
    val paymentsViewModel: PaymentsViewModel = viewModel(factory = myApplication.viewModelFactory)
    val userViewModel: UserViewModel = viewModel(factory = myApplication.viewModelFactory)
    val transactionRepository = myApplication.transactionRepository
    val reviewManager = myApplication.reviewManager
    val coroutineScope = rememberCoroutineScope()

    // Get Activity context safely
    val activity = LocalContext.current as? ComponentActivity
    val userPreferencesViewModel: UserPreferencesViewModel = viewModel(factory = myApplication.viewModelFactory)
    val themeMode by userPreferencesViewModel.themeMode.collectAsState(initial = PreferenceKeys.ThemeMode.SYSTEM)

    val userId = getUserIdFromPreferences(context)
    val screenState by paymentsViewModel.paymentScreenState.collectAsState()
    val users by userViewModel.users.collectAsState(emptyList())
    val navigationState by paymentsViewModel.navigationState.collectAsState()
    val group = paymentsViewModel.getGroup()
    val groupDefaultCurrency = group?.defaultCurrency ?: "GBP"
    var showCurrencySheet by remember { mutableStateOf(false) }

    val editablePayment = screenState.editablePayment
    val editableSplits = screenState.editableSplits
    val groupMembers = screenState.groupMembers
    val paymentOperationStatus = screenState.paymentOperationStatus

    // For keyboard and scrolling
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()

    // Track if any field in the split section is focused
    var isSplitSectionFocused by remember { mutableStateOf(false) }

    // Split section reference for scrolling
    val splitSectionKey = remember { Any() }

    // Exchange rates for previewing conversion rates and amounts
    val exchangeRate by paymentsViewModel.exchangeRate.collectAsState(null)
    val exchangeRateDate by paymentsViewModel.exchangeRateDate.collectAsState(null)
    var showConversionSheetTrigger by remember { mutableStateOf(false) }
    var showSaveBeforeConvertDialog by remember { mutableStateOf(false) }
    var showConversionSheetAfterSave by remember { mutableStateOf(false) }

    // For handling unsaved changes
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    var pendingNavigationAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Function to check for unsaved changes before navigation
    fun checkUnsavedChangesBeforeNavigation(navigateAction: () -> Unit) {
        val hasUnsavedChanges = screenState.payment != screenState.editablePayment ||
                screenState.splits != screenState.editableSplits

        if (hasUnsavedChanges) {
            pendingNavigationAction = navigateAction
            showUnsavedChangesDialog = true
        } else {
            // No changes, just navigate
            navigateAction()
        }
    }

    // Handle system back button/gesture
    BackHandler {
        checkUnsavedChangesBeforeNavigation {
            navController.popBackStack()
        }
    }

    // When the split section is focused, ensure we scroll to it
    LaunchedEffect(isSplitSectionFocused) {
        if (isSplitSectionFocused) {
            // Add a small delay to ensure keyboard is shown
            kotlinx.coroutines.delay(300)
            // Scroll down to see the split section
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

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

    LaunchedEffect(editablePayment) {
        Log.d("PaymentScreen", "Payment amount updated: ${editablePayment?.amount}")
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
                // If payment was saved successfully
                if (screenState.paymentOperationStatus is PaymentsViewModel.PaymentOperationStatus.Success) {
                    // If we were in the "save then convert" flow
                    if (showConversionSheetAfterSave) {
                        showConversionSheetAfterSave = false
                        paymentsViewModel.resetNavigationState()

                        // Fetch exchange rate and trigger sheet display
                        paymentsViewModel.fetchExchangeRate(
                            fromCurrency = screenState.payment?.currency ?: "GBP",
                            toCurrency = groupDefaultCurrency,
                            paymentDate = screenState.payment?.paymentDate
                        )

                        // Trigger showing the sheet
                        showConversionSheetTrigger = true
                    } else {
                        // Normal save completion - show review and go back
                        activity?.let {
                            coroutineScope.launch {
                                reviewManager.maybeAskForReview(it)
                            }
                        }
                        navController.popBackStack()
                        paymentsViewModel.resetNavigationState()
                    }
                } else {
                    // If save failed, just navigate back
                    navController.popBackStack()
                    paymentsViewModel.resetNavigationState()
                }
            }
            else -> {}
        }
    }

    // Reset trigger once the ConvertCurrencyButton has processed it
    LaunchedEffect(showConversionSheetTrigger) {
        if (showConversionSheetTrigger) {
            // Give the component time to process the trigger
            delay(100)
            showConversionSheetTrigger = false
        }
    }

    val focusRequesterDescription = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Handle result from currency selection screen
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    savedStateHandle?.getLiveData<String>("currency")?.observe(navController.currentBackStackEntry!!) { selectedCurrency ->
        paymentsViewModel.processAction(PaymentAction.UpdateCurrency(selectedCurrency))
    }


    GlobalTheme(themeMode = themeMode) {
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
                                    contentDescription = "Archive Payment"
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
                // The main scrollable content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        // Use imePadding to ensure content adjusts for keyboard
                        .imePadding()
                        .navigationBarsPadding()
                        .background(color = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .wrapContentWidth(),
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
                                            PaymentAction.UpdateAmount(newAmount)
                                        )
                                    },
                                    enabled = !screenState.shouldLockUI,
                                    focusManager = focusManager
                                )
                            }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
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
                                        capitalization = KeyboardCapitalization.Sentences,
                                        imeAction = ImeAction.Next
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onNext = {
                                            focusManager.moveFocus(FocusDirection.Next)
                                        }
                                    )
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                GlobalDatePickerDialog(
                                    date = DateUtils.extractDatePart(editablePayment?.paymentDate),
                                    enabled = !screenState.isTransaction,
                                    onDateChange = { newDate ->
                                        paymentsViewModel.processAction(
                                            PaymentAction.UpdatePaymentDate(newDate)
                                        )
                                    },
                                    modifier = Modifier.weight(2f)
                                )
                            }
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center // Center content in the Box
                        ) {
                            Row(
                                modifier = Modifier
                                    .wrapContentWidth(), // Only take needed width instead of filling
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp) // Add consistent spacing
                            ) {
                                if (editablePayment?.paymentType == "transferred") {
                                    PaymentTypeDropdown(paymentsViewModel)
                                    Text("out of app by")
                                    PayerDropdown(paymentsViewModel, userId, users)
                                    Text("to")
                                    RecipientDropdown(paymentsViewModel, userId, users)
                                } else if (editablePayment?.paymentType == "received"){
                                    PaymentTypeDropdown(paymentsViewModel)
                                    Text("by")
                                    PayerDropdown(paymentsViewModel, userId, users)
                                    Text("and shared")
                                    SplitModeDropdown(paymentsViewModel)
                                } else {
                                    PaymentTypeDropdown(paymentsViewModel)
                                    Text("by")
                                    PayerDropdown(paymentsViewModel, userId, users)
                                    Text("and split")
                                    SplitModeDropdown(paymentsViewModel)
                                }
                            }
                        }

                        //Convert currency button
                        if (editablePayment != null && editablePayment.id != 0) {  // Check if this is an existing payment

                            if (editablePayment.currency != groupDefaultCurrency) {
                                ConvertCurrencyButton(
                                    currentCurrency = editablePayment.currency ?: "GBP",
                                    targetCurrency = groupDefaultCurrency,
                                    amount = editablePayment.amount,
                                    isConverting = screenState.isConverting,
                                    conversionError = screenState.conversionError,
                                    exchangeRate = exchangeRate,
                                    rateDate = exchangeRateDate?.let { DateUtils.formatForDisplay(it) },
                                    onPrepareConversion = {
                                        val hasUnsavedChanges = screenState.payment != screenState.editablePayment ||
                                                screenState.splits != screenState.editableSplits

                                        if (hasUnsavedChanges) {
                                            showSaveBeforeConvertDialog = true
                                            false // Don't show the sheet yet
                                        } else {
                                            // No changes, proceed with conversion
                                            paymentsViewModel.fetchExchangeRate(
                                                fromCurrency = editablePayment.currency ?: "GBP",
                                                toCurrency = groupDefaultCurrency,
                                                paymentDate = editablePayment.paymentDate
                                            )
                                            true // Show the sheet
                                        }
                                    },
                                    onConvertClicked = { confirmed, customRate ->
                                        if (confirmed) {
                                            paymentsViewModel.convertPaymentCurrency(
                                                useCustomRate = customRate != null,
                                                customRate = customRate
                                            )
                                        }
                                    },
                                    showSheetTrigger = showConversionSheetTrigger, // Connect to our trigger
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }

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
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // Add focus awareness through entire split section
                                    .onFocusChanged { focusState ->
                                        isSplitSectionFocused = focusState.hasFocus
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Split With",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )

                                    // Add focus-tracking wrapper around GroupMemberSplits
                                    WrappedGroupMemberSplits(
                                        paymentViewModel = paymentsViewModel,
                                        userViewModel = userViewModel,
                                        currentUserId = userId,
                                        onFocusChange = { hasFocus ->
                                            isSplitSectionFocused = hasFocus
                                            if (hasFocus) {
                                                coroutineScope.launch {
                                                    // Delay to ensure keyboard is shown
                                                    kotlinx.coroutines.delay(300)
                                                    // Scroll to bottom
                                                    scrollState.animateScrollTo(scrollState.maxValue)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
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
    // And the dialog itself
    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. What would you like to do?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnsavedChangesDialog = false
                        paymentsViewModel.savePayment() // This usually triggers navigation when done
                    }
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showUnsavedChangesDialog = false
                            pendingNavigationAction?.invoke()
                        }
                    ) {
                        Text("Discard")
                    }
                    TextButton(
                        onClick = {
                            showUnsavedChangesDialog = false
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
    if (showSaveBeforeConvertDialog) {
        AlertDialog(
            onDismissRequest = { showSaveBeforeConvertDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. Would you like to save them before converting the currency?") },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveBeforeConvertDialog = false

                        // Perform save
                        paymentsViewModel.savePayment()

                        // Observer NavigationState.NavigateBack will handle this
                        // We'll set a flag to know we should show the conversion sheet after save
                        showConversionSheetAfterSave = true
                    }
                ) {
                    Text("Save & Convert")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showSaveBeforeConvertDialog = false

                            // Discard changes
                            paymentsViewModel.resetEditableState(userId ?: 0)

                            // Fetch exchange rate and show the conversion sheet
                            paymentsViewModel.fetchExchangeRate(
                                fromCurrency = screenState.payment?.currency ?: "GBP",
                                toCurrency = groupDefaultCurrency,
                                paymentDate = screenState.payment?.paymentDate
                            )

                            // Immediately trigger showing the sheet
                            showConversionSheetTrigger = true
                        }
                    ) {
                        Text("Discard Changes")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            showSaveBeforeConvertDialog = false
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
    // Check for error status and display message
    if (paymentOperationStatus is PaymentsViewModel.PaymentOperationStatus.Error) {
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = {
                    // Reset error state
                    paymentsViewModel.resetOperationStatus()
                }) {
                    Text("Dismiss")
                }
            }
        ) {
            Text(paymentOperationStatus.message)
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

@Composable
fun CurrencyButton(
    currencyCode: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp, // Add subtle elevation
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(end = 8.dp) // Add spacing between currency and amount
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
    textStyle: TextStyle = MaterialTheme.typography.displayLarge
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused = interactionSource.collectIsFocusedAsState().value
    val context = LocalContext.current

    // Define maximum value constant
    val MAX_AMOUNT = 9_999_999.99

    // State to show error message
    var showErrorMessage by remember { mutableStateOf(false) }

    // Use TextFieldValue to track both text content and cursor position
    var textFieldValue by remember { mutableStateOf(TextFieldValue()) }

    // Add this key to force recomposition when amount changes
    var lastProcessedAmount by remember { mutableStateOf(0.0) }

    val isNewPayment = amount > 0.0

    // Calculate width based on content
    // Add a baseline minimum width and some padding
    val textToMeasure = if (textFieldValue.text.isEmpty()) {
        "0.00"
    } else {
        // If the text already has a decimal part, use it as is
        // Otherwise, append ".00" to calculate proper width
        if (textFieldValue.text.contains(".")) {
            val parts = textFieldValue.text.split(".")
            val integerPart = parts[0]
            val decimalPart = if (parts.size > 1) parts[1] else ""

            // Ensure we account for both decimal places
            if (decimalPart.length >= 2) {
                textFieldValue.text
            } else if (decimalPart.length == 1) {
                "$integerPart.$decimalPart" + "0"
            } else {
                "$integerPart.00"
            }
        } else {
            // No decimal point, so append ".00"
            textFieldValue.text + ".00"
        }
    }
    val textMeasurer = rememberTextMeasurer()
    val textWidth = with(LocalDensity.current) {
        // Measure the exact width of the text
        val measuredWidth = textMeasurer.measure(
            text = AnnotatedString(textToMeasure),
            style = textStyle
        ).size.width.toDp()

        // Add just a small amount of padding for the cursor
        measuredWidth + 8.dp
    }

    // Handle error message display
    LaunchedEffect(showErrorMessage) {
        if (showErrorMessage) {
            // Show error using Toast
            Toast.makeText(
                context,
                "Amount cannot exceed ${NumberFormat.getCurrencyInstance().format(MAX_AMOUNT)}",
                Toast.LENGTH_LONG
            ).show()

            // Reset the error state after showing message
            delay(100)
            showErrorMessage = false
        }
    }

    // Initialize from provided amount
    LaunchedEffect(amount) {
        // Only update when amount has actually changed
        if (amount != lastProcessedAmount) {
            lastProcessedAmount = amount

            // Only update when not focused to prevent cursor jumping
            if (!isFocused && amount != 0.0) {
                val absAmount = kotlin.math.abs(amount)
                if (absAmount > 0.0) {
                    // Format with 2 decimal places and include commas
                    val formattedText = NumberFormat.getNumberInstance(Locale.US).apply {
                        maximumFractionDigits = 2
                        minimumFractionDigits = 2
                    }.format(absAmount)  // Use absolute value here

                    textFieldValue = TextFieldValue(
                        text = formattedText,
                        selection = TextRange(formattedText.length) // Place cursor at end
                    )
                } else if (!isFocused && amount == 0.0) {
                    textFieldValue = TextFieldValue(text = "")
                }
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

                // Check if the value exceeds the maximum
                if (numericValue > MAX_AMOUNT) {
                    // Show error message
                    showErrorMessage = true

                    // Format the maximum allowed value
                    val maxValueStr = NumberFormat.getNumberInstance(Locale.US).apply {
                        maximumFractionDigits = 2
                        minimumFractionDigits = 2
                    }.format(MAX_AMOUNT)

                    // Update text field with max value
                    textFieldValue = TextFieldValue(
                        text = maxValueStr,
                        selection = TextRange(maxValueStr.length)
                    )

                    // Notify listener with max value
                    onAmountChange(MAX_AMOUNT)
                } else {
                    // Update with new text and adjusted cursor position
                    textFieldValue = TextFieldValue(
                        text = validInput,
                        selection = TextRange(adjustedPosition)
                    )

                    // Notify listener with the new value
                    onAmountChange(numericValue)
                }
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
            .wrapContentWidth()
            .focusRequester(focusRequester),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.width(textWidth),
                contentAlignment = Alignment.CenterStart
            ) {
                // Display formatted text based on state
                when {
                    // Empty and focused/unfocused - show placeholder
                    textFieldValue.text.isEmpty() -> {
                        Text(
                            text = "0.00",
                            style = textStyle,
                            color = if (isFocused) Color.LightGray else MaterialTheme.colorScheme.onSurface
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
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.wrapContentSize(),
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
                Box (
                    modifier = Modifier.wrapContentSize(),
                    ) {
                    innerTextField()
                }
            }
        }
    )

    // Request focus on initial display ONLY for new payments
    LaunchedEffect(Unit) {
        if (isNewPayment) {
            focusRequester.requestFocus()
        }
    }
}

//This includes a wrapper for Group Member Splits that ensures that the whole composable sits above the keyboard when focused
@Composable
fun WrappedGroupMemberSplits(
    paymentViewModel: PaymentsViewModel,
    userViewModel: UserViewModel,
    currentUserId: Int?,
    onFocusChange: (Boolean) -> Unit
) {
    // Create a state to track internal focus
    var isAnyChildFocused by remember { mutableStateOf(false) }

    // Set up the touch interceptor for the entire area
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // This makes the entire area "focusable" to detect touches
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null // No visual indication
            ) {
                // When area is touched, signal that it's focused
                onFocusChange(true)
            }
            // When actual focus changes within children, report upward
            .onFocusChanged { focusState ->
                if (focusState.hasFocus) {
                    onFocusChange(true)
                    isAnyChildFocused = true
                }
            }
    ) {
        // The actual GroupMemberSplits component
        GroupMemberSplits(
            paymentViewModel = paymentViewModel,
            userViewModel = userViewModel,
            currentUserId = currentUserId
        )
    }

    // Report focus upward when this composable enters/leaves composition
    DisposableEffect(Unit) {
        onDispose {
            // Clear focus state when component is removed from composition
            onFocusChange(false)
        }
    }
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val currentOnBack by rememberUpdatedState(onBack)

    val backCallback = remember {
        object : OnBackPressedCallback(enabled) {
            override fun handleOnBackPressed() {
                currentOnBack()
            }
        }
    }

    // Update the enabled state
    SideEffect {
        backCallback.isEnabled = enabled
    }

    DisposableEffect(backDispatcher) {
        backDispatcher?.addCallback(backCallback)
        onDispose {
            backCallback.remove()
        }
    }
}