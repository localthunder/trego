package com.splitter.splittr.data.calculators

import com.splitter.splittr.data.local.entities.PaymentEntity
import com.splitter.splittr.data.local.entities.PaymentSplitEntity
import com.splitter.splittr.data.sync.SyncStatus
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.sign

class DefaultSplitCalculator : SplitCalculator {
    override fun calculateSplits(
        payment: PaymentEntity,
        splits: List<PaymentSplitEntity>,
        targetAmount: BigDecimal,
        targetCurrency: String,
        userId: Int,
        currentTime: String
    ): List<PaymentSplitEntity> {
        return if (payment.splitMode == "equally") {
            calculateEqualSplits(splits, targetAmount, targetCurrency, userId, currentTime)
        } else {
            calculateUnequalSplits(splits, targetAmount, targetCurrency, userId, currentTime)
        }
    }

    private fun calculateEqualSplits(
        splits: List<PaymentSplitEntity>,
        targetAmount: BigDecimal,
        targetCurrency: String,
        userId: Int,
        currentTime: String
    ): List<PaymentSplitEntity> {
        val numSplits = splits.size
        val perSplitBD = targetAmount
            .divide(BigDecimal(numSplits), 2, RoundingMode.FLOOR)
            .abs()
            .multiply(BigDecimal(if (targetAmount >= BigDecimal.ZERO) 1 else -1))

        val totalWithBaseSplits = perSplitBD.multiply(BigDecimal(numSplits))
        val remainingAmount = targetAmount.subtract(totalWithBaseSplits)
        val centsDifference = remainingAmount
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .abs()
            .intValueExact()

        return splits
            .sortedBy { it.userId }
            .mapIndexed { index, split ->
                val adjustedAmount = if (index < centsDifference) {
                    if (targetAmount <= BigDecimal.ZERO) {
                        perSplitBD.add(BigDecimal("0.01"))
                    } else {
                        perSplitBD.subtract(BigDecimal("0.01"))
                    }
                } else {
                    perSplitBD
                }

                split.copy(
                    currency = targetCurrency,
                    amount = adjustedAmount.toDouble(),
                    updatedAt = currentTime,
                    updatedBy = userId,
                    syncStatus = SyncStatus.PENDING_SYNC
                )
            }
    }

    private fun calculateUnequalSplits(
        splits: List<PaymentSplitEntity>,
        targetAmount: BigDecimal,
        targetCurrency: String,
        userId: Int,
        currentTime: String
    ): List<PaymentSplitEntity> {
        val totalOriginalAbs = splits.sumOf { kotlin.math.abs(it.amount) }

        val initialSplits = splits.associate { split ->
            val proportion = BigDecimal(kotlin.math.abs(split.amount) / totalOriginalAbs)
            val splitAmount = proportion
                .multiply(targetAmount.abs())
                .setScale(2, RoundingMode.FLOOR)
                .multiply(BigDecimal(split.amount.sign))

            split.userId to splitAmount
        }

        val splitSum = initialSplits.values.reduce { acc, value -> acc.add(value) }
        val difference = targetAmount.subtract(splitSum)
        val centsDifference = difference
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .abs()
            .intValueExact()

        return splits
            .sortedWith(compareByDescending { split ->
                kotlin.math.abs(initialSplits[split.userId]!!.toDouble())
            })
            .mapIndexed { index, split ->
                val baseAmount = initialSplits[split.userId]!!
                val adjustedAmount = if (index < centsDifference) {
                    if (targetAmount <= BigDecimal.ZERO) {
                        baseAmount.add(BigDecimal("0.01"))
                    } else {
                        baseAmount.subtract(BigDecimal("0.01"))
                    }
                } else {
                    baseAmount
                }

                split.copy(
                    currency = targetCurrency,
                    amount = adjustedAmount.toDouble(),
                    updatedAt = currentTime,
                    updatedBy = userId,
                    syncStatus = SyncStatus.PENDING_SYNC
                )
            }
    }
}