package com.helgolabs.trego.data.managers

import android.util.Log
import com.helgolabs.trego.data.calculators.SplitCalculator
import com.helgolabs.trego.data.local.dao.CurrencyConversionDao
import com.helgolabs.trego.data.local.dao.PaymentDao
import com.helgolabs.trego.data.local.dao.PaymentSplitDao
import com.helgolabs.trego.data.local.dataClasses.CurrencyConversionData
import com.helgolabs.trego.data.local.entities.CurrencyConversionEntity
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.EntityServerConverter
import com.helgolabs.trego.utils.NetworkUtils
import com.helgolabs.trego.utils.NetworkUtils.hasNetworkCapabilities
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.sign

private const val TAG = "CurrencyConversionManager"

class CurrencyConversionManager(
    private val apiService: ApiService,
    private val currencyConversionDao: CurrencyConversionDao,
    private val paymentDao: PaymentDao,
    private val paymentSplitDao: PaymentSplitDao,
    private val splitCalculator: SplitCalculator,
    private val entityServerConverter: EntityServerConverter,
    private val dispatchers: CoroutineDispatchers
) {
    suspend fun performConversion(
        paymentId: Int,
        fromCurrency: String,
        toCurrency: String,
        amount: Double,
        exchangeRate: Double,
        userId: Int,
        source: String,
        skipNotification: Boolean = false
    ): Result<CurrencyConversionEntity> = withContext(dispatchers.io) {
        try {
            Log.d(TAG, """
                Starting conversion:
                Payment ID: $paymentId
                From: $fromCurrency
                To: $toCurrency
                Amount: $amount
                Exchange Rate: $exchangeRate
            """.trimIndent())

            val currentTime = DateUtils.getCurrentTimestamp()

            // Store the sign of the original amount
            val originalSign = amount.sign
            val absAmount = kotlin.math.abs(amount)

            // Calculate total converted amount using BigDecimal for precision
            val convertedAmountBD = BigDecimal.valueOf(absAmount)
                .multiply(BigDecimal.valueOf(exchangeRate))
                .setScale(2, RoundingMode.HALF_UP)

            // Apply the sign to the BigDecimal amount for splits calculation
            val signedConvertedAmountBD = if (originalSign < 0) {
                convertedAmountBD.negate()
            } else {
                convertedAmountBD
            }

            val convertedAmount = signedConvertedAmountBD.toDouble()

            Log.d(TAG, """
                Conversion calculation:
                Converted amount (BigDecimal): $convertedAmountBD
                Signed converted amount (BigDecimal): $signedConvertedAmountBD
                Final converted amount: $convertedAmount
                Original sign: $originalSign
            """.trimIndent())

            // Get existing payment and its splits
            val existingPayment = paymentDao.getPaymentById(paymentId).first()
                ?: throw Exception("Payment not found")
            val existingSplits = paymentSplitDao.getPaymentSplitsByPayment(paymentId).first()

            Log.d(TAG, """
                Found existing payment and splits:
                Payment: ${existingPayment.id}, amount: ${existingPayment.amount}
                Split mode: ${existingPayment.splitMode}
                Number of splits: ${existingSplits.size}
                Original splits: ${existingSplits.joinToString { "${it.userId}: ${it.amount}" }}
            """.trimIndent())

            // Create updated payment
            val updatedPayment = existingPayment.copy(
                currency = toCurrency,
                amount = convertedAmount,
                updatedAt = currentTime,
                updatedBy = userId,
                syncStatus = SyncStatus.PENDING_SYNC,
                paymentDate = existingPayment.paymentDate
            )

            Log.d(TAG, "Original payment date: ${existingPayment.paymentDate}")
            Log.d(TAG, "Updated payment date after copy: ${updatedPayment.paymentDate}")

            // Create conversion entity
            val conversion = CurrencyConversionEntity(
                paymentId = paymentId,
                originalCurrency = fromCurrency,
                originalAmount = amount,
                finalCurrency = toCurrency,
                finalAmount = convertedAmount,
                exchangeRate = exchangeRate,
                source = source,
                createdBy = userId,
                updatedBy = userId,
                createdAt = currentTime,
                updatedAt = currentTime,
                syncStatus = SyncStatus.PENDING_SYNC
            )

            // Determine if splits are equal
            val areAllSplitsEqual = existingPayment.splitMode == "equally"
            Log.d(TAG, "Are all splits equal? $areAllSplitsEqual")

            // Use your existing split calculator with the exact BigDecimal target amount
            // The calculator should handle penny distribution automatically
            val convertedSplits = splitCalculator.calculateSplits(
                payment = updatedPayment, // Use updated payment with new currency/amount
                splits = existingSplits,
                targetAmount = signedConvertedAmountBD, // Use the exact BigDecimal amount with sign
                targetCurrency = toCurrency,
                userId = userId,
                currentTime = currentTime
            )

            Log.d(TAG, """
                Split calculator results:
                Calculated splits: ${convertedSplits.joinToString { "${it.userId}: ${it.amount}" }}
                Split sum: ${convertedSplits.sumOf { it.amount }}
                Target amount: $convertedAmount
            """.trimIndent())

            // Verify the splits using your existing verification functions
            val targetAmountBD = signedConvertedAmountBD
            if (!SplitCalculator.verifySplits(convertedSplits, targetAmountBD)) {
                Log.e(TAG, """
                    Split verification failed:
                    Target amount: $targetAmountBD
                    Split amounts: ${convertedSplits.map { "${it.userId}: ${BigDecimal.valueOf(it.amount).setScale(2, RoundingMode.HALF_UP)}" }}
                    Split sum: ${convertedSplits.map { BigDecimal.valueOf(it.amount).setScale(2, RoundingMode.HALF_UP) }.fold(BigDecimal.ZERO) { acc, amount -> acc.add(amount) }}
                """.trimIndent())
                throw IllegalStateException("Split verification failed - splits do not sum to target amount")
            }

            if (areAllSplitsEqual && !SplitCalculator.verifyEqualDistribution(convertedSplits)) {
                Log.e(TAG, """
                    Equal distribution verification failed:
                    Split amounts (cents): ${convertedSplits.map { (it.amount * 100).toLong() }}
                """.trimIndent())
                throw IllegalStateException("Equal distribution verification failed")
            }

            Log.d(TAG, "All split verifications passed successfully")

            // Perform database transaction
            var localConversionId: Long = 0
            paymentDao.runInTransaction {
                localConversionId = currencyConversionDao.insertConversion(conversion)
                paymentDao.updatePayment(updatedPayment)
                convertedSplits.forEach { split ->
                    paymentSplitDao.updatePaymentSplit(split)
                }
            }

            // Handle server sync if online
            if (hasNetworkCapabilities()) {
                try {
                    // Convert and update payment on server
                    val serverPaymentModel = entityServerConverter
                        .convertPaymentToServer(updatedPayment)
                        .getOrThrow()

                    val serverPayment = apiService.updatePayment(
                        paymentId = updatedPayment.serverId ?: throw Exception("No server ID for payment"),
                        payment = serverPaymentModel,
                        headers = mapOf(
                            "X-Currency-Conversion" to "true",
                            "X-Skip-Expense-Notification" to "true",
                            "X-Skip-Currency-Notification" to skipNotification.toString()
                        )
                    )

                    // Create currency conversion on server
                    val serverConversionModel = entityServerConverter
                        .convertCurrencyConversionToServer(conversion)
                        .getOrThrow()

                    val response = apiService.createCurrencyConversion(
                        serverConversionModel,
                        headers = mapOf("X-Skip-Currency-Notification" to skipNotification.toString())
                    )

                    val serverConversion = CurrencyConversionData(
                        currencyConversion = response,
                        payment = null,  // These could be populated if needed
                        group = null
                    )

                    // Update splits on server
                    convertedSplits.forEach { split ->
                        val serverSplitModel = entityServerConverter
                            .convertPaymentSplitToServer(split)
                            .getOrThrow()

                        if (split.serverId != null) {
                            apiService.updatePaymentSplit(
                                paymentId = updatedPayment.serverId,
                                splitId = split.serverId,
                                paymentSplit = serverSplitModel
                            )
                        } else {
                            apiService.createPaymentSplit(
                                paymentId = updatedPayment.serverId,
                                paymentSplit = serverSplitModel
                            )
                        }
                    }

                    // Update local entities with server data
                    val syncedConversion = conversion.copy(
                        id = localConversionId.toInt(),
                        serverId = response.id,
                        syncStatus = SyncStatus.SYNCED
                    )

                    paymentDao.runInTransaction {
                        currencyConversionDao.insertOrUpdateConversion(syncedConversion)
                        paymentDao.updatePayment(updatedPayment.copy(syncStatus = SyncStatus.SYNCED))
                        convertedSplits.forEach { split ->
                            paymentSplitDao.updatePaymentSplit(split.copy(syncStatus = SyncStatus.SYNCED))
                        }
                    }

                    Log.d(TAG, "Server payment date: ${serverPayment.paymentDate}")

                    Result.success(syncedConversion)
                } catch (e: Exception) {
                    Log.e("CurrencyConversionManager", "Failed to sync with server", e)
                    Result.success(conversion.copy(id = localConversionId.toInt()))
                }
            } else {
                Result.success(conversion.copy(id = localConversionId.toInt()))
            }
        } catch (e: Exception) {
            Log.e("CurrencyConversionManager", "Error performing conversion", e)
            Result.failure(e)
        }
    }
}