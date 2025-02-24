package com.helgolabs.trego.data.managers

import android.util.Log
import com.helgolabs.trego.data.calculators.SplitCalculator
import com.helgolabs.trego.data.calculators.SplitCalculator.Companion.verifyEqualDistribution
import com.helgolabs.trego.data.calculators.SplitCalculator.Companion.verifySplits
import com.helgolabs.trego.data.local.dao.CurrencyConversionDao
import com.helgolabs.trego.data.local.dao.PaymentDao
import com.helgolabs.trego.data.local.dao.PaymentSplitDao
import com.helgolabs.trego.data.local.dataClasses.CurrencyConversionData
import com.helgolabs.trego.data.local.entities.CurrencyConversionEntity
import com.helgolabs.trego.data.local.entities.PaymentEntity
import com.helgolabs.trego.data.local.entities.PaymentSplitEntity
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.EntityServerConverter
import com.helgolabs.trego.utils.NetworkUtils
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
        source: String
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

            // Calculate total converted amount
            val absConvertedAmount = (absAmount * exchangeRate)
                .toBigDecimal()
                .setScale(2, RoundingMode.HALF_UP)
                .toDouble()
            val convertedAmount = absConvertedAmount * originalSign

            Log.d(TAG, """
                Conversion calculation:
                Abs converted amount: $absConvertedAmount
                Final converted amount: $convertedAmount
            """.trimIndent())

            // Get existing payment and its splits
            val existingPayment = paymentDao.getPaymentById(paymentId).first()
                ?: throw Exception("Payment not found")
            val existingSplits = paymentSplitDao.getPaymentSplitsByPayment(paymentId).first()

            Log.d(TAG, """
                Found existing payment and splits:
                Payment: ${existingPayment.id}, amount: ${existingPayment.amount}
                Number of splits: ${existingSplits.size}
                Original splits: ${existingSplits.joinToString { "${it.userId}: ${it.amount}" }}
            """.trimIndent())

            // Create updated payment
            val updatedPayment = existingPayment.copy(
                currency = toCurrency,
                amount = convertedAmount,
                updatedAt = currentTime,
                updatedBy = userId,
                syncStatus = SyncStatus.PENDING_SYNC
            )

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
            val areAllSplitsEqual = existingPayment.splitMode == "equally" // Note: "equally" not "equal"
            Log.d(TAG, "Are all splits equal? $areAllSplitsEqual")

            // Convert total amount to cents
            val totalCents = (kotlin.math.abs(convertedAmount) * 100).toLong()
            val splitCount = existingSplits.size

            val convertedSplits = splitCalculator.calculateSplits(
                payment = existingPayment,
                splits = existingSplits,
                targetAmount = convertedAmount.toBigDecimal().setScale(2, RoundingMode.HALF_UP),
                targetCurrency = toCurrency,
                userId = userId,
                currentTime = currentTime
            )

            // Verify the splits
            if (!verifySplits(convertedSplits, convertedAmount.toBigDecimal())) {
                throw IllegalStateException("Split verification failed")
            }

            if (areAllSplitsEqual && !verifyEqualDistribution(convertedSplits)) {
                throw IllegalStateException("Equal distribution verification failed")
            }

//            // Calculate new splits
//            val convertedSplits = if (areAllSplitsEqual) {
//                Log.d(TAG, "Processing equal splits calculation")
//
//                // Convert total amount to BigDecimal with proper scale
//                val totalBD = BigDecimal(convertedAmount).setScale(2, RoundingMode.HALF_UP)
//                val numSplits = existingSplits.size
//
//                Log.d(TAG, """
//                    Equal splits initial values:
//                    Total (BigDecimal): $totalBD
//                    Number of splits: $numSplits
//                """.trimIndent())
//
//                // Calculate per-split amount using BigDecimal division
//                val perSplitBD = totalBD
//                    .divide(BigDecimal(numSplits), 2, RoundingMode.FLOOR)
//                    .abs() // Work with absolute values for calculation
//                    .multiply(BigDecimal(if (convertedAmount >= 0) 1 else -1)) // Reapply sign
//
//                Log.d(TAG, "Base per-split amount: $perSplitBD")
//
//                // Calculate how much we need to distribute to make up the total
//                val totalWithBaseSplits = perSplitBD.multiply(BigDecimal(numSplits))
//                val remainingAmount = totalBD.subtract(totalWithBaseSplits)
//                val centsDifference = remainingAmount
//                    .multiply(BigDecimal(100))
//                    .setScale(0, RoundingMode.HALF_UP)
//                    .abs()
//                    .intValueExact()
//
//                Log.d(TAG, """
//                    Distribution calculation:
//                    Total with base splits: $totalWithBaseSplits
//                    Remaining amount: $remainingAmount
//                    Cents to distribute: $centsDifference
//                """.trimIndent())
//
//                // Sort splits for consistent distribution
//                existingSplits
//                    .sortedBy { it.userId }
//                    .mapIndexed { index, split ->
//                        val adjustedAmount = if (index < centsDifference) {
//                            // Add or subtract one cent based on original amount sign
//                            if (convertedAmount <= 0) {
//                                perSplitBD.add(BigDecimal("0.01"))
//                            } else {
//                                perSplitBD.subtract(BigDecimal("0.01"))
//                            }
//                        } else {
//                            perSplitBD
//                        }
//
//                        Log.d(TAG, "Split ${split.userId} adjusted amount: $adjustedAmount")
//
//                        split.copy(
//                            currency = toCurrency,
//                            amount = adjustedAmount.toDouble(),
//                            updatedAt = currentTime,
//                            updatedBy = userId,
//                            syncStatus = SyncStatus.PENDING_SYNC
//                        ).also {
//                            Log.d("CurrencyConversionManager",
//                                "Split ${split.userId}: amount=${adjustedAmount}")
//                        }
//                    }
//            } else {
//                // For unequal splits, maintain proportions with BigDecimal
//                val totalOriginalAbs = existingSplits.sumOf { kotlin.math.abs(it.amount) }
//                val convertedBD = BigDecimal(convertedAmount).setScale(2, RoundingMode.HALF_UP)
//
//                // First pass: calculate proportional amounts
//                val initialSplits = existingSplits.map { split ->
//                    val proportion = BigDecimal(kotlin.math.abs(split.amount) / totalOriginalAbs)
//                    val splitAmount = proportion
//                        .multiply(convertedBD.abs())
//                        .setScale(2, RoundingMode.FLOOR)  // Use FLOOR to prevent exceeding total
//                        .multiply(BigDecimal(split.amount.sign))
//
//                    split.userId to splitAmount
//                }.toMap()
//
//                // Calculate difference from target amount
//                val splitSum = initialSplits.values.reduce { acc, value -> acc.add(value) }
//                val difference = convertedBD.subtract(splitSum)
//                val centsDifference = difference
//                    .multiply(BigDecimal(100))
//                    .setScale(0, RoundingMode.HALF_UP)
//                    .abs()
//                    .intValueExact()
//
//                // Second pass: distribute remaining cents to largest splits
//                existingSplits
//                    .sortedWith(compareByDescending { split ->
//                        kotlin.math.abs(initialSplits[split.userId]!!.toDouble())
//                    })
//                    .mapIndexed { index, split ->
//                        val baseAmount = initialSplits[split.userId]!!
//                        val adjustedAmount = if (index < centsDifference) {
//                            if (convertedAmount <= 0) {
//                                baseAmount.add(BigDecimal("0.01"))
//                            } else {
//                                baseAmount.subtract(BigDecimal("0.01"))
//                            }
//                        } else {
//                            baseAmount
//                        }
//
//                        split.copy(
//                            currency = toCurrency,
//                            amount = adjustedAmount.toDouble(),
//                            updatedAt = currentTime,
//                            updatedBy = userId,
//                            syncStatus = SyncStatus.PENDING_SYNC
//                        )
//                    }
//            }
//
//            // Verify splits
//            val splitSum = convertedSplits.sumOf {
//                it.amount.toBigDecimal().setScale(2, RoundingMode.HALF_UP)
//            }
//            val convertedBD = convertedAmount.toBigDecimal().setScale(2, RoundingMode.HALF_UP)
//
//            if (splitSum != convertedBD) {
//                throw IllegalStateException("""
//                    Split sum doesn't match payment amount!
//                    Payment amount: $convertedAmount
//                    Split sum: $splitSum
//                    Difference: ${convertedAmount - splitSum.toDouble()}
//                    Splits: ${convertedSplits.joinToString { "${it.userId}: ${it.amount}" }}
//                """.trimIndent())
//            }
//
//            // Verify distribution for equal splits
//            if (areAllSplitsEqual) {
//                val splitAmounts = convertedSplits.map { (it.amount * 100).toLong() }
//                val maxDiff = splitAmounts.maxOrNull()!! - splitAmounts.minOrNull()!!
//                if (maxDiff > 1) {
//                    throw IllegalStateException("""
//                        Uneven split distribution detected!
//                        Maximum difference between splits: ${maxDiff/100.0}
//                        Split amounts: ${convertedSplits.joinToString { "${it.userId}: ${it.amount}" }}
//                    """.trimIndent())
//                }
//            }

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
            if (NetworkUtils.isOnline()) {
                try {
                    // Convert and update payment on server
                    val serverPaymentModel = entityServerConverter
                        .convertPaymentToServer(updatedPayment)
                        .getOrThrow()
                    val serverPayment = apiService.updatePayment(
                        paymentId = updatedPayment.serverId
                            ?: throw Exception("No server ID for payment"),
                        payment = serverPaymentModel
                    )

                    // Create currency conversion on server
                    val serverConversionModel = entityServerConverter
                        .convertCurrencyConversionToServer(conversion)
                        .getOrThrow()

                    val response = apiService.createCurrencyConversion(serverConversionModel)

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
                    val syncedConversion = entityServerConverter
                        .convertCurrencyConversionFromServer(serverConversion)
                        .getOrThrow()
                        .copy(id = localConversionId.toInt())

                    paymentDao.runInTransaction {
                        currencyConversionDao.updateConversion(syncedConversion.copy(syncStatus = SyncStatus.SYNCED))
                        paymentDao.updatePayment(updatedPayment.copy(syncStatus = SyncStatus.SYNCED))
                        convertedSplits.forEach { split ->
                            paymentSplitDao.updatePaymentSplit(split.copy(syncStatus = SyncStatus.SYNCED))
                        }
                    }

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