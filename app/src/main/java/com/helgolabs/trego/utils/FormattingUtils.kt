package com.helgolabs.trego.utils

import android.util.Log
import androidx.compose.ui.graphics.Color
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

object FormattingUtils {
    fun formatAmount(amount: String): String {
        val value = amount.toDoubleOrNull() ?: return amount
        return if (value < 0) {
            "${DecimalFormat("#,##0.00").format(-value)}"
        } else {
            "+${DecimalFormat("#,##0.00").format(value)}"
        }
    }

    fun formatPaymentAmount(amount: String): String {
        val value = abs(amount.toDoubleOrNull() ?: return amount)

        // Handle zero value case explicitly
        if (value == 0.0) {
            return "0.00"
        }

        return "${DecimalFormat("#,###.00").format(value)}"
    }

    fun Double.formatAsCurrency(currencyCode: String): String {
        return formatAnyCurrency(this, currencyCode)
    }

    // Extension function for String
    fun String.formatAsCurrency(currencyCode: String): String {
        return try {
            val value = this.toDouble()
            formatAnyCurrency(value, currencyCode)
        } catch (e: NumberFormatException) {
            Log.e("FormattingUtils", "Failed to parse string as double: $this")
            formatAnyCurrency(0.0, currencyCode)
        }
    }

    // Private helper function that does the actual formatting
    private fun formatAnyCurrency(value: Double, currencyCode: String): String {
        return try {
            val currency = Currency.getInstance(currencyCode)
            NumberFormat.getCurrencyInstance().apply {
                this.currency = currency
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }.format(value)
        } catch (e: IllegalArgumentException) {
            // Fallback for unknown currency codes
            "$currencyCode ${NumberFormat.getNumberInstance().apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }.format(value)}"
        }
    }

    fun getAmountColor(amount: String): Color {
        val value = amount.toDoubleOrNull() ?: return Color.Black
        return if (value < 0) Color.Black else Color.Green
    }
}

