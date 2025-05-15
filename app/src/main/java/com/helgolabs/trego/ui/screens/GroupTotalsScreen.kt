package com.helgolabs.trego.ui.screens

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.dataClasses.PaymentEntityWithSplits
import com.helgolabs.trego.data.local.dataClasses.PreferenceKeys
import com.helgolabs.trego.ui.components.GlobalTopAppBar
import com.helgolabs.trego.ui.theme.AnimatedDynamicThemeProvider
import com.helgolabs.trego.ui.viewmodels.GroupViewModel
import com.helgolabs.trego.ui.viewmodels.PaymentsViewModel
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.FormattingUtils.formatAsCurrency
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import kotlin.math.abs

@Composable
fun GroupTotalsScreen(
    navController: NavController,
    context: Context,
    groupId: Int,
    groupViewModel: GroupViewModel,
    themeMode: String = PreferenceKeys.ThemeMode.SYSTEM,
) {
    val myApplication = context.applicationContext as MyApplication
    val paymentViewModel: PaymentsViewModel = viewModel(factory = myApplication.viewModelFactory)

    val payments by paymentViewModel.groupPaymentsAndSplits.collectAsStateWithLifecycle()
    val loading by paymentViewModel.loading.collectAsStateWithLifecycle()
    val error by paymentViewModel.error.collectAsStateWithLifecycle()
    val groupMembers by paymentViewModel.users.collectAsStateWithLifecycle()
    val groupDetailsState by groupViewModel.groupDetailsState.collectAsState()
    val groupColorScheme = groupDetailsState.groupColorScheme

    // Get the group details to access default currency
    val group by groupViewModel.groupDetailsState.collectAsStateWithLifecycle()
    val defaultCurrency = group.group?.defaultCurrency ?: "GBP"

    // Create scroll state that we can control
    val scrollState = rememberScrollState()

    // Add state for expansion
    var isTransfersExpanded by remember { mutableStateOf(false) }
    var isMemberDetailsExpanded by remember { mutableStateOf(false) }

    // Scroll to end when first composed
    LaunchedEffect(Unit) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    // Load group details when screen is first displayed
    LaunchedEffect(groupId) {
        paymentViewModel.fetchGroupPayments(groupId)
        // Also ensure we have active members and usernames loaded
        groupViewModel.loadGroupMembersWithUsers(groupId)
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

    // Get different payment types for the period
    val expenses = filteredPayments.filter { it.payment.paymentType == "spent" }
    val incomes = filteredPayments.filter { it.payment.paymentType == "received" }
    val transfers = filteredPayments.filter { it.payment.paymentType == "transferred" }

    // Calculate all member activity data
    val memberActivityData = remember(filteredPayments) {
        calculateMemberActivity(
            payments = filteredPayments,
            defaultCurrency = defaultCurrency,
            activeMembers = groupDetailsState.activeMembers.map { it.userId },
            usernames = groupDetailsState.usernames
        )
    }

    AnimatedDynamicThemeProvider(groupId, groupColorScheme, themeMode) {
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
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                                contentColor = if (selectedPeriod == "All time")
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.7f)
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
                            val expensesByCurrency = expenses
                                .groupBy { it.payment.currency ?: defaultCurrency }
                                .mapValues { it.value.sumOf { payment -> payment.payment.amount } }

                            val incomeByCurrency = incomes
                                .groupBy { it.payment.currency ?: defaultCurrency }
                                .mapValues { it.value.sumOf { payment -> payment.payment.amount } }

                            val transfersByCurrency = transfers
                                .groupBy { it.payment.currency ?: defaultCurrency }
                                .mapValues { it.value.sumOf { payment -> payment.payment.amount } }

                            // Make sure all currency types are represented in each category
                            val allCurrencies =
                                (expensesByCurrency.keys + incomeByCurrency.keys + transfersByCurrency.keys).toSet()

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

                                    // If no expenses, show 0 in default currency
                                    if (expensesByCurrency.isEmpty()) {
                                        Text(
                                            "0.00".formatAsCurrency(defaultCurrency),
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        // Show all currencies, including those with 0 amount
                                        allCurrencies.forEach { currency ->
                                            val amount = expensesByCurrency[currency] ?: 0.0
                                            Text(
                                                abs(amount).formatAsCurrency(currency),
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        "Total Received",
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    // If no income, show 0 in default currency
                                    if (incomeByCurrency.isEmpty()) {
                                        Text(
                                            "0.00".formatAsCurrency(defaultCurrency),
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        // Show all currencies, including those with 0 amount
                                        allCurrencies.forEach { currency ->
                                            val amount = incomeByCurrency[currency] ?: 0.0
                                            Text(
                                                abs(amount).formatAsCurrency(currency),
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
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
                                        IconButton(onClick = {
                                            isTransfersExpanded = !isTransfersExpanded
                                        }) {
                                            Icon(
                                                if (isTransfersExpanded) Icons.Default.KeyboardArrowUp
                                                else Icons.Default.KeyboardArrowDown,
                                                contentDescription = if (isTransfersExpanded) "Collapse" else "Expand"
                                            )
                                        }
                                    }

                                    // Total amounts
                                    if (transfersByCurrency.isEmpty()) {
                                        Text(
                                            "0.00".formatAsCurrency(defaultCurrency),
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        // Show all currencies, including those with 0 amount
                                        allCurrencies.forEach { currency ->
                                            val amount = transfersByCurrency[currency] ?: 0.0
                                            if (amount != 0.0 || transfersByCurrency.size == 1) {
                                                Text(
                                                    abs(amount).formatAsCurrency(currency),
                                                    style = MaterialTheme.typography.titleLarge,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
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

                                            if (transfers.isEmpty()) {
                                                Text(
                                                    "No transfers in this period",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.padding(vertical = 8.dp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
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
                                                                payment.payment.description
                                                                    ?: "No description",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }

                                                        // Amount
                                                        Text(
                                                            abs(payment.payment.amount)
                                                                .formatAsCurrency(
                                                                    payment.payment.currency
                                                                        ?: defaultCurrency
                                                                ),
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
                                    // Header row with expansion arrow
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Member Summary",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        IconButton(onClick = {
                                            isMemberDetailsExpanded = !isMemberDetailsExpanded
                                        }) {
                                            Icon(
                                                if (isMemberDetailsExpanded) Icons.Default.KeyboardArrowUp
                                                else Icons.Default.KeyboardArrowDown,
                                                contentDescription = if (isMemberDetailsExpanded) "Collapse" else "Expand"
                                            )
                                        }
                                    }

                                    if (memberActivityData.userSummaries.isEmpty()) {
                                        Text(
                                            "No member activity in this period",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        // Show compact summary for all members even when not expanded
                                        memberActivityData.userSummaries.forEach { (userId, summary) ->
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 8.dp)
                                            ) {
                                                val username = summary.username

                                                Text(
                                                    text = username,
                                                    style = MaterialTheme.typography.titleSmall
                                                )

                                                // For each currency, show a compact summary
                                                summary.currencySummaries.forEach { (currency, currencySummary) ->
                                                    val net = currencySummary.netBalance

                                                    // Show just the currency and net total
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            currency,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.secondary
                                                        )

                                                        Text(
                                                            net.formatAsCurrency(currency),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = when {
                                                                net > 0 -> MaterialTheme.colorScheme.primary
                                                                net < 0 -> MaterialTheme.colorScheme.error
                                                                else -> MaterialTheme.colorScheme.onSurface
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                            if (userId != memberActivityData.userSummaries.keys.last()) {
                                                Divider(
                                                    modifier = Modifier.padding(vertical = 4.dp),
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(
                                                        alpha = 0.5f
                                                    )
                                                )
                                            }
                                        }

                                        // Show detailed breakdown when expanded
                                        AnimatedVisibility(
                                            visible = isMemberDetailsExpanded,
                                            enter = expandVertically() + fadeIn(),
                                            exit = shrinkVertically() + fadeOut()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 16.dp)
                                            ) {
                                                Divider(
                                                    modifier = Modifier.padding(
                                                        bottom = 16.dp
                                                    ),
                                                    color = MaterialTheme.colorScheme.primary
                                                )

                                                Text(
                                                    "Detailed Member Activity",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )

                                                Spacer(modifier = Modifier.height(8.dp))

                                                memberActivityData.userSummaries.forEach { (userId, summary) ->
                                                    DetailedMemberSummary(
                                                        userId = userId,
                                                        summary = summary,
                                                        defaultCurrency = defaultCurrency
                                                    )

                                                    if (userId != memberActivityData.userSummaries.keys.last()) {
                                                        Divider(
                                                            modifier = Modifier.padding(vertical = 12.dp),
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
                }
            }
        }
    }
}

/**
 * Data class to hold all member activity for all users
 */
data class MemberActivityData(
    val userSummaries: Map<Int, UserSummary>
)

/**
 * Data class to hold a user's summary across all currencies
 */
data class UserSummary(
    val username: String,
    val currencySummaries: Map<String, CurrencySummary>
)

/**
 * Data class to hold a user's summary for a specific currency
 */
data class CurrencySummary(
    val paidTotal: Double = 0.0,            // Total amount paid by this user (expenses only)
    val splitTotal: Double = 0.0,           // Total amount of expenses shared by this user (own + others)
    val receivedTotal: Double = 0.0,        // Total amount received by this user (income only)
    val receivedSplitTotal: Double = 0.0,   // Total share of income received by everyone
    val transferSentTotal: Double = 0.0,    // Total amount sent in transfers
    val transferReceivedTotal: Double = 0.0, // Total amount received in transfers
    val netBalance: Double = 0.0            // Final balance (positive means user is owed money)
)

/**
 * Calculates detailed activity data for all members
 */
fun calculateMemberActivity(
    payments: List<PaymentEntityWithSplits>,
    defaultCurrency: String,
    activeMembers: List<Int>,
    usernames: Map<Int, String>
): MemberActivityData {
    val userSummaries = mutableMapOf<Int, UserSummary>()

    // Initialize data structures for all active members
    activeMembers.forEach { userId ->
        val username = usernames[userId] ?: "User $userId"
        userSummaries[userId] = UserSummary(
            username = username,
            currencySummaries = mutableMapOf()
        )
    }

    // Add any other users who have activity but aren't active anymore
    payments.forEach { paymentWithSplits ->
        val paidByUserId = paymentWithSplits.payment.paidByUserId
        val fromUserId = paymentWithSplits.payment.paidByUserId
        val toUserId = paymentWithSplits.splits.first().userId

        // Add payer if not already in our list
        if (paidByUserId > 0 && !userSummaries.containsKey(paidByUserId)) {
            val username = usernames[paidByUserId] ?: "User $paidByUserId"
            userSummaries[paidByUserId] = UserSummary(
                username = username,
                currencySummaries = mutableMapOf()
            )
        }

        // Add transfer sender if not already in our list
        if (fromUserId != null && !userSummaries.containsKey(fromUserId)) {
            val username = usernames[fromUserId] ?: "User $fromUserId"
            userSummaries[fromUserId] = UserSummary(
                username = username,
                currencySummaries = mutableMapOf()
            )
        }

        // Add transfer receiver if not already in our list
        if (toUserId != null && !userSummaries.containsKey(toUserId)) {
            val username = usernames[toUserId] ?: "User $toUserId"
            userSummaries[toUserId] = UserSummary(
                username = username,
                currencySummaries = mutableMapOf()
            )
        }

        // Add all users from splits if not already in our list
        paymentWithSplits.splits.forEach { split ->
            val splitUserId = split.userId
            if (!userSummaries.containsKey(splitUserId)) {
                val username = usernames[splitUserId] ?: "User $splitUserId"
                userSummaries[splitUserId] = UserSummary(
                    username = username,
                    currencySummaries = mutableMapOf()
                )
            }
        }
    }

    // Process all payments to calculate totals
    payments.forEach { paymentWithSplits ->
        val payment = paymentWithSplits.payment
        val currency = payment.currency ?: defaultCurrency
        val amount = payment.amount
        val paymentType = payment.paymentType

        // Process based on payment type
        when (paymentType) {
            "spent" -> {
                // The person who paid
                val paidByUserId = payment.paidByUserId

                // Add to paid total for the payer
                updateCurrencySummary(userSummaries, paidByUserId, currency) { summary ->
                    summary.copy(paidTotal = summary.paidTotal + amount)
                }

                // Add split amounts to each user's split total
                paymentWithSplits.splits.forEach { split ->
                    val userId = split.userId
                    val splitAmount = split.amount

                    updateCurrencySummary(userSummaries, userId, currency) { summary ->
                        summary.copy(splitTotal = summary.splitTotal + splitAmount)
                    }
                }
            }
            "received" -> {
                // The person who received
                val paidByUserId = payment.paidByUserId

                // Add to received total for the receiver
                updateCurrencySummary(userSummaries, paidByUserId, currency) { summary ->
                    summary.copy(receivedTotal = summary.receivedTotal + amount)
                }

                // Add split amounts to each user's received split total
                paymentWithSplits.splits.forEach { split ->
                    val userId = split.userId
                    val splitAmount = split.amount

                    updateCurrencySummary(userSummaries, userId, currency) { summary ->
                        summary.copy(receivedSplitTotal = summary.receivedSplitTotal + splitAmount)
                    }
                }
            }
            "transferred" -> {
                // Process transfers between users
                val fromUserId = payment.paidByUserId
                val toUserId = paymentWithSplits.splits.first().userId

                // Add to sent total for the sender
                if (fromUserId != null) {
                    updateCurrencySummary(userSummaries, fromUserId, currency) { summary ->
                        summary.copy(transferSentTotal = summary.transferSentTotal + amount)
                    }
                }

                // Add to received total for the receiver
                if (toUserId != null) {
                    updateCurrencySummary(userSummaries, toUserId, currency) { summary ->
                        summary.copy(transferReceivedTotal = summary.transferReceivedTotal + amount)
                    }
                }
            }
        }
    }

    // Calculate net balances for all users
    val finalUserSummaries = userSummaries.mapValues { (_, userSummary) ->
        val updatedCurrencySummaries = userSummary.currencySummaries.mapValues { (_, currencySummary) ->
            // Net balance formula:
            // (Paid Total - Split Total) + (Received Total - Received Split Total) + (Transfer Received - Transfer Sent)
            val netBalance = (currencySummary.paidTotal - currencySummary.splitTotal) +
                    (currencySummary.receivedTotal - currencySummary.receivedSplitTotal) +
                    (currencySummary.transferReceivedTotal - currencySummary.transferSentTotal)

            currencySummary.copy(netBalance = netBalance)
        }

        userSummary.copy(currencySummaries = updatedCurrencySummaries)
    }

    return MemberActivityData(finalUserSummaries)
}

/**
 * Helper function to update a currency summary for a user
 */
private fun updateCurrencySummary(
    userSummaries: MutableMap<Int, UserSummary>,
    userId: Int,
    currency: String,
    update: (CurrencySummary) -> CurrencySummary
) {
    val userSummary = userSummaries[userId] ?: return
    val currencySummaries = userSummary.currencySummaries.toMutableMap()
    val currencySummary = currencySummaries[currency] ?: CurrencySummary()
    currencySummaries[currency] = update(currencySummary)
    userSummaries[userId] = userSummary.copy(currencySummaries = currencySummaries)
}

/**
 * Displays a detailed breakdown of a member's financial activity
 */
@Composable
fun DetailedMemberSummary(
    userId: Int,
    summary: UserSummary,
    defaultCurrency: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = summary.username,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        summary.currencySummaries.forEach { (currency, currencySummary) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    // Currency header
                    Text(
                        text = currency,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Divider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Payments section
                    Text(
                        text = "Payments",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Paid by you:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = abs(currencySummary.paidTotal).formatAsCurrency(currency),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Your share of all expenses:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = abs(currencySummary.splitTotal).formatAsCurrency(currency),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Divider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Income section
                    Text(
                        text = "Income",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Received by you:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = abs(currencySummary.receivedTotal).formatAsCurrency(currency),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Your share of income:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = abs(currencySummary.receivedSplitTotal).formatAsCurrency(currency),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Divider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Transfers section
                    Text(
                        text = "Transfers",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Sent by you:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = abs(currencySummary.transferSentTotal).formatAsCurrency(currency),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Received in transfers:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = abs(currencySummary.transferReceivedTotal).formatAsCurrency(currency),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Divider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Total/Net section with larger, bold text
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BALANCE:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        val netBalance = currencySummary.netBalance
                        Text(
                            text = netBalance.formatAsCurrency(currency),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                netBalance > 0 -> MaterialTheme.colorScheme.primary
                                netBalance < 0 -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }

                    // Explanation text
                    Text(
                        text = when {
                            currencySummary.netBalance > 0 -> "You are owed money"
                            currencySummary.netBalance < 0 -> "You owe money"
                            else -> "You're all settled up"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}