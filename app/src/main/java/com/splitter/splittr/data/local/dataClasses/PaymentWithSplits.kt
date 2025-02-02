package com.splitter.splittr.data.local.dataClasses

import com.splitter.splittr.data.local.entities.PaymentEntity
import com.splitter.splittr.data.local.entities.PaymentSplitEntity
import com.splitter.splittr.data.model.Payment
import com.splitter.splittr.data.model.PaymentSplit

data class PaymentWithSplits(
    val payment: Payment,
    val splits: List<PaymentSplit>,
)

data class PaymentEntityWithSplits(
    val payment: PaymentEntity,
    val splits: List<PaymentSplitEntity> = emptyList()
)