package com.helgolabs.trego.data.local.dataClasses

import com.helgolabs.trego.data.model.Payment
import com.helgolabs.trego.data.model.PaymentSplit

data class PaymentWithSplitsAndLocalIds(
    val payment: Payment,
    val splits: List<PaymentSplit>,
    val localPaymentId: Int,
    val localSplitIds: List<Int>,
    val deletedSplitIds: List<Int>

)