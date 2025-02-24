package com.helgolabs.trego.data.local.dataClasses

import com.helgolabs.trego.data.local.entities.PaymentEntity
import com.helgolabs.trego.data.local.entities.PaymentSplitEntity
import com.helgolabs.trego.data.model.Payment
import com.helgolabs.trego.data.model.PaymentSplit

data class PaymentWithSplits(
    val payment: Payment,
    val splits: List<PaymentSplit>,
)

data class PaymentEntityWithSplits(
    val payment: PaymentEntity,
    val splits: List<PaymentSplitEntity> = emptyList()
)