package com.helgolabs.trego.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.helgolabs.trego.utils.PasswordStrength

@Composable
fun PasswordStrengthIndicator(password: String) {
    val strength = PasswordStrength.calculate(password)
    val color = when (strength) {
        PasswordStrength.WEAK -> MaterialTheme.colorScheme.error
        PasswordStrength.MEDIUM -> MaterialTheme.colorScheme.inversePrimary
        PasswordStrength.STRONG -> MaterialTheme.colorScheme.primary
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        LinearProgressIndicator(
            progress = when (strength) {
                PasswordStrength.WEAK -> 0.33f
                PasswordStrength.MEDIUM -> 0.66f
                PasswordStrength.STRONG -> 1f
            },
            color = color,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = when (strength) {
                PasswordStrength.WEAK -> "Weak password"
                PasswordStrength.MEDIUM -> "Medium strength password"
                PasswordStrength.STRONG -> "Strong password"
            },
            color = color,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}