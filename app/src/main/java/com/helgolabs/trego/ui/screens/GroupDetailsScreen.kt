package com.helgolabs.trego.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.Bitmap
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.entities.GroupMemberEntity
import com.helgolabs.trego.ui.components.GlobalFAB
import com.helgolabs.trego.ui.components.GlobalTopAppBar
import com.helgolabs.trego.ui.components.AddMembersBottomSheet
import com.helgolabs.trego.ui.components.AddPeopleAvatar
import com.helgolabs.trego.ui.components.BatchCurrencyConversionButton
import com.helgolabs.trego.ui.components.PaymentItem
import com.helgolabs.trego.ui.components.SettleUpButton
import com.helgolabs.trego.ui.components.UserAvatar
import com.helgolabs.trego.ui.theme.GlobalTheme
import com.helgolabs.trego.ui.viewmodels.GroupViewModel
import com.helgolabs.trego.ui.viewmodels.UserViewModel
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.FormattingUtils.formatAsCurrency
import com.helgolabs.trego.utils.ImageUtils
import com.helgolabs.trego.utils.PlaceholderImageGenerator
import com.helgolabs.trego.utils.StatusBarHelper
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailsScreen(
    navController: NavController,
    groupId: Int
) {
    val context = LocalContext.current
    val myApplication = context.applicationContext as MyApplication
    val groupViewModel: GroupViewModel = viewModel(factory = myApplication.viewModelFactory)
    val userViewModel: UserViewModel = viewModel(factory = myApplication.viewModelFactory)
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

                // Upload the image with the captured URI
                Log.d("CameraCapture", "Calling uploadGroupImage with URI: $uri")
                groupViewModel.uploadGroupImage(groupId, uri)

                // Also show a Toast for user feedback
                Toast.makeText(context, "Uploading image...", Toast.LENGTH_SHORT).show()
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

    LaunchedEffect(groupId) {
        groupViewModel.initializeGroupDetails(groupId)
    }

    LaunchedEffect(groupDetailsState.payments) {
        groupViewModel.checkCurrentUserBalance(groupId)
    }

    LaunchedEffect(groupDetailsState.groupImage) {
        groupViewModel.loadGroupDetails(groupId)
    }

    val refreshing = groupDetailsState.isLoading
    val pullToRefreshState = rememberPullToRefreshState()


    // Get Window instance and control system bars
    val activity = LocalContext.current as Activity
    val windowInsets = WindowInsets.systemBars

    // This is important for edge-to-edge
    DisposableEffect(Unit) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false) // Enable edge-to-edge
        val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        insetsController.isAppearanceLightStatusBars = true // Dark icons for status bar (for light background)

        onDispose {
            // Reset when leaving this screen
            WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        }
    }

    GlobalTheme {
        val activity = LocalContext.current as Activity

        SideEffect {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false) // Allow edge-to-edge
            val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            insetsController.isAppearanceLightStatusBars = true // Dark icons on light background
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

//            topBar = {
//                GlobalTopAppBar(
//                    title = {}, // Remove title
//                    actions = {
//                        IconButton(onClick = {
//                            navController.navigate("groupSettings/${groupId}")
//                        }) {
//                            Icon(
//                                imageVector = Icons.Default.Settings,
//                                contentDescription = "Group Settings"
//                            )
//                        }
//                    },
//                    isTransparent = true
//                )
//            },
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
                                    Column(modifier = Modifier.padding(16.dp)) {
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
                                            // Parse the timestamp - handle both ISO format and unix timestamp
                                            when {
                                                payment.updatedAt.toLongOrNull() != null -> payment.updatedAt.toLong()
                                                else -> {
                                                    // Try parsing ISO format
                                                    val instant = java.time.Instant.parse(payment.updatedAt)
                                                    instant.toEpochMilli()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            // If parsing fails, put it at the end
                                            0L
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
                    groupViewModel.loadGroupDetails(groupId) // Reload group details when sheet is dismissed
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
        if (activity != null) {
            val insetsController = WindowCompat.getInsetsController(
                activity.window, activity.window.decorView
            )
            // statusBarShouldBeDark = true means we want light icons (dark background)
            // statusBarShouldBeDark = false means we want dark icons (light background)
            insetsController.isAppearanceLightStatusBars = !statusBarShouldBeDark
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
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
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .padding(top = 40.dp) // Extra padding for status bar
        ) {
            IconButton(
                onClick = onEditClick,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Image",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

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
        Button(
            onClick = onBalancesClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            )
        ) {
            Text("Balances")
        }

        Button(
            onClick = onTotalsClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            )
        ) {
            Text("Totals")
        }

        // Primary color Settle Up button last
        Button(
            onClick = onSettleUpClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
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