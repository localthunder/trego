package com.helgolabs.trego.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.helgolabs.trego.data.local.dataClasses.RateLimitInfo
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay


/**
 * A custom SwipeRefresh component that displays warnings based on API rate limits
 */
@Composable
fun TransactionPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    rateLimitInfo: RateLimitInfo,
    content: @Composable () -> Unit
) {
    // Format the time remaining until reset
    val resetTimeFormatted = remember(rateLimitInfo.timeUntilReset) {
        rateLimitInfo.timeUntilReset?.let {
            val resetTime = LocalDateTime.now().plus(it)
            // If it's today, just show the time, otherwise show date and time
            if (resetTime.toLocalDate() == LocalDateTime.now().toLocalDate()) {
                resetTime.format(DateTimeFormatter.ofPattern("h:mm a"))
            } else {
                resetTime.format(DateTimeFormatter.ofPattern("MMM d 'at' h:mm a"))
            }
        } ?: "soon"
    }

    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)
    var showBanner by remember { mutableStateOf(false) }
    var showCooldownBanner by remember { mutableStateOf(false) }

    // Hide banners when refresh completes
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            // Keep banner visible for 5 seconds after refresh completes
            delay(5000)
            showBanner = false
            showCooldownBanner = false
        }
    }

    // Calculate warning states
    val isAtRateLimit = rateLimitInfo.remainingCalls == 0
    val isNearRateLimit = rateLimitInfo.remainingCalls == 1
    val isInCooldown = rateLimitInfo.cooldownMinutesRemaining > 0

    // Show appropriate message when refresh is triggered
    LaunchedEffect(swipeRefreshState.isRefreshing) {
        if (swipeRefreshState.isRefreshing) {
            // Show rate limit warning if applicable
            showBanner = isAtRateLimit || isNearRateLimit
            showCooldownBanner = isInCooldown
        }
    }

    Box {
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = {
                // Always allow the refresh, but may show a confirmation dialog later
                onRefresh()

                // Show appropriate warnings
                showBanner = isAtRateLimit || isNearRateLimit
                showCooldownBanner = isInCooldown
            },
            indicator = { state, trigger ->
                // Custom indicator with rate limit info
                SwipeRefreshIndicator(
                    state = state,
                    refreshTriggerDistance = trigger,
                    contentColor = when {
                        isAtRateLimit -> MaterialTheme.colorScheme.error
                        isNearRateLimit -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        isInCooldown -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    backgroundColor = MaterialTheme.colorScheme.surface
                )
            }
        ) {
            Column {
                // Rate limit warning banner
                AnimatedVisibility(
                    visible = showBanner,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    RateLimitBanner(
                        rateLimitInfo = rateLimitInfo,
                        resetTimeFormatted = resetTimeFormatted
                    )
                }

                // Main content
                content()
            }
        }
    }
}

@Composable
private fun RateLimitBanner(
    rateLimitInfo: RateLimitInfo,
    resetTimeFormatted: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (rateLimitInfo.remainingCalls == 0)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (rateLimitInfo.remainingCalls == 0)
                    Icons.Default.Warning
                else
                    Icons.Default.Info,
                contentDescription = "Rate limit warning",
                tint = if (rateLimitInfo.remainingCalls == 0)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = if (rateLimitInfo.remainingCalls == 0)
                        "Rate limit reached"
                    else
                        "Rate limit warning",
                    fontWeight = FontWeight.Bold,
                    color = if (rateLimitInfo.remainingCalls == 0)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer
                )

                Text(
                    text = if (rateLimitInfo.remainingCalls == 0)
                        "You've used all ${rateLimitInfo.maxCalls} bank refreshes for today. Limit resets at $resetTimeFormatted."
                    else
                        "You have ${rateLimitInfo.remainingCalls} bank refresh${if (rateLimitInfo.remainingCalls > 1) "es" else ""} remaining. Limit resets at $resetTimeFormatted.",
                    color = if (rateLimitInfo.remainingCalls == 0)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 14.sp
                )
            }
        }
    }
}


@Composable
fun RateLimitInfo(
    rateLimitInfo: RateLimitInfo,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Circular progress indicator showing remaining calls
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(40.dp)
        ) {
            CircularProgressIndicator(
                progress = rateLimitInfo.remainingCalls.toFloat() / rateLimitInfo.maxCalls.toFloat(),
                modifier = Modifier.size(40.dp),
                color = when {
                    rateLimitInfo.remainingCalls == 0 -> MaterialTheme.colorScheme.error
                    rateLimitInfo.remainingCalls == 1 -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    else -> MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Text(
                text = "${rateLimitInfo.remainingCalls}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = "Bank refreshes remaining",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            val resetTimeFormatted = remember(rateLimitInfo.timeUntilReset) {
                rateLimitInfo.timeUntilReset?.let {
                    val resetTime = LocalDateTime.now().plus(it)
                    // If it's today, just show the time, otherwise show date and time
                    if (resetTime.toLocalDate() == LocalDateTime.now().toLocalDate()) {
                        "Resets at ${resetTime.format(DateTimeFormatter.ofPattern("h:mm a"))}"
                    } else {
                        "Resets on ${resetTime.format(DateTimeFormatter.ofPattern("MMM d 'at' h:mm a"))}"
                    }
                } ?: "Resets soon"
            }

            Text(
                text = resetTimeFormatted,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}