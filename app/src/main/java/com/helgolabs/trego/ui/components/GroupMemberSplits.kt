package com.helgolabs.trego.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.helgolabs.trego.ui.screens.PaymentAmountField
import com.helgolabs.trego.ui.viewmodels.PaymentsViewModel
import com.helgolabs.trego.ui.viewmodels.PaymentsViewModel.PaymentAction
import com.helgolabs.trego.ui.viewmodels.UserViewModel
import com.helgolabs.trego.utils.FormattingUtils.formatPaymentAmount
import com.helgolabs.trego.utils.createKeyboardOptions
import com.helgolabs.trego.utils.getCurrencySymbol

/**
 * A composable that displays a list of group members with their split amounts.
 * The display changes based on the split mode (equally, unequally, percentage).
 */
@Composable
fun GroupMemberSplits(
    paymentViewModel: PaymentsViewModel,
    userViewModel: UserViewModel,
    currentUserId: Int?,
) {
    val screenState by paymentViewModel.paymentScreenState.collectAsState()
    val editablePayment = screenState.editablePayment
    val editableSplits = screenState.editableSplits
    val groupMembers = screenState.groupMembers
    val selectedMembers = screenState.selectedMembers

    // Extract user IDs from group members and load them
    val userIds = groupMembers.map { it.userId }

    val focusManager = LocalFocusManager.current

    // Load users and collect the current state
    LaunchedEffect(userIds) {
        userViewModel.loadUsers(userIds)
    }
    val users by userViewModel.users.collectAsState(emptyList())

    // Add the auto-calculate remainder component if in percentage or unequal mode
    if ((editablePayment?.splitMode == "percentage" || editablePayment?.splitMode == "unequally") &&
        selectedMembers.size > 1) {
        AutoCalculateRemainder(
            paymentViewModel = paymentViewModel,
            screenState = screenState
        )
    }

    Column(
        modifier = Modifier
            .padding(bottom = 16.dp)
            .background(MaterialTheme.colorScheme.surface)
            .fillMaxWidth()
    ) {
        // Member selection list
        groupMembers.forEach { member ->
            val username = when (member.userId) {
                currentUserId -> "Me"
                else -> users.find { it.userId == member.userId }?.username ?: "Loading..."
            }
            val isSelected = selectedMembers.contains(member)
            val split = editableSplits.find { it.userId == member.userId }

            ListItem(
                headlineContent = { Text(username) },
                leadingContent = {
                    // Member selection checkbox
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { checked ->
                            val newSelection = if (checked) {
                                selectedMembers + member
                            } else {
                                selectedMembers - member
                            }
                            paymentViewModel.processAction(
                                PaymentAction.UpdateSelectedMembers(
                                    newSelection
                                )
                            )
                        }
                    )
                },
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(56.dp)
                    ) {
                        // For percentage mode, show percentage and amount for ALL members (selected or not)
                        if (editablePayment?.splitMode == "percentage" && split != null) {
                            // Display the calculated amount
                            Text(
                                text = formatCurrencyAmount(
                                    split.amount,
                                    editablePayment.currency ?: "GBP"
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Use TextFieldValue for all members (including auto-calculated ones)
                            var textFieldValue by remember {
                                val initialText = if (split.percentage != null) {
                                    if (split.percentage!! % 1 == 0.0) {
                                        split.percentage!!.toInt().toString()
                                    } else {
                                        split.percentage.toString()
                                    }
                                } else {
                                    "0"
                                }

                                mutableStateOf(
                                    TextFieldValue(
                                        text = initialText,
                                        selection = TextRange(initialText.length)
                                    )
                                )
                            }

                            // Track if the text field is focused
                            val interactionSource = remember { MutableInteractionSource() }
                            val isFocused by interactionSource.collectIsFocusedAsState()

                            // Update the text when the percentage changes from elsewhere, but only if not focused
                            LaunchedEffect(split.percentage, isFocused) {
                                if (!isFocused) {
                                    val newText = if (split.percentage != null) {
                                        if (split.percentage!! % 1 == 0.0) {
                                            split.percentage!!.toInt().toString()
                                        } else {
                                            split.percentage.toString()
                                        }
                                    } else {
                                        "0"
                                    }

                                    textFieldValue = TextFieldValue(
                                        text = newText,
                                        selection = TextRange(newText.length) // Position cursor at the end
                                    )
                                }
                            }

                            TextField(
                                value = textFieldValue,
                                onValueChange = { newValue ->
                                    // Only process changes if the member is selected
                                    if (isSelected) {
                                        // Keep track of current text and cursor
                                        val oldText = textFieldValue.text
                                        val newText = newValue.text
                                        val cursorPosition = newValue.selection.start

                                        // Track that this specific split was edited by the user
                                        paymentViewModel.processAction(
                                            PaymentAction.TrackSplitEdit(member.userId)
                                        )

                                        // Handle special cases
                                        when {
                                            // Empty input gets converted to "0" with cursor after it
                                            newText.isEmpty() -> {
                                                textFieldValue = TextFieldValue(
                                                    text = "0",
                                                    selection = TextRange(1) // Position cursor after the "0"
                                                )
                                                paymentViewModel.processAction(
                                                    PaymentAction.UpdateSplitPercentage(
                                                        member.userId,
                                                        0.0
                                                    )
                                                )
                                            }

                                            // If current value is "0" and typing a new digit (not decimal)
                                            oldText == "0" && newText.length > 1 && !newText.startsWith("0.") -> {
                                                // Extract the new character (what user typed)
                                                val newChar = newText.substring(cursorPosition - 1, cursorPosition)

                                                // Create text with just the typed character
                                                textFieldValue = TextFieldValue(
                                                    text = newChar,
                                                    selection = TextRange(1) // Position cursor after the new character
                                                )

                                                // Update model
                                                try {
                                                    val newPercentage = newChar.toDoubleOrNull() ?: 0.0
                                                    paymentViewModel.processAction(
                                                        PaymentAction.UpdateSplitPercentage(
                                                            member.userId,
                                                            newPercentage
                                                        )
                                                    )
                                                } catch (e: Exception) {
                                                    Log.e("GroupMemberSplits", "Error updating percentage", e)
                                                }
                                            }

                                            // Normal input handling
                                            else -> {
                                                // Filter to only allow digits and one decimal point (improved version)
                                                val decimalCount = newText.count { it == '.' }
                                                val filtered = if (decimalCount > 1) {
                                                    // If there are multiple decimal points, keep only the first one
                                                    val parts = newText.split('.', limit = 2)
                                                    parts[0] + if (parts.size > 1) "." + parts[1].filter { it.isDigit() } else ""
                                                } else {
                                                    // Otherwise just filter to digits and the decimal point
                                                    newText.filter { char -> char.isDigit() || char == '.' }
                                                }

                                                // Calculate how many characters were removed by filtering
                                                val filteringDiff = newText.length - filtered.length

                                                // Adjust cursor position if characters were filtered out
                                                val adjustedCursorPosition = maxOf(0, cursorPosition - filteringDiff)

                                                // Handle decimal places (only allow 2)
                                                val processedText = if (filtered.contains('.')) {
                                                    val parts = filtered.split('.')
                                                    if (parts.size > 1 && parts[1].length > 2) {
                                                        parts[0] + "." + parts[1].take(2)
                                                    } else {
                                                        filtered
                                                    }
                                                } else {
                                                    filtered
                                                }

                                                // Further adjust cursor position if decimal places were limited
                                                val finalCursorPosition = minOf(processedText.length, adjustedCursorPosition)

                                                // Parse the percentage for validation
                                                val newPercentage = processedText.toDoubleOrNull() ?: 0.0

                                                // Only update if valid and within allowed range (0-100)
                                                if (processedText.isNotEmpty() && processedText != "." &&
                                                    newPercentage >= 0 && newPercentage <= 100) {
                                                    textFieldValue = TextFieldValue(
                                                        text = processedText,
                                                        selection = TextRange(finalCursorPosition)
                                                    )

                                                    try {
                                                        if (newPercentage.isFinite()) {
                                                            paymentViewModel.processAction(
                                                                PaymentAction.UpdateSplitPercentage(
                                                                    member.userId,
                                                                    newPercentage
                                                                )
                                                            )
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("GroupMemberSplits", "Error updating percentage", e)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    textAlign = TextAlign.End, // Right-align the text
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onBackground
                                    else
                                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    errorContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                    errorIndicatorColor = MaterialTheme.colorScheme.error,
                                ),
                                // Add error styling if percentage is invalid
                                isError = (textFieldValue.text.toDoubleOrNull() ?: 0.0) > 100,
                                enabled = isSelected,
                                visualTransformation = VisualTransformation { text ->
                                    TransformedText(
                                        AnnotatedString(text.text + "%"),
                                        // Custom offset mapping that accounts for the added "%" character
                                        object : OffsetMapping {
                                            override fun originalToTransformed(offset: Int): Int = offset
                                            override fun transformedToOriginal(offset: Int): Int =
                                                minOf(offset, text.text.length)
                                        }
                                    )
                                },
                                modifier = Modifier.width(120.dp),
                                singleLine = true,
                                interactionSource = interactionSource,
                                keyboardOptions = createKeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next,
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = {
                                        focusManager.moveFocus(FocusDirection.Next)
                                    }
                                )
                            )
                        }
                        // For unequal splits, show editable amount field or auto-calculated value for ALL members
                        else if (editablePayment?.splitMode == "unequally" && split != null) {
                            Box(
                                modifier = Modifier.width(180.dp),  // Constrain the width to match your original TextField
                                contentAlignment = Alignment.CenterEnd  // Align content to the right
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End  // Align row content to the right
                                ) {
                                    // Currency symbol
                                    Text(
                                        text = getCurrencySymbol(editablePayment.currency ?: "GBP"),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.onBackground
                                        else
                                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                    )

                                    // Use the PaymentAmountField instead of TextField for selected members
                                    if (isSelected) {
                                        PaymentAmountField(
                                            amount = split.amount,
                                            onAmountChange = { newAmount ->
                                                paymentViewModel.processAction(
                                                    PaymentAction.TrackSplitEdit(member.userId)
                                                )
                                                paymentViewModel.processAction(
                                                    PaymentAction.UpdateSplit(
                                                        member.userId,
                                                        newAmount
                                                    )
                                                )
                                            },
                                            enabled = isSelected,
                                            focusManager = LocalFocusManager.current,
                                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                color = MaterialTheme.colorScheme.onBackground
                                            ),
                                            modifier = Modifier.wrapContentWidth()  // Only take needed width
                                        )
                                    } else {
                                        // For unselected members, just show the formatted amount
                                        Text(
                                            text = formatPaymentAmount(split.amount.toString()),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                            textAlign = TextAlign.End
                                        )
                                    }

                                    // Error indicator if needed
                                    if (isSelected && split.amount > editablePayment.amount) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        ErrorTooltip(message = "Amount exceeds total payment")
                                    }
                                }
                            }
                        }
                        // For equal splits, show the amount as text (same as before)
                        else {
                            val amount = split?.amount ?: 0.0
                            Text(
                                text = formatCurrencyAmount(
                                    amount,
                                    editablePayment?.currency ?: "GBP"
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onBackground
                                else
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Helper function to format currency amounts with currency symbol
 */
@Composable
private fun formatCurrencyAmount(amount: Double, currencyCode: String): String {
    val symbol = getCurrencySymbol(currencyCode)

    // Format amount with proper zero handling
    val formattedAmount = if (amount == 0.0) {
        "0.00"
    } else {
        formatPaymentAmount(amount.toString())
    }

    return "$symbol$formattedAmount"
}

/**
 * Auto-calculates the remaining percentage or amount for the least recently edited member
 */
@Composable
private fun AutoCalculateRemainder(
    paymentViewModel: PaymentsViewModel,
    screenState: PaymentsViewModel.PaymentScreenState
) {
    // Get payment details
    val editablePayment = screenState.editablePayment ?: return
    val editableSplits = screenState.editableSplits
    val selectedMembers = screenState.selectedMembers
    val editOrderMap = screenState.editOrderMap

    // Only proceed if we have multiple selected members
    if (selectedMembers.size <= 1) return

    // Find the member that was least recently edited
    // First, filter to get only selected members
    val selectedMemberIds = selectedMembers.map { it.userId }

    // Determine which member to auto-calculate
    val memberToAutoCalculate = if (editOrderMap.isNotEmpty()) {
        // Get selected members that have edit history
        val membersWithEdits = selectedMemberIds.filter { editOrderMap.containsKey(it) }

        if (membersWithEdits.size < selectedMemberIds.size) {
            // If some members haven't been edited yet, pick one of those
            selectedMemberIds.firstOrNull { !editOrderMap.containsKey(it) }
        } else {
            // All members have been edited, pick the least recently edited
            membersWithEdits.minByOrNull { editOrderMap[it] ?: Long.MAX_VALUE }
        }
    } else {
        // No edit history, pick the last member in the list
        selectedMembers.lastOrNull()?.userId
    } ?: return

    val splitToAutoCalculate = editableSplits.find { it.userId == memberToAutoCalculate } ?: return

    // Auto-calculate based on split mode
    LaunchedEffect(editableSplits, editablePayment.amount, editablePayment.splitMode, editOrderMap) {
        when (editablePayment.splitMode) {
            "percentage" -> {
                // Calculate total percentage (excluding auto-calculated member)
                val otherMembersPercentage = editableSplits
                    .filter { split ->
                        split.userId != memberToAutoCalculate &&
                                selectedMemberIds.contains(split.userId)
                    }
                    .sumOf { it.percentage ?: 0.0 }

                // Calculate what the auto-calculated member's percentage should be
                val autoCalculatedPercentage = 100.0 - otherMembersPercentage

                // Only update if it's a reasonable value
                if (autoCalculatedPercentage >= 0 && autoCalculatedPercentage <= 100) {
                    // Round to 2 decimal places
                    val roundedPercentage = (autoCalculatedPercentage * 100).toInt() / 100.0

                    // Check if we need to update
                    if (roundedPercentage != splitToAutoCalculate.percentage) {
                        // We're using a special flag with isAutoCalculated = true
                        // to avoid recording this update in the edit history
                        paymentViewModel.processAction(
                            PaymentAction.UpdateSplitPercentage(
                                memberToAutoCalculate,
                                roundedPercentage
                            )
                        )
                    }
                }
            }
            "unequally" -> {
                val totalAmount = editablePayment.amount

                // Calculate the sum of all amounts except the auto-calculated member
                val otherMembersAmount = editableSplits
                    .filter { split ->
                        split.userId != memberToAutoCalculate &&
                                selectedMemberIds.contains(split.userId)
                    }
                    .sumOf { it.amount }

                // Calculate what the auto-calculated member's amount should be
                val autoCalculatedAmount = totalAmount - otherMembersAmount

                // Only update if it's a reasonable value
                if (autoCalculatedAmount >= 0 && autoCalculatedAmount <= totalAmount) {
                    // Round to 2 decimal places
                    val roundedAmount = (autoCalculatedAmount * 100).toInt() / 100.0

                    // Check if we need to update
                    if (roundedAmount != splitToAutoCalculate.amount) {
                        // Use the isAutoCalculated flag when updating to avoid recording in edit history
                        paymentViewModel.processAction(
                            PaymentAction.UpdateSplit(
                                memberToAutoCalculate,
                                roundedAmount
                            )
                        )
                    }
                }
            }
        }
    }
}