package com.splitter.splittr.ui.screens

import android.content.Context
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
import com.splitter.splittr.MyApplication
import com.splitter.splittr.data.sync.SyncWorker
import com.splitter.splittr.ui.viewmodels.AuthViewModel
import com.splitter.splittr.utils.AuthUtils
import com.splitter.splittr.utils.TokenManager
import com.splitter.splittr.utils.storeUserIdInPreferences

data class LoginRequest(
    val email: String,
    val password: String
)

@Composable
fun LoginScreen(navController: NavController) {
    val context = LocalContext.current
    val myApplication = context.applicationContext as MyApplication
    val authViewModel: AuthViewModel = viewModel(factory = myApplication.viewModelFactory)

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val authResult by authViewModel.authResult.collectAsStateWithLifecycle()
    val loading by authViewModel.loading.collectAsStateWithLifecycle()

    LaunchedEffect(authResult) {
        authResult?.onSuccess { authResponse ->
            handleLoginSuccess(context, authResponse.userId, authResponse.token ?: "")
            navController.navigate("home") {
                popUpTo("login") { inclusive = true }
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
            Text(error.message ?: "Unknown error occurred", color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = {
                navController.navigate("register")
            }
        ) {
            Text("Don't have an account? Register")
        }
    }
}

private fun handleLoginSuccess(context: Context, userId: Int, token: String) {
    storeUserIdInPreferences(context, userId)
    AuthUtils.storeLoginState(context, token)
    TokenManager.saveAccessToken(context, token)
    Toast.makeText(context, "Login Successful", Toast.LENGTH_SHORT).show()
}