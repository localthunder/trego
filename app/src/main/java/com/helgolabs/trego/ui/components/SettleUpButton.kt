package com.helgolabs.trego.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.helgolabs.trego.data.local.dataClasses.SettlingInstruction
import com.helgolabs.trego.utils.FormattingUtils.formatAsCurrency
import kotlinx.coroutines.launch

/**
 * A reusable button component for settling up balances with built-in bottom sheet.
 * This can be used on various screens when a settlement action is needed.
 *
 * @param instruction The settling instruction containing payment details
 * @param onPaymentRecorded Callback to be invoked when the payment is recorded
 * @param modifier Modifier for styling the button
 * @param enabled Whether the button is enabled
 * @param buttonText Custom text to display on the button (defaults to "Settle Up")
 * @param sheetTitle Custom title for the settlement sheet (defaults to "Record Payment")
 * @param confirmButtonText Custom text for the confirmation button (defaults to "Record Out-of-App Payment")
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleUpButton(
    instruction: SettlingInstruction,
    onPaymentRecorded: (SettlingInstruction) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buttonText: String = "Settle Up",
    sheetTitle: String = "Record Payment",
    confirmButtonText: String = "Record Out-of-App Payment"
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    Button(
        onClick = { showBottomSheet = true },
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(buttonText)
    }

    if (showBottomSheet) {
        SettlementBottomSheet(
            instruction = instruction,
            sheetTitle = sheetTitle,
            confirmButtonText = confirmButtonText,
            sheetState = sheetState,
            onConfirm = {
                onPaymentRecorded(instruction)
                scope.launch {
                    sheetState.hide()
                    showBottomSheet = false
                }
            },
            onDismiss = {
                showBottomSheet = false
            }
        )
    }
}

/**
 * A simpler version of SettleUpButton that doesn't include the sheet.
 * Useful when you need to customize the dialog handling logic separately.
 */
@Composable
fun SimpleSettleUpButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String = "Settle Up"
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(text)
    }
}

/**
 * A reusable bottom sheet for recording settlement payments.
 *
 * @param instruction The settling instruction containing payment details
 * @param sheetTitle Title of the bottom sheet
 * @param confirmButtonText Text for the confirmation button
 * @param sheetState The bottom sheet state
 * @param onConfirm Callback invoked when the payment is confirmed
 * @param onDismiss Callback invoked when the sheet is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementBottomSheet(
    instruction: SettlingInstruction,
    sheetTitle: String,
    confirmButtonText: String,
    sheetState: SheetState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = sheetTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Payment details
            Text(
                text = "Record payment from ${instruction.fromName} to ${instruction.toName}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = instruction.amount.formatAsCurrency(instruction.currency),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Buttons
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(confirmButtonText)
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}