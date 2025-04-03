package com.helgolabs.trego.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Base field component that provides consistent layout and styling
 * for different types of interactive fields.
 */
@Composable
fun BaseField(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable () -> Unit,
    trailingContent: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(modifier = Modifier.padding(top = 8.dp)) {
                content()
            }

            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        trailingContent()
    }
}

/**
 * A field that can be edited inline.
 */
@Composable
fun EditableField(
    label: String,
    value: String,
    isEditing: Boolean,
    editedValue: String,
    onEditStart: () -> Unit,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    actionIconTint: Color = MaterialTheme.colorScheme.primary
) {
    // Effect to request focus when editing starts
    LaunchedEffect(isEditing) {
        if (isEditing) {
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Handle potential focus request exceptions
            }
        }
    }

    val fieldModifier = if (isEditing) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier.clickable(onClick = onEditStart)
    }

    BaseField(
        label = label,
        modifier = modifier,
        description = description,
        content = {
            BasicTextField(
                value = if (isEditing) editedValue else value,
                onValueChange = {
                    if (isEditing) {
                        onValueChange(it)
                    }
                },
                enabled = isEditing,
                readOnly = !isEditing,
                modifier = fieldModifier,
                textStyle = textStyle.copy(color = contentColor),
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = KeyboardActions(onDone = { onSave() }),
                singleLine = true
            )
        },
        trailingContent = {
            if (isEditing) {
                Row {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel"
                        )
                    }
                    IconButton(onClick = onSave) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save"
                        )
                    }
                }
            } else {
                IconButton(onClick = onEditStart) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit $label",
                        tint = actionIconTint
                    )
                }
            }
        }
    )
}

/**
 * A field for password display with special handling.
 */
@Composable
fun PasswordField(
    label: String,
    value: String = "••••••••••",
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionIconTint: Color = MaterialTheme.colorScheme.primary
) {
    BaseField(
        label = label,
        modifier = modifier,
        description = description,
        content = {
            BasicTextField(
                value = value,
                onValueChange = { /* Read-only */ },
                enabled = false,
                readOnly = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                visualTransformation = PasswordVisualTransformation()
            )
        },
        trailingContent = {
            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Change $label",
                    tint = actionIconTint
                )
            }
        }
    )
}

/**
 * A field that opens a selector when clicked rather than directly editing inline.
 */
@Composable
fun SelectableField(
    label: String,
    value: String,
    onSelectClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector = Icons.Default.Edit,
    iconDescription: String = "Change $label",
    actionIconTint: Color = MaterialTheme.colorScheme.primary
) {
    BaseField(
        label = label,
        modifier = modifier,
        description = description,
        content = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        trailingContent = {
            IconButton(onClick = onSelectClick) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconDescription,
                    tint = actionIconTint
                )
            }
        }
    )
}