package com.splitter.splittr.data.local.dataClasses

data class BatchConversionResult(
    val totalPayments: Int,
    val successfulConversions: List<ConversionAttempt>,
    val failedConversions: List<ConversionAttempt>
) {
    val successCount: Int
        get() = successfulConversions.size

    val failureCount: Int
        get() = failedConversions.size

    val allSuccessful: Boolean
        get() = failedConversions.isEmpty() && successfulConversions.size == totalPayments
}