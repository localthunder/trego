package com.splitter.splitter.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitter.splitter.model.Transaction
import com.splitter.splitter.network.ApiService
import com.splitter.splitter.utils.FormattingUtils.formatAmount
import com.splitter.splitter.utils.GocardlessUtils.getInstitutionLogoUrl
import com.splitter.splitter.utils.GradientBorderUtils.getAverageColor
import com.splitter.splitter.utils.GradientBorderUtils.getDominantColors
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
    var dominantColors by remember { mutableStateOf(listOf<Color>()) }

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

            logoFile?.let { file ->
                val bitmap = BitmapFactory.decodeFile(file.path)
                if (bitmap != null) {
                    Log.d("TransactionItem", "Bitmap width: ${bitmap.width}, height: ${bitmap.height}")
                    dominantColors = getDominantColors(bitmap).map { Color(it) }
                    if (dominantColors.size < 2) {
                        // Use the average color and a slightly different shade of it to create a gradient
                        val averageColor = Color(getAverageColor(bitmap))
                        dominantColors = listOf(averageColor, averageColor.copy(alpha = 0.7f))
                    }
                    Log.d("TransactionItem", "Dominant colors: $dominantColors")
                } else {
                    Log.e("TransactionItem", "Failed to decode image file: ${file.path}")
                }
            }
        } else {
            Log.d("TransactionItem", "Institution ID is null")
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
