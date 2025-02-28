package com.helgolabs.trego.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.entities.GroupMemberEntity
import com.helgolabs.trego.ui.components.GlobalFAB
import com.helgolabs.trego.ui.components.GlobalTopAppBar
import com.helgolabs.trego.ui.components.AddMembersBottomSheet
import com.helgolabs.trego.ui.components.BatchCurrencyConversionButton
import com.helgolabs.trego.ui.components.PaymentItem
import com.helgolabs.trego.ui.components.SettleUpButton
import com.helgolabs.trego.ui.theme.GlobalTheme
import com.helgolabs.trego.ui.viewmodels.GroupViewModel
import com.helgolabs.trego.ui.viewmodels.UserViewModel
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.FormattingUtils.formatAsCurrency
import com.helgolabs.trego.utils.ImageUtils
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
    val paymentUpdateTrigger by groupViewModel.paymentsUpdateTrigger.collectAsState()




    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { groupViewModel.uploadGroupImage(groupId, it) }
    }

    LaunchedEffect(groupId) {
        groupViewModel.initializeGroupDetails(groupId)
    }

    LaunchedEffect(groupDetailsState.payments) {
        groupViewModel.checkCurrentUserBalance()
    }

    val refreshing = groupDetailsState.isLoading
    val pullToRefreshState = rememberPullToRefreshState()


    GlobalTheme {
        Scaffold(modifier = Modifier.pullToRefresh(
            isRefreshing = refreshing,
            state = pullToRefreshState,
            enabled = !refreshing,
            onRefresh = { groupViewModel.loadGroupDetails(groupId)}

        ),
            topBar = {
                GlobalTopAppBar(
                    title = { Text(groupDetailsState.group?.name ?: "Group Details") },
                    actions = {
                        IconButton(onClick = {
                            // Navigate to settings page
                            navController.navigate("groupSettings/${groupId}")
                        }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Group Settings"
                            )
                        }
                    }
                )
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
                                GroupImageSection(
                                    groupImage = groupDetailsState.groupImage,
                                    uploadStatus = groupDetailsState.uploadStatus,
                                    imageLoadingState = groupDetailsState.imageLoadingState,
                                    onImageClick = { launcher.launch("image/*") },
                                    viewModel = groupViewModel,
                                    groupId = groupId
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
                                SettleUpButton(
                                    groupId = groupId,
                                    balances = groupBalances,
                                    onSettleUpClick = { navController.navigate("settleUp/$groupId") }
                                )
                            }
                            item {
                                GroupMembersSection(
                                    groupMembers = groupDetailsState.groupMembers,
                                    usernames = groupDetailsState.usernames
                                )
                            }
                            item {
                                GroupActionButtons(
                                    onAddMembersClick = { showAddMembersBottomSheet = true },
                                    onBalancesClick = { navController.navigate("groupBalances/$groupId") },
                                    onTotalsClick = { navController.navigate("groupTotals/$groupId") }
                                )
                            }

                            item {
                                Text(
                                    "Payments",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                            item {
                                BatchCurrencyConversionButton(
                                    paymentsCount = groupDetailsState.payments
                                        .filter {
                                            it.currency != groupDetailsState.group?.defaultCurrency &&
                                                    it.currency != null
                                        }.size,
                                    targetCurrency = groupDetailsState.group?.defaultCurrency
                                        ?: "GBP",
                                    isConverting = groupDetailsState.isConverting,
                                    conversionError = groupDetailsState.conversionError,
                                    onConvertClicked = {
                                        groupDetailsState.group?.id?.let { groupId ->
                                            groupViewModel.batchConvertCurrencies(groupId)
                                        }
                                    }
                                )
                            }
                            items(
                                groupDetailsState.payments
                                    .sortedByDescending { payment ->
                                        try {
                                            // Parse the timestamp - handle both ISO format and unix timestamp
                                            when {
                                                payment.updatedAt.toLongOrNull() != null -> payment.updatedAt.toLong()
                                                else -> {
                                                    // Try parsing ISO format
                                                    val instant =
                                                        java.time.Instant.parse(payment.updatedAt)
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
                                        onClick = { navController.navigate("paymentDetails/$groupId/${payment.id}") }
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
fun GroupImageSection(
    groupImage: String?,
    uploadStatus: GroupViewModel.UploadStatus,
    imageLoadingState: GroupViewModel.ImageLoadingState,
    groupId: Int,
    onImageClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupViewModel
) {
    val context = LocalContext.current
    var showImageSourceDialog by remember { mutableStateOf(false) }

    // Store temporary file URI for camera capture
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }

    // Camera launcher - declare this before the permission launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempImageUri?.let { uri ->
                viewModel.uploadGroupImage(groupId, uri)
            }
        }
    }

    // Permission state for camera
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Create file and launch camera
            createImageFileAndLaunchCamera(context) { uri ->
                tempImageUri = uri
                cameraLauncher.launch(uri)
            }
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadGroupImage(groupId, it) }
    }

    val imageSource = remember(groupImage) {
        when {
            groupImage == null -> null
            groupImage.startsWith("/") -> "file://$groupImage" // Local path
            else -> ImageUtils.getFullImageUrl(groupImage) // Server URL
        }
    }

    // Effect to handle initial image loading
    LaunchedEffect(groupImage) {
        if (groupImage != null && imageLoadingState == GroupViewModel.ImageLoadingState.Idle) {
            viewModel.reloadGroupImage()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(16.dp)
            .clickable { showImageSourceDialog = true },
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (imageLoadingState) {
                is GroupViewModel.ImageLoadingState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is GroupViewModel.ImageLoadingState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(16.dp)
                            .clickable { viewModel.reloadGroupImage() }
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
                else -> {
                    if (imageSource != null) {
                        AsyncImage(
                            model = imageSource,
                            contentDescription = "Group Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = "Add Photo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Add Group Photo",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

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

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("Select Image Source") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showImageSourceDialog = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Choose from Gallery")
                    }
                    TextButton(
                        onClick = {
                            showImageSourceDialog = false
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Take Photo")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImageSourceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun GroupMembersSection(
    groupMembers: List<GroupMemberEntity>,
    usernames: Map<Int, String>
) {
    Column {
        Text(
            "Members",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            groupMembers.forEach { member ->
                val username = usernames[member.userId] ?: "Loading..."
                Text(
                    username,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
fun GroupActionButtons(
    onAddMembersClick: () -> Unit,
    onBalancesClick: () -> Unit,
    onTotalsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(onClick = onAddMembersClick) {
            Text("Add People")
        }
        Button(onClick = onBalancesClick) {
            Text("Balances")
        }
        Button(onClick = onTotalsClick) {
            Text("Totals")
        }
    }
}

private fun createImageFileAndLaunchCamera(context: Context, onUriCreated: (Uri) -> Unit) {
    try {
        // Create an image file name
        val timeStamp = DateUtils.getCurrentTimestamp()
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        val tempFile = File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        )

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
        onUriCreated(uri)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}