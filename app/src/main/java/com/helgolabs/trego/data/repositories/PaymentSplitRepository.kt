package com.helgolabs.trego.data.repositories

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.extensions.toEntity
import com.helgolabs.trego.data.extensions.toModel
import com.helgolabs.trego.data.local.dao.GroupDao
import com.helgolabs.trego.data.local.dao.PaymentDao
import com.helgolabs.trego.data.local.dao.PaymentSplitDao
import com.helgolabs.trego.data.local.entities.PaymentSplitEntity
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.data.model.PaymentSplit
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.NetworkUtils
import com.helgolabs.trego.utils.getUserIdFromPreferences
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