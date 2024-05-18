package com.splitter.splitter.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.splitter.splitter.network.ApiService
import com.splitter.splitter.network.AuthResponse
import com.splitter.splitter.network.RetrofitClient
import com.splitter.splitter.network.User
import com.splitter.splitter.utils.TokenManager
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@Composable
fun LoginScreen(navController: NavController, context: Context) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loginState by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
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
                val user = User(email = email, password = password)
                apiService.loginUser(user).enqueue(object : Callback<AuthResponse> {
                    override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                        if (response.isSuccessful && response.body()?.token != null) {
                            val token = response.body()?.token
                            TokenManager.saveAccessToken(context, token!!)
                            loginState = "Login successful!"
                            navController.navigate("home") // Navigate to HomeScreen
                        } else {
                            loginState = "Login failed: ${response.body()?.message ?: "Unknown error"}"
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
