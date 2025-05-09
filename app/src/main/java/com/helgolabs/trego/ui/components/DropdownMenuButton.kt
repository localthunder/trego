package com.helgolabs.trego.ui.components

import android.widget.Toast
import androidx.compose.material3.ButtonDefaults

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.helgolabs.trego.data.local.entities.GroupMemberEntity
import com.helgolabs.trego.data.local.entities.UserEntity
import com.helgolabs.trego.ui.viewmodels.PaymentsViewModel
import com.helgolabs.trego.ui.viewmodels.PaymentsViewModel.PaymentAction
import kotlinx.coroutines.launch

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
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer,
    enabled: Boolean = true
) {
    Box {
        TextButton(
            onClick = onExpandChange,
            colors = ButtonDefaults.textButtonColors(
                containerColor = if (enabled)
                    containerColor
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                contentColor = if (enabled)
                    contentColor
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            ),
            enabled = enabled
        ) {
            Text(
                text = label,
                color = if (enabled)
                    contentColor
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        DropdownMenu(
            expanded = expanded && enabled,
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Get if it's a transaction
    val isTransaction = screenState.isTransaction
    val isEnabled = !isTransaction

    val paymentTypeLabel = screenState.editablePayment?.paymentType?.replaceFirstChar { it.uppercase() } ?: ""

    if (!isEnabled) {
        // Custom disabled version with toast
        Box {
            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        coroutineScope.launch {
                            Toast.makeText(
                                context,
                                "Cannot change payment type for imported transactions",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
            ) {
                TextButton(
                    onClick = { /* disabled */ },
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    ),
                    enabled = false
                ) {
                    Text(
                        text = paymentTypeLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    } else {
        // Regular dropdown when enabled
        DropdownMenuButton(
            label = paymentTypeLabel,
            expanded = screenState.expandedPaymentTypeList,
            onExpandChange = {
                viewModel.processAction(PaymentAction.ToggleExpandedPaymentTypeList)
            },
            containerColor = containerColor,
            contentColor = contentColor,
            enabled = true,
            content = {
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
}

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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Get if it's a transaction
    val isTransaction = screenState.isTransaction
    val isEnabled = !isTransaction

    val username = if (editablePayment?.paidByUserId == currentUserId) "me" else
        users.find { it.userId == editablePayment?.paidByUserId }?.username ?: ""

    if (!isEnabled) {
        // Custom disabled version with toast
        Box(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    coroutineScope.launch {
                        Toast.makeText(
                            context,
                            "Cannot change payer for imported transactions",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        ) {
            TextButton(
                onClick = { /* disabled */ },
                colors = ButtonDefaults.textButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                ),
                enabled = false
            ) {
                Text(
                    text = username,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    } else {
        // Regular behavior when enabled
        Box {
            TextButton(
                onClick = { viewModel.processAction(PaymentAction.ToggleExpandedPaidByUserList) },
                colors = ButtonDefaults.textButtonColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                )
            ) {
                Text(text = username)
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
}

@Composable
fun SplitModeDropdown(
    viewModel: PaymentsViewModel,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val screenState by viewModel.paymentScreenState.collectAsState()
    val splitMode = screenState.editablePayment?.splitMode ?: "equally"

    DropdownMenuButton(
        label = splitMode,
        expanded = screenState.expandedSplitTypeList,
        onExpandChange = {
            viewModel.processAction(PaymentAction.ToggleExpandedSplitTypeList)
        },
        containerColor = containerColor,
        contentColor = contentColor,
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

@Composable
fun RecipientDropdown(
    viewModel: PaymentsViewModel,
    currentUserId: Int?,
    users: List<UserEntity>,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val screenState by viewModel.paymentScreenState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Get if it's a transaction
    val isTransaction = screenState.isTransaction
    val isEnabled = !isTransaction

    Box {
        TextButton(
            onClick = {
                if (isEnabled) {
                    viewModel.processAction(PaymentAction.ToggleExpandedPaidToUserList)
                } else {
                    // Show toast message when disabled
                    coroutineScope.launch {
                        Toast.makeText(
                            context,
                            "Cannot change recipient for imported transactions",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            colors = ButtonDefaults.textButtonColors(
                containerColor = if (isEnabled)
                    containerColor
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                contentColor = if (isEnabled)
                    contentColor
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            ),
            enabled = isEnabled
        ) {
            Text(
                text = if (screenState.paidToUser == currentUserId) {
                    "me"
                } else {
                    users.find { it.userId == screenState.paidToUser }?.username
                        ?: users.find { it.userId != currentUserId }?.username ?: ""
                },
                color = if (isEnabled)
                    contentColor
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        DropdownMenu(
            expanded = screenState.expandedPaidToUserList && isEnabled,
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