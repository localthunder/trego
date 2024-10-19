package com.splitter.splittr.ui.components

import android.content.Context
import android.util.Log
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitter.splittr.MyApplication
import com.splitter.splittr.model.Transaction
import com.splitter.splittr.ui.viewmodels.InstitutionViewModel
import downloadAndSaveImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun TransactionItem(
    transaction: Transaction,
    context: Context,
    onClick: () -> Unit = {}
) {
    val myApplication = context.applicationContext as MyApplication
    val institutionViewModel: InstitutionViewModel = viewModel(factory = myApplication.viewModelFactory)
    var logoFile by remember { mutableStateOf<File?>(null) }
    var dominantColors by remember { mutableStateOf(listOf<Color>()) }

    // Collect the logo URL as State
    val logoUrl by institutionViewModel.institutionLogoUrl.collectAsState()

    LaunchedEffect(transaction.institutionId) {
        if (transaction.institutionId != null) {
            Log.d("TransactionItem", "Processing institution ID: ${transaction.institutionId}")
            val logoFilename = "${transaction.institutionId}.png"

            // Trigger the ViewModel to fetch the logo URL
            institutionViewModel.getInstitutionLogoUrl(transaction.institutionId)
        } else {
            Log.d("TransactionItem", "Institution ID is null")
        }
    }

    // Use another LaunchedEffect to react to changes in logoUrl
    LaunchedEffect(logoUrl) {
        logoUrl?.let {
            Log.d("TransactionItem", "Downloading logo from URL: $it")
            withContext(Dispatchers.IO) {
                val logoFilename = "${transaction.institutionId}.png"
                // Use your existing downloadAndSaveImage function
                downloadAndSaveImage(context, it, logoFilename)?.let { file ->
                    Log.d("TransactionItem", "Logo downloaded and saved at: ${file.path}")
                    logoFile = file
                }
            }
        }
    }

    val borderSize = 2.dp
    val borderBrush = if (dominantColors.size >= 2) {
        Brush.linearGradient(dominantColors)
    } else {
        Brush.linearGradient(listOf(Color.Gray, Color.LightGray))
    }

    val nameToShow = transaction.creditorName
        ?: transaction.debtorName
        ?: transaction.remittanceInformationUnstructured
        ?: "N/A"

    PaymentAndTransactionCard(
        logoFile = logoFile,
        nameToShow = nameToShow,
        amount = transaction.transactionAmount.amount,
        bookingDateTime = transaction.bookingDateTime,
        institutionName = transaction.institutionName ?: "N/A",
        borderSize = borderSize,
        borderBrush = borderBrush,
        onClick = onClick
    )
}

@Composable
fun TransactionsList(
    transactions: List<Transaction>,
    context: Context,
    onClick: (Transaction) -> Unit
) {
    val myApplication = context.applicationContext as MyApplication
    val institutionViewModel: InstitutionViewModel = viewModel(factory = myApplication.viewModelFactory)

    val sortedTransactions = transactions.sortedByDescending { it.bookingDateTime }

    LazyColumn {
        items(sortedTransactions) { transaction ->
            TransactionItem(
                transaction = transaction,
                context = context,
                onClick = { onClick(transaction) }
            )
        }
    }
}