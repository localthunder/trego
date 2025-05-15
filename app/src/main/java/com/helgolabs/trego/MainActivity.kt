package com.helgolabs.trego

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.Manifest
import android.app.Activity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.room.Database
import com.google.android.play.core.install.model.AppUpdateType
import com.google.firebase.messaging.FirebaseMessaging
import com.helgolabs.trego.data.local.AppDatabase
import com.helgolabs.trego.data.local.dataClasses.PreferenceKeys
import com.helgolabs.trego.data.managers.InAppUpdateManager
import com.helgolabs.trego.data.repositories.RequisitionRepository
import com.helgolabs.trego.data.network.ApiService
import com.helgolabs.trego.data.repositories.UserPreferencesRepository
import com.helgolabs.trego.data.repositories.UserRepository
import com.helgolabs.trego.ui.navigation.NavGraph
import com.helgolabs.trego.ui.theme.GlobalTheme
import com.helgolabs.trego.ui.theme.LocalThemeChangeCounter
import com.helgolabs.trego.ui.theme.LocalThemeMode
import com.helgolabs.trego.ui.theme.ThemeManager
import com.helgolabs.trego.ui.viewmodels.UserPreferencesViewModel
import com.helgolabs.trego.utils.AuthManager
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var requisitionRepository: RequisitionRepository
    private lateinit var apiService: ApiService
    private lateinit var userRepository: UserRepository
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var updateManager: InAppUpdateManager

    private var referenceState: MutableState<String?> = mutableStateOf(null)
    private var pendingInvite: MutableState<InviteData?> = mutableStateOf(null)
    private var previousRoute: MutableState<String?> = mutableStateOf(null)
    private var pendingNavigation: (() -> Unit)? = null
    private var pendingDeepLink = mutableStateOf<DeepLinkTarget?>(null)


    sealed class InviteData {
        data class GroupInvite(val inviteCode: String) : InviteData()
        data class ProvisionalUserInvite(
            val provisionalUserId: Int,
            val inviteCode: String?
        ) : InviteData()
    }

    sealed class DeepLinkTarget {
        data class GroupDetails(val groupId: Int) : DeepLinkTarget()
        data class PaymentDetails(val groupId: Int, val paymentId: Int) : DeepLinkTarget()
        data class SettleUp(val groupId: Int) : DeepLinkTarget()
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            // FCM SDK (and your app) can post notifications.
        } else {
            Toast.makeText(this,  "Notifications turned off, you can enable at any time in settings", Toast.LENGTH_LONG).show()
        }
    }

    // Activity result launcher for update flow using the newer API
    private val updateResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            Log.e("MainActivity", "Update flow failed! Result code: ${result.resultCode}")
            // Maybe show a message to the user about the failed update
        }
    }


    private fun checkForAppUpdates() {
        updateManager.checkForUpdates(
            onUpdateAvailable = { appUpdateInfo ->
                Log.d("MainActivity", "Update available: ${appUpdateInfo.availableVersionCode()}")

                // The update manager will have determined the best update type (FLEXIBLE or IMMEDIATE)
                // based on priority, staleness, etc.
                val updateType = if (appUpdateInfo.updatePriority() >= InAppUpdateManager.HIGH_PRIORITY_UPDATE) {
                    AppUpdateType.IMMEDIATE
                } else {
                    AppUpdateType.FLEXIBLE
                }

                // Start the update with the appropriate type
                updateManager.startUpdate(
                    appUpdateInfo = appUpdateInfo,
                    updateType = updateType,
                    activityResultLauncher = updateResultLauncher,
                    allowAssetPackDeletion = true // Set to true to allow removing asset packs if needed
                )
            },
            onNoUpdateAvailable = {
                Log.d("MainActivity", "No updates available")
            }
        )
    }

    private fun getFCMToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("MainActivity", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d("MainActivity", "FCM Token: $token")

            // Get current user ID
            val userId = getUserIdFromPreferences(this)

            // If user is logged in, register the token
            if (userId != null) {
                val myApplication = applicationContext as MyApplication
                val repository = myApplication.notificationRepository

                // Register token in coroutine scope
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        repository.registerDeviceToken(token, userId)
                        Log.d("MainActivity", "Successfully registered FCM token")
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Failed to register FCM token", e)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate called, intent: ${intent?.data}")

        // Initialize the update manager
        updateManager = InAppUpdateManager(this)

        // Handle deep links
        if (intent?.action == Intent.ACTION_VIEW) {
            handleIntent(intent)
        }

        val myApplication = applicationContext as MyApplication
        viewModelFactory = myApplication.viewModelFactory
        requisitionRepository = myApplication.requisitionRepository
        apiService = myApplication.apiService
        userRepository = myApplication.userRepository
        userPreferencesRepository = myApplication.userPreferencesRepository

        // Ask for notification permission
        askNotificationPermission()

        // Get FCM token
        getFCMToken()

        // Check for updates
        checkForAppUpdates()

        setContent {
            val navController = rememberNavController()
            val context = LocalContext.current
            val userId = getUserIdFromPreferences(context)

            // Get the userPreferencesViewModel
            val userPreferencesViewModel: UserPreferencesViewModel =
                viewModelFactory.create(UserPreferencesViewModel::class.java)

            // Collect the theme mode
            val themeMode by ThemeManager.themeMode.collectAsState()
            val themeChangeCounter by ThemeManager.themeChangeCounter.collectAsState()

            // Load the initial theme from preferences
            LaunchedEffect(Unit) {
                val userId = getUserIdFromPreferences(context)
                if (userId != null) {
                    val savedTheme = userPreferencesRepository.getThemeModeFlow(userId).first()
                    ThemeManager.setThemeMode(savedTheme)
                }
            }

            // Load preferences when the app starts
            LaunchedEffect(Unit) {
                userPreferencesViewModel.loadPreferences()
            }
            // Apply theme without recreating navigation
            CompositionLocalProvider(
                LocalThemeMode provides themeMode,
                LocalThemeChangeCounter provides themeChangeCounter
            ) {
                GlobalTheme(themeMode = themeMode) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Log.d("MainActivity", "User ID: $userId")

                        // Show update progress for flexible updates
                        val updateProgress by updateManager.updateProgress.collectAsState()
                        if (updateProgress in 1..99) {
                            LinearProgressIndicator(
                                progress = { updateProgress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            )
                        }

                        // Show install prompt when download is complete
                        var showUpdateReadySnackbar by remember { mutableStateOf(false) }

                        // Check if update is downloaded and waiting for install
                        LaunchedEffect(Unit) {
                            updateManager.checkIfUpdateDownloaded {
                                showUpdateReadySnackbar = true
                            }
                        }

                        // Show snackbar when update is ready to install
                        if (showUpdateReadySnackbar) {
                            Snackbar(
                                modifier = Modifier
                                    .padding(16.dp),
                                action = {
                                    TextButton(onClick = {
                                        updateManager.completeUpdate()
                                        showUpdateReadySnackbar = false
                                    }) {
                                        Text("INSTALL")
                                    }
                                },
                                dismissAction = {
                                    IconButton(onClick = { showUpdateReadySnackbar = false }) {
                                        // You would need to add an icon here
                                        Text("×")
                                    }
                                }
                            ) {
                                Text("An update has been downloaded and is ready to install.")
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
                            referenceState = referenceState,
                            previousRoute = previousRoute
                        )

                        HandleInvite(
                            navController = navController,
                            pendingInvite = pendingInvite
                        )

                        HandleDeepLinkTarget(
                            navController = navController,
                            pendingDeepLink = pendingDeepLink
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("MainActivity", "onNewIntent called")
        Log.d("MainActivity", "New intent: $intent")
        Log.d("MainActivity", "Intent data: ${intent.data}")
        Log.d("MainActivity", "Intent action: ${intent.action}")
        Log.d("MainActivity", "Intent categories: ${intent.categories}")
        Log.d("MainActivity", "Intent flags: ${intent.flags}")

        setIntent(intent)  // Important: update the stored intent

        if (intent.action == Intent.ACTION_VIEW) {
            handleIntent(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume called")
        Log.d("MainActivity", "Current intent: ${intent}")
        Log.d("MainActivity", "Intent data: ${intent?.data}")

        // Resume any updates that were in progress
        updateManager.resumeUpdates(updateResultLauncher)

        // Check if we have an update that's already downloaded
        updateManager.checkIfUpdateDownloaded {
            // Show a UI prompt when the update is ready
            Log.d("MainActivity", "Update is downloaded and ready to install")
            // This will be handled in the Compose UI with the snackbar
        }

        // Check if user is authenticated and prompt for biometrics if needed
        val userId = getUserIdFromPreferences(this)
        if (userId != null && !AuthManager.isUserLoggedIn(this)) {
            Log.d("MainActivity", "User returning to app, prompting for biometrics")
            // Only prompt for biometrics if user exists but isn't authenticated
            AuthManager.promptForBiometrics(
                this,
                userRepository = userRepository,
                userId = userId,
                onSuccess = {
                    Log.d("MainActivity", "Biometric authentication succeeded")
                    // User is already on the correct screen, just update authentication state
                    AuthManager.setAuthenticated(this, true)
                },
                onFailure = {
                    Log.e("MainActivity", "Biometric authentication failed")
                    // Navigate to login screen
                    lifecycleScope.launch {
                        setContent {
                            val navController = rememberNavController()
                            navController.navigate("login") {
                                popUpTo("home") { inclusive = true }
                            }
                        }
                    }
                }
            )
        }
    }

    override fun onPause() {
        super.onPause()
        // When app goes to background, mark that biometric will be needed on return
        val userId = getUserIdFromPreferences(this)
        if (userId != null && AuthManager.isUserLoggedIn(this)) {
            AuthManager.setNeedsBiometric(this, true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up the update manager
        updateManager.cleanup()
    }

    private fun askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // FCM SDK (and your app) can post notifications.
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // TODO: display an educational UI explaining to the user the features that will be enabled
                //       by them granting the POST_NOTIFICATION permission. This UI should provide the user
                //       "OK" and "No thanks" buttons. If the user selects "OK," directly request the permission.
                //       If the user selects "No thanks," allow the user to continue without notifications.
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        Log.d("MainActivity", "handleIntent START")
        val data = intent.data
        Log.d("MainActivity", "handleIntent called with data: $data")
        if (data != null) {
            val uriString = data.toString().replace("&amp;", "&")
            val decodedUri = data
            val scheme = decodedUri.scheme
            val host = decodedUri.host
            val path = decodedUri.path

            Log.d("MainActivity", "Decoded URI: $decodedUri")
            Log.d("MainActivity", "scheme: $scheme, host: $host, path: $path")

            if (scheme == "trego") {
                when (host) {
                    // Handle group invites
                    "groups" -> {
                        when {
                            // Group invite links
                            path?.startsWith("/invite/") == true -> {
                                val inviteCode = path.removePrefix("/invite/")
                                referenceState.value = null
                                pendingInvite.value = InviteData.GroupInvite(inviteCode)
                            }
                            // Regular group navigation (for REMOVED_FROM_GROUP notification)
                            else -> {
                                val groupId = path?.removePrefix("/")?.toIntOrNull()
                                if (groupId != null) {
                                    // Store navigation target for later execution
                                    pendingDeepLink.value = DeepLinkTarget.GroupDetails(groupId)
                                }
                            }
                        }
                    }

                    // Handle user invites
                    "users" -> {
                        if (path?.startsWith("/invite/") == true) {
                            val provisionalUserId =
                                decodedUri.getQueryParameter("userId")?.toIntOrNull()
                            val groupInviteCode = decodedUri.getQueryParameter("groupCode")

                            if (provisionalUserId != null) {
                                referenceState.value = null
                                pendingInvite.value = InviteData.ProvisionalUserInvite(
                                    provisionalUserId = provisionalUserId,
                                    inviteCode = groupInviteCode
                                )
                            }
                        }
                    }

                    // Handle bank account links
                    "bankaccounts" -> {
                        // Check for both reference (from GoCardless) and ref (from our app)
                        val reference = decodedUri.getQueryParameter("reference")
                        val returnRoute = decodedUri.getQueryParameter("returnRoute")

                        Log.d(
                            "MainActivity",
                            "Bank account deep link - reference: $reference, returnRoute: $returnRoute"
                        )

                        if (reference != null) {
                            referenceState.value = reference
                            previousRoute.value = returnRoute
                            pendingInvite.value = null
                        } else {
                            Log.e("MainActivity", "No reference found in deep link")
                        }
                    }

                    // Handle group details with payment ID (for NEW_EXPENSE, UPDATED_EXPENSE, TRANSFER_COMPLETED, CURRENCY_CONVERTED)
                    "groupDetails" -> {
                        val segments = path?.removePrefix("/")?.split("/")
                        if (segments?.size == 2) {
                            val groupId = segments[0].toIntOrNull()
                            val paymentId = segments[1].toIntOrNull()

                            if (groupId != null && paymentId != null) {
                                // Store navigation target for later execution
                                pendingDeepLink.value =
                                    DeepLinkTarget.PaymentDetails(groupId, paymentId)
                            } else if (groupId != null) {
                                // If only group ID is valid, navigate to group details
                                pendingDeepLink.value = DeepLinkTarget.GroupDetails(groupId)
                            }
                        }
                    }

                    // Handle settle up links (for SETTLE_UP_REMINDER)
                    "settleUp" -> {
                        val groupId = path?.removePrefix("/")?.toIntOrNull()
                        if (groupId != null) {
                            // Store navigation target for later execution
                            pendingDeepLink.value = DeepLinkTarget.SettleUp(groupId)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun NavigationSetup(
        navController: NavHostController,
        context: Context,
        userId: Int,
        viewModelFactory: ViewModelProvider.Factory,
        apiService: ApiService
    ) {
        // Get the userPreferencesViewModel
        val userPreferencesViewModel = viewModelFactory.create(UserPreferencesViewModel::class.java)

        // Collect the theme mode
        val themeMode by userPreferencesViewModel.themeMode.collectAsState(initial = PreferenceKeys.ThemeMode.SYSTEM)

        // Determine if we're in dark theme
        val isDarkTheme = when (themeMode) {
            PreferenceKeys.ThemeMode.LIGHT -> false
            PreferenceKeys.ThemeMode.DARK -> true
            else -> isSystemInDarkTheme()
        }

        // Apply status bar settings - ADD THE SIDE EFFECT HERE
        val activity = LocalContext.current as? Activity

        SideEffect {
            activity?.let { act ->
                Log.d("StatusBarDebug", "NavigationSetup: Setting status bar with isDarkTheme=$isDarkTheme, themeMode=$themeMode")

                // Set status bar to transparent for edge-to-edge layout
                act.window.statusBarColor = android.graphics.Color.TRANSPARENT

                // Apply the correct status bar icon color based on the theme
                val insetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
                val currentState = insetsController.isAppearanceLightStatusBars

                // In dark theme we want light icons (isAppearanceLightStatusBars = false)
                // In light theme we want dark icons (isAppearanceLightStatusBars = true)
                val targetState = !isDarkTheme

                Log.d("StatusBarDebug", "NavigationSetup: current=$currentState, target=$targetState")

                if (currentState != targetState) {
                    Log.d("StatusBarDebug", "NavigationSetup: CHANGING status bar icons from " +
                            "${if (currentState) "DARK" else "LIGHT"} to ${if (targetState) "DARK" else "LIGHT"}")
                    insetsController.isAppearanceLightStatusBars = targetState
                } else {
                    Log.d("StatusBarDebug", "NavigationSetup: No change needed for status bar icons")
                }
            }
        }

        LaunchedEffect(Unit) {
            // Load preferences on startup
            userPreferencesViewModel.loadPreferences()

            // Get fresh user ID
            val currentUserId = getUserIdFromPreferences(context)

            // Check login state
            val isLoggedIn = AuthManager.isUserLoggedIn(context)
            val needsBiometric = AuthManager.needsBiometricAuthentication(context)

            Log.d("MainActivity", "Navigation check - isLoggedIn: $isLoggedIn, needsBiometric: $needsBiometric, userId: $currentUserId")

            when {
                // User is logged in and doesn't need biometric
                isLoggedIn && !needsBiometric -> {
                    Log.d("MainActivity", "User is authenticated, navigating to home")
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }

                // User is logged in but needs biometric
                isLoggedIn && needsBiometric -> {
                    Log.d("MainActivity", "User needs biometric, prompting...")
                    // Don't navigate yet, let onResume handle biometric
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }

                // User is not logged in at all
                else -> {
                    Log.d("MainActivity", "User not logged in, navigating to login")
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            }
        }

        // Execute any pending navigation once the navController is ready
        LaunchedEffect(navController) {
            pendingNavigation?.invoke()
            pendingNavigation = null
        }

        NavGraph(
            navController = navController,
            context = context,
            userId = userId,
            viewModelFactory = viewModelFactory,
            apiService = apiService,
            themeMode = themeMode
        )
    }

    @Composable
    private fun HandleDeepLink(
        navController: NavHostController,
        referenceState: MutableState<String?>,
        previousRoute: MutableState<String?>
    ) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(referenceState.value) {
            val reference = referenceState.value
            if (reference != null) {
                Log.d("HandleDeepLink", "Processing reference: $reference")
                coroutineScope.launch {
                    fetchRequisitionAndNavigate(reference, navController, context)
                    // Clear the reference state after handling
                    referenceState.value = null
                }
            }
        }
    }

    @Composable
    private fun HandleDeepLinkTarget(
        navController: NavHostController,
        pendingDeepLink: MutableState<DeepLinkTarget?>
    ) {
        // When pendingDeepLink changes, process the navigation
        LaunchedEffect(pendingDeepLink.value) {
            val target = pendingDeepLink.value
            if (target != null) {
                Log.d("HandleDeepLinkTarget", "Processing deep link target: $target")

                when (target) {
                    is DeepLinkTarget.GroupDetails -> {
                        Log.d("HandleDeepLinkTarget", "Navigating to group details for group ${target.groupId}")
                        navController.navigate("groupDetails/${target.groupId}")
                    }
                    is DeepLinkTarget.PaymentDetails -> {
                        Log.d("HandleDeepLinkTarget", "Navigating to payment details for group ${target.groupId}, payment ${target.paymentId}")
                        navController.navigate("paymentDetails/${target.groupId}/${target.paymentId}")
                    }
                    is DeepLinkTarget.SettleUp -> {
                        Log.d("HandleDeepLinkTarget", "Navigating to settle up for group ${target.groupId}")
                        navController.navigate("settleUp/${target.groupId}")
                    }
                }

                // Clear the target after navigation
                pendingDeepLink.value = null
            }
        }
    }


    @Composable
    private fun HandleInvite(
        navController: NavHostController,
        pendingInvite: MutableState<InviteData?>
    ) {
        val context = LocalContext.current
        val userId = getUserIdFromPreferences(context)

        LaunchedEffect(pendingInvite.value) {
            when (val invite = pendingInvite.value) {
                is InviteData.GroupInvite -> {
                    if (userId != null) {
                        // User is logged in, navigate directly to group join
                        navController.navigate("invite/${invite.inviteCode}")
                    } else {
                        // User needs to log in first, pass invite code
                        navController.navigate("login?pendingInvite=${invite.inviteCode}")
                    }
                    pendingInvite.value = null
                }

                is InviteData.ProvisionalUserInvite -> {
                    if (userId != null) {
                        // User is logged in, handle merge
                        try {
                            userRepository.mergeProvisionalUser(
                                provisionalUserId = invite.provisionalUserId,
                                targetUserId = userId
                            ).onSuccess {
                                // If there's a group invite, handle it after merge
                                invite.inviteCode?.let { code ->
                                    navController.navigate("invite/$code")
                                } ?: navController.navigate("home")
                            }.onFailure {
                                // Show error dialog
                                // You'll need to implement error handling UI
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error merging users", e)
                            // Handle error
                        }
                    } else {
                        // User needs to register, pass provisional ID and optional group code
                        navController.navigate(
                            "register?provisionalId=${invite.provisionalUserId}" +
                                    (invite.inviteCode?.let { "&groupCode=$it" } ?: "")
                        )
                    }
                    pendingInvite.value = null
                }

                null -> { /* No pending invite */
                }
            }
        }
    }

    private suspend fun fetchRequisitionAndNavigate(
        reference: String,
        navController: NavController,
        context: Context
    ) {
        Log.d("fetchRequisitionAndNavigate", "Fetching requisition for reference: $reference")
        try {
            val requisition = requisitionRepository.getRequisitionByReference(reference)
            Log.d("fetchRequisitionAndNavigate", "Requisition response: $requisition")

            if (requisition != null) {
                // First navigate to bank accounts screen
                Log.d("fetchRequisitionAndNavigate", "Navigating to bankaccounts/${requisition.requisitionId}")
                navController.navigate("bankaccounts/${requisition.requisitionId}")

                // IMPORTANT: Only process return route if explicitly requested by user
                // Otherwise, we let the BankAccountsScreen handle the navigation flow
                val returnRoute = intent?.data?.getQueryParameter("manualReturn")
                Log.d("fetchRequisitionAndNavigate", "Manual return route from deep link: $returnRoute")

                if (!returnRoute.isNullOrEmpty()) {
                    // Only give more time for bank account processing if manual return is requested
                    kotlinx.coroutines.delay(1500)

                    val decodedRoute = Uri.decode(returnRoute)
                    Log.d(
                        "fetchRequisitionAndNavigate",
                        "Navigating back to decoded route: $decodedRoute"
                    )

                    navController.navigate(decodedRoute) {
                        // Pop up the bank accounts screen from the back stack
                        popUpTo("bankaccounts/${requisition.requisitionId}") { inclusive = true }
                    }
                }
                // Otherwise, let the account selection flow complete naturally
            } else {
                Log.e(
                    "fetchRequisitionAndNavigate",
                    "Requisition not found for reference: $reference"
                )
            }
        } catch (e: Exception) {
            Log.e("fetchRequisitionAndNavigate", "Error fetching requisition", e)
        }
    }

    // Helper function to ensure navigation happens when navController is ready
    private fun launchNavigation(navigationAction: () -> Unit) {
        // Store the navigation action to be executed when the navController is ready
        pendingNavigation = navigationAction
    }
}