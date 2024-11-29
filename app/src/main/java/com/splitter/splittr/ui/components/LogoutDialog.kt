package com.splitter.splittr.ui.components

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.splitter.splittr.MyApplication
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.AppDatabase
import com.splitter.splittr.data.local.dataClasses.SyncResult
import com.splitter.splittr.data.model.Payment
import com.splitter.splittr.utils.AuthManager
import com.splitter.splittr.utils.AuthUtils
import com.splitter.splittr.utils.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LogoutDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var hasPendingPayments by remember { mutableStateOf(false) }
    var syncResult by remember { mutableStateOf<SyncResult<Payment>?>(null) }
    var logoutSuccess by remember { mutableStateOf(false) }
    val database = remember { AppDatabase.getDatabase(context) }

    LaunchedEffect(showDialog) {
        if (showDialog) {
            hasPendingPayments = database
                .paymentDao()
                .getUnsyncedPayments()
                .first()
                .isNotEmpty()
            syncResult = null
            logoutSuccess = false
        }
    }

    LaunchedEffect(logoutSuccess) {
        if (logoutSuccess) {
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
            }
            onDismiss()
        }
    }

    fun clearAllPreferences(context: Context) {
        // Clear AuthUtils preferences
        context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE).edit().clear().apply()

        // Clear TokenManager preferences
        context.getSharedPreferences("splitter_prefs", Context.MODE_PRIVATE).edit().clear().apply()

        // Clear user ID preferences
        context.getSharedPreferences("splitter_preferences", Context.MODE_PRIVATE).edit().clear().apply()

        // Clear AuthManager preferences
        context.getSharedPreferences("AuthPrefs", Context.MODE_PRIVATE).edit().clear().apply()
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Logout") },
            text = {
                Column {
                    if (hasPendingPayments && syncResult !is SyncResult.Success) {
                        Text(
                            "Warning: You have unsynchronized payments that will be lost if you logout now.",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Button(
                            onClick = {
                                scope.launch {
                                    isSyncing = true
                                    try {
                                        val application = context.applicationContext as MyApplication
                                        application.syncManagerProvider
                                            .paymentSyncManager
                                            .performSync()

                                        val unsyncedPayments = database
                                            .paymentDao()
                                            .getUnsyncedPayments()
                                            .first()

                                        hasPendingPayments = unsyncedPayments.isNotEmpty()

                                        syncResult = if (!hasPendingPayments) {
                                            SyncResult.Success(
                                                updatedItems = emptyList(),
                                                timestamp = System.currentTimeMillis()
                                            )
                                        } else {
                                            SyncResult.Error(
                                                error = Exception("Some payments failed to sync"),
                                                failedItems = unsyncedPayments.map { it.toModel() }
                                            )
                                        }
                                    } catch (e: Exception) {
                                        Log.e("LogoutDialog", "Sync failed", e)
                                        syncResult = SyncResult.Error(
                                            error = e,
                                            failedItems = database
                                                .paymentDao()
                                                .getUnsyncedPayments()
                                                .first()
                                                .map { it.toModel() }
                                        )
                                    } finally {
                                        isSyncing = false
                                    }
                                }
                            },
                            enabled = !isLoading && !isSyncing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Text(if (syncResult is SyncResult.Error) "Retry Sync" else "Attempt Sync")
                            }
                        }
                    }

                    syncResult?.let { result ->
                        when(result) {
                            is SyncResult.Success -> {
                                Text(
                                    "Successfully synced all payments!",
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            is SyncResult.Error -> {
                                Column {
                                    Text(
                                        "Sync failed: ${result.error.message}",
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                    result.failedItems?.let { failed ->
                                        Text(
                                            "${failed.size} payments failed to sync",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                            is SyncResult.Skipped -> {
                                Text(
                                    "Sync skipped: ${result.reason}",
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Text("Are you sure you want to logout?")
                    if (isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            isLoading = true
                            try {
                                database.clearAllTables()
                                withContext(Dispatchers.Main) {
                                    clearAllPreferences(context)
                                    AuthUtils.clearLoginState(context)
                                    TokenManager.clearTokens(context)
                                    AuthManager.setAuthenticated(context, false)
                                    logoutSuccess = true
                                }
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    enabled = !isLoading && !isSyncing,
                    colors = if (hasPendingPayments && syncResult !is SyncResult.Success) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isLoading && !isSyncing
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}