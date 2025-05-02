package com.helgolabs.trego.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.helgolabs.trego.data.local.dataClasses.RateLimitInfo
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun RateLimitInfoWithHelp(
    rateLimitInfo: RateLimitInfo,
    modifier: Modifier = Modifier
) {
    var showInfoDialog by remember { mutableStateOf(false) }

    // Format reset time
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

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "Bank refreshes remaining",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = resetTimeFormatted,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Info button
        IconButton(
            onClick = { showInfoDialog = true },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Rate limit information",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }

    // Show info dialog when clicked
    if (showInfoDialog) {
        RateLimitInfoDialog(
            onDismiss = { showInfoDialog = false }
        )
    }
}

@Composable
fun RateLimitInfoDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transaction Refresh Limits",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "We use GoCardless to securely access your banking information. GoCardless enforces a limit of 4 transaction refreshes per account within a 24-hour period.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "This limit is based on a rolling 24-hour window from your first refresh, not calendar days.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Important Notes:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )

                Text(
                    text = "• Each time you manually refresh transactions by pulling down on the screen, it counts toward your daily limit.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                    text = "• Adding a new bank account will not count against your refresh limit.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                    text = "• There's a 30-minute cooldown period between refreshes to prevent excessive API calls.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "• If you reach your limit, you'll need to wait until the 24-hour period resets before refreshing transactions again.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("GOT IT")
                }
            }
        }
    }
}