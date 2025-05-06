package com.helgolabs.trego.ui.screens

import android.content.Context
import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.dataClasses.PreferenceKeys
import com.helgolabs.trego.data.local.dataClasses.UserBalanceWithCurrency
import com.helgolabs.trego.ui.components.GlobalTopAppBar
import com.helgolabs.trego.ui.theme.AnimatedDynamicThemeProvider
import com.helgolabs.trego.ui.viewmodels.GroupViewModel
import com.helgolabs.trego.utils.getCurrencySymbol
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max

@Composable
fun GroupBalancesScreen(
    navController: NavController,
    context: Context,
    groupId: Int,
    groupViewModel: GroupViewModel,
    themeMode: String = PreferenceKeys.ThemeMode.SYSTEM,
) {
    val balances by groupViewModel.groupBalances.collectAsStateWithLifecycle()
    val loading by groupViewModel.loading.collectAsStateWithLifecycle()
    val error by groupViewModel.error.collectAsStateWithLifecycle()
    val groupDetailsState by groupViewModel.groupDetailsState.collectAsStateWithLifecycle()
    val groupColorScheme = groupDetailsState.groupColorScheme

    LaunchedEffect(groupId) {
        groupViewModel.fetchGroupBalances(groupId)
    }
    AnimatedDynamicThemeProvider(groupId, groupColorScheme, themeMode) {

        Scaffold(
            topBar = {
                GlobalTopAppBar(title = { Text("Group Balances") })
            },
            content = { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    when {
                        loading -> {
                            item {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(top = 24.dp)
                                )
                            }
                        }

                        error != null -> {
                            item {
                                Text(
                                    "Error: $error",
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }

                        else -> {
                            // Chart section at the top
                            item {
                                // Add the vertical center bar chart - make sure to use the new component
                                ChristmasTreeBarChart(balances = balances)

                                Spacer(modifier = Modifier.height(16.dp))

                            }

                            // Detailed balances section header
                            item {
                                Text(
                                    text = "Detailed Balances",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 8.dp)
                                )
                            }

                            // Individual balance items
                            items(balances) { balance ->
                                DetailedBalanceItem(balance = balance)
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun DetailedBalanceItem(balance: UserBalanceWithCurrency) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        // Username header
        Text(
            text = balance.username,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Individual currency balances
        balance.balances.forEach { (currency, amount) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currency,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                val currencySymbol = getCurrencySymbol(currency)

                Text(
                    text = currencySymbol+"%.2f".format(amount),
                    style = MaterialTheme.typography.bodyLarge,
                    color = when {
                        amount > 0 -> MaterialTheme.colorScheme.primary
                        amount < 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }

        // Total summary if multiple currencies
        if (balance.balances.size > 1) {
            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            val total = balance.balances.values.sum()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    )

                Text(
                    text = "%.2f".format(total),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        total > 0 -> MaterialTheme.colorScheme.primary
                        total < 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

@Composable
fun ChristmasTreeBarChart(
    balances: List<UserBalanceWithCurrency>,
    modifier: Modifier = Modifier
) {
    // Process balances data - convert to single balance value per user and sort
    val sortedBalances = balances.map { balance ->
        val total = balance.balances.values.sum()
        val currency = if (balance.balances.size == 1) balance.balances.keys.first() else "GBP"
        Triple(balance.username, total, currency)
    }.sortedBy { abs(it.second) }

    val maxAbsBalance = sortedBalances.maxOfOrNull { abs(it.second) } ?: 1.0

    // Animation state
    var animationStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Delay to ensure layout is ready before animation starts
        animationStarted = true
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Chart container with vertical center line
        Box(
            modifier = Modifier
                .wrapContentHeight()
                .fillMaxWidth()
        ) {
            // Vertical center line
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            )

            // Bars
            Column(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth()
                    .padding(start = 84.dp), // Make space for usernames on the left
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                sortedBalances.forEach { (username, balance, currency) ->
                    val targetFraction = abs(balance) / maxAbsBalance
                    val animatedFraction by animateFloatAsState(
                        targetValue = if (animationStarted) targetFraction.toFloat() else 0f,
                        animationSpec = tween(durationMillis = 800),
                        label = "BarAnimation"
                    )

                    // Get currency symbol and format value
                    val currencySymbol = getCurrencySymbol(currency)
                    val formattedValue = "$currencySymbol${String.format("%.2f", abs(balance))}"

                    // Get the configuration and density
                    val configuration = LocalConfiguration.current
                    val density = LocalDensity.current

                    // Calculate the bar width in dp
                    val halfScreenWidth = configuration.screenWidthDp / 2.0
                    val barWidthDp = (animatedFraction * 0.98f * halfScreenWidth).dp

                    // Get the text width in dp
                    val textStyle = MaterialTheme.typography.labelMedium
                    val textSizePx = with(density) { 12.sp.toPx() }
                    val paint = Paint().apply {
                        textSize = textSizePx
                    }
                    val textWidthPx = paint.measureText(formattedValue)
                    val textWidthDp = with(density) { textWidthPx.toDp() }

                    // Add padding for text (8dp on each side)
                    val textWidthWithPadding = textWidthDp + 16.dp

                    // Compare actual width with the fully animated bar width
                    // Use a safety margin of 1.2x the text width to ensure comfortable fit
                    val labelFitsInside = barWidthDp > (textWidthWithPadding * 1.2f)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left side (negative values)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            if (balance < 0) {
                                // Calculate the position for outside label
                                val outsideLabelPosition = with(density) {
                                    // Calculate the position where the bar ends
                                    val barEndPosition = (halfScreenWidth * animatedFraction * 0.98f).dp
                                    // Position just outside the bar
                                    barEndPosition + 4.dp
                                }

                                // Draw the bar first without any label
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(animatedFraction * 0.98f)
                                        .background(
                                            MaterialTheme.colorScheme.error,
                                            shape = RoundedCornerShape(
                                                topStart = 4.dp,
                                                bottomStart = 4.dp,
                                                topEnd = 0.dp,
                                                bottomEnd = 0.dp
                                            )
                                        )
                                )

                                // Draw the appropriate label
                                if (labelFitsInside) {
                                    // Inside label
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Text(
                                            text = "-$formattedValue",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onError,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    }
                                } else {
                                    // Outside label - positioned to the right of the bar
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Text(
                                            text = "-$formattedValue",  // Add negative sign back for outside labels
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(
                                                start = 0.dp,
                                                end = with(density) { ((animatedFraction * halfScreenWidth * 0.98f)).dp }
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Right side (positive values)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (balance > 0) {
                                // Draw the bar first without any label
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(animatedFraction * 0.98f)
                                        .background(
                                            MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(
                                                topStart = 0.dp,
                                                bottomStart = 0.dp,
                                                topEnd = 4.dp,
                                                bottomEnd = 4.dp
                                            )
                                        )
                                )

                                // Draw the appropriate label
                                if (labelFitsInside) {
                                    // Inside label
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = formattedValue,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                } else {
                                    // Outside label - positioned to the right of the bar
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = formattedValue,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(
                                                start = with(density) { (animatedFraction * halfScreenWidth * 0.98f).dp }
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Usernames on the left side
            Column(
                modifier = Modifier
                    .wrapContentHeight()
                    .width(80.dp)
                    .align(Alignment.TopStart),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Add usernames in the same order as the bars
                sortedBalances.forEach { (username, _, _) ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = username,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }
        }
    }
}