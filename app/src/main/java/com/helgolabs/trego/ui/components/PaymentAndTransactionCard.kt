package com.helgolabs.trego.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helgolabs.trego.utils.CurrencyUtils.currencySymbols
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.InstitutionLogoManager

@Composable
fun PaymentAndTransactionCard(
    logoInfo: InstitutionLogoManager.LogoInfo?,
    nameToShow: String,
    amount: Double,
    bookingDateTime: String?,
    institutionName: String?,
    borderSize: Dp = 2.dp,
    borderBrush: Brush = Brush.linearGradient(listOf(Color.Gray, Color.LightGray)),
    paidByUser: String? = null,
    currency: String,
    onClick: () -> Unit = {}
) {
    val currencySymbol = currencySymbols[currency]
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        border = BorderStroke(borderSize, borderBrush)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (logoInfo != null) {
                Image(
                    bitmap = logoInfo.bitmap.asImageBitmap(),
                    contentDescription = "Institution Logo",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = nameToShow,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                paidByUser?.let {
                    Text(
                        text = "Paid by: $it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }

                Text(
                    text = "You owe ${currencySymbol}${amount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFFA726)
                )
            }

            Column(
                horizontalAlignment = Alignment.End
            ) {
                val formattedAmount = if (amount < 0) {
                    "${currencySymbol}${-amount}"
                } else {
                    "+${currencySymbol}$amount"
                }
                val amountColor = if (amount < 0) Color.Black else Color.Green

                Text(
                    text = formattedAmount,
                    color = amountColor,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                bookingDateTime?.let {
                    Text(
                        text = DateUtils.formatForDisplay(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }

                Text(
                    text = institutionName ?: "N/A",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}