package com.splitter.splittr.ui.screens

import PaymentItem
import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberImagePainter
import com.splitter.splittr.MyApplication
import com.splitter.splittr.ui.components.AddMembersDialog
import com.splitter.splittr.ui.components.GlobalFAB
import com.splitter.splittr.ui.components.GlobalTopAppBar
import com.splitter.splittr.model.Group
import com.splitter.splittr.model.GroupMember
import com.splitter.splittr.model.Payment
import com.splitter.splittr.ui.theme.GlobalTheme
import com.splitter.splittr.ui.viewmodels.GroupViewModel
import com.splitter.splittr.ui.viewmodels.PaymentsViewModel
import com.splitter.splittr.ui.viewmodels.UserViewModel
import com.splitter.splittr.utils.ImageUtils
import kotlinx.coroutines.launch

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
                                    onImageClick = { launcher.launch("image/*") }
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
    onImageClick: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(128.dp)
            .clickable(onClick = onImageClick),
        contentAlignment = Alignment.Center
    ) {
        when (uploadStatus) {
            is GroupViewModel.UploadStatus.Loading -> {
                CircularProgressIndicator()
            }
            is GroupViewModel.UploadStatus.Error -> {
                Text("Error: ${uploadStatus.message}", style = MaterialTheme.typography.bodyMedium)
            }
            is GroupViewModel.UploadStatus.Success -> {
                val imagePath = ImageUtils.getImageFile(context, uploadStatus.imagePath ?: groupImage ?: "").absolutePath
                Image(
                    painter = rememberImagePainter(imagePath),
                    contentDescription = "Group Image",
                    modifier = Modifier.fillMaxSize()
                )
            }
            is GroupViewModel.UploadStatus.Idle -> {
                if (groupImage != null) {
                    val imagePath = ImageUtils.getImageFile(context, groupImage).absolutePath
                    Image(
                        painter = rememberImagePainter(imagePath),
                        contentDescription = "Group Image",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("Upload Image", style = MaterialTheme.typography.bodyLarge)
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