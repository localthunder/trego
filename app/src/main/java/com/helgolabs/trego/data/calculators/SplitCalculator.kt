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
            val splitSum = splits.sumOf {
                it.amount.toBigDecimal().setScale(2, RoundingMode.HALF_UP)
            }
            return splitSum == targetAmount
        }

        fun verifyEqualDistribution(splits: List<PaymentSplitEntity>): Boolean {
            val splitAmounts = splits.map { (it.amount * 100).toLong() }
            val maxDiff = splitAmounts.maxOrNull()!! - splitAmounts.minOrNull()!!
            return maxDiff <= 1
        }
    }
}