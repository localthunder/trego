package com.helgolabs.trego.ui.components

import androidx.compose.material3.ButtonDefaults

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.helgolabs.trego.data.local.entities.GroupMemberEntity
import com.helgolabs.trego.data.local.entities.UserEntity
import com.helgolabs.trego.ui.viewmodels.PaymentsViewModel
import com.helgolabs.trego.ui.viewmodels.PaymentsViewModel.PaymentAction

/**
 * A reusable dropdown menu button component.
 *
 * @param label The current selected value to display
 * @param expanded Whether the dropdown menu is currently expanded
 * @param onExpandChange Callback to handle expanding/collapsing the dropdown
 * @param content The dropdown menu content
 * @param containerColor Optional override for the button container color
 * @param contentColor Optional override for the button content color
 */
@Composable
fun DropdownMenuButton(
    label: String,
    expanded: Boolean,
    onExpandChange: () -> Unit,
    content: @Composable () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Box {
        TextButton(
            onClick = onExpandChange,
            colors = ButtonDefaults.textButtonColors(
                containerColor = containerColor,
                contentColor = contentColor
            )
        ) {
            Text(
                text = label,
                color = contentColor
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onExpandChange
        ) {
            content()
        }
    }
}

/**
 * Dropdown menu for payment type selection (spent, received, transferred).
 */
@Composable
fun PaymentTypeDropdown(
    viewModel: PaymentsViewModel,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val screenState by viewModel.paymentScreenState.collectAsState()

    DropdownMenuButton(
        label = screenState.editablePayment?.paymentType?.replaceFirstChar { it.uppercase() } ?: "",
        expanded = screenState.expandedPaymentTypeList,
        onExpandChange = {
            viewModel.processAction(PaymentAction.ToggleExpandedPaymentTypeList)
        },
        containerColor = containerColor,
        contentColor = contentColor,
        content =
        {
            listOf("Spent", "Received", "Transferred").forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        when (type) {
                            "Spent" -> {
                                viewModel.processAction(PaymentAction.UpdatePaymentType("spent"))
                                viewModel.processAction(PaymentAction.UpdateAmount(
                                    screenState.payment?.amount?.let { kotlin.math.abs(it) } ?: 0.0
                                ))
                            }
                            "Received" -> {
                                viewModel.processAction(PaymentAction.UpdatePaymentType("received"))
                                viewModel.processAction(PaymentAction.UpdateAmount(
                                    screenState.payment?.amount?.let { kotlin.math.abs(it) * -1 } ?: 0.0
                                ))
                            }
                            "Transferred" -> {
                                // Find first available user who isn't the payer to be the recipient
                                val currentPayer = screenState.editablePayment?.paidByUserId
                                val defaultRecipient = screenState.groupMembers
                                    .firstOrNull { it.userId != currentPayer }
                                    ?.userId

                                // Set the recipient first
                                if (defaultRecipient != null) {
                                    viewModel.processAction(PaymentAction.UpdatePaidToUser(defaultRecipient))
                                }

                                // Then update the payment type
                                viewModel.processAction(PaymentAction.UpdatePaymentType("transferred"))
                            }
                        }
                        viewModel.processAction(PaymentAction.ToggleExpandedPaymentTypeList)
                    }
                )
            }
        }
    )
}

/**
 * Dropdown menu for payer selection.
 */
@Composable
fun PayerDropdown(
    viewModel: PaymentsViewModel,
    currentUserId: Int?,
    users: List<UserEntity>,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val screenState by viewModel.paymentScreenState.collectAsState()
    val editablePayment = screenState.editablePayment

    Box {
        TextButton(
            onClick = { viewModel.processAction(PaymentAction.ToggleExpandedPaidByUserList) },
            colors = ButtonDefaults.textButtonColors(
                containerColor = containerColor,
                contentColor = contentColor
            )
        ) {
            Text(
                text = if (editablePayment?.paidByUserId == currentUserId) "me" else
                    users.find { it.userId == editablePayment?.paidByUserId }?.username ?: ""
            )
        }

        DropdownMenu(
            expanded = screenState.expandedPaidByUserList,
            onDismissRequest = { viewModel.processAction(PaymentAction.ToggleExpandedPaidByUserList) }
        ) {
            screenState.groupMembers.forEach { member ->
                val user = users.find { it.userId == member.userId }
                val username = user?.username ?: member.userId.toString()
                DropdownMenuItem(
                    text = { Text(text = if (member.userId == currentUserId) "me" else username) },
                    onClick = {
                        viewModel.processAction(PaymentAction.UpdatePaidByUser(member.userId))
                        viewModel.processAction(PaymentAction.ToggleExpandedPaidByUserList)
                    }
                )
            }
        }
    }
}

/**
 * Dropdown menu for split mode selection (equally, percentage, unequally).
 */
@Composable
fun SplitModeDropdown(viewModel: PaymentsViewModel) {
    val screenState by viewModel.paymentScreenState.collectAsState()
    val splitMode = screenState.editablePayment?.splitMode ?: "equally"

    DropdownMenuButton(
        label = splitMode,
        expanded = screenState.expandedSplitTypeList,
        onExpandChange = {
            viewModel.processAction(PaymentAction.ToggleExpandedSplitTypeList)
        },
        content = {
            listOf("equally", "percentage", "unequally").forEach { mode ->
                DropdownMenuItem(
                    text = { if(mode == "percentage") { Text("by percentage")} else { Text(mode) } },
                    onClick = {
                        viewModel.processAction(PaymentAction.UpdateSplitMode(mode))
                        viewModel.processAction(PaymentAction.ToggleExpandedSplitTypeList)
                    }
                )
            }
        }
    )
}

/**
 * Dropdown menu for recipient selection (for transferred payment type).
 */
@Composable
fun RecipientDropdown(
    viewModel: PaymentsViewModel,
    currentUserId: Int?,
    users: List<UserEntity>,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val screenState by viewModel.paymentScreenState.collectAsState()

    Box {
        TextButton(
            onClick = { viewModel.processAction(PaymentAction.ToggleExpandedPaidToUserList) },
            colors = ButtonDefaults.textButtonColors(
                containerColor = containerColor,
                contentColor = contentColor
            )
        ) {
            Text(
                text = if (screenState.paidToUser == currentUserId) {
                    "me"
                } else {
                    users.find { it.userId == screenState.paidToUser }?.username
                        ?: users.find { it.userId != currentUserId }?.username ?: ""
                },
                style = MaterialTheme.typography.bodyLarge
            )
        }

        DropdownMenu(
            expanded = screenState.expandedPaidToUserList,
            onDismissRequest = { viewModel.processAction(PaymentAction.ToggleExpandedPaidToUserList) }
        ) {
            screenState.groupMembers.forEach { member ->
                val user = users.find { it.userId == member.userId }
                val username = user?.username ?: member.userId.toString()
                DropdownMenuItem(
                    text = { Text(text = if (member.userId == currentUserId) "me" else username) },
                    onClick = {
                        viewModel.processAction(PaymentAction.UpdatePaidToUser(member.userId))
                        viewModel.processAction(PaymentAction.ToggleExpandedPaidToUserList)
                    }
                )
            }
        }
    }
}