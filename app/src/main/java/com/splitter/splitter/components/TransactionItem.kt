package com.splitter.splitter.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitter.splitter.model.Transaction
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun TransactionItem(
    transaction: Transaction,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                val nameToShow = transaction.creditorName
                    ?: transaction.debtorName
                    ?: transaction.remittanceInformationUnstructured
                    ?: "N/A"

                Text(
                    text = nameToShow,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "You owe £6.40",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFFA726)
                )
            }

            Column(
                horizontalAlignment = Alignment.End
            ) {
                // Conditionally format the amount
                val amount = transaction.transactionAmount.amount
                val formattedAmount = if (amount < 0) {
                    "+${-amount}" // Convert negative amount to positive with "+" sign
                } else {
                    "£$amount"
                }
                val amountColor = if (amount < 0) Color.Green else Color.Black

                Text(
                    text = formattedAmount,
                    color = amountColor,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                transaction.bookingDateTime?.let {
                    Text(
                        text = formatDate(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }

                // Display institution name
                Text(
                    text = transaction.institutionName ?: "N/A",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
fun TransactionsList(
    transactions: List<Transaction>,
    onClick: (Transaction) -> Unit
) {
    val sortedTransactions = transactions.sortedByDescending { it.bookingDateTime }

    LazyColumn {
        items(sortedTransactions) { transaction ->
            TransactionItem(transaction = transaction) {
                onClick(transaction)
            }
        }
    }
}

fun formatDate(isoDate: String): String {
    return try {
        val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        val dateTime = OffsetDateTime.parse(isoDate, formatter)
        val currentYear = OffsetDateTime.now().year
        val formattedDate = if (dateTime.year == currentYear) {
            dateTime.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        } else {
            dateTime.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
        }
        formattedDate
    } catch (e: Exception) {
        "N/A"
    }
}
