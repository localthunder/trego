package com.splitter.splittr.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.splitter.splittr.ui.screens.UserBalanceWithCurrency
import kotlin.math.abs

@Composable
fun SettleUpButton(
    groupId: Int,
    onSettleUpClick: () -> Unit,
    balances: List<UserBalanceWithCurrency>,
    modifier: Modifier = Modifier
) {
    val hasNonZeroBalances = balances.any { userBalance ->
        userBalance.balances.any { (_, amount) -> abs(amount) >= 0.01 }
    }

    if (hasNonZeroBalances) {
        Button(
            onClick = onSettleUpClick,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("View Settlement Plan")
        }
    }
}