package com.splitter.splittr.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.splitter.splittr.data.repositories.PaymentRepository
import com.splitter.splittr.utils.CurrencyUtils
import kotlinx.coroutines.launch

@Composable
fun ConvertCurrencyButton(
    currentCurrency: String,
    targetCurrency: String,
    amount: Double,
    isConverting: Boolean,
    onConvertClicked: (Boolean, Double?) -> Unit,
    conversionError: String? = null,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    var isCustomRate by remember { mutableStateOf(false) }
    var customRate by remember { mutableStateOf("") }

    IconButton(
        onClick = { showDialog = true },
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.CompareArrows,
            contentDescription = "Convert to $targetCurrency",
            tint = MaterialTheme.colorScheme.primary
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                isCustomRate = false
                customRate = ""
            },
            title = { Text("Convert Currency") },
            text = {
                Column {
                    Text(
                        "Convert ${CurrencyUtils.currencySymbols[currentCurrency]}$amount to " +
                                "${CurrencyUtils.currencySymbols[targetCurrency]}?"
                    )

                    // Custom rate input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isCustomRate) {
                            OutlinedTextField(
                                value = customRate,
                                onValueChange = {
                                    if (it.isEmpty() || it.toDoubleOrNull() != null) {
                                        customRate = it
                                    }
                                },
                                label = { Text("Custom Rate") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        TextButton(
                            onClick = { isCustomRate = !isCustomRate }
                        ) {
                            Text(if (isCustomRate) "Use Market Rate" else "Custom Rate")
                        }
                    }

                    if (isConverting) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        )
                    }

                    if (conversionError != null) {
                        Text(
                            text = conversionError,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val rate = if (isCustomRate) customRate.toDoubleOrNull() else null
                        onConvertClicked(true, rate)
                        showDialog = false
                    },
                    enabled = !isConverting && (!isCustomRate || customRate.toDoubleOrNull() != null)
                ) {
                    Text("Convert")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        isCustomRate = false
                        customRate = ""
                        onConvertClicked(false, null)
                    },
                    enabled = !isConverting
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}