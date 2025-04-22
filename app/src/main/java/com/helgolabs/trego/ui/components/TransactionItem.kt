package com.helgolabs.trego.ui.components

import android.content.Context
import android.icu.text.DecimalFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.dataClasses.UserInvolvement
import com.helgolabs.trego.data.model.Transaction
import com.helgolabs.trego.ui.viewmodels.InstitutionViewModel
import com.helgolabs.trego.utils.CurrencyUtils.currencySymbols
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.InstitutionLogoManager

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionItem(
    transaction: Transaction,
    context: Context,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val logoInfo = rememberLogoInfo(transaction, context)

    // Colors for card border/gradient
    val gradientColors = if (logoInfo?.dominantColors?.size ?: 0 >= 2) {
        logoInfo?.dominantColors ?: listOf(Color.Gray, Color.LightGray)
    } else {
        listOf(Color.Gray, Color.LightGray)
    }

    // Single Card with combined clickable
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp)
            // Apply selection border when selected
            .then(
                if (isSelected)
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                else
                    Modifier
            )
            // Use combinedClickable for both regular click and long press
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        // Content goes directly inside the clickable card
        Box(modifier = Modifier.fillMaxWidth()) {
            // Transaction content directly in the main card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (logoInfo != null) {
                    Image(
                        bitmap = logoInfo.bitmap.asImageBitmap(),
                        contentDescription = "Institution Logo",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = (transaction.creditorName
                            ?: transaction.debtorName
                            ?: transaction.remittanceInformationUnstructured
                            ?: "N/A").split(" ").joinToString(" ") { word ->
                            word.lowercase().replaceFirstChar { it.uppercase() }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    // Display user involvement if needed
                    UserInvolvementDisplay(
                        involvement = null, // We don't have involvement for transactions
                        currency = transaction.getEffectiveCurrency()
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    // Create a decimal format with exactly 2 decimal places
                    val decimalFormat = DecimalFormat("0.00")
                    val currencySymbol = currencySymbols[transaction.getEffectiveCurrency()] ?: "£"

                    // Format the amount with consistent 2 decimal places
                    val amount = transaction.getEffectiveAmount()
                    val formattedAmount = if (amount < 0) {
                        "${currencySymbol}${decimalFormat.format(-amount)}"
                    } else {
                        "+${currencySymbol}${decimalFormat.format(amount)}"
                    }
                    val amountColor = if (amount < 0) Color.Black else Color.Green

                    Text(
                        text = formattedAmount,
                        color = amountColor,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    transaction.bookingDateTime?.let {
                        Text(
                            text = DateUtils.formatForDisplay(it),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }

                    Text(
                        text = transaction.institutionName ?: "N/A",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Selection indicator overlay
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

/**
 * Helper function to remember logo information for consistent rendering
 */
@Composable
private fun rememberLogoInfo(
    transaction: Transaction,
    context: Context
): InstitutionLogoManager.LogoInfo? {
    var logoInfo by remember(transaction.institutionId) {
        mutableStateOf<InstitutionLogoManager.LogoInfo?>(null)
    }

    val institutionViewModel = viewModel<InstitutionViewModel>(
        factory = (context.applicationContext as MyApplication).viewModelFactory
    )

    LaunchedEffect(transaction.institutionId) {
        transaction.institutionId?.let { id ->
            institutionViewModel.loadInstitutionLogo(id)
        }
    }

    LaunchedEffect(transaction.institutionId) {
        institutionViewModel.logoInfo.collect { logoMap: Map<String, InstitutionLogoManager.LogoInfo> ->
            transaction.institutionId?.let { id ->
                logoInfo = logoMap[id]
            }
        }
    }

    return logoInfo
}