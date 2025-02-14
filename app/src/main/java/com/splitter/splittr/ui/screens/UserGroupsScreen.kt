package com.splitter.splittr.ui.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.splitter.splittr.MyApplication
import com.splitter.splittr.data.local.dataClasses.UserGroupListItem
import com.splitter.splittr.ui.components.GlobalFAB
import com.splitter.splittr.ui.components.GlobalTopAppBar
import com.splitter.splittr.ui.theme.GlobalTheme
import com.splitter.splittr.ui.viewmodels.GroupViewModel
import com.splitter.splittr.utils.ImageUtils
import com.splitter.splittr.utils.getUserIdFromPreferences

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

    LaunchedEffect(Unit) {
        Log.d("UserGroupsScreen", "LaunchedEffect triggered")
        if (userId > 0) {
            Log.d("UserGroupsScreen", "Loading groups for user: $userId")
            groupViewModel.loadUserGroupsList(userId)
        } else {
            Log.e("UserGroupsScreen", "Invalid userId: $userId")
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
            ){
                // Active Groups section
                item {
                    Text(
                        "Active Groups",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(groupItems) { groupItem ->
                    GroupListItem(groupItem, navController)
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
    isArchived: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { navController.navigate("groupDetails/${groupItem.id}") }
            .alpha(if (isArchived) 0.7f else 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = when {
                    groupItem.groupImg?.startsWith("/") == true -> "file://${groupItem.groupImg}"
                    else -> ImageUtils.getFullImageUrl(groupItem.groupImg)
                },
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .padding(end = 8.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    groupItem.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                groupItem.description?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isArchived) {
                Text(
                    "Archived",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}