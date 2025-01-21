package com.splitter.splittr.data.local.dataClasses

import com.splitter.splittr.data.model.Payment
import com.splitter.splittr.data.model.PaymentSplit

data class PaymentWithSplitsAndLocalIds(
    val payment: Payment,
    val splits: List<PaymentSplit>,
    val localPaymentId: Int,
    val localSplitIds: List<Int>
)