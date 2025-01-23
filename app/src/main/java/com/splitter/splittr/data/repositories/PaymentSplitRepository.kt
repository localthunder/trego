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
import com.splitter.splittr.data.local.entities.PaymentSplitEntity
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

    fun getPaymentSplitsByPayment(paymentId: Int): Flow<List<PaymentSplitEntity>> = flow {
        Log.d("PaymentSplitRepository", "Fetching splits for payment: $paymentId")

        // Get local splits - collect instead of first()
        paymentSplitDao.getPaymentSplitsByPayment(paymentId)
            .collect { localSplits ->
                Log.d("PaymentSplitRepository", "Found local splits: ${localSplits.size}")
                emit(localSplits)
            }

        // Then fetch from API if online
        if (NetworkUtils.isOnline()) {
            try {
                // Get the payment to find its server ID
                val payment = paymentDao.getPaymentById(paymentId).first()
                    ?: throw Exception("Payment not found")

                val serverPaymentId = payment.serverId
                    ?: throw Exception("Payment has no server ID")

                Log.d("PaymentSplitRepository", "Fetching splits from API for server ID: $serverPaymentId")
                val remotePaymentSplits = apiService.getPaymentSplitsByPayment(serverPaymentId)
                Log.d("PaymentSplitRepository", "Received remote splits: ${remotePaymentSplits.size}")

                // Convert and save splits
                val convertedSplits = remotePaymentSplits.mapNotNull { serverSplit ->
                    try {
                        Log.d("PaymentSplitRepository", "Converting split: ${serverSplit.id}")
                        myApplication.entityServerConverter
                            .convertPaymentSplitFromServer(serverSplit)
                            .getOrNull()
                    } catch (e: Exception) {
                        Log.e("PaymentSplitRepository", "Error converting split: ${serverSplit.id}", e)
                        null
                    }
                }

                if (convertedSplits.isNotEmpty()) {
                    paymentSplitDao.runInTransaction {
                        try {
                            paymentSplitDao.insertAllPaymentSplits(convertedSplits)
                            Log.d("PaymentSplitRepository", "Successfully saved splits to database")
                        } catch (e: Exception) {
                            Log.e("PaymentSplitRepository", "Error saving splits to database", e)
                        }
                    }
                    // The updated splits will be emitted through the Flow automatically
                    // due to Room's observation of the database
                }
            } catch (e: Exception) {
                Log.e("PaymentSplitRepository", "Error in remote splits fetch/processing", e)
            }
        }
    }.flowOn(dispatchers.io)
}