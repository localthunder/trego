package com.splitter.splitter.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitter.splitter.model.Transaction
import com.splitter.splitter.network.ApiService
import com.splitter.splitter.utils.GocardlessUtils.getInstitutionLogoUrl
import downloadAndSaveImage
import isLogoSaved
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun TransactionItem(
    transaction: Transaction,
    context: Context,
    apiService: ApiService,
    onClick: () -> Unit = {}
) {

    var logoFile by remember { mutableStateOf<File?>(null) }



    LaunchedEffect(transaction.institutionId) {
        if (transaction.institutionId != null) {
            Log.d("TransactionItem", "Processing institution ID: ${transaction.institutionId}")
            val logoFilename = "${transaction.institutionId}.png"
            val logoSaved = isLogoSaved(context, transaction.institutionId)
            if (!logoSaved) {
                Log.d("TransactionItem", "Logo not saved locally, fetching from API")
                val logoUrl = withContext(Dispatchers.IO) {
                    getInstitutionLogoUrl(apiService, transaction.institutionId)
                }
                logoUrl?.let {
                    Log.d("TransactionItem", "Downloading logo from URL: $it")
                    withContext(Dispatchers.IO) {
                        downloadAndSaveImage(context, it, logoFilename)?.let { file ->
                            Log.d("TransactionItem", "Logo downloaded and saved at: ${file.path}")
                            logoFile = file
                        }
                    }
                }
            } else {
                Log.d("TransactionItem", "Logo already saved locally")
                logoFile = File(context.filesDir, logoFilename)
            }
        } else {
            Log.d("TransactionItem", "Institution ID is null")
        }
    }

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

            if (logoFile != null) {
                val bitmap = BitmapFactory.decodeFile(logoFile?.path)
                if (bitmap != null) {
                    Log.d("TransactionItem", "Displaying logo from file: ${logoFile?.path}")
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Institution Logo",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Log.e("TransactionItem", "Failed to decode image file: ${logoFile?.path}")
                    Spacer(modifier = Modifier.size(40.dp))
                }
            } else {
                Log.d("TransactionItem", "Logo file is null, displaying placeholder")
                Spacer(modifier = Modifier.size(40.dp))
            }

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
    context: Context,
    apiService: ApiService,
    onClick: (Transaction) -> Unit
) {
    val sortedTransactions = transactions.sortedByDescending { it.bookingDateTime }

    LazyColumn {
        items(sortedTransactions) { transaction ->
            TransactionItem(transaction, context, apiService) {
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
