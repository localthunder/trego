package com.splitter.splitter.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.splitter.splitter.model.User
import com.splitter.splitter.data.network.ApiService
import com.splitter.splitter.data.network.AuthResponse
import com.splitter.splitter.data.network.RetrofitClient
import com.splitter.splitter.utils.AuthUtils
import com.splitter.splitter.utils.TokenManager
import com.splitter.splitter.utils.storeUserIdInPreferences
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

data class LoginRequest(
    val email: String,
    val password: String
)

fun handleLoginSuccess(context: Context, userId: Int, token: String) {
    storeUserIdInPreferences(context, userId)
    AuthUtils.storeLoginState(context, token)
    Toast.makeText(context, "Login Successful", Toast.LENGTH_SHORT).show()
    // Continue with other login success actions, such as navigating to the home screen
}

@Composable
fun LoginScreen(navController: NavController, context: Context) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loginState by remember { mutableStateOf<String?>(null) }

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
                val apiService = RetrofitClient.getInstance(context).create(ApiService::class.java)
                val loginRequest = LoginRequest(email = email, password = password)
                apiService.loginUser(loginRequest).enqueue(object : Callback<AuthResponse> {
                    override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                        if (response.isSuccessful) {
                            val authResponse = response.body()
                            if (authResponse != null) {
                                val token = authResponse.token
                                val userId = authResponse.userId
                                if (token != null) {
                                    TokenManager.saveAccessToken(context, token)
                                    handleLoginSuccess(context, userId, token)
                                    loginState = "Login successful!"
                                    navController.navigate("home") // Navigate to HomeScreen
                                } else {
                                    loginState = "Login failed: Token is null"
                                }
                            } else {
                                loginState = "Login failed: ${response.body()?.message ?: "Unknown error"}"
                            }
                        } else {
                            loginState = "Login failed: ${response.message()}"
                        }
                    }

                    override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                        loginState = "Login failed: ${t.message}"
                    }
                })
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login")
        }
        loginState?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colors.error)
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
