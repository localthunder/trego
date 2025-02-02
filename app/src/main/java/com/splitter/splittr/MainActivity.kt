package com.splitter.splittr

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.room.Database
import com.splitter.splittr.data.local.AppDatabase
import com.splitter.splittr.data.repositories.RequisitionRepository
import com.splitter.splittr.data.network.ApiService
import com.splitter.splittr.data.repositories.UserRepository
import com.splitter.splittr.ui.navigation.NavGraph
import com.splitter.splittr.ui.theme.GlobalTheme
import com.splitter.splittr.utils.AuthManager
import com.splitter.splittr.utils.TokenManager
import com.splitter.splittr.utils.TokenManager.getRefreshToken
import com.splitter.splittr.utils.TokenManager.isTokenExpired
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class MainActivity : FragmentActivity() {

    private var referenceState: MutableState<String?> = mutableStateOf(null)
    private lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var requisitionRepository: RequisitionRepository
    private lateinit var apiService: ApiService
    private lateinit var userRepository: UserRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate called, intent: ${intent?.data}")
        handleIntent(intent)

        val myApplication = applicationContext as com.splitter.splittr.MyApplication
        viewModelFactory = myApplication.viewModelFactory
        requisitionRepository = myApplication.requisitionRepository
        apiService = myApplication.apiService
        userRepository = myApplication.userRepository

        setContent {
            GlobalTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val context = LocalContext.current
                    val userId = getUserIdFromPreferences(context)

                    Log.d("MainActivity", "User ID: $userId")

                    LaunchedEffect(Unit) {
                        Log.d("MainActivity", "LaunchedEffect triggered")
                        if (userId != null) {
                            Log.d("MainActivity", "User exists, prompting for biometrics")
                            AuthManager.promptForBiometrics(
                                this@MainActivity,
                                userRepository = userRepository,
                                userId = userId,
                                onSuccess = {
                                    Log.d("MainActivity", "Biometric authentication succeeded, navigating to home")
                                    navController.navigate("home") {
                                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                    }
                                },
                                onFailure = {
                                    Log.e("MainActivity", "Biometric authentication failed")
                                    // Stay on current screen
                                }
                            )
                        } else {
                            Log.d("MainActivity", "No user found, navigating to login")
                            navController.navigate("login")
                        }
                    }

                    NavigationSetup(
                        navController = navController,
                        context = context,
                        userId = userId ?: -1,
                        viewModelFactory = viewModelFactory,
                        apiService = apiService
                    )
                    HandleDeepLink(
                        navController = navController,
                        referenceState = referenceState
                    )
                }
            }
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
                // This part is crucial:
                referenceState.value = reference
                Log.d("MainActivity", "Reference set: $reference")
            }
        }
    }

    @Composable
    private fun NavigationSetup(
        navController: NavHostController,
        context: Context,
        userId: Int,
        viewModelFactory: ViewModelProvider.Factory,
        apiService: ApiService
    ) {
        LaunchedEffect(Unit) {
            if (AuthManager.isUserLoggedIn(context)) {
                AuthManager.promptForBiometrics(
                    this@MainActivity,
                    userRepository = userRepository,
                    userId = userId,
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
        NavGraph(
            navController = navController,
            context = context,
            userId = userId,
            viewModelFactory = viewModelFactory,
            apiService = apiService
        )
    }

    @Composable
    private fun HandleDeepLink(navController: NavHostController, referenceState: MutableState<String?>) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(referenceState.value) {
            referenceState.value?.let { reference ->
                Log.d("HandleDeepLink", "Handling deep link with reference: $reference")
                coroutineScope.launch {
                    fetchRequisitionAndNavigate(reference, navController, context)
                }
                referenceState.value = null // Reset the state after handling
            }
        }
    }

    private suspend fun fetchRequisitionAndNavigate(reference: String, navController: NavHostController, context: Context) {
        Log.d("fetchRequisitionAndNavigate", "Fetching requisition for reference: $reference")
        try {
            val requisition = requisitionRepository.getRequisitionByReference(reference)
            if (requisition != null) {
                Log.d("fetchRequisitionAndNavigate", "Fetched requisition: $requisition")
                Log.d("fetchRequisitionAndNavigate", "Navigating to bankaccounts/${requisition.requisitionId}")
                navController.navigate("bankaccounts/${requisition.requisitionId}")
            } else {
                Log.e("fetchRequisitionAndNavigate", "Requisition not found for reference: $reference")
            }
        } catch (e: Exception) {
            Log.e("fetchRequisitionAndNavigate", "Error fetching requisition: ${e.message}")
        }
    }
}