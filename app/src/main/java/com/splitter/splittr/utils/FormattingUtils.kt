package com.splitter.splittr.utils

import androidx.compose.ui.graphics.Color
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

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
        val value = amount.toDoubleOrNull() ?: return amount
        return "${DecimalFormat("#,##0.00").format(value)}"
    }

    fun getAmountColor(amount: String): Color {
        val value = amount.toDoubleOrNull() ?: return Color.Black
        return if (value < 0) Color.Black else Color.Green
    }

    fun formatDate(dateStr: String): String {
        val dateFormats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", // ISO 8601 with milliseconds and timezone (e.g., 2024-07-15T00:00:00.000Z)
            "yyyy-MM-dd'T'HH:mm:ssXXX",    // ISO 8601 with timezone (e.g., 2024-07-15T00:00:00Z)
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",   // ISO 8601 with milliseconds and timezone offset (e.g., 2024-07-15T00:00:00.000+02:00)
            "yyyy-MM-dd'T'HH:mm:ss",        // ISO 8601 without timezone (e.g., 2024-07-15T00:00:00)
            "yyyy-MM-dd"                    // Date only (e.g., 2024-07-15)
        )

        val outputFormatterCurrentYear = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        val outputFormatterFull = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

        for (format in dateFormats) {
            try {
                val formatter = DateTimeFormatter.ofPattern(format, Locale.getDefault())
                val parsedDate = when {
                    format.contains("XXX") -> ZonedDateTime.parse(dateStr, formatter)
                    else -> LocalDate.parse(dateStr, formatter)
                }

                // Determine if current year formatting is needed
                val currentYear = LocalDate.now().year
                return if (parsedDate is LocalDate || (parsedDate is ZonedDateTime && parsedDate.year == currentYear)) {
                    outputFormatterCurrentYear.format(parsedDate)
                } else {
                    outputFormatterFull.format(parsedDate)
                }
            } catch (e: DateTimeParseException) {
                // Continue to next format if parsing fails
            }
        }
        // Return "N/A" if no formats match
        return "N/A"
    }
}

