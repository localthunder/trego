package com.helgolabs.trego.ui.screens

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.helgolabs.trego.data.local.entities.GroupMemberEntity
import com.helgolabs.trego.ui.components.GlobalFAB
import com.helgolabs.trego.ui.components.AddMembersBottomSheet
import com.helgolabs.trego.ui.components.AddPeopleAvatar
import com.helgolabs.trego.ui.components.BatchCurrencyConversionButton
import com.helgolabs.trego.ui.components.PaymentItem
import com.helgolabs.trego.ui.components.UserAvatar
import com.helgolabs.trego.ui.theme.AnimatedDynamicThemeProvider
import com.helgolabs.trego.ui.viewmodels.GroupViewModel
import com.helgolabs.trego.utils.ColorSchemeCache
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.FormattingUtils.formatAsCurrency
import com.helgolabs.trego.utils.ImageOrientationFixer
import com.helgolabs.trego.utils.ImageUtils
import com.helgolabs.trego.utils.PlaceholderImageGenerator
import com.helgolabs.trego.utils.StatusBarHelper.StatusBarProtection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailsScreen(
    navController: NavController,
    groupId: Int,
    groupViewModel: GroupViewModel
) {
    val context = LocalContext.current
    var showArchiveConfirmDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    val archiveState by groupViewModel.archiveGroupState.collectAsState()
    val restoreState by groupViewModel.restoreGroupState.collectAsState()
    val groupBalances by groupViewModel.groupBalances.collectAsState()
    val groupDetailsState by groupViewModel.groupDetailsState.collectAsState()
    var showAddMembersBottomSheet by remember { mutableStateOf(false) }
    var showLeaveGroupDialog by remember { mutableStateOf(false) }
    var showImageOptionsBottomSheet by remember { mutableStateOf(false) }
    val paymentUpdateTrigger by groupViewModel.paymentsUpdateTrigger.collectAsState()
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val isLoading = groupDetailsState.isLoading
    val group = groupDetailsState.group
    val snackbarHostState = remember { SnackbarHostState() }

    val groupColorScheme = groupDetailsState.groupColorScheme
    val groupImage = groupDetailsState.groupImage
    var groupImageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(groupId) {
        // Try to read cached color scheme first for immediate UI feedback
        try {
            val cachedScheme = withContext(Dispatchers.IO) {
                ColorSchemeCache.getColorScheme(context, groupId)
            }

            if (cachedScheme != null) {
                // Apply cached scheme immediately
                groupViewModel.setGroupColorScheme(cachedScheme)
            }
        } catch (e: Exception) {
            Log.e("GroupDetailsScreen", "Error reading cached color scheme", e)
        }

        // Initialize group data
        groupViewModel.initializeGroupDetails(groupId)
    }

    LaunchedEffect(groupDetailsState.payments) {
        groupViewModel.checkCurrentUserBalance(groupId)
    }

    // 3. Check for messages from previous screen navigation
    LaunchedEffect(Unit) {
        // Get navigation arguments
        val batchCount = navController.currentBackStackEntry
            ?.savedStateHandle
            ?.get<Int>("batchAddCount")

        // If we received a batch count, show a snackbar
        if (batchCount != null && batchCount > 0) {
            snackbarHostState.showSnackbar(
                message = "$batchCount expenses added successfully"
            )
            // Clear the argument after showing the snackbar
            navController.currentBackStackEntry?.savedStateHandle?.remove<Int>("batchAddCount")
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { groupViewModel.uploadGroupImage(groupId, it) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        // Debug log the success status
        Log.d("CameraCapture", "Camera returned with success: $success")

        if (success) {
            // Debug log the URI
            Log.d("CameraCapture", "Captured image URI: $tempImageUri")

            tempImageUri?.let { uri ->
                // Close the bottom sheet first
                showImageOptionsBottomSheet = false

                // Process the image to fix orientation before uploading
                coroutineScope.launch {
                    try {
                        // Show loading indication
                        Toast.makeText(context, "Processing image...", Toast.LENGTH_SHORT).show()

                        // Use the ImageOrientationFixer to process and get a corrected URI
                        val processedUri = ImageOrientationFixer.processAndSaveImage(context, uri)

                        if (processedUri != null) {
                            // Upload the processed image
                            Log.d("CameraCapture", "Uploading processed image with URI: $processedUri")
                            groupViewModel.uploadGroupImage(groupId, processedUri)

                            // Show feedback
                            Toast.makeText(context, "Uploading image...", Toast.LENGTH_SHORT).show()
                        } else {
                            // Fall back to original if processing fails
                            Log.d("CameraCapture", "Image processing failed, using original URI: $uri")
                            groupViewModel.uploadGroupImage(groupId, uri)
                            Toast.makeText(context, "Uploading image (processing failed)...", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("CameraCapture", "Error processing image", e)
                        // Fall back to original if exception occurs
                        groupViewModel.uploadGroupImage(groupId, uri)
                        Toast.makeText(context, "Uploading unprocessed image due to error...", Toast.LENGTH_SHORT).show()
                    }
                }
            } ?: run {
                Log.e("CameraCapture", "Error: tempImageUri is null after camera capture")
                Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Create file and launch camera
            createImageFileAndLaunchCamera(context) { uri ->
                tempImageUri = uri
                cameraLauncher.launch(uri)
            }
        } else {
            Toast.makeText(context, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    // Load bitmap from the image path
    LaunchedEffect(groupImage) {
        if (groupImage != null) {
            withContext(Dispatchers.IO) {
                try {
                    val processedPath = when {
                        groupImage.startsWith("placeholder://") -> {
                            val localPath = PlaceholderImageGenerator.getImageForPath(context, groupImage)
                            "file://$localPath"
                        }
                        groupImage.startsWith("/data/") || groupImage.contains("/files/placeholder_images/") -> {
                            "file://$groupImage"
                        }
                        else -> {
                            ImageUtils.getFullImageUrl(groupImage)
                        }
                    }

                    // Load bitmap based on path type
                    if (processedPath != null) {
                        groupImageBitmap = if (processedPath.startsWith("file://")) {
                            val filePath = processedPath.substring(7)
                            BitmapFactory.decodeFile(filePath)
                        } else {
                            val imageLoader = ImageLoader(context)
                            val request = ImageRequest.Builder(context)
                                .data(processedPath)
                                .allowHardware(false)
                                .build()
                            val result = imageLoader.execute(request)
                            if (result is SuccessResult) {
                                (result.image as? BitmapDrawable)?.bitmap ?: result.image.toBitmap()
                            } else null
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GroupDetailsScreen", "Error loading bitmap", e)
                    groupImageBitmap = null
                }
            }
        } else {
            groupImageBitmap = null
        }
    }

    val refreshing = groupDetailsState.isLoading
    val pullToRefreshState = rememberPullToRefreshState()


    // Apply the dynamic theme
    AnimatedDynamicThemeProvider(groupId, groupColorScheme) {
        // Get Window instance and control system bars
        val activity = LocalContext.current as Activity
        val windowInsets = WindowInsets.systemBars

        // This is important for edge-to-edge
        DisposableEffect(Unit) {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false) // Enable edge-to-edge
            val insetsController =
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            insetsController.isAppearanceLightStatusBars =
                true // Dark icons for status bar (for light background)

            onDispose {
                // Reset when leaving this screen
                WindowCompat.setDecorFitsSystemWindows(activity.window, true)
            }
        }

        // Show loading screen while initializing
        if (isLoading && group == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading group...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            return@AnimatedDynamicThemeProvider
        }

        // Show error screen if there's an error
        if (groupDetailsState.error != null && groupDetailsState.group == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Error: ${groupDetailsState.error}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { groupViewModel.loadGroupDetails(groupId, true) }
                    ) {
                        Text("Retry")
                    }
                }
            }
            return@AnimatedDynamicThemeProvider
        }

        Scaffold(
            modifier = Modifier.pullToRefresh(
                isRefreshing = refreshing,
                state = pullToRefreshState,
                enabled = !refreshing,
                onRefresh = { groupViewModel.loadGroupDetails(groupId) }
            ),
            containerColor = Color.Transparent, // Allow content behind status bar
            contentWindowInsets = WindowInsets(0, 0, 0, 0), // No insets for content - critical for edge-to-edge
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            floatingActionButton = {
                GlobalFAB(
                    onClick = { navController.navigate("addExpense/$groupId") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = "Add") },
                    text = "Add Expense"
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                when {
                    groupDetailsState.isLoading && groupDetailsState.group == null -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    groupDetailsState.error != null -> {
                        Text(
                            "Error: ${groupDetailsState.error}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    groupDetailsState.group == null -> {
                        Text(
                            "No group data available",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    else -> {
                        LazyColumn {
                            item {
                                GroupImageFullWidth(
                                    uploadStatus = groupDetailsState.uploadStatus,
                                    imageLoadingState = groupDetailsState.imageLoadingState,
                                    isPlaceholderImage = groupDetailsState.isPlaceholderImage,
                                    onEditClick = { showImageOptionsBottomSheet = true },
                                    viewModel = groupViewModel,
                                    groupId = groupId,
                                    context = context,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight() // Allows image to reach top
                                )
                            }
                            item {
                                groupDetailsState.group?.let { group ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 24.dp, start = 16.dp, end = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        // Title and description in a column
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                group.name,
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                            group.description?.let {
                                                Text(
                                                    it,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.onBackground.copy(
                                                        alpha = 0.7f
                                                    )
                                                )
                                            }
                                        }

                                        // Settings button
                                        IconButton(
                                            onClick = { navController.navigate("groupSettings/${groupId}") },
                                            modifier = Modifier
                                                .size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = "Group Settings",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            item {
                                GroupMembersSection(
                                    groupMembers = groupDetailsState.groupMembers,
                                    usernames = groupDetailsState.usernames,
                                    onAddPeopleClick = { showAddMembersBottomSheet = true }
                                )
                            }
                            item {
                                GroupActionButtons(
                                    onSettleUpClick = { navController.navigate("settleUp/$groupId") },
                                    onBalancesClick = { navController.navigate("groupBalances/$groupId") },
                                    onTotalsClick = { navController.navigate("groupTotals/$groupId") }
                                )
                            }

                            // Title section for Payments
                            item {
                                Text(
                                    "Payments",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 24.dp,
                                        bottom = 8.dp
                                    )
                                )
                            }

                            // Conditional Currency Conversion Button
                            item {
                                val nonDefaultCurrencyPayments = groupDetailsState.payments
                                    .filter {
                                        it.currency != groupDetailsState.group?.defaultCurrency &&
                                                it.currency != null
                                    }

                                val paymentsCount = nonDefaultCurrencyPayments.size

                                // Only show the button when there are payments in different currencies
                                if (paymentsCount > 0) {
                                    BatchCurrencyConversionButton(
                                        paymentsCount = paymentsCount,
                                        targetCurrency = groupDetailsState.group?.defaultCurrency ?: "GBP",
                                        isConverting = groupDetailsState.isConverting,
                                        conversionError = groupDetailsState.conversionError,
                                        onConvertClicked = {
                                            groupDetailsState.group?.id?.let { groupId ->
                                                groupViewModel.batchConvertCurrencies(groupId)
                                            }
                                        },
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }

                            // Empty state message when there are no payments
                            item {
                                if (groupDetailsState.payments.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            "No payments yet",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Click the + button below to add your first expense",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            // List of all payments, sorted by date
                            items(
                                groupDetailsState.payments
                                    .sortedByDescending { payment ->
                                        try {
                                            // Use your existing parseTimestamp function
                                            DateUtils.parseTimestamp(payment.paymentDate ?: "")
                                        } catch (e: Exception) {
                                            // If parsing fails, use epoch start (will sort to the end)
                                            Instant.EPOCH
                                        }
                                    }
                            ) { payment ->
                                key(payment.id.toString() + payment.updatedAt + paymentUpdateTrigger) {
                                    PaymentItem(
                                        payment = payment,
                                        context = context,
                                        onClick = { navController.navigate("paymentDetails/$groupId/${payment.id}") },
                                    )
                                }

                                // Add a divider between payment items for better visual separation
                                if (groupDetailsState.payments.indexOf(payment) < groupDetailsState.payments.size - 1) {
                                    Divider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddMembersBottomSheet) {
            AddMembersBottomSheet(
                groupId = groupId,
                onDismissRequest = {
                    showAddMembersBottomSheet = false
//                    groupViewModel.loadGroupDetails(groupId) // Reload group details when sheet is dismissed
                }
            )
        }

        // Image Options Bottom Sheet
        if (showImageOptionsBottomSheet) {
            ImageOptionsBottomSheet(
                onDismissRequest = { showImageOptionsBottomSheet = false },
                onCameraClick = {
                    showImageOptionsBottomSheet = false

                    // Create a file and get the URI before launching camera
                    createImageFileAndLaunchCamera(context) { uri ->
                        Log.d("CameraCapture", "Created file for camera with URI: $uri")
                        tempImageUri = uri
                        cameraLauncher.launch(uri)
                    }
                },
                onGalleryClick = {
                    showImageOptionsBottomSheet = false
                    launcher.launch("image/*")
                },
                onRegenerateClick = {
                    showImageOptionsBottomSheet = false
                    coroutineScope.launch {
                        Log.d("onRegenerateClick", "Regenerating image for groupId: $groupId")
                        // Just call the ViewModel method - don't generate an image yourself
                        groupViewModel.regeneratePlaceholderImage(groupId)
                    }
                }
            )
        }

        // Archive confirmation dialog
        if (showArchiveConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showArchiveConfirmDialog = false },
                title = { Text("Archive Group?") },
                text = {
                    Text(
                        "Are you sure you want to archive this group? " +
                                "You will still be able to access archived groups and can restore the group in the future."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            groupViewModel.archiveGroup(groupId)
                            showArchiveConfirmDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Archive")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showArchiveConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Unarchive confirmation dialog
        if (showRestoreConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showRestoreConfirmDialog = false },
                title = { Text("Unarchive Group?") },
                text = {
                    Text("Are you sure you want to unarchive this group? The group will be moved back to your active groups.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            groupViewModel.restoreGroup(groupId)
                            showRestoreConfirmDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Unarchive")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        //Leave group confirmation dialog
        if (showLeaveGroupDialog) {
            val userBalance by groupViewModel.currentUserBalance.collectAsStateWithLifecycle()
            val hasNonZeroBalance = userBalance?.balances?.any { (_, amount) ->
                Math.abs(amount) > 0.01
            } ?: false

            AlertDialog(
                onDismissRequest = { showLeaveGroupDialog = false },
                title = { Text("Leave Group") },
                text = {
                    Column {
                        if (hasNonZeroBalance) {
                            Text("You have outstanding balances in this group:")
                            Spacer(modifier = Modifier.height(8.dp))
                            userBalance?.balances?.forEach { (currency, amount) ->
                                if (Math.abs(amount) > 0.01) {
                                    Text(
                                        buildString {
                                            if (amount < 0) append("You owe: ") else append("You are owed: ")
                                            append(amount.absoluteValue.formatAsCurrency(currency))
                                        },
                                        color = if (amount < 0)
                                            MaterialTheme.colorScheme.error
                                        else
                                            MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("You'll need to settle up before leaving.")
                        } else {
                            Text("Are you sure you want to leave this group?")
                        }
                    }
                },
                confirmButton = {
                    if (hasNonZeroBalance) {
                        val isOwed =
                            userBalance?.balances?.any { (_, amount) -> amount > 0 } ?: false
                        TextButton(
                            onClick = {
                                showLeaveGroupDialog = false
                                navController.navigate("group/${groupId}/balances")
                            }
                        ) {
                            Text(if (isOwed) "Request Payment" else "Settle Up")
                        }
                    } else {
                        TextButton(
                            onClick = {
                                showLeaveGroupDialog = false
                                groupViewModel.getCurrentMemberId()?.let { memberId ->
                                    groupViewModel.removeMember(memberId)
                                }
                            }
                        ) {
                            Text("Leave")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLeaveGroupDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        // Handle archive state changes
        LaunchedEffect(archiveState) {
            when (archiveState) {
                is GroupViewModel.ArchiveGroupState.Success -> {
                    Toast.makeText(context, "Group successfully archived", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
                is GroupViewModel.ArchiveGroupState.Error -> {
                    Toast.makeText(
                        context,
                        "Failed to archive group: ${(archiveState as GroupViewModel.ArchiveGroupState.Error).message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> { /* Handle other states */ }
            }
        }

        // Handle restore state changes
        LaunchedEffect(restoreState) {
            when (restoreState) {
                is GroupViewModel.RestoreGroupState.Success -> {
                    Toast.makeText(context, "Group successfully unarchived", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
                is GroupViewModel.RestoreGroupState.Error -> {
                    Toast.makeText(
                        context,
                        "Failed to unarchive group: ${(restoreState as GroupViewModel.RestoreGroupState.Error).message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> { /* Handle other states */ }
            }
        }
    }
}

@Composable
fun GroupImageFullWidth(
    uploadStatus: GroupViewModel.UploadStatus,
    imageLoadingState: GroupViewModel.ImageLoadingState,
    isPlaceholderImage: Boolean,
    onEditClick: () -> Unit,
    viewModel: GroupViewModel,
    groupId: Int,
    modifier: Modifier = Modifier,
    context: Context
) {
    val uniqueKey = remember { System.currentTimeMillis() }
    val activity = LocalContext.current as? Activity
    val coroutineScope = rememberCoroutineScope()

    val groupDetailsState by viewModel.groupDetailsState.collectAsState()
    val groupImage = groupDetailsState.groupImage
    var processedImagePath by remember { mutableStateOf<String?>(null) }

    // Collect status bar information from ViewModel
    val statusBarShouldBeDark by viewModel.statusBarShouldBeDark.collectAsState()

    // Process the image path and trigger analysis in ViewModel
    LaunchedEffect(groupImage, uniqueKey) {
        if (groupImage == null) {
            processedImagePath = null
            return@LaunchedEffect
        }

        processedImagePath = when {
            // Process image path as before...
            groupImage.startsWith("placeholder://") -> {
                val localPath = PlaceholderImageGenerator.getImageForPath(context, groupImage)
                "file://$localPath"
            }
            groupImage.startsWith("/data/") || groupImage.contains("/files/placeholder_images/") -> {
                "file://$groupImage"
            }
            else -> {
                ImageUtils.getFullImageUrl(groupImage)
            }
        }

        // Trigger analysis in the ViewModel
        processedImagePath?.let { path ->
            viewModel.analyzeImageForStatusBar(path)
        }
    }

    // Update status bar based on viewModel analysis
    LaunchedEffect(statusBarShouldBeDark) {
        activity?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = !statusBarShouldBeDark
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
    ) {
        when (imageLoadingState) {
            is GroupViewModel.ImageLoadingState.Loading -> {
                // Loading UI...
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            is GroupViewModel.ImageLoadingState.Error -> {
                // Error UI with retry click handler
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { viewModel.reloadGroupImage() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${imageLoadingState.message}\nTap to retry",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            else -> {
                // If we have a processed image path, display the image
                if (processedImagePath != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(processedImagePath)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Group Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onSuccess = {
                            Log.d("GroupImage", "Successfully loaded image: $processedImagePath")
                        },
                        onError = { error ->
                            Log.e("GroupImage", "Error loading image: $processedImagePath")
                        }
                    )
                } else {
                    // No image placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = "Add Photo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
            }
        }

        // Edit button overlay (always visible)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        ) {
            IconButton(
                onClick = onEditClick,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Edit Image",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        StatusBarProtection(
            color = if (statusBarShouldBeDark) {
                // For dark image tops, use darker protection
                Color.Black.copy(alpha = 0.4f)
            } else {
                // For light image tops, use lighter protection
                MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
            }
        )

        // Upload status overlay
        if (uploadStatus is GroupViewModel.UploadStatus.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageOptionsBottomSheet(
    onDismissRequest: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onRegenerateClick: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Change Group Image",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Option 1: Camera
            BottomSheetOption(
                icon = Icons.Default.CameraAlt,
                title = "Camera",
                subtitle = "Take a new photo",
                onClick = onCameraClick
            )

            // Option 2: Gallery
            BottomSheetOption(
                icon = Icons.Default.PhotoLibrary,
                title = "Gallery",
                subtitle = "Choose from your photos",
                onClick = onGalleryClick
            )

            // Option 4: Regenerate
            BottomSheetOption(
                icon = Icons.Default.Refresh,
                title = "Random Pattern",
                subtitle = "Generate a new random image",
                onClick = onRegenerateClick
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun BottomSheetOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(48.dp)
                .padding(end = 16.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun GroupMembersSection(
    groupMembers: List<GroupMemberEntity>,
    usernames: Map<Int, String>,
    onAddPeopleClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            "Members",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            // Add People Circle - always first item
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp)
                ) {
                    AddPeopleAvatar(
                        onClick = onAddPeopleClick,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }

            // Member Avatars
            items(groupMembers) { member ->
                val username = usernames[member.userId] ?: "?"
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp)
                ) {
                    UserAvatar(
                        username = username,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = username,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun GroupActionButtons(
    onSettleUpClick: () -> Unit,
    onBalancesClick: () -> Unit,
    onTotalsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Secondary color buttons first
        OutlinedButton(
            onClick = onBalancesClick,
//            colors = ButtonDefaults.buttonColors(
//                containerColor = MaterialTheme.colorScheme.tertiary,
//                contentColor = MaterialTheme.colorScheme.onTertiary
//            )
        ) {
            Text("Balances")
        }

        OutlinedButton(
            onClick = onTotalsClick,
//            colors = ButtonDefaults.buttonColors(
//                containerColor = MaterialTheme.colorScheme.secondary,
//                contentColor = MaterialTheme.colorScheme.onSecondary
//            )
        ) {
            Text("Totals")
        }

        // Primary color Settle Up button last
        Button(
            onClick = onSettleUpClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("Settle Up")
        }
    }
}

private fun createImageFileAndLaunchCamera(context: Context, onUriCreated: (Uri) -> Unit) {
    try {
        // Create an image file name
        val timeStamp = DateUtils.getCurrentTimestamp()
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        Log.d("CameraCapture", "Creating temp file in $storageDir")

        val tempFile = File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        )

        Log.d("CameraCapture", "Created temp file: ${tempFile.absolutePath}")

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )

        Log.d("CameraCapture", "FileProvider URI: $uri")
        onUriCreated(uri)
    } catch (e: Exception) {
        Log.e("CameraCapture", "Error creating image file", e)
        e.printStackTrace()
        Toast.makeText(context, "Could not create image file", Toast.LENGTH_SHORT).show()
    }
}