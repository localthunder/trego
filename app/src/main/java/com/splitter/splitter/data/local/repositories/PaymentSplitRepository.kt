package com.splitter.splitter.data.local.repositories

import com.splitter.splitter.data.extensions.toEntity
import com.splitter.splitter.data.extensions.toModel
import com.splitter.splitter.data.local.dao.PaymentSplitDao
import com.splitter.splitter.model.PaymentSplit

class PaymentSplitRepository(private val paymentSplitDao: PaymentSplitDao) {

    suspend fun insert(paymentSplit: PaymentSplit) {
        paymentSplitDao.insert(paymentSplit.toEntity())
    }

    suspend fun getAllPaymentSplits(): List<PaymentSplit> {
        return paymentSplitDao.getAllPaymentSplits().map { it.toModel() }
    }

    suspend fun getPaymentSplitById(id: Int): PaymentSplit? {
        return paymentSplitDao.getPaymentSplitById(id)?.toModel()
    }

    suspend fun deletePaymentSplitById(id: Int) {
        paymentSplitDao.deletePaymentSplitById(id)
    }
}
