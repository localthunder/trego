package com.splitter.splitter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.splitter.splitter.data.TransactionRepository
import com.splitter.splitter.network.ApiService
import com.splitter.splitter.network.RetrofitClient
import com.splitter.splitter.screens.NavGraph
import com.splitter.splitter.ui.theme.GlobalTheme
import com.splitter.splitter.model.Requisition
import com.splitter.splitter.utils.AuthManager
import com.splitter.splitter.utils.AuthUtils
import com.splitter.splitter.utils.getUserIdFromPreferences
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class MainActivity : FragmentActivity() {

    private var referenceState: MutableState<String?> = mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate called, intent: ${intent?.data}")
        handleIntent(intent)
        setContent {
            GlobalTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val context = LocalContext.current
                    val userId = getUserIdFromPreferences(context)
                    val apiService = remember { RetrofitClient.getInstance(context).create(ApiService::class.java) }
                    val repository = TransactionRepository(apiService)

                    Log.d("MainActivity", "User ID: $userId")

                    // Pass the required variables to composables
                        NavigationSetup(
                            navController = navController,
                            context = context,
                            userId = userId ?: -1,
                            apiService = apiService,
                            repository = repository
                        )
                    HandleDeepLink(
                        navController = navController,
                        referenceState = referenceState,
                        apiService = apiService
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d("MainActivity", "onNewIntent called with intent: $intent")
        setIntent(intent) // Ensure the new intent is set
        intent?.let {
            handleIntent(it)
        }
    }

    private fun handleIntent(intent: Intent) {
        val data = intent.data
        Log.d("MainActivity", "handleIntent called with data: $data")
        if (data != null) {
            val uriString = data.toString().replace("&amp;", "&")
            val decodedUri = Uri.parse(URLDecoder.decode(uriString, StandardCharsets.UTF_8.name()))
            val scheme = decodedUri.scheme
            val host = decodedUri.host
            val reference = decodedUri.getQueryParameter("reference")
            Log.d("MainActivity", "Decoded URI: $decodedUri")
            Log.d("MainActivity", "scheme: $scheme, host: $host, reference: $reference")

            if (scheme == "splitter" && host == "bankaccounts" && reference != null) {
                referenceState.value = reference
                Log.d("MainActivity", "Reference set: $reference")
            } else {
                Log.e("MainActivity", "Deep link data is invalid or incomplete")
            }
        } else {
            Log.e("MainActivity", "Intent data is null")
        }
    }

    @Composable
    private fun NavigationSetup(
        navController: NavHostController,
        context: Context,
        userId: Int,
        apiService: ApiService,
        repository: TransactionRepository
    ) {
        LaunchedEffect(Unit) {
            if (AuthManager.isUserLoggedIn(context)) {
                AuthManager.promptForBiometrics(
                    this@MainActivity,
                    onSuccess = {
                        Log.d("MainActivity", "Biometric authentication succeeded")
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    onFailure = {
                        Log.e("MainActivity", "Biometric authentication failed")
                        navController.navigate("login") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                )
            } else {
                navController.navigate("login") {
                    popUpTo("home") { inclusive = true }
                }
            }
        }
        NavGraph(navController = navController, context = context, userId = userId, apiService = apiService, repository = repository)
    }


    @Composable
    private fun HandleDeepLink(navController: NavHostController, referenceState: MutableState<String?>, apiService: ApiService) {
        val context = LocalContext.current

        LaunchedEffect(referenceState.value) {
            referenceState.value?.let { reference ->
                Log.d("HandleDeepLink", "Handling deep link with reference: $reference")
                fetchRequisitionAndNavigate(reference, navController, context, apiService)
                referenceState.value = null // Reset the state after handling
            }
        }
    }

    private fun fetchRequisitionAndNavigate(reference: String, navController: NavHostController, context: Context, apiService: ApiService) {
        Log.d("fetchRequisitionAndNavigate", "Fetching requisition for reference: $reference")
        apiService.getRequisitionByReference(reference).enqueue(object : Callback<Requisition> {
            override fun onResponse(call: Call<Requisition>, response: Response<Requisition>) {
                Log.d("fetchRequisitionAndNavigate", "API response received")
                if (response.isSuccessful) {
                    val requisition = response.body()
                    val requisitionId = requisition?.requisitionId
                    Log.d("fetchRequisitionAndNavigate", "Fetched requisition: $requisition")
                    if (requisitionId != null) {
                        Log.d("fetchRequisitionAndNavigate", "Navigating to bankaccounts/$requisitionId")
                        navController.navigate("bankaccounts/$requisitionId")
                    } else {
                        Log.e("fetchRequisitionAndNavigate", "Requisition ID is null")
                    }
                } else {
                    Log.e("fetchRequisitionAndNavigate", "Error response: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<Requisition>, t: Throwable) {
                Log.e("fetchRequisitionAndNavigate", "API call failed: ${t.message}")
            }
        })
    }
}
