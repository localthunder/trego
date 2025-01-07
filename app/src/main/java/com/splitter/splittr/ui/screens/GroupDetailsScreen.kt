package com.splitter.splittr.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.splitter.splittr.MyApplication
import com.splitter.splittr.ui.components.GlobalFAB
import com.splitter.splittr.ui.components.GlobalTopAppBar
import com.splitter.splittr.data.model.GroupMember
import com.splitter.splittr.ui.components.AddMembersBottomSheet
import com.splitter.splittr.ui.components.PaymentItem
import com.splitter.splittr.ui.theme.GlobalTheme
import com.splitter.splittr.ui.viewmodels.GroupViewModel
import com.splitter.splittr.ui.viewmodels.UserViewModel
import com.splitter.splittr.utils.ImageUtils

@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun GroupDetailsScreen(
    navController: NavController,
    groupId: Int
) {
    val context = LocalContext.current
    val myApplication = context.applicationContext as MyApplication
    val groupViewModel: GroupViewModel = viewModel(factory = myApplication.viewModelFactory)
    val userViewModel: UserViewModel = viewModel(factory = myApplication.viewModelFactory)

    val groupDetailsState by groupViewModel.groupDetailsState.collectAsState()

    var showAddMembersBottomSheet by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { groupViewModel.uploadGroupImage(groupId, it) }
    }

    LaunchedEffect(groupId) {
        groupViewModel.loadGroupDetails(groupId)
    }

    val refreshing = groupDetailsState.isLoading
    val pullRefreshState = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = { groupViewModel.loadGroupDetails(groupId) }
    )

    GlobalTheme {
        Scaffold(
            topBar = {
                GlobalTopAppBar(title = { Text(groupDetailsState.group?.name ?: "Group Details") })
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
                    .pullRefresh(pullRefreshState)
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
                                    viewModel = groupViewModel
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
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
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
                                    onBalancesClick = { navController.navigate("groupBalances/$groupId") }
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
                                PaymentItem(
                                    payment = payment,
                                    context = context,
                                    onClick = { navController.navigate("paymentDetails/$groupId/${payment.id}") }
                                )
                            }
                        }
                    }
                }
                PullRefreshIndicator(
                    refreshing = refreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                )
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
    }
}

@Composable
fun GroupImageSection(
    groupImage: String?,
    uploadStatus: GroupViewModel.UploadStatus,
    imageLoadingState: GroupViewModel.ImageLoadingState,
    onImageClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupViewModel // Add ViewModel parameter
) {
    val context = LocalContext.current


    val imageSource = remember(groupImage) {
        when {
            groupImage == null -> null
            groupImage.startsWith("/") -> "file://$groupImage" // Local path
            else -> ImageUtils.getFullImageUrl(groupImage) // Fallback to server URL
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
            .clickable(onClick = onImageClick),
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
                            .clickable { viewModel.reloadGroupImage() } // Add retry functionality
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
                is GroupViewModel.ImageLoadingState.Success,
                is GroupViewModel.ImageLoadingState.Idle -> {
                    if (imageSource != null) {
                        AsyncImage(
                            model = imageSource,
                            contentDescription = "Group Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // No image placeholder
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
}

@Composable
fun GroupMembersSection(
    groupMembers: List<GroupMember>,
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
    onBalancesClick: () -> Unit
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
    }
}