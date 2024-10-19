package com.splitter.splittr.ui.components

import android.graphics.BitmapFactory
import android.util.Log
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
import com.splitter.splittr.utils.FormattingUtils.formatDate
import java.io.File

@Composable
fun PaymentAndTransactionCard(
    logoFile: File?,
    nameToShow: String,
    amount: Double,
    bookingDateTime: String?,
    institutionName: String?,
    borderSize: Dp = 2.dp,
    borderBrush: Brush = Brush.linearGradient(listOf(Color.Gray, Color.LightGray)),
    paidByUser: String? = null,
    onClick: () -> Unit = {}
) {

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
            if (logoFile != null) {
                val bitmap = BitmapFactory.decodeFile(logoFile.path)
                if (bitmap != null) {
                    Log.d("SharedTransactionCard", "Displaying logo from file: ${logoFile.path}")
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Institution Logo",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Log.e("SharedTransactionCard", "Failed to decode image file: ${logoFile.path}")
                    Spacer(modifier = Modifier.size(40.dp))
                }
            } else {
                Log.d("SharedTransactionCard", "Logo file is null, displaying placeholder")
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
                    text = "You owe £${amount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFFA726)
                )
            }

            Column(
                horizontalAlignment = Alignment.End
            ) {
                val formattedAmount = if (amount < 0) {
                    "£${-amount}" // Convert negative amount to positive without "+" sign
                } else {
                    "+£$amount" // Add "+" sign for positive amount
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
                        text = formatDate(it),
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
