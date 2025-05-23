package com.helgolabs.trego.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helgolabs.trego.data.local.dataClasses.UserInvolvement
import com.helgolabs.trego.utils.CurrencyUtils
import java.text.DecimalFormat
import kotlin.math.abs

/**
 * A component that displays the user's involvement in a payment
 * with color-coding but without icons
 */
@Composable
fun UserInvolvementDisplay(
    involvement: UserInvolvement?,
    currency: String
) {
    if (involvement == null) {
        // Loading state or no data
        return
    }

    val currencySymbol = CurrencyUtils.currencySymbols[currency] ?: "£"
    val decimalFormat = DecimalFormat("0.00")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        // Data based on involvement type
        val (tint, text, amount) = when (involvement) {
            // SPENT scenarios
            is UserInvolvement.YouPaidAndAreOwed -> Triple(
                Color(0xFF4CAF50),  // Green
                "You're owed",
                abs(involvement.amount)
            )
            is UserInvolvement.SomeoneElsePaidAndYouOwe -> Triple(
                Color(0xFFF44336),  // Red
                "You owe",
                abs(involvement.amount)
            )

            // RECEIVED scenarios
            is UserInvolvement.YouReceivedAndAreSharing -> Triple(
                Color(0xFFF44336),  // Red
                "You owe",
                abs(involvement.amount)
            )
            is UserInvolvement.SomeoneElseReceivedAndIsSharing -> Triple(
                Color(0xFF4CAF50),  // Green
                "You're owed",
                abs(involvement.amount)
            )

            // TRANSFERRED scenarios
            is UserInvolvement.YouSentMoney -> Triple(
                Color(0xFF2196F3),  // Blue
                "You sent",
                abs(involvement.amount)
            )
            is UserInvolvement.YouReceivedMoney -> Triple(
                Color(0xFF2196F3),  // Blue
                "You received",
                abs(involvement.amount)
            )

            // Common scenario
            is UserInvolvement.NotInvolved -> Triple(
                Color.Gray,
                "Not involved",
                0.0
            )
        }

        Text(
            text = text,
            color = tint,
            style = MaterialTheme.typography.bodyMedium
        )

        if (amount > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$currencySymbol${decimalFormat.format(amount)}",
                color = tint,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp
                )
            )
        }
    }
}