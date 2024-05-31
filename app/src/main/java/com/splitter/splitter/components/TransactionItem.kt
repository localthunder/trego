package com.splitter.splitter.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.splitter.splitter.model.Transaction

@Composable
fun TransactionItem(transaction: Transaction, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Transaction ID: ${transaction.transactionId ?: "N/A"}")
            Text("Booking Date: ${transaction.bookingDate ?: "N/A"}")
            Text("Booking DateTime: ${transaction.bookingDateTime ?: "N/A"}")

            // Conditionally format the amount
            val amount = transaction.transactionAmount?.amount ?: "N/A"
            val formattedAmount = if (amount is Double && amount < 0) {
                "+${-amount}" // Convert negative amount to positive with "+" sign
            } else {
                amount.toString()
            }
            val amountColor = if (amount is Double && amount < 0) Color.Green else Color.Black

            Text(
                text = "Amount: $formattedAmount",
                color = amountColor
            )

            Text("Currency: ${transaction.transactionAmount?.currency ?: "N/A"}")
            Text("Creditor Name: ${transaction.creditorName ?: "N/A"}")
            Text("Creditor Account BBAN: ${transaction.creditorAccount?.bban ?: "N/A"}")
            Text("Remittance Info: ${transaction.remittanceInformationUnstructured ?: "N/A"}")
            Text("Proprietary Bank Transaction Code: ${transaction.proprietaryBankTransactionCode ?: "N/A"}")
            Text("Internal Transaction ID: ${transaction.internalTransactionId ?: "N/A"}")
        }
    }
}
