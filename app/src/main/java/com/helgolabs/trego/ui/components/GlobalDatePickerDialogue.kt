package com.helgolabs.trego.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalDatePickerDialog(
    date: String,
    enabled: Boolean,
    onDateChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State for controlling the dialog
    var showDatePicker by remember { mutableStateOf(false) }

    // Parse date from string or use today's date
    val initialDate = try {
        if (date.isNotBlank()) {
            LocalDate.parse(date, DateTimeFormatter.ISO_DATE)
        } else {
            LocalDate.now()
        }
    } catch (e: Exception) {
        LocalDate.now()
    }

    // Format date for display
    val displayText = when {
        initialDate.isEqual(LocalDate.now()) -> "Today"
        initialDate.isEqual(LocalDate.now().minusDays(1)) -> "Yesterday"
        else -> initialDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }

    // Use Box instead of Button when disabled to capture clicks
    if (!enabled) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null // No visual indication
                ) {
                    // Show toast when clicked while disabled
                    coroutineScope.launch {
                        Toast.makeText(
                            context,
                            "Cannot change date for imported transactions",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    shape = ButtonDefaults.shape
                )
                .padding(ButtonDefaults.ContentPadding)
        ) {
            // Copy the button content
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "Date",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Icon(
                    Icons.Default.CalendarToday,
                    contentDescription = "Select date",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    } else {
        // Regular button when enabled
        Button(
            onClick = {
                println("Date button clicked")
                showDatePicker = true
            },
            enabled = true, // Always enabled since we handle the disabled state above
            modifier = modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "Date",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Icon(Icons.Default.CalendarToday, contentDescription = "Select date")
            }
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        val today = LocalDate.now()

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    // Don't allow future dates
                    val date = Instant.ofEpochMilli(utcTimeMillis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                    return !date.isAfter(today)
                }
            }
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val newDate = Instant
                            .ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .format(DateTimeFormatter.ISO_DATE)
                        onDateChange(newDate)
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}