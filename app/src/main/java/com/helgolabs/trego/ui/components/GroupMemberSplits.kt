package com.helgolabs.trego.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.helgolabs.trego.ui.viewmodels.PaymentsViewModel
import com.helgolabs.trego.ui.viewmodels.PaymentsViewModel.PaymentAction
import com.helgolabs.trego.ui.viewmodels.UserViewModel
import com.helgolabs.trego.utils.FormattingUtils.formatPaymentAmount
import com.helgolabs.trego.utils.getCurrencySymbol

/**
 * A composable that displays a list of group members with their split amounts.
 * The display changes based on the split mode (equally, unequally, percentage).
 */
@Composable
fun GroupMemberSplits(
    paymentViewModel: PaymentsViewModel,
    userViewModel: UserViewModel,
    currentUserId: Int?
) {
    val screenState by paymentViewModel.paymentScreenState.collectAsState()
    val editablePayment = screenState.editablePayment
    val editableSplits = screenState.editableSplits
    val groupMembers = screenState.groupMembers
    val selectedMembers = screenState.selectedMembers

    // Extract user IDs from group members and load them
    val userIds = groupMembers.map { it.userId }

    // Load users and collect the current state
    LaunchedEffect(userIds) {
        userViewModel.loadUsers(userIds)
    }
    val users by userViewModel.users.collectAsState(emptyList())

    Column(
        modifier = Modifier.padding(bottom = 16.dp)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // For percentage mode, show percentage and amount
                        if (editablePayment?.splitMode == "percentage" && isSelected && split != null) {

                            // Display the calculated amount
                            Text(
                                text = formatCurrencyAmount(
                                    split.amount,
                                    editablePayment.currency ?: "GBP"
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onBackground
                                else
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            TextField(
                                value = (split.percentage?.let {
                                    // Format as whole number if no decimal part
                                    if (it % 1 == 0.0) it.toInt().toString() else it.toString()
                                } ?: "0"),
                                onValueChange = { input ->
                                    val filtered = input.filter { char -> char.isDigit() || char == '.' }

                                    // Allow only up to 2 decimal places
                                    val newInput = if (filtered.contains('.')) {
                                        val parts = filtered.split('.')
                                        if (parts.size > 1 && parts[1].length > 2) {
                                            parts[0] + "." + parts[1].take(2)
                                        } else {
                                            filtered
                                        }
                                    } else {
                                        filtered
                                    }

                                    val newPercentage = newInput.toDoubleOrNull() ?: 0.0
                                    paymentViewModel.processAction(
                                        PaymentAction.UpdateSplitPercentage(
                                            member.userId,
                                            newPercentage
                                        )
                                    )
                                },
                                trailingIcon = { Text("%") },
                                modifier = Modifier.width(80.dp),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default.copy(
                                    keyboardType = KeyboardType.Number
                                )
                            )
                        }
                        // For unequal splits, show editable amount field
                        else if (editablePayment?.splitMode == "unequally" && isSelected && split != null) {
                            TextField(
                                value = formatPaymentAmount(split.amount.toString()),
                                onValueChange = { input ->
                                    val newAmount = input.filter { char ->
                                        char.isDigit() || char == '.'
                                    }.toDoubleOrNull() ?: 0.0
                                    paymentViewModel.processAction(
                                        PaymentAction.UpdateSplit(
                                            member.userId,
                                            newAmount
                                        )
                                    )
                                },
                                modifier = Modifier.width(100.dp),
                                singleLine = true,
                                leadingIcon = {
                                    Text(
                                        getCurrencySymbol(
                                            editablePayment.currency ?: "GBP"
                                        )
                                    )
                                },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default.copy(
                                    keyboardType = KeyboardType.Number
                                )
                            )
                        }
                        // For equal splits or unselected members, show the amount as text
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

            Divider()
        }
    }
}

/**
 * Helper function to format currency amounts with currency symbol
 */
@Composable
private fun formatCurrencyAmount(amount: Double, currencyCode: String): String {
    val symbol = getCurrencySymbol(currencyCode)
    return "$symbol${formatPaymentAmount(amount.toString())}"
}