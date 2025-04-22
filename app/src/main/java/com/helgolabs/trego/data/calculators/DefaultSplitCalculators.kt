package com.helgolabs.trego.data.calculators

import com.helgolabs.trego.data.local.entities.PaymentEntity
import com.helgolabs.trego.data.local.entities.PaymentSplitEntity
import com.helgolabs.trego.data.sync.SyncStatus
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
        return when (payment.splitMode) {
            "equally" -> calculateEqualSplits(splits, targetAmount, targetCurrency, userId, currentTime)
            "percentage" -> calculatePercentageSplits(splits, targetAmount, targetCurrency, userId, currentTime)
            else -> calculateUnequalSplits(splits, targetAmount, targetCurrency, userId, currentTime)
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
        if (numSplits == 0) return emptyList()

        // Calculate the base amount per split with FLOOR to ensure we don't exceed the total
        val perSplitBD = targetAmount
            .divide(BigDecimal(numSplits), 2, RoundingMode.FLOOR)

        // Calculate the initial total with the floored amounts
        val initialTotal = perSplitBD.multiply(BigDecimal(numSplits))

        // Calculate the difference (what needs to be distributed as pennies)
        val difference = targetAmount.subtract(initialTotal)

        // Convert difference to pennies (cents)
        val pennies = difference
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .toInt()

        // Sort splits to ensure consistent distribution
        // We'll adjust the first 'pennies' splits by adding one penny to each
        val sortedSplits = splits.sortedBy { it.userId }

        return sortedSplits.mapIndexed { index, split ->
            // Determine if this split gets an extra penny
            val adjustedAmount = if (index < pennies) {
                perSplitBD.add(BigDecimal("0.01"))
            } else {
                perSplitBD
            }

            // Create the adjusted split
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

    private fun calculatePercentageSplits(
        splits: List<PaymentSplitEntity>,
        targetAmount: BigDecimal,
        targetCurrency: String,
        userId: Int,
        currentTime: String
    ): List<PaymentSplitEntity> {
        // Get total percentages to handle cases where they might not sum to 100%
        val totalPercentages = splits.sumOf { it.percentage ?: 0.0 }

        // Initial calculation of amounts based on percentages
        val initialSplits = splits.associate { split ->
            val percentage = split.percentage ?: 0.0
            val proportion = BigDecimal(percentage / totalPercentages)
            val splitAmount = proportion
                .multiply(targetAmount.abs())
                .setScale(2, RoundingMode.HALF_UP) // First round to 2 decimal places
                .multiply(BigDecimal(if (targetAmount >= BigDecimal.ZERO) 1 else -1))

            split.userId to splitAmount
        }

        // Sum up all initial splits to check for rounding differences
        val splitSum = initialSplits.values.fold(BigDecimal.ZERO) { acc, value -> acc.add(value) }
        val difference = targetAmount.subtract(splitSum)

        // If there's no difference, just return the splits
        if (difference.compareTo(BigDecimal.ZERO) == 0) {
            return splits.map { split ->
                split.copy(
                    currency = targetCurrency,
                    amount = initialSplits[split.userId]!!.toDouble(),
                    updatedAt = currentTime,
                    updatedBy = userId,
                    syncStatus = SyncStatus.PENDING_SYNC
                )
            }
        }

        // Handle rounding difference (could be positive or negative)
        // Sort by the distance between exact percentage amount and rounded amount
        // to distribute the penny to the split that's most affected by rounding
        val pennyCorrectionOrder = splits.sortedByDescending { split ->
            val exactAmount = targetAmount.multiply(BigDecimal(split.percentage!! / totalPercentages))
            val roundedAmount = initialSplits[split.userId]!!
            // Calculate the error percentage (how much rounding affected this split)
            exactAmount.subtract(roundedAmount).abs().divide(exactAmount, 8, RoundingMode.HALF_UP)
        }

        // Get the count of one-cent adjustments needed
        val centsDifference = difference
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .abs()
            .intValueExact()

        // Apply the adjustments to the splits
        return splits.map { split ->
            val baseAmount = initialSplits[split.userId]!!
            val adjustmentIndex = pennyCorrectionOrder.indexOf(split)

            val adjustedAmount = if (adjustmentIndex < centsDifference) {
                if (difference < BigDecimal.ZERO) {
                    // We allocated too much, need to subtract
                    baseAmount.subtract(BigDecimal("0.01"))
                } else {
                    // We allocated too little, need to add
                    baseAmount.add(BigDecimal("0.01"))
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