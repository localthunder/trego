package com.helgolabs.trego.ui.screens

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.dataClasses.UserBalanceWithCurrency
import com.helgolabs.trego.ui.components.GlobalTopAppBar
import com.helgolabs.trego.ui.theme.AnimatedDynamicThemeProvider
import com.helgolabs.trego.ui.viewmodels.GroupViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max

@Composable
fun GroupBalancesScreen(
    navController: NavController,
    context: Context,
    groupId: Int,
    groupViewModel: GroupViewModel
) {
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(vertical = 8.dp),
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
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                // Add the vertical center bar chart - make sure to use the new component
                                ChristmasTreeBarChart(balances = balances)
                            }
                        }

                        // Detailed balances section header
                        item {
                            Text(
                                text = "Detailed Balances",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
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

@Composable
fun DetailedBalanceItem(balance: UserBalanceWithCurrency) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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

                    Text(
                        text = "%.2f".format(amount),
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
}

@Composable
fun ChristmasTreeBarChart(
    balances: List<UserBalanceWithCurrency>,
    modifier: Modifier = Modifier
) {
    // Process balances data - convert to single balance value per user
    val sortedBalances = balances.map { balance ->
        val total = balance.balances.values.sum()
        balance.username to total
    }.sortedBy { it.second } // Sort from negative to positive

    val maxAbsBalance = sortedBalances.maxOfOrNull { abs(it.second) } ?: 1.0
    val labelThreshold = 50.dp // Minimum bar width to show label inside

    // Animation state
    var animationStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Delay to ensure layout is ready before animation starts
        animationStarted = true
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Chart container with vertical center line
        Box(modifier = Modifier
            .wrapContentHeight()
        ) {
            // Vertical center line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            )

            // Bars
            Column(
                modifier = Modifier.wrapContentHeight().fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                sortedBalances.forEach { (username, balance) ->
                    val targetFraction = abs(balance) / maxAbsBalance
                    val animatedFraction by animateFloatAsState(
                        targetValue = if (animationStarted) targetFraction.toFloat() else 0f,
                        animationSpec = tween(durationMillis = 800),
                        label = "BarAnimation"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp),
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
                                // Calculate animated width
                                val barWidth = animatedFraction * 0.98f // 98% of available space

                                Row(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(barWidth)
                                        .background(
                                            MaterialTheme.colorScheme.error,
                                            shape = MaterialTheme.shapes.small
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    // Check if bar is wide enough for internal label
                                    val widthInDp = with(LocalDensity.current) {
                                        (barWidth * (LocalConfiguration.current.screenWidthDp.dp / 2))
                                    }

                                    if (widthInDp > labelThreshold) {
                                        Text(
                                            text = "%.2f".format(balance),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onError,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    }
                                }

                                // External label for small bars
                                if (barWidth < 0.25f) {
                                    Text(
                                        text = "%.2f".format(balance),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                }
                            }
                        }

                        // Center with username
                        Box(
                            modifier = Modifier.width(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = username,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                        shape = MaterialTheme.shapes.small
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }

                        // Right side (positive values)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (balance > 0) {
                                // Calculate animated width
                                val barWidth = animatedFraction * 0.98f // 98% of available space

                                Row(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(barWidth)
                                        .background(
                                            MaterialTheme.colorScheme.primary,
                                            shape = MaterialTheme.shapes.small
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    // Check if bar is wide enough for internal label
                                    val widthInDp = with(LocalDensity.current) {
                                        (barWidth * (LocalConfiguration.current.screenWidthDp.dp / 2))
                                    }

                                    if (widthInDp > labelThreshold) {
                                        Text(
                                            text = "%.2f".format(balance),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }

                                // External label for small bars
                                if (barWidth < 0.25f) {
                                    Text(
                                        text = "%.2f".format(balance),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Legend
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Negative (You owe) legend
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(12.dp)
                        .background(MaterialTheme.colorScheme.error, shape = MaterialTheme.shapes.small)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "You owe",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Positive (Owed to you) legend
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(12.dp)
                        .background(MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Owed to you",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}