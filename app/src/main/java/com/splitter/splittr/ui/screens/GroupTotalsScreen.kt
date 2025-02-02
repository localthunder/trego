package com.splitter.splittr.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.splitter.splittr.MyApplication
import com.splitter.splittr.ui.components.GlobalTopAppBar
import com.splitter.splittr.ui.viewmodels.GroupViewModel
import com.splitter.splittr.ui.viewmodels.PaymentsViewModel
import com.splitter.splittr.utils.DateUtils
import com.splitter.splittr.utils.FormattingUtils.formatAsCurrency
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.Currency
import kotlin.math.abs

@Composable
fun GroupTotalsScreen(
    navController: NavController,
    context: Context,
    groupId: Int
) {
    val myApplication = context.applicationContext as MyApplication
    val groupViewModel: GroupViewModel = viewModel(factory = myApplication.viewModelFactory)
    val paymentViewModel: PaymentsViewModel = viewModel(factory = myApplication.viewModelFactory)

    val payments by paymentViewModel.groupPaymentsAndSplits.collectAsStateWithLifecycle()
    val loading by paymentViewModel.loading.collectAsStateWithLifecycle()
    val error by paymentViewModel.error.collectAsStateWithLifecycle()
    val groupMembers by paymentViewModel.users.collectAsStateWithLifecycle()

    // Create scroll state that we can control
    val scrollState = rememberScrollState()

    // Add state for expansion
    var isTransfersExpanded by remember { mutableStateOf(false) }

    // Scroll to end when first composed
    LaunchedEffect(Unit) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    // State for selected time period
    var selectedPeriod by remember { mutableStateOf("All time") }

    // Generate list of months for the selector
    val timeOptions = remember(payments) {  // Add payments as a dependency to recalculate when they change
        val options = mutableListOf<String>()
        val currentDate = LocalDateTime.now()
        val currentYear = currentDate.year

        // Find oldest payment date
        val oldestDate = payments
            .mapNotNull { payment ->
                try {
                    when {
                        payment.payment.paymentDate.contains("T") -> {
                            Instant.parse(payment.payment.paymentDate)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime()
                        }
                        else -> {
                            LocalDate.parse(payment.payment.paymentDate)
                                .atStartOfDay()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GroupTotalsScreen", "Error parsing date: ${payment.payment.paymentDate}", e)
                    null
                }
            }
            .minByOrNull { it.toLocalDate() }
            ?.toLocalDate()
            ?: currentDate.toLocalDate()

        // Calculate number of months between oldest payment and now
        val monthsBetween = ChronoUnit.MONTHS.between(
            YearMonth.from(oldestDate),
            YearMonth.from(currentDate)
        )

        // Add all months from oldest to current
        for (i in 0..monthsBetween) {
            val date = currentDate.minusMonths(i.toLong())
            val formattedDate = if (date.year == currentYear) {
                // Just month for current year
                date.format(DateTimeFormatter.ofPattern("MMM"))
            } else {
                // Month and year for other years
                date.format(DateTimeFormatter.ofPattern("MMM yyyy"))
            }
            options.add(formattedDate)
        }

        options // Don't reverse, we'll handle order in the UI
    }

    // Wait for layout to complete and then smoothly scroll to end
    LaunchedEffect(timeOptions) {
        delay(100) // Brief delay to ensure layout is complete
        scrollState.animateScrollTo(
            value = scrollState.maxValue,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    // Filter payments based on selected period
    val filteredPayments = remember(selectedPeriod, payments) {
        if (selectedPeriod == "All time") {
            payments
        } else {
            // Parse selected period
            val selectedDateTime = if (selectedPeriod.contains(" ")) {
                // Format with year (e.g., "Jan 2024")
                YearMonth.parse(selectedPeriod, DateTimeFormatter.ofPattern("MMM yyyy"))
            } else {
                // Format without year (e.g., "Jan") - assume current year
                val monthNumber = DateTimeFormatter.ofPattern("MMM")
                    .parse(selectedPeriod)
                    .get(ChronoField.MONTH_OF_YEAR)
                YearMonth.of(LocalDateTime.now().year, monthNumber)
            }

            payments.filter { payment ->
                try {
                    val paymentDate = when {
                        payment.payment.paymentDate.contains("T") -> {
                            // Handle ISO format (2025-01-31T15:17:22.587Z)
                            Instant.parse(payment.payment.paymentDate)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime()
                        }
                        else -> {
                            // Handle date-only format (2025-01-31)
                            LocalDate.parse(payment.payment.paymentDate)
                                .atStartOfDay()
                        }
                    }

                    YearMonth.from(paymentDate) == selectedDateTime
                } catch (e: Exception) {
                    Log.e(
                        "GroupTotalsScreen",
                        "Error parsing date: ${payment.payment.paymentDate}",
                        e
                    )
                    false
                }
            }
        }
    }

    // Get transfers for the period
    val transfers = filteredPayments.filter {
        it.payment.paymentType == "transferred"
    }.sortedByDescending {
        it.payment.paymentDate
    }

    LaunchedEffect(groupId) {
        paymentViewModel.fetchGroupPayments(groupId)
    }

    Scaffold(
        topBar = {
            GlobalTopAppBar(
                title = { Text("Group Totals") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Month selector row with elevation and background
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    // Reverse the options when displaying
                    timeOptions.reversed().forEach { period ->
                        Button(
                            onClick = { selectedPeriod = period },
                            modifier = Modifier.padding(end = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedPeriod == period)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = period,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    // Add "All time" button at the end
                    Button(
                        onClick = { selectedPeriod = "All time" },
                        modifier = Modifier.padding(end = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedPeriod == "All time")
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "All time",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Rest of the content using filteredPayments
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when {
                    loading -> CircularProgressIndicator()
                    error != null -> Text(
                        "Error: $error",
                        color = MaterialTheme.colorScheme.error
                    )

                    else -> {
                        // Use filteredPayments instead of payments
                        val expenses = filteredPayments.filter {
                            it.payment.paymentType == "spent"
                        }
                        val incomes = filteredPayments.filter {
                            it.payment.paymentType == "received"
                        }
                        val transfers = filteredPayments.filter {
                            it.payment.paymentType == "transferred"
                        }

                        Log.d(
                            "GroupTotalsScreen", """
                        Filtering Results:
                        - Total Payments: ${payments.size}
                        - Expenses: ${expenses.size}
                        - Incomes: ${incomes.size}
                        - Payment Types: ${payments.map { it.payment.paymentType }.distinct()}
                    """.trimIndent()
                        )

                        val expensesByCurrency = expenses
                            .groupBy { it.payment.currency ?: "Unknown" }
                            .mapValues { it.value.sumOf { payment -> payment.payment.amount } }
                            .also { Log.d("GroupTotalsScreen", "Expenses by currency: $it") }

                        val incomeByCurrency = incomes
                            .groupBy { it.payment.currency ?: "Unknown" }
                            .mapValues { it.value.sumOf { payment -> payment.payment.amount } }
                            .also { Log.d("GroupTotalsScreen", "Income by currency: $it") }

                        val transfersByCurrency = transfers
                            .groupBy { it.payment.currency ?: "Unknown" }
                            .mapValues { it.value.sumOf { payment -> payment.payment.amount } }
                            .also { Log.d("GroupTotalsScreen", "Transfers by currency: $it") }

                        // Group Totals Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    "Total Spent",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                expensesByCurrency.forEach { (currency, total) ->
                                    Text(
                                        "${abs(total).formatAsCurrency(currency)}",  // Use abs() here
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary

                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    "Total Received",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                incomeByCurrency.forEach { (currency, total) ->
                                    Text(
                                        "${abs(total).formatAsCurrency(currency)}",  // Use abs() here
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        //Transfers card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                // Header row with expansion arrow
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Total Transferred",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    IconButton(onClick = { isTransfersExpanded = !isTransfersExpanded }) {
                                        Icon(
                                            if (isTransfersExpanded) Icons.Default.KeyboardArrowUp
                                            else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isTransfersExpanded) "Collapse" else "Expand"
                                        )
                                    }
                                }

                                // Total amounts
                                transfersByCurrency.forEach { (currency, total) ->
                                    Text(
                                        "${abs(total).formatAsCurrency(currency)}",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Show details if expanded
                                AnimatedVisibility(
                                    visible = isTransfersExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        // Header divider
                                        Divider()

                                        // List of transfers
                                        transfers.forEach { payment ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                // Date and description
                                                Column(
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        DateUtils.formatForDisplay(payment.payment.paymentDate),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        payment.payment.description ?: "No description",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                // Amount
                                                Text(
                                                    abs(payment.payment.amount)
                                                        .formatAsCurrency(payment.payment.currency ?: "Unknown"),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(start = 16.dp)
                                                )
                                            }

                                            if (transfers.last() != payment) {
                                                Divider(
                                                    modifier = Modifier.padding(vertical = 4.dp),
                                                    color = MaterialTheme.colorScheme.outlineVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // User Spending Summary Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    "Member Summary",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // Calculate spent by each user (from splits)
                                val spentByUserAndCurrency = mutableMapOf<Pair<Int, String>, Double>()
                                filteredPayments
                                    .filter { it.payment.paymentType != "transferred" } // Exclude transferred payments
                                    .forEach { paymentWithSplits ->
                                        val currency = paymentWithSplits.payment.currency ?: "Unknown"
                                        Log.d("MemberSummary", """
                                                    Processing splits for payment:
                                                    - Payment ID: ${paymentWithSplits.payment.id}
                                                    - Amount: ${paymentWithSplits.payment.amount}
                                                    - Currency: $currency
                                                    - Type: ${paymentWithSplits.payment.paymentType}
                                                    - Split count: ${paymentWithSplits.splits.size}
                                                """.trimIndent())

                                        paymentWithSplits.splits.forEach { split ->
                                            val key = Pair(split.userId, currency)
                                            val currentAmount = spentByUserAndCurrency[key] ?: 0.0
                                            val newAmount = currentAmount + split.amount
                                            spentByUserAndCurrency[key] = newAmount

                                            Log.d("MemberSummary", """
                                                        Split details:
                                                        - User ID: ${split.userId}
                                                        - Split Amount: ${split.amount}
                                                        - Running Total: $newAmount
                                                    """.trimIndent())
                                        }
                                    }

                                // Log spent totals
                                Log.d("MemberSummary", "Final spent totals by user and currency:")
                                spentByUserAndCurrency.forEach { (key, amount) ->
                                    Log.d(
                                        "MemberSummary",
                                        "User ${key.first} in ${key.second}: $amount"
                                    )
                                }

                                // Calculate paid by each user
                                val paidByUserAndCurrency = filteredPayments
                                    .filter { it.payment.paymentType != "transferred" } // Exclude transferred payments
                                    .groupBy { Pair(it.payment.paidByUserId, it.payment.currency ?: "Unknown") }
                                    .mapValues { (_, payments) ->
                                        payments.sumOf { it.payment.amount }
                                    }
                                    .also { paidMap ->
                                        Log.d("MemberSummary", "Paid totals by user and currency:")
                                        paidMap.forEach { (key, amount) ->
                                            Log.d("MemberSummary", "User ${key.first} in ${key.second}: $amount")
                                        }
                                    }

                                // Get unique sets
                                val allUserIds = paidByUserAndCurrency.keys.map { it.first }
                                    .union(spentByUserAndCurrency.keys.map { it.first })
                                val allCurrencies = paidByUserAndCurrency.keys.map { it.second }
                                    .union(spentByUserAndCurrency.keys.map { it.second })

                                Log.d(
                                    "MemberSummary", """
                                        Summary totals:
                                        - Total unique users: ${allUserIds.size}
                                        - User IDs: $allUserIds
                                        - Currencies: $allCurrencies
                                    """.trimIndent()
                                )

                                // Show both spent and paid for each user
                                allUserIds.forEach { userId ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        val username =
                                            groupMembers.find { it.userId == userId }?.username
                                                ?: "User $userId"
                                        Log.d(
                                            "MemberSummary",
                                            "Processing user: $username (ID: $userId)"
                                        )

                                        Text(
                                            text = username,
                                            style = MaterialTheme.typography.titleSmall
                                        )

                                        allCurrencies.forEach { currency ->
                                            val spent =
                                                spentByUserAndCurrency[Pair(userId, currency)]
                                                    ?: 0.0
                                            val paid =
                                                paidByUserAndCurrency[Pair(userId, currency)] ?: 0.0
                                            val net = paid - spent

                                            Log.d(
                                                "MemberSummary", """
                                                    User $username ($userId) in $currency:
                                                    - Spent: $spent
                                                    - Paid: $paid
                                                    - Net: $net
                                                """.trimIndent()
                                            )

                                            if (spent != 0.0 || paid != 0.0) {
                                                Text(
                                                    currency,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column {
                                                        Text(
                                                            // Show 0 instead of empty when amount is 0
                                                            "Spent: ${if (spent == 0.0) "0.00".formatAsCurrency(currency) else abs(spent).formatAsCurrency(currency)}",
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                        Text(
                                                            "Paid: ${if (paid == 0.0) "0.00".formatAsCurrency(currency) else abs(paid).formatAsCurrency(currency)}",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }

                                                    Text(
                                                        // Show 0 for net amount as well
                                                        if (net == 0.0) "0.00".formatAsCurrency(currency) else net.formatAsCurrency(currency),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (net >= 0)
                                                            MaterialTheme.colorScheme.primary
                                                        else
                                                            MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Divider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

