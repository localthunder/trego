package com.splitter.splittr.ui.screens

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.work.WorkManager
import com.splitter.splittr.MyApplication
import com.splitter.splittr.data.local.dataClasses.LoginRequest
import com.splitter.splittr.data.repositories.InstitutionRepository
import com.splitter.splittr.data.sync.SyncWorker
import com.splitter.splittr.ui.viewmodels.AuthViewModel
import com.splitter.splittr.ui.viewmodels.InstitutionViewModel
import com.splitter.splittr.ui.viewmodels.UserViewModel
import com.splitter.splittr.utils.AuthUtils
import com.splitter.splittr.utils.TokenManager
import com.splitter.splittr.utils.storeUserIdInPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(navController: NavController) {
    val context = LocalContext.current
    val myApplication = context.applicationContext as MyApplication
    val authViewModel: AuthViewModel = viewModel(factory = myApplication.viewModelFactory)
    val institutionViewModel: InstitutionViewModel = viewModel(factory = myApplication.viewModelFactory)
    val userViewModel: UserViewModel = viewModel(factory = myApplication.viewModelFactory)

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val authResult by authViewModel.authResult.collectAsStateWithLifecycle()
    val loading by authViewModel.loading.collectAsStateWithLifecycle()

    LaunchedEffect(authResult) {
        authResult?.onSuccess { authResponse ->
            try {
                // 1. First store the tokens and wait for completion
                withContext(Dispatchers.IO) {
                    val token = authResponse.token ?: throw IllegalStateException("No token received")
                    TokenManager.saveAccessToken(context, token)
                    AuthUtils.storeLoginState(context, token)
                    WorkManager.getInstance(context).pruneWork() // Clean up any lingering work
                }

                // 2. Get user info and store user ID
                withContext(Dispatchers.IO) {
                    val userResult = userViewModel.getUserByServerId(authResponse.userId)
                    val user = userResult.getOrNull()
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
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
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
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val loginRequest = LoginRequest(email = email, password = password)
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
    }
}