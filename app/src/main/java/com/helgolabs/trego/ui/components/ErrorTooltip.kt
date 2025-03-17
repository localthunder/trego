package com.helgolabs.trego.ui.components

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ErrorTooltip(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = message,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .size(18.dp)
                .hoverable(
                    interactionSource = remember { MutableInteractionSource() },
                    enabled = true
                )
        )
    }
}