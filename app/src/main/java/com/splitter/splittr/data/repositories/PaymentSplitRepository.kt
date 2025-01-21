package com.splitter.splittr.data.repositories

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import com.splitter.splittr.MyApplication
import com.splitter.splittr.data.extensions.toEntity
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.dao.GroupDao
import com.splitter.splittr.data.local.dao.PaymentDao
import com.splitter.splittr.data.local.dao.PaymentSplitDao
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.sync.SyncStatus
import com.splitter.splittr.data.model.PaymentSplit
import com.splitter.splittr.utils.CoroutineDispatchers
import com.splitter.splittr.utils.NetworkUtils
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PaymentSplitRepository(
    private val paymentSplitDao: PaymentSplitDao,
    private val paymentDao: PaymentDao,
    private val groupDao: GroupDao,
    private val apiService: ApiService,
    private val dispatchers: CoroutineDispatchers,
    private val context: Context
) {
    val myApplication = context.applicationContext as MyApplication

    fun getPaymentSplitsByPayment(paymentId: Int): Flow<List<PaymentSplit>> = flow {
        // Emit data from local database first
        emitAll(paymentSplitDao.getPaymentSplitsByPayment(paymentId).map { entities ->
            entities.map { entity -> entity.toModel() }
        })

        // Then fetch from API
        try {
            val remotePaymentSplits = apiService.getPaymentSplitsByPayment(paymentId)
            // Update local database with new data
            paymentSplitDao.insertAllPaymentSplits(remotePaymentSplits.map { it.toEntity() })
            // Emit new data
            emit(remotePaymentSplits)
        } catch (e: Exception) {
            // Log error or handle it as needed
            Log.e("PaymentSplitRepository", "Error fetching payment splits from API", e)
        }
    }.flowOn(dispatchers.io)

}