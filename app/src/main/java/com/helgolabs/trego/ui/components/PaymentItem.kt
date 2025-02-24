package com.helgolabs.trego.ui.components

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.entities.PaymentEntity
import com.helgolabs.trego.data.model.Payment
import com.helgolabs.trego.ui.viewmodels.PaymentsViewModel

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

    LaunchedEffect(payment.id) {
        paymentItemViewModel.loadPaymentItemInfo(payment)
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
        currency = payment.currency ?: "no currency"
    )
}