package com.helgolabs.trego.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.helgolabs.trego.utils.CurrencyUtils
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertCurrencyButton(
    modifier: Modifier = Modifier,
    currentCurrency: String,
    targetCurrency: String,
    amount: Double,
    isConverting: Boolean,
    onPrepareConversion: () -> Boolean,
    onConvertClicked: (Boolean, Double?) -> Unit,
    conversionError: String? = null,
    exchangeRate: Double? = null,
    rateDate: String? = null,
    // Add a function to trigger showing the sheet from outside
    showSheetTrigger: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by remember { mutableStateOf(false) }
    var isCustomRate by remember { mutableStateOf(false) }
    var customRate by remember { mutableStateOf("") }

    // React to the trigger
    LaunchedEffect(showSheetTrigger) {
        if (showSheetTrigger && !showSheet) {
            showSheet = true
            sheetState.show()
        }
    }

    IconButton(
        onClick = {
            // Call preparation logic before showing sheet
            val shouldProceed = onPrepareConversion()
            if (shouldProceed) {
                showSheet = true
                scope.launch { sheetState.show() }
            }
        },
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.CompareArrows,
            contentDescription = "Convert to $targetCurrency",
        )
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showSheet = false
                isCustomRate = false
                customRate = ""
                onConvertClicked(false, null)
            },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    "Convert Currency",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Currency conversion details
                val absAmount = abs(amount)
                val formattedOriginalAmount = "${CurrencyUtils.currencySymbols[currentCurrency]}$absAmount"

                val effectiveRate = if (isCustomRate && customRate.isNotEmpty()) {
                    customRate.toDoubleOrNull() ?: exchangeRate
                } else {
                    exchangeRate
                }

                // Calculate preview amount if rate is available
                val previewAmount = if (effectiveRate != null) {
                    absAmount * effectiveRate
                } else {
                    null
                }

                // Original amount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "From:",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.width(80.dp)
                    )
                    Text(
                        "$formattedOriginalAmount ${currentCurrency.uppercase()}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Target amount preview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "To:",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.width(80.dp)
                    )
                    if (previewAmount != null) {
                        Text(
                            "${CurrencyUtils.currencySymbols[targetCurrency]}${String.format("%.2f", previewAmount)} ${targetCurrency.uppercase()}",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.primary,
                            )
                        )
                    } else {
                        Text(
                            "Loading...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Exchange rate information
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        if (!isCustomRate) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Exchange Rate:",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                if (exchangeRate != null) {
                                    Text(
                                        "1 ${currentCurrency.uppercase()} = ${String.format("%.4f", exchangeRate)} ${targetCurrency.uppercase()}",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                } else {
                                    Text(
                                        "Loading...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Rate date information
                            if (rateDate != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Rate Date:",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Text(
                                        rateDate,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        } else {
                            CustomRateInput(
                                fromCurrency = currentCurrency,
                                toCurrency = targetCurrency,
                                rate = customRate,
                                onRateChange = { customRate = it }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Toggle between market and custom rate
                OutlinedButton(
                    onClick = { isCustomRate = !isCustomRate },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isCustomRate) Icons.Default.Refresh else Icons.Default.Edit,
                        contentDescription = if (isCustomRate) "Use Market Rate" else "Use Custom Rate",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isCustomRate) "Use Market Rate" else "Use Custom Rate")
                }

                if (isConverting) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (conversionError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = conversionError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            showSheet = false
                            isCustomRate = false
                            customRate = ""
                            onConvertClicked(false, null)
                        },
                        enabled = !isConverting
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = {
                            val rate = if (isCustomRate) customRate.toDoubleOrNull() else null
                            onConvertClicked(true, rate)
                            showSheet = false
                        },
                        enabled = !isConverting && (!isCustomRate || customRate.toDoubleOrNull() != null)
                    ) {
                        Text("Convert")
                    }
                }
            }
        }
    }
}