package com.helgolabs.trego.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.dataClasses.CurrencySettlingInstructions
import com.helgolabs.trego.data.local.dataClasses.PreferenceKeys
import com.helgolabs.trego.data.local.dataClasses.SettlingInstruction
import com.helgolabs.trego.ui.components.GlobalTopAppBar
import com.helgolabs.trego.ui.components.SettleUpButton
import com.helgolabs.trego.ui.theme.AnimatedDynamicThemeProvider
import com.helgolabs.trego.ui.viewmodels.GroupViewModel
import com.helgolabs.trego.ui.viewmodels.PaymentsViewModel
import com.helgolabs.trego.utils.FormattingUtils.formatAsCurrency
import kotlinx.coroutines.launch

@Composable
fun SettleUpScreen(
    navController: NavController,
    groupId: Int,
    groupViewModel: GroupViewModel,
    themeMode: String = PreferenceKeys.ThemeMode.SYSTEM,
) {
    val context = LocalContext.current
    val myApplication = context.applicationContext as MyApplication
    val paymentsViewModel: PaymentsViewModel = viewModel(factory = myApplication.viewModelFactory)

    val balances by groupViewModel.groupBalances.collectAsStateWithLifecycle()
    val loading by groupViewModel.loading.collectAsStateWithLifecycle()
    val error by groupViewModel.error.collectAsStateWithLifecycle()
    val settlingInstructions by groupViewModel.settlingInstructions.collectAsStateWithLifecycle()
    val groupDetailsState by groupViewModel.groupDetailsState.collectAsStateWithLifecycle()
    val groupColorScheme = groupDetailsState.groupColorScheme

    // Track operation status
    val paymentOperationStatus by paymentsViewModel.paymentScreenState.collectAsStateWithLifecycle()

    // Show message when payment is recorded successfully
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(paymentOperationStatus.paymentOperationStatus) {
        when (paymentOperationStatus.paymentOperationStatus) {
            is PaymentsViewModel.PaymentOperationStatus.Success -> {
                snackbarHostState.showSnackbar("Payment recorded successfully")
                // Refresh settling instructions
                groupViewModel.fetchSettlingInstructions(groupId)
            }
            is PaymentsViewModel.PaymentOperationStatus.Error -> {
                val errorMessage = (paymentOperationStatus.paymentOperationStatus as PaymentsViewModel.PaymentOperationStatus.Error).message
                snackbarHostState.showSnackbar("Error: $errorMessage")
            }
            else -> {}
        }
    }

    LaunchedEffect(groupId) {
        groupViewModel.fetchSettlingInstructions(groupId)
    }

    // Organize settlements by users who owe and users who are owed
    val settlements = remember(settlingInstructions) {
        // Maps to store settlements by user
        val userOwes = mutableMapOf<String, MutableList<SettlingInstruction>>()
        val userIsOwed = mutableMapOf<String, MutableList<SettlingInstruction>>()

        // Process all settling instructions
        settlingInstructions.forEach { currencyInstructions ->
            currencyInstructions.instructions.forEach { instruction ->
                // Add to "owes" map
                val fromName = instruction.fromName
                if (!userOwes.containsKey(fromName)) {
                    userOwes[fromName] = mutableListOf()
                }
                userOwes[fromName]?.add(instruction)

                // Add to "is owed" map
                val toName = instruction.toName
                if (!userIsOwed.containsKey(toName)) {
                    userIsOwed[toName] = mutableListOf()
                }
                userIsOwed[toName]?.add(instruction)
            }
        }

        Pair(userOwes, userIsOwed)
    }

    AnimatedDynamicThemeProvider(groupId, groupColorScheme, themeMode) {
        Scaffold(
            topBar = {
                GlobalTopAppBar(
                    title = { Text("Settle Up") }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            when {
                loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                error != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Error: $error",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                settlements.first.isEmpty() && settlements.second.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "All Settled!",
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "There are no balances to settle in this group.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        // Section header for users who owe money
                        if (settlements.first.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Money to Pay",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }

                            // Users who owe money
                            settlements.first.forEach { (username, owedInstructions) ->
                                item {
                                    UserOwesSection(
                                        username = username,
                                        instructions = owedInstructions,
                                        onPaymentRecorded = { instruction ->
                                            // Call our new method to record the payment
                                            paymentsViewModel.recordSettlementPayment(instruction)
                                        }
                                    )
                                }
                            }
                        }

                        // Section header for users who are owed money
                        if (settlements.second.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Money to Receive",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }

                            // Users who are owed money
                            settlements.second.forEach { (username, receivingInstructions) ->
                                item {
                                    UserIsOwedSection(
                                        username = username,
                                        instructions = receivingInstructions
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
fun UserOwesSection(
    username: String,
    instructions: List<SettlingInstruction>,
    onPaymentRecorded: (SettlingInstruction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f
    )

    // Calculate total amount owed by currency
    val totalByCurrency = instructions.groupBy { it.currency }
        .mapValues { (_, instructionList) ->
            instructionList.sumOf { it.amount }
        }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header row (always visible)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null, // Remove the ripple effect
                        onClick = { expanded = !expanded }
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = username,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Display total amount owed per currency
                    Column(horizontalAlignment = Alignment.End) {
                        totalByCurrency.forEach { (currency, amount) ->
                            Text(
                                text = "Owes: ${amount.formatAsCurrency(currency)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Dropdown arrow
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .rotate(rotationState),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expandable content
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(
                        text = "Payments to make:",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    instructions.forEach { instruction ->
                        SettlementInstructionItem(
                            instruction = instruction,
                            onPaymentRecorded = onPaymentRecorded
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun UserIsOwedSection(
    username: String,
    instructions: List<SettlingInstruction>
) {
    var expanded by remember { mutableStateOf(false) }
    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f
    )

    // Calculate total amount to receive by currency
    val totalByCurrency = instructions.groupBy { it.currency }
        .mapValues { (_, instructionList) ->
            instructionList.sumOf { it.amount }
        }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header row (always visible for UserIsOwedSection)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null, // Remove the ripple effect
                        onClick = { expanded = !expanded }
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = username,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Display total amount to receive per currency
                    Column(horizontalAlignment = Alignment.End) {
                        totalByCurrency.forEach { (currency, amount) ->
                            Text(
                                text = "Receives: ${amount.formatAsCurrency(currency)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Dropdown arrow
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .rotate(rotationState),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expandable content
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(
                        text = "Payments to receive:",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    instructions.forEach { instruction ->
                        // Display information about who owes this user money
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "From: ${instruction.fromName}",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = instruction.amount.formatAsCurrency(instruction.currency),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SettlementInstructionItem(
    instruction: SettlingInstruction,
    onPaymentRecorded: (SettlingInstruction) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "To: ${instruction.toName}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = instruction.amount.formatAsCurrency(instruction.currency),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            SettleUpButton(
                instruction = instruction,
                onPaymentRecorded = onPaymentRecorded,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}