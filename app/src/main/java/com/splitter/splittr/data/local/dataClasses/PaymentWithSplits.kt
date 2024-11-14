package com.splitter.splittr.data.local.dataClasses

import com.splitter.splittr.data.model.Payment
import com.splitter.splittr.data.model.PaymentSplit

data class PaymentWithSplits(
    val payment: Payment,
    val splits: List<PaymentSplit>,
)