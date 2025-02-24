package com.helgolabs.trego.ui.screens

import BankAccountViewModel
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.helgolabs.trego.data.model.BankAccount
import com.helgolabs.trego.ui.components.PasswordStrengthIndicator
import com.helgolabs.trego.ui.viewmodels.UserViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    userViewModel: UserViewModel,
    bankAccountViewModel: BankAccountViewModel,
    userId: Int,
    modifier: Modifier = Modifier,
) {
//    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showChangeUsernameDialog by remember { mutableStateOf(false) }
    var showNotificationSettings by remember { mutableStateOf(false) }

    val user by userViewModel.user.collectAsState()
    val bankAccounts by bankAccountViewModel.bankAccounts.collectAsState()
    val loading by bankAccountViewModel.loading.collectAsState()
    val deleteStatus by bankAccountViewModel.deleteStatus.collectAsState()
    val error by bankAccountViewModel.error.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resetToken by userViewModel.resetToken.collectAsState()
    val usernameUpdateResult by userViewModel.usernameUpdateResult.collectAsState()


    LaunchedEffect(deleteStatus) {
        deleteStatus?.onSuccess {
            // Optionally show success message
            bankAccountViewModel.clearDeleteStatus()
        }?.onFailure { exception ->
            // Error is already handled by the ViewModel's error state
            bankAccountViewModel.clearDeleteStatus()
        }
    }

    LaunchedEffect(userId) {
        userViewModel.loadUser(userId)
        bankAccountViewModel.loadBankAccounts(userId)
    }

    LaunchedEffect(resetToken) {
        resetToken?.onSuccess { token ->
            navController.navigate("reset_password/$token")
            userViewModel.clearPasswordChangeResult()
        }?.onFailure { error ->
            Toast.makeText(
                context,
                error.message ?: "Failed to initiate password change",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(usernameUpdateResult) {
        usernameUpdateResult?.onSuccess {
            Toast.makeText(context, "Username updated successfully", Toast.LENGTH_SHORT).show()
            userViewModel.clearUsernameUpdateResult()
        }?.onFailure { error ->
            Toast.makeText(context, error.message ?: "Failed to update username", Toast.LENGTH_LONG).show()
            userViewModel.clearUsernameUpdateResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // User Info Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "Account Information",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Username: ${user?.username ?: ""}")
                        Text("Email: ${user?.email ?: ""}")
                    }
                }
            }

            // Account Actions
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "Account Actions",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { showChangeUsernameDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Change Username")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    userViewModel.initiatePasswordChange()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Change Password")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { showNotificationSettings = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Notification Settings")
                        }
                    }
                }
            }

            // Bank Accounts Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Connected Bank Accounts",
                                style = MaterialTheme.typography.titleLarge
                            )
                            FilledTonalButton(
                                onClick = { navController.navigate("institutions") }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add")
                            }
                        }

                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            items(bankAccounts) { account ->
                BankAccountItem(
                    account = account,
                    onRemoveAccount = { accountId ->
                        bankAccountViewModel.deleteBankAccount(accountId)
                    }
                )
            }
        }

//        // Dialogs
//        if (showChangePasswordDialog) {
//            ChangePasswordDialog(
//                onDismiss = { showChangePasswordDialog = false },
//                onConfirm = { currentPassword, newPassword ->
//                    // Implement password change
//                    showChangePasswordDialog = false
//                }
//            )
//        }

        if (showChangeUsernameDialog) {
            ChangeUsernameDialog(
                currentUsername = user?.username ?: "",
                onDismiss = {
                    showChangeUsernameDialog = false
                    userViewModel.clearUsernameUpdateResult()
                },
                onConfirm = { newUsername ->
                    userViewModel.updateUsername(newUsername)
                    showChangeUsernameDialog = false
                }
            )
        }

        if (showNotificationSettings) {
            NotificationSettingsDialog(
                onDismiss = { showNotificationSettings = false }
            )
        }
    }
}

@Composable
fun BankAccountItem(
    account: BankAccount,
    onRemoveAccount: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = account.institutionId,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = account.iban ?: "",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row {
                    IconButton(onClick = { onRemoveAccount(account.accountId) }) {
                        Icon(Icons.Default.Delete, "Remove account")
                    }
                }
            }
        }
    }
}

@Composable
fun ChangeUsernameDialog(
    currentUsername: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newUsername by remember { mutableStateOf(currentUsername) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Username") },
        text = {
            Column {
                OutlinedTextField(
                    value = newUsername,
                    onValueChange = {
                        newUsername = it
                        error = null
                    },
                    label = { Text("New Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        newUsername.length < 2 -> {
                            error = "Username must be at least 2 characters"
                        }
                        else -> {
                            onConfirm(newUsername)
                        }
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun NotificationSettingsDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notification Settings") },
        text = {
            Column {
                // Placeholder notification settings
                Text("Notification settings will be implemented in a future update.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

//@Composable
//fun ChangePasswordDialog(
//    onDismiss: () -> Unit,
//    onConfirm: (String, String) -> Unit
//) {
//    var currentPassword by remember { mutableStateOf("") }
//    var newPassword by remember { mutableStateOf("") }
//    var confirmPassword by remember { mutableStateOf("") }
//    var error by remember { mutableStateOf<String?>(null) }
//
//    AlertDialog(
//        onDismissRequest = onDismiss,
//        title = { Text("Change Password") },
//        text = {
//            Column(
//                modifier = Modifier.fillMaxWidth(),
//                verticalArrangement = Arrangement.spacedBy(8.dp)
//            ) {
//                OutlinedTextField(
//                    value = currentPassword,
//                    onValueChange = { currentPassword = it },
//                    label = { Text("Current Password") },
//                    visualTransformation = PasswordVisualTransformation(),
//                    modifier = Modifier.fillMaxWidth(),
//                    singleLine = true
//                )
//
//                OutlinedTextField(
//                    value = newPassword,
//                    onValueChange = {
//                        newPassword = it
//                        error = null
//                    },
//                    label = { Text("New Password") },
//                    visualTransformation = PasswordVisualTransformation(),
//                    modifier = Modifier.fillMaxWidth(),
//                    singleLine = true
//                )
//
//                // Password Strength Indicator
//                if (newPassword.isNotEmpty()) {
//                    PasswordStrengthIndicator(newPassword)
//                }
//
//                OutlinedTextField(
//                    value = confirmPassword,
//                    onValueChange = {
//                        confirmPassword = it
//                        error = null
//                    },
//                    label = { Text("Confirm New Password") },
//                    visualTransformation = PasswordVisualTransformation(),
//                    modifier = Modifier.fillMaxWidth(),
//                    singleLine = true
//                )
//
//                if (error != null) {
//                    Text(
//                        text = error!!,
//                        color = MaterialTheme.colorScheme.error,
//                        style = MaterialTheme.typography.bodySmall
//                    )
//                }
//            }
//        },
//        confirmButton = {
//            TextButton(
//                onClick = {
//                    when {
//                        currentPassword.isEmpty() -> {
//                            error = "Please enter your current password"
//                        }
//                        newPassword.isEmpty() -> {
//                            error = "Please enter a new password"
//                        }
//                        newPassword.length < 8 -> {
//                            error = "Password must be at least 8 characters"
//                        }
//                        newPassword != confirmPassword -> {
//                            error = "New passwords do not match"
//                        }
//                        else -> {
//                            onConfirm(currentPassword, newPassword)
//                        }
//                    }
//                }
//            ) {
//                Text("Change Password")
//            }
//        },
//        dismissButton = {
//            TextButton(onClick = onDismiss) {
//                Text("Cancel")
//            }
//        }
//    )
//}