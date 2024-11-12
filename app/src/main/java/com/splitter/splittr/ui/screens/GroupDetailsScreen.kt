package com.splitter.splittr.ui.screens

import PaymentItem
import android.annotation.SuppressLint
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.rememberImagePainter
import coil.request.ImageRequest
import com.splitter.splitter.R
import com.splitter.splittr.MyApplication
import com.splitter.splittr.ui.components.AddMembersDialog
import com.splitter.splittr.ui.components.GlobalFAB
import com.splitter.splittr.ui.components.GlobalTopAppBar
import com.splitter.splittr.data.model.GroupMember
import com.splitter.splittr.ui.theme.GlobalTheme
import com.splitter.splittr.ui.viewmodels.GroupViewModel
import com.splitter.splittr.ui.viewmodels.UserViewModel
import com.splitter.splittr.utils.ImageUtils
import kotlinx.coroutines.Dispatchers

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
    val sortedPayments by groupViewModel.sortedPayments.collectAsState()

    var showAddMembersDialog by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { groupViewModel.uploadGroupImage(groupId, it) }
    }

    LaunchedEffect(groupId) {
        groupViewModel.loadGroupDetails(groupId)
    }

    val refreshing by derivedStateOf { groupDetailsState.isLoading }
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
                                    onAddMembersClick = { showAddMembersDialog = true },
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
                            items(sortedPayments) { payment ->
                                PaymentItem(
                                    payment = payment,
                                    onClick = { navController.navigate("paymentDetails/$groupId/${payment.id}") }
                                )
                            }
                        }
                    }
                }
                PullRefreshIndicator(
                    refreshing = refreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }

        if (showAddMembersDialog) {
            AddMembersDialog(
                onDismissRequest = { showAddMembersDialog = false },
                onCreateInviteLinkClick = { /* Handle create invite link */ },
                onInviteMembersClick = { navController.navigate("inviteMembers/$groupId") }
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

//    // Remember the URL construction
//    val imageUrl = remember(groupImage) {
//        val url = ImageUtils.getFullImageUrl(groupImage)
//        Log.d("GroupImageSection", "Constructed image URL: $url from path: $groupImage")
//        url
//    }

    val imageSource = remember(groupImage) {
        if (groupImage != null && ImageUtils.imageExistsLocally(context, groupImage)) {
            "file://${ImageUtils.getLocalImagePath(context, groupImage)}"
        } else {
            ImageUtils.getFullImageUrl(groupImage)
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