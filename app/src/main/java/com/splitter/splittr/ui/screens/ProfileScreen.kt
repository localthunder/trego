package com.splitter.splittr.ui.screens

import BankAccountViewModel
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.splitter.splittr.data.model.BankAccount
import com.splitter.splittr.ui.viewmodels.UserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    userViewModel: UserViewModel,
    bankAccountViewModel: BankAccountViewModel,
    userId: Int,
    modifier: Modifier = Modifier
) {
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showChangeUsernameDialog by remember { mutableStateOf(false) }
    var showNotificationSettings by remember { mutableStateOf(false) }

    val user by userViewModel.user.collectAsState()
    val bankAccounts by bankAccountViewModel.bankAccounts.collectAsState()
    val loading by bankAccountViewModel.loading.collectAsState()
    val deleteStatus by bankAccountViewModel.deleteStatus.collectAsState()
    val error by bankAccountViewModel.error.collectAsState()

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
                            onClick = { showChangePasswordDialog = true },
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

        // Dialogs
        if (showChangePasswordDialog) {
            ChangePasswordDialog(
                onDismiss = { showChangePasswordDialog = false },
                onConfirm = { currentPassword, newPassword ->
                    // Implement password change
                    showChangePasswordDialog = false
                }
            )
        }

        if (showChangeUsernameDialog) {
            ChangeUsernameDialog(
                currentUsername = user?.username ?: "",
                onDismiss = { showChangeUsernameDialog = false },
                onConfirm = { newUsername ->
                    // Implement username change
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Password") },
        text = {
            Column {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("Current Password") },
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password") },
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm New Password") },
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newPassword == confirmPassword) {
                        onConfirm(currentPassword, newPassword)
                    }
                }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeUsernameDialog(
    currentUsername: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newUsername by remember { mutableStateOf(currentUsername) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Username") },
        text = {
            OutlinedTextField(
                value = newUsername,
                onValueChange = { newUsername = it },
                label = { Text("New Username") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(newUsername) }
            ) {
                Text("Confirm")
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