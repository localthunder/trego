package com.helgolabs.trego.data.calculators

import android.util.Log
import com.helgolabs.trego.data.local.entities.PaymentEntity
import com.helgolabs.trego.data.local.entities.PaymentSplitEntity
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.utils.SecureLogger
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
            "equally" -> calculateEqualSplits(
                splits,
                targetAmount,
                targetCurrency,
                userId,
                currentTime
            )

            "percentage" -> calculatePercentageSplits(
                splits,
                targetAmount,
                targetCurrency,
                userId,
                currentTime
            )

            else -> calculateUnequalSplits(
                splits,
                targetAmount,
                targetCurrency,
                userId,
                currentTime
            )
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

        // Use the exact target amount as provided
        val targetAmountBD = targetAmount.setScale(2, RoundingMode.HALF_UP)
        val numSplitsBD = BigDecimal(numSplits)

        // Calculate the base amount per split with FLOOR to ensure we don't exceed the total
        // For negative amounts, we need to be careful with the direction of rounding
        val perSplitBD = if (targetAmountBD >= BigDecimal.ZERO) {
            targetAmountBD.divide(numSplitsBD, 2, RoundingMode.FLOOR)
        } else {
            // For negative amounts, FLOOR rounds towards negative infinity, which increases the absolute value
            // We want to use CEILING to round towards zero (decrease absolute value) to avoid overshooting
            targetAmountBD.divide(numSplitsBD, 2, RoundingMode.CEILING)
        }

        // Calculate the initial total with the base amounts
        val initialTotal = perSplitBD.multiply(numSplitsBD)

        // Calculate the difference (what needs to be distributed as pennies)
        val difference = targetAmountBD.subtract(initialTotal)

        // Convert difference to pennies (cents)
        val pennies = difference
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .abs()
            .toInt()

        // Sort splits to ensure consistent distribution
        val sortedSplits = splits.sortedBy { it.userId }

        return sortedSplits.mapIndexed { index, split ->
            // Determine if this split gets an extra penny
            val adjustedAmount = if (index < pennies) {
                if (difference >= BigDecimal.ZERO) {
                    perSplitBD.add(BigDecimal("0.01"))
                } else {
                    perSplitBD.subtract(BigDecimal("0.01"))
                }
            } else {
                perSplitBD
            }

            // Create the adjusted split - convert to Double at the very end
            split.copy(
                currency = targetCurrency,
                amount = adjustedAmount.setScale(2, RoundingMode.HALF_UP).toDouble(),
                percentage = split.percentage,
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
        if (totalOriginalAbs == 0.0) return splits.map {
            it.copy(
                currency = targetCurrency,
                amount = 0.0
            )
        }

        val targetAmountBD = targetAmount.setScale(2, RoundingMode.HALF_UP)
        val totalOriginalAbsBD = BigDecimal.valueOf(totalOriginalAbs)

        // Calculate proportional splits based on absolute values
        val initialSplits = splits.associate { split ->
            val proportion = BigDecimal.valueOf(kotlin.math.abs(split.amount))
                .divide(totalOriginalAbsBD, 10, RoundingMode.HALF_UP)
            val splitAmount = proportion
                .multiply(targetAmountBD.abs())
                .setScale(2, RoundingMode.FLOOR) // Use FLOOR to avoid exceeding target
                .multiply(BigDecimal(targetAmountBD.signum())) // Apply the sign from target amount

            split.userId to splitAmount
        }

        // Calculate difference that needs to be distributed
        val splitSum = initialSplits.values.reduce { acc, value -> acc.add(value) }
        val difference = targetAmountBD.subtract(splitSum)

        // Convert difference to cents for penny-by-penny adjustment
        val centsDifference = difference
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .abs()
            .intValueExact()

        // Sort by absolute amount (largest first) to distribute pennies to largest splits
        val sortedSplits = splits.sortedWith(
            compareByDescending { split -> kotlin.math.abs(initialSplits[split.userId]!!.toDouble()) }
        )

        return sortedSplits.mapIndexed { index, split ->
            val baseAmount = initialSplits[split.userId]!!
            val adjustedAmount = if (index < centsDifference) {
                // Add or subtract one cent based on whether we need to increase or decrease the total
                if (difference > BigDecimal.ZERO) {
                    // We need to add to reach the target
                    baseAmount.add(BigDecimal("0.01"))
                } else {
                    // We need to subtract to reach the target
                    baseAmount.subtract(BigDecimal("0.01"))
                }
            } else {
                baseAmount
            }

            split.copy(
                currency = targetCurrency,
                amount = adjustedAmount.setScale(2, RoundingMode.HALF_UP).toDouble(),
                percentage = if (splits.any { it.percentage != null }) split.percentage else null,
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
        SecureLogger.d("DefaultSplitCalculator", "=== CALCULATING PERCENTAGE SPLITS ===")
        SecureLogger.d("DefaultSplitCalculator", "Input splits:")
        splits.forEach { split ->
            SecureLogger.d(
                "DefaultSplitCalculator",
                "  Split userId=${split.userId}, amount=${split.amount}, percentage=${split.percentage}"
            )
        }
        // Get total percentages to handle cases where they might not sum to 100%
        val totalPercentages = splits.sumOf { it.percentage ?: 0.0 }
        Log.d("DefaultSplitCalculator", "Total percentages: $totalPercentages")

        if (totalPercentages == 0.0) return splits.map {
            it.copy(
                currency = targetCurrency,
                amount = 0.0
            )
        }

        val targetAmountBD = targetAmount.setScale(2, RoundingMode.HALF_UP)
        val totalPercentagesBD = BigDecimal.valueOf(totalPercentages)

        // Initial calculation of amounts based on percentages
        val initialSplits = splits.associate { split ->
            val percentage = split.percentage ?: 0.0
            Log.d(
                "DefaultSplitCalculator",
                "Processing split for user ${split.userId} with percentage $percentage"
            )

            val proportion =
                BigDecimal.valueOf(percentage).divide(totalPercentagesBD, 10, RoundingMode.HALF_UP)
            val splitAmount = proportion
                .multiply(targetAmountBD.abs())
                .setScale(2, RoundingMode.FLOOR) // Use FLOOR to avoid exceeding target
                .multiply(BigDecimal(targetAmountBD.signum())) // Apply the sign from target amount

            split.userId to splitAmount
        }

        // Sum up all initial splits to check for rounding differences
        val splitSum = initialSplits.values.fold(BigDecimal.ZERO) { acc, value -> acc.add(value) }
        val difference = targetAmountBD.subtract(splitSum)

        // If there's no difference, just return the splits
        if (difference.compareTo(BigDecimal.ZERO) == 0) {
            return splits.map { split ->
                split.copy(
                    currency = targetCurrency,
                    amount = initialSplits[split.userId]!!.setScale(2, RoundingMode.HALF_UP)
                        .toDouble(),
                    percentage = split.percentage,
                    updatedAt = currentTime,
                    updatedBy = userId,
                    syncStatus = SyncStatus.PENDING_SYNC
                )
            }
        }

        // Handle rounding difference by distributing pennies
        val centsDifference = difference
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .abs()
            .intValueExact()

        // Sort by percentage (highest first) to give penny adjustments to largest splits
        val sortedSplits = splits.sortedByDescending { it.percentage ?: 0.0 }

        // Apply the adjustments to the splits
        val result = splits.map { split ->
            val baseAmount = initialSplits[split.userId]!!
            val adjustmentIndex = sortedSplits.indexOf(split)

            val adjustedAmount = if (adjustmentIndex < centsDifference) {
                if (difference > BigDecimal.ZERO) {
                    // We need to add to reach the target
                    baseAmount.add(BigDecimal("0.01"))
                } else {
                    // We need to subtract to reach the target
                    baseAmount.subtract(BigDecimal("0.01"))
                }
            } else {
                baseAmount
            }

            val resultSplit = split.copy(
                currency = targetCurrency,
                amount = adjustedAmount.setScale(2, RoundingMode.HALF_UP).toDouble(),
                updatedAt = currentTime,
                updatedBy = userId,
                syncStatus = SyncStatus.PENDING_SYNC,
                percentage = split.percentage  // PRESERVE THE PERCENTAGE
            )

            Log.d(
                "DefaultSplitCalculator",
                "Result split: userId=${resultSplit.userId}, amount=${resultSplit.amount}, percentage=${resultSplit.percentage}"
            )
            resultSplit
        }

        Log.d("DefaultSplitCalculator", "=== END CALCULATING PERCENTAGE SPLITS ===")
        return result
    }
}