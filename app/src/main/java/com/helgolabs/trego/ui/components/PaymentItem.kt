package com.helgolabs.trego.ui.components

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.dataClasses.UserInvolvement
import com.helgolabs.trego.data.local.entities.PaymentEntity
import com.helgolabs.trego.data.model.Payment
import com.helgolabs.trego.ui.viewmodels.PaymentsViewModel
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.catch

@Composable
fun PaymentItem(
    payment: PaymentEntity,
    context: Context,
    onClick: () -> Unit = {}
) {
    val paymentItemViewModel: PaymentsViewModel = viewModel(
        factory = (context.applicationContext as MyApplication).viewModelFactory
    )

    val paymentItemInfoMap by paymentItemViewModel.paymentItemInfo.collectAsState()
    val itemInfo = paymentItemInfoMap[payment.id]

    // State to track user involvement in the payment
    var userInvolvement by remember { mutableStateOf<UserInvolvement?>(null) }
    val currentUserId = remember { getUserIdFromPreferences(context) }
    LaunchedEffect(payment.id) {
        try {
            // Load payment item info (logos, transaction details, etc.)
            paymentItemViewModel.loadPaymentItemInfo(payment)

            // Only proceed if we have a current user ID
            if (currentUserId != null) {
                Log.d("PaymentItem", "Loading user involvement for payment ${payment.id} and user $currentUserId")

                // Collect user involvement with error handling
                paymentItemViewModel.getUserInvolvementInPayment(payment.id, currentUserId)
                    .catch { error ->
                        Log.e("PaymentItem", "Error collecting user involvement", error)
                        userInvolvement = UserInvolvement.NotInvolved
                    }
                    .collect { involvement ->
                        Log.d("PaymentItem", "Got involvement: $involvement for payment ${payment.id}")
                        userInvolvement = involvement
                    }
            } else {
                Log.d("PaymentItem", "Current user ID is null")
                userInvolvement = UserInvolvement.NotInvolved
            }
        } catch (e: Exception) {
            Log.e("PaymentItem", "Error in PaymentItem LaunchedEffect", e)
            userInvolvement = UserInvolvement.NotInvolved
        }
    }

    DisposableEffect(payment.id) {
        onDispose {
            paymentItemViewModel.clearPaymentItemInfo(payment.id)
        }
    }

    val gradientColors = remember(itemInfo?.paymentImage) {
        when (val image = itemInfo?.paymentImage) {
            is PaymentsViewModel.PaymentImage.Logo -> {
                if (image.logoInfo.dominantColors.size >= 2) {
                    image.logoInfo.dominantColors
                } else {
                    listOf(Color.Gray, Color.LightGray)
                }
            }
            else -> listOf(Color.Gray, Color.LightGray)
        }
    }

    PaymentAndTransactionCard(
        logoInfo = when (val image = itemInfo?.paymentImage) {
            is PaymentsViewModel.PaymentImage.Logo -> image.logoInfo
            else -> null
        },
        nameToShow = payment.description
            ?: itemInfo?.transaction?.creditorName
            ?: "no name",
        amount = payment.amount,
        bookingDateTime = payment.paymentDate,
        institutionName = itemInfo?.transaction?.institutionName
            ?: payment.institutionId
            ?: "N/A",
        borderSize = 2.dp,
        borderBrush = Brush.linearGradient(gradientColors),
        onClick = onClick,
        paidByUser = itemInfo?.paidByUsername,
        currency = payment.currency ?: "GBP",
        isPaidByCurrentUser = payment.paidByUserId == currentUserId,
        userInvolvement = userInvolvement
    )
}