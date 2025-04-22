package com.helgolabs.trego.data.calculators

import com.helgolabs.trego.data.local.entities.PaymentEntity
import com.helgolabs.trego.data.local.entities.PaymentSplitEntity
import java.math.BigDecimal
import java.math.RoundingMode

interface SplitCalculator {
    fun calculateSplits(
        payment: PaymentEntity,
        splits: List<PaymentSplitEntity>,
        targetAmount: BigDecimal,
        targetCurrency: String,
        userId: Int,
        currentTime: String
    ): List<PaymentSplitEntity>

    companion object {
        fun verifySplits(splits: List<PaymentSplitEntity>, targetAmount: BigDecimal): Boolean {
            if (splits.isEmpty()) return targetAmount == BigDecimal.ZERO

            // Sum up the splits, ensuring consistent rounding
            val splitSum = splits
                .map { it.amount.toBigDecimal().setScale(2, RoundingMode.HALF_UP) }
                .fold(BigDecimal.ZERO) { acc, amount -> acc.add(amount) }
                .setScale(2, RoundingMode.HALF_UP)

            // Compare with target amount, allowing a very tiny difference due to floating point
            val targetRounded = targetAmount.setScale(2, RoundingMode.HALF_UP)

            // Check if they are equal or extremely close (difference less than 0.01)
            return splitSum.subtract(targetRounded).abs() < BigDecimal("0.01")
        }

        fun verifyEqualDistribution(splits: List<PaymentSplitEntity>): Boolean {
            val splitAmounts = splits.map { (it.amount * 100).toLong() }
            val maxDiff = splitAmounts.maxOrNull()!! - splitAmounts.minOrNull()!!
            return maxDiff <= 1
        }
    }
}