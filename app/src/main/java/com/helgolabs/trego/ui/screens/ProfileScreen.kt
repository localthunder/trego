package com.helgolabs.trego.ui.screens

import BankAccountViewModel
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.helgolabs.trego.data.local.dataClasses.PreferenceKeys
import com.helgolabs.trego.data.model.BankAccount
import com.helgolabs.trego.ui.components.DropdownMenuButton
import com.helgolabs.trego.ui.components.EditableField
import com.helgolabs.trego.ui.components.PasswordField
import com.helgolabs.trego.ui.components.SectionHeader
import com.helgolabs.trego.ui.viewmodels.UserPreferencesViewModel
import com.helgolabs.trego.ui.viewmodels.UserViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    userViewModel: UserViewModel,
    bankAccountViewModel: BankAccountViewModel,
    userPreferencesViewModel: UserPreferencesViewModel,
    userId: Int,
    modifier: Modifier = Modifier,
) {
//    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showChangeUsernameDialog by remember { mutableStateOf(false) }
    var showNotificationSettings by remember { mutableStateOf(false) }

    val user by userViewModel.user.collectAsState()
    val bankAccounts by bankAccountViewModel.bankAccounts.collectAsState()
    val deleteStatus by bankAccountViewModel.deleteStatus.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resetToken by userViewModel.resetToken.collectAsState()
    val usernameUpdateResult by userViewModel.usernameUpdateResult.collectAsState()
    var isEditingUsername by remember { mutableStateOf(false) }
    var editedUsername by remember { mutableStateOf("") }
    var showChangeEmailDialog by remember { mutableStateOf(false) }

    val notificationsEnabled by userPreferencesViewModel.notificationsEnabled.collectAsState()
    val themeMode by userPreferencesViewModel.themeMode.collectAsState()
    val preferencesLoading by userPreferencesViewModel.loading.collectAsState()
    val preferencesError by userPreferencesViewModel.error.collectAsState()

    val focusRequester = remember { FocusRequester() }

    // Map theme mode to UI selection
    val selectedTheme = when(themeMode) {
        PreferenceKeys.ThemeMode.LIGHT -> "Light"
        PreferenceKeys.ThemeMode.DARK -> "Dark"
        else -> "System settings (recommended)"
    }

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
        userPreferencesViewModel.loadPreferences()
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

    LaunchedEffect(preferencesError) {
        preferencesError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            userPreferencesViewModel.clearError()
        }
    }

    LaunchedEffect(isEditingUsername) {
        if (isEditingUsername) {
            focusRequester.requestFocus()
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
                },
                actions = {
                    if (isEditingUsername) {
                        IconButton(
                            onClick = {
                                userViewModel.updateUsername(editedUsername)
                                isEditingUsername = false
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Save changes"
                            )
                        }
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
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth()
                ) {
                    SectionHeader("Account Information")

                    Spacer(modifier = Modifier.height(16.dp))

                    // Username field
//                    BasicTextField(
//                        value = if (isEditingUsername) editedUsername else user?.username ?: "",
//                        onValueChange = {
//                            if (isEditingUsername) {
//                                editedUsername = it
//                            }
//                        },
//                        enabled = isEditingUsername,
//                        readOnly = !isEditingUsername,
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .clickable(enabled = !isEditingUsername) {
//                                editedUsername = user?.username ?: ""
//                                isEditingUsername = true
//                            }
//                            .then(if (isEditingUsername) Modifier.focusRequester(focusRequester) else Modifier),
//                        textStyle = MaterialTheme.typography.bodyLarge.copy(
//                            color = if (isEditingUsername)
//                                MaterialTheme.colorScheme.onSurface
//                            else
//                                MaterialTheme.colorScheme.onSurface
//                        ),
//                        decorationBox = { innerTextField ->
//                            Row(
//                                modifier = Modifier.fillMaxWidth(),
//                                verticalAlignment = Alignment.CenterVertically
//                            ) {
//                                Column(modifier = Modifier.weight(1f)) {
//                                    Text(
//                                        text = "Username",
//                                        style = MaterialTheme.typography.bodySmall,
//                                        color = MaterialTheme.colorScheme.onSurfaceVariant
//                                    )
//
//                                    Box(modifier = Modifier.padding(top = 8.dp)) {
//                                        innerTextField()
//                                    }
//                                }
//
//                                // Trailing icon
//                                if (isEditingUsername) {
//                                    IconButton(
//                                        onClick = {
//                                            userViewModel.updateUsername(editedUsername)
//                                            isEditingUsername = false
//                                        }
//                                    ) {
//                                        Icon(
//                                            imageVector = Icons.Default.Check,
//                                            contentDescription = "Save username"
//                                        )
//                                    }
//                                } else {
//                                    IconButton(
//                                        onClick = {
//                                            editedUsername = user?.username ?: ""
//                                            isEditingUsername = true
//                                        }
//                                    ) {
//                                        Icon(
//                                            imageVector = Icons.Default.Edit,
//                                            contentDescription = "Edit username"
//                                        )
//                                    }
//                                }
//                            }
//                        },
//                        keyboardOptions = KeyboardOptions.Default.copy(
//                            imeAction = ImeAction.Done
//                        ),
//                        keyboardActions = KeyboardActions(
//                            onDone = {
//                                userViewModel.updateUsername(editedUsername)
//                                isEditingUsername = false
//                            }
//                        ),
//                    )

                    EditableField(
                        label = "Username",
                        value = user?.username ?: "",
                        isEditing = isEditingUsername,
                        editedValue = editedUsername,
                        onEditStart = {
                            editedUsername = user?.username ?: ""
                            isEditingUsername = true
                        },
                        onValueChange = { editedUsername = it },
                        onSave = {
                            userViewModel.updateUsername(editedUsername)
                            isEditingUsername = false
                        },
                        onCancel = { isEditingUsername = false }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Password field (masked)
//                    BasicTextField(
//                        value = "••••••••••", // Placeholder for password
//                        onValueChange = { /* Read-only */ },
//                        enabled = false,
//                        readOnly = true,
//                        modifier = Modifier.fillMaxWidth(),
//                        textStyle = MaterialTheme.typography.bodyLarge.copy(
//                            color = MaterialTheme.colorScheme.onSurface
//                        ),
//                        visualTransformation = PasswordVisualTransformation(),
//                        decorationBox = { innerTextField ->
//                            Row(
//                                modifier = Modifier.fillMaxWidth(),
//                                verticalAlignment = Alignment.CenterVertically
//                            ) {
//                                Column(modifier = Modifier.weight(1f)) {
//                                    Text(
//                                        text = "Password",
//                                        style = MaterialTheme.typography.bodySmall,
//                                        color = MaterialTheme.colorScheme.onSurfaceVariant
//                                    )
//
//                                    Box(modifier = Modifier.padding(top = 8.dp)) {
//                                        innerTextField()
//                                    }
//                                }
//
//                                // Trailing icon
//                                IconButton(
//                                    onClick = {
//                                        // Launch password change flow
//                                        scope.launch {
//                                            userViewModel.initiatePasswordChange()
//                                        }
//                                    }
//                                ) {
//                                    Icon(
//                                        imageVector = Icons.Default.Edit,
//                                        contentDescription = "Change Password"
//                                    )
//                                }
//                            }
//                        }
//                    )

                    PasswordField(
                        label = "Password",
                        onEditClick = {
                            scope.launch {
                                userViewModel.initiatePasswordChange()
                            }
                        }
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    SectionHeader("Notifications")

                    NotificationSettingsItem(
                        enabled = notificationsEnabled,
                        onEnabledChange = { userPreferencesViewModel.setNotificationsEnabled(it) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SectionHeader("Appearance")

                    AppearanceSettingsItem(
                        selectedTheme = selectedTheme,
                        onThemeSelected = { themeName ->
                            val newMode = when(themeName) {
                                "Light" -> PreferenceKeys.ThemeMode.LIGHT
                                "Dark" -> PreferenceKeys.ThemeMode.DARK
                                else -> PreferenceKeys.ThemeMode.SYSTEM
                            }
                            userPreferencesViewModel.setThemeMode(newMode)
                        }
                    )
                }
            }

            // Bank Accounts Section
            item {
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth()
                ) {
                    SectionHeader("Connected accounts")

                    Spacer(modifier = Modifier.height(8.dp))

                    ActionButton(
                        icon = Icons.Default.AccountBalance,
                        text = "Connect another account",
                        onClick = {
                            val currentRoute = "profile/$userId" //Check this?!?!?
                            navController.navigate("institutions?returnRoute=${Uri.encode(currentRoute)}")
                        }
                    )

                    if (preferencesLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
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
fun SettingsListItem(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left side with title and description
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Right side with the content
        Box(
            modifier = Modifier,
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
fun NotificationSettingsItem(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsListItem(
        title = "Push notifications",
        description = "Receive push notifications for new expenses, transfer recorded etc",
        modifier = modifier
    ) {
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange
        )
    }
}

@Composable
fun AppearanceSettingsItem(
    selectedTheme: String,
    onThemeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val themes = listOf("Light", "Dark", "System")
    var expanded by remember { mutableStateOf(false) }

    SettingsListItem(
        title = "Light or Dark Mode",
        description = "Choose light mode, dark mode or follow your system settings.",
        modifier = modifier
    ) {
        DropdownMenuButton(
            label = selectedTheme,
            expanded = expanded,
            onExpandChange = { expanded = !expanded },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            content = {
                themes.forEach { theme ->
                    DropdownMenuItem(
                        text = { Text(theme) },
                        onClick = {
                            onThemeSelected(theme)
                            expanded = false
                        }
                    )
                }
            }
        )
    }
}

@Composable
fun BankAccountItem(
    account: BankAccount,
    onRemoveAccount: (String) -> Unit
) {
    val TAG = "BankAccountItem"
    val context = LocalContext.current

    // State for logo and colors
    var logoFile by remember { mutableStateOf<File?>(null) }
    var dominantColors by remember { mutableStateOf(listOf<Color>()) }
    var logoExists by remember { mutableStateOf(false) }

    // First check local storage for institution logo
    LaunchedEffect(account.institutionId) {
        if (account.institutionId != null) {
            val logoFilename = "${account.institutionId}.png"

            // Check if logo exists locally
            val file = File(context.filesDir, logoFilename)
            logoExists = file.exists() && file.length() > 0
            Log.d(TAG, "Local logo check: exists=${file.exists()}, size=${file.length()} bytes for ${account.institutionId}")

            if (logoExists) {
                // Logo exists locally, load it
                logoFile = file
                Log.d(TAG, "Using local logo from: ${file.absolutePath}")

                try {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        Log.d(TAG, "Loaded local bitmap: ${bitmap.width}x${bitmap.height}")

                        // Extract colors
                        try {
                            val colors = com.helgolabs.trego.utils.GradientBorderUtils.getDominantColors(bitmap).map { Color(it) }
                            dominantColors = if (colors.size < 2) {
                                val averageColor = Color(com.helgolabs.trego.utils.GradientBorderUtils.getAverageColor(bitmap))
                                listOf(averageColor, averageColor.copy(alpha = 0.7f))
                            } else {
                                colors
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error extracting colors", e)
                            dominantColors = listOf(Color.Gray, Color.LightGray)
                        }
                    } else {
                        Log.e(TAG, "Failed to decode local bitmap, file might be corrupted")
                        dominantColors = listOf(Color.Gray, Color.LightGray)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading local logo", e)
                    dominantColors = listOf(Color.Gray, Color.LightGray)
                }
            } else {
                // No logo available
                dominantColors = listOf(Color.Gray, Color.LightGray)
            }
        }
    }

    // Create image bitmap from file
    val logoImage = remember(logoFile, logoExists) {
        if (logoExists && logoFile != null) {
            try {
                val bitmap = BitmapFactory.decodeFile(logoFile?.absolutePath)
                bitmap?.asImageBitmap()
            } catch (e: Exception) {
                Log.e(TAG, "Error creating bitmap for display", e)
                null
            }
        } else {
            null
        }
    }

    // Get a display name for the account (e.g., last 4 digits of account number)
    val displayName = remember(account) {
        when {
            !account.iban.isNullOrBlank() -> {
                val lastFour = account.iban.takeLast(4)
                "•••• $lastFour"
            }
            !account.name.isNullOrBlank() -> account.name
            else -> account.accountId.takeLast(4)
        }
    }

    // Create card with logo and gradient border
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        border = BorderStroke(2.dp, Brush.linearGradient(dominantColors.ifEmpty {
            listOf(Color.Gray, Color.LightGray)
        }))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logo or placeholder
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (logoImage != null) {
                        Image(
                            bitmap = logoImage,
                            contentDescription = "Bank Logo",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        // Placeholder with institution initial
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.LightGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = account.institutionId.firstOrNull()?.toString() ?: "?",
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Account details
                Column {
                    Text(
                        text = account.institutionId,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Delete button
            IconButton(onClick = { onRemoveAccount(account.accountId) }) {
                Icon(Icons.Default.Delete, "Remove account")
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