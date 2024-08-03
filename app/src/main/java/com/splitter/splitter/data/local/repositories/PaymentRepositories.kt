package com.splitter.splitter.data.local.repositories

import com.splitter.splitter.data.extensions.toEntity
import com.splitter.splitter.data.extensions.toModel
import com.splitter.splitter.data.local.dao.PaymentDao
import com.splitter.splitter.model.Payment

class PaymentRepository(private val paymentDao: PaymentDao) {

    suspend fun insert(payment: Payment) {
        paymentDao.insert(payment.toEntity())
    }

    suspend fun getAllPayments(): List<Payment> {
        return paymentDao.getAllPayments().map { it.toModel() }
    }

    suspend fun getPaymentById(id: Int): Payment? {
        return paymentDao.getPaymentById(id)?.toModel()
    }

    suspend fun deletePaymentById(id: Int) {
        paymentDao.deletePaymentById(id)
    }
}
