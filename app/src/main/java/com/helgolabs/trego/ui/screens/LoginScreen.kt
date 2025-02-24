package com.helgolabs.trego.ui.screens

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.work.WorkManager
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.dataClasses.LoginRequest
import com.helgolabs.trego.data.local.dataClasses.secure
import com.helgolabs.trego.data.model.User
import com.helgolabs.trego.data.repositories.InstitutionRepository
import com.helgolabs.trego.data.sync.SyncWorker
import com.helgolabs.trego.ui.viewmodels.AuthViewModel
import com.helgolabs.trego.ui.viewmodels.InstitutionViewModel
import com.helgolabs.trego.ui.viewmodels.UserViewModel
import com.helgolabs.trego.utils.AuthUtils
import com.helgolabs.trego.utils.TokenManager
import com.helgolabs.trego.utils.storeUserIdInPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(navController: NavController, inviteCode: String? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val myApplication = context.applicationContext as MyApplication
    val authViewModel: AuthViewModel = viewModel(factory = myApplication.viewModelFactory)
    val institutionViewModel: InstitutionViewModel = viewModel(factory = myApplication.viewModelFactory)
    val userViewModel: UserViewModel = viewModel(factory = myApplication.viewModelFactory)

    val authResult by authViewModel.authResult.collectAsStateWithLifecycle()
    val loading by authViewModel.loading.collectAsStateWithLifecycle()


    // Add security settings through DisposableEffect
    DisposableEffect(Unit) {
        view.apply {
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        onDispose { }
    }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(authResult) {
        authResult?.onSuccess { authResponse ->
            try {
                // 1. First store the tokens and wait for completion
                withContext(Dispatchers.IO) {
                    val token = authResponse.token ?: throw IllegalStateException("No token received")
                    TokenManager.saveAccessToken(context, token)
                    AuthUtils.storeLoginState(context, token)
                    WorkManager.getInstance(context).pruneWork() // Clean up any lingering work
                    // Add a small delay to ensure token propagation
                    delay(500)
                }

                // 2. Get user info and store user ID
                withContext(Dispatchers.IO) {
                    // Retry logic for getting user info
                    var attempts = 0
                    var userResult: Result<User>? = null

                    while (attempts < 3) {
                        userResult = userViewModel.getUserByServerId(authResponse.userId)
                        if (userResult.isSuccess) break

                        Log.d("LoginScreen", "Attempt ${attempts + 1} failed, retrying...")
                        delay(500)
                        attempts++
                    }
                    val user = userResult?.getOrNull()
                        ?: throw IllegalStateException("Failed to get user info")

                    storeUserIdInPreferences(context, user.userId)
                }

                // 3. Show success message on main thread
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Login Successful", Toast.LENGTH_SHORT).show()
                }

                // 4. Start background sync operations
                myApplication.applicationScope.launch(Dispatchers.IO) {
                    try {
                        institutionViewModel.syncInstitutions("GB")
                        SyncWorker.requestSync(context)
                    } catch(e: Exception) {
                        Log.e("LoginScreen", "Error during sync", e)
                    }
                }

                // 5. Navigate to home screen
                withContext(Dispatchers.Main) {
                    if (inviteCode != null) {
                        navController.navigate("invite/$inviteCode") {
                            popUpTo("login") { inclusive = true }
                        }
                    } else {
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("LoginScreen", "Error during login sequence", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Login failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }?.onFailure { error ->
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Login failed: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                autoCorrect = false,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = {
                    focusManager.moveFocus(FocusDirection.Down)
                }
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrect = false,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    val loginRequest = LoginRequest(email = email, password = password.secure())
                    authViewModel.login(context, loginRequest)
                }
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Password Input"
                },
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val loginRequest = LoginRequest(email = email, password = password.secure())
                authViewModel.login(context, loginRequest)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading
        ) {
            if (loading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Login")
            }
        }

        authResult?.onFailure { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                error.message ?: "Unknown error occurred",
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = { navController.navigate("register") }
        ) {
            Text("Don't have an account? Register")
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = { navController.navigate("forgot_password") }
        ) {
            Text("Forgot your password?")
        }
    }
}