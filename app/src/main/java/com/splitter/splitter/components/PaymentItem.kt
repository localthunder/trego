package com.splitter.splitter.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.splitter.splitter.model.Payment
import com.splitter.splitter.model.User
import com.splitter.splitter.data.network.ApiService
import com.splitter.splitter.utils.GocardlessUtils
import com.splitter.splitter.utils.GradientBorderUtils
import downloadAndSaveImage
import isLogoSaved
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File

@Composable
fun PaymentItem(payment: Payment, apiService: ApiService, context: Context, onClick: () -> Unit) {
    var paidByUserName by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // Log the entire payment object
    Log.d("PaymentItem", "Payment object: $payment")

    // Fetch the payer's name based on paid_by_user_id
    LaunchedEffect(payment.paidByUserId) {
        payment.paidByUserId?.let { userId ->
            apiService.getUserById(userId).enqueue(object : Callback<User> {
                override fun onResponse(call: Call<User>, response: Response<User>) {
                    if (response.isSuccessful) {
                        paidByUserName = response.body()?.username
                        Log.d("PaymentItem", "Payer Name: $paidByUserName")
                    } else {
                        error = response.message()
                        Log.e("PaymentItem", "Error fetching payer: $error")
                    }
                }

                override fun onFailure(call: Call<User>, t: Throwable) {
                    error = t.message
                    Log.e("PaymentItem", "Failed to fetch payer: $error")
                }
            })
        }
    }

    var logoFile by remember { mutableStateOf<File?>(null) }
    var dominantColors by remember { mutableStateOf(listOf<Color>()) }
    var institutionId by remember { mutableStateOf<String?>(null) }
    var institutionName by remember { mutableStateOf<String?>(null) }

    // Fetch institutionId from Transaction using transactionId from Payment
    LaunchedEffect(payment.transactionId) {
        if (payment.transactionId != null) {
            Log.d("PaymentItem", "Payment's transactionId: ${payment.transactionId}")
            val response = withContext(Dispatchers.IO) {
                apiService.getTransactionById(payment.transactionId).execute()
            }
            if (response.isSuccessful) {
                val transaction = response.body()
                Log.d("PaymentItem", "Fetched transaction: $transaction")
                institutionId = transaction?.institutionId
                institutionName = transaction?.institutionName
                Log.d("PaymentItem", "Fetched institutionId: $institutionId from transaction")
            } else {
                Log.e("PaymentItem", "Failed to fetch transaction: ${response.message()}")
            }
        }
    }

    // Proceed with existing logic using institutionId
    LaunchedEffect(institutionId) {
        if (institutionId != null) {
            Log.d("PaymentItem", "Processing institution ID: $institutionId")
            val logoFilename = "$institutionId.png"
            val logoSaved = isLogoSaved(context, institutionId!!)
            if (!logoSaved) {
                Log.d("PaymentItem", "Logo not saved locally, fetching from API")
                val logoUrl = withContext(Dispatchers.IO) {
                    GocardlessUtils.getInstitutionLogoUrl(apiService, institutionId!!)
                }
                logoUrl?.let {
                    Log.d("PaymentItem", "Downloading logo from URL: $it")
                    withContext(Dispatchers.IO) {
                        downloadAndSaveImage(context, it, logoFilename)?.let { file ->
                            Log.d("PaymentItem", "Logo downloaded and saved at: ${file.path}")
                            logoFile = file
                        }
                    }
                }
            } else {
                Log.d("PaymentItem", "Logo already saved locally")
                logoFile = File(context.filesDir, logoFilename)
            }

            logoFile?.let { file ->
                val bitmap = BitmapFactory.decodeFile(file.path)
                if (bitmap != null) {
                    Log.d("PaymentItem", "Bitmap width: ${bitmap.width}, height: ${bitmap.height}")
                    dominantColors = GradientBorderUtils.getDominantColors(bitmap).map { Color(it) }
                    if (dominantColors.size < 2) {
                        // Use the average color and a slightly different shade of it to create a gradient
                        val averageColor = Color(GradientBorderUtils.getAverageColor(bitmap))
                        dominantColors = listOf(averageColor, averageColor.copy(alpha = 0.7f))
                    }
                    Log.d("PaymentItem", "Dominant colors: $dominantColors")
                } else {
                    Log.e("PaymentItem", "Failed to decode image file: ${file.path}")
                }
            }
        } else {
            Log.d("PaymentItem", "Institution ID is null")
        }
    }

    val borderSize = 2.dp
    val borderBrush = if (dominantColors.size >= 2) {
        Brush.linearGradient(dominantColors)
    } else {
        Brush.linearGradient(listOf(Color.Gray, Color.LightGray))
    }

    // Log specific fields of the payment object
    Log.d("PaymentItem", "Payment description: ${payment.description}")
    Log.d("PaymentItem", "Payment amount: ${payment.amount}")
    Log.d("PaymentItem", "Payment date: ${payment.paymentDate}")
    Log.d("PaymentItem", "Institution name: $institutionName")

    payment.description?.let {
        PaymentAndTransactionCard(
            logoFile = logoFile,
            nameToShow = it,
            amount = payment.amount,
            bookingDateTime = payment.paymentDate,
            institutionName = institutionName,
            paidByUser = paidByUserName,
            borderBrush = borderBrush,
            borderSize = borderSize,
            onClick = onClick
        )
    }
}
