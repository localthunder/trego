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
fun RegisterScreen(navController: NavController, context: Context) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var registerState by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
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
                val user = User(username = username, email = email, password = password)
                apiService.registerUser(user).enqueue(object : Callback<AuthResponse> {
                    override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                        if (response.isSuccessful) {
                            val authResponse = response.body()
                            if (authResponse?.success == true && authResponse.token != null) {
                                TokenManager.saveAccessToken(context, authResponse.token)
                                registerState = "Registration successful!"
                                navController.navigate("home") // Navigate to HomeScreen
                            } else {
                                registerState = "Registration failed: ${authResponse?.message ?: "Unknown error"}"
                            }
                        } else {
                            registerState = "Registration failed: ${response.message()}"
                        }
                    }

                    override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                        registerState = "Registration failed: ${t.message}"
                    }
                })
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Register")
        }
        registerState?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colors.error)
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = {
                navController.navigate("login")
            }
        ) {
            Text("Already have an account? Login")
        }
    }
}