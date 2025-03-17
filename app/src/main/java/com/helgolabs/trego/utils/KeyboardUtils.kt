package com.helgolabs.trego.utils

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/**
 * Creates KeyboardOptions that minimize accessory bar issues on Android
 * by using "Done" action for numeric keyboards unless explicitly needed
 */
fun createKeyboardOptions(
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    autoCorrect: Boolean = false
): KeyboardOptions {
    // For numeric keyboards, prefer Done to avoid the oversized accessory bar
    val effectiveImeAction = if (keyboardType == KeyboardType.Number ||
        keyboardType == KeyboardType.Decimal) {
        ImeAction.Done
    } else {
        imeAction
    }

    return KeyboardOptions(
        keyboardType = keyboardType,
        imeAction = effectiveImeAction,
        autoCorrect = autoCorrect
    )
}

/**
 * Simple solution that works by setting the soft input mode
 * This is the most reliable way to control the keyboard behavior
 */
@Composable
fun FixKeyboardMode() {
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(Unit) {
        // Save the current soft input mode
        val originalMode = activity?.window?.attributes?.softInputMode

        // Set the mode to adjust resize without additional UI elements
        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        )

        onDispose {
            // Restore the original mode when component is disposed
            originalMode?.let { mode ->
                activity?.window?.setSoftInputMode(mode)
            }
        }
    }
}

/**
 * Call this function in your PaymentScreen at the top level
 */
@Composable
fun ConfigureForNumericInput() {
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(Unit) {
        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        onDispose { }
    }
}