package com.helgolabs.trego.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.dataClasses.UserGroupListItem
import com.helgolabs.trego.ui.components.GlobalFAB
import com.helgolabs.trego.ui.components.GlobalTopAppBar
import com.helgolabs.trego.ui.theme.GlobalTheme
import com.helgolabs.trego.ui.viewmodels.GroupViewModel
import com.helgolabs.trego.utils.FormattingUtils.formatAsCurrency
import com.helgolabs.trego.utils.ImageUtils
import com.helgolabs.trego.utils.PlaceholderImageGenerator
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlin.math.abs

@Composable
fun UserGroupsScreen(navController: NavController) {
    val context = LocalContext.current
    val myApplication = context.applicationContext as MyApplication
    val groupViewModel: GroupViewModel = viewModel(factory = myApplication.viewModelFactory)

    // Add state for archived groups expansion
    var showArchivedGroups by remember { mutableStateOf(false) }

    val userId = getUserIdFromPreferences(context) ?: 0
    val groupItems by groupViewModel.userGroupItems.collectAsStateWithLifecycle()
    val archivedGroupItems by groupViewModel.archivedGroupItems.collectAsStateWithLifecycle()
    val loading by groupViewModel.loading.collectAsStateWithLifecycle()
    val error by groupViewModel.error.collectAsStateWithLifecycle()
    val imageUpdateEvent by groupViewModel.imageUpdateEvent.collectAsStateWithLifecycle()

    // Refresh counter for forcing updates
    var refreshCounter by remember { mutableStateOf(0) }

    // Force a refresh when screen appears
    DisposableEffect(Unit) {
        // Force refresh when screen is shown
        groupViewModel.loadUserGroupsList(userId, true)
        onDispose {}
    }

    // Add this to preload color schemes when the list is visible
    LaunchedEffect(Unit) {
        // First load balances for all groups
        groupViewModel.loadUserGroupsList(userId, true)
        // Then preload color schemes
        groupViewModel.preloadGroupColorSchemes(context)
    }

    // This is important - log the refresh counter to check if it's changing
    LaunchedEffect(refreshCounter) {
        Log.d("UserGroupsScreen", "refreshCounter updated to: $refreshCounter")
    }

    // Refresh when we receive updates from elsewhere
    LaunchedEffect(imageUpdateEvent) {
        if (imageUpdateEvent > 0) {
            refreshCounter++
            groupViewModel.loadUserGroupsList(userId, true)
        }
    }

    val archiveState by groupViewModel.archiveGroupState.collectAsState()
    val restoreState by groupViewModel.restoreGroupState.collectAsState()

    // Reset archive/restore state when navigating to a group
    LaunchedEffect(archiveState, restoreState) {
        if (archiveState is GroupViewModel.ArchiveGroupState.Success) {
            // Reset the archive state after showing toast
            groupViewModel.resetArchiveGroupState()
        }

        if (restoreState is GroupViewModel.RestoreGroupState.Success) {
            // Reset the restore state after showing toast
            groupViewModel.resetRestoreGroupState()
        }
    }

    GlobalTheme {
        Scaffold(
            topBar = {
                GlobalTopAppBar(
                    title = {
                        Text(
                            "Your Groups",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                getUserIdFromPreferences(context)?.let { userId ->
                                    navController.navigate("profile/$userId")
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Profile"
                            )
                        }
                })
            },
            floatingActionButton = {
                GlobalFAB(
                    onClick = { navController.navigate("addGroup") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = "Add") },
                    text = "Add Group"
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surface),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
            ){
                // Active Groups section
                items(groupItems) { groupItem ->
                    GroupListItem(
                        groupItem = groupItem,
                        navController = navController,
                        refreshCounter = refreshCounter
                    )
                }

                // Archived Groups section
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showArchivedGroups = !showArchivedGroups }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (showArchivedGroups)
                                Icons.Default.KeyboardArrowDown else
                                Icons.Default.KeyboardArrowRight,
                            contentDescription = "Toggle archived groups"
                        )
                        Text(
                            "Archived Groups (${archivedGroupItems.size})",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                // Show archived groups if expanded
                if (showArchivedGroups) {
                    if (archivedGroupItems.isEmpty()) {
                        item {
                            Text(
                                "No archived groups",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 32.dp)
                            )
                        }
                    } else {
                        items(archivedGroupItems) { groupItem ->
                            GroupListItem(
                                groupItem = groupItem,
                                navController = navController,
                                isArchived = true
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupListItem(
    groupItem: UserGroupListItem,
    navController: NavController,
    isArchived: Boolean = false,
    refreshCounter: Int = 0
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { navController.navigate("groupDetails/${groupItem.id}") }
            .alpha(if (isArchived) 0.7f else 1f),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .padding(4.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Generate a unique key for this image to prevent caching issues
            val imageKey = "${groupItem.id}_${groupItem.groupImg}_$refreshCounter"


            val context = LocalContext.current
            val isPlaceholder = PlaceholderImageGenerator.isPlaceholderImage(groupItem.groupImg)

            // Determine the correct image source
            val imageSource = when {
                groupItem.groupImg == null -> null
                isPlaceholder -> {
                    // Always use PlaceholderImageGenerator to get the correct local path
                    val localPath = PlaceholderImageGenerator.getImageForPath(context, groupItem.groupImg)
                    "file://$localPath"
                }
                groupItem.groupImg.startsWith("/") -> "file://${groupItem.groupImg}"
                else -> ImageUtils.getFullImageUrl(groupItem.groupImg)
            }

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageSource)
                    .diskCacheKey(imageKey)  // Force unique cache key
                    .memoryCacheKey(imageKey)  // Force unique memory key
                    // Disable caching to ensure fresh image loads
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    groupItem.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Display user balance or no expenses message
                if (groupItem.userBalance == null) {
                    // When userBalance is null, it means there are no expenses yet
                    Text(
                        text = "No expenses in this group yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                } else {
                    // Handle the case when userBalance exists
                    groupItem.userBalance.let { userBalance ->
                        if (userBalance.balances.isEmpty() || userBalance.balances.all { abs(it.value) < 0.01 }) {
                            // When user has no balances or all balances are effectively zero
                            Text(
                                text = "You're even in this group",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        } else {
                            // Get the first currency and balance
                            val firstEntry = userBalance.balances.entries.firstOrNull()

                            firstEntry?.let { (currency, amount) ->
                                if (abs(amount) < 0.01) {
                                    // This specific currency is even
                                    Text(
                                        text = "You're even in this group",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (amount < 0) {
                                                "You owe: ${(-amount).formatAsCurrency(currency)}"
                                            } else {
                                                "You are owed: ${amount.formatAsCurrency(currency)}"
                                            },
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (amount < 0)
                                                MaterialTheme.colorScheme.error
                                            else
                                                MaterialTheme.colorScheme.primary
                                        )

                                        // Add indicator for multiple currencies
                                        if (userBalance.balances.size > 1) {
                                            Text(
                                                text = "& ...",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(start = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}