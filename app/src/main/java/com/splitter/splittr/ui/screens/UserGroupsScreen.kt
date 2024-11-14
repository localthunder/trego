package com.splitter.splittr.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    val userId = getUserIdFromPreferences(context)
    val groupItems by groupViewModel.userGroupItems.collectAsStateWithLifecycle()
    val loading by groupViewModel.loading.collectAsStateWithLifecycle()
    val error by groupViewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(userId) {
        if (userId != null && userId > 0) {
            groupViewModel.loadUserGroupsList(userId)
        }
    }

    GlobalTheme {
        Scaffold(
            topBar = {
                GlobalTopAppBar(title = { Text("Your Groups", style = MaterialTheme.typography.headlineSmall) })
            },
            floatingActionButton = {
                GlobalFAB(
                    onClick = { navController.navigate("addGroup") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = "Add") },
                    text = "Add Group"
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when {
                    loading -> CircularProgressIndicator()
                    error != null -> Text("Error: $error", color = MaterialTheme.colorScheme.error)
                    groupItems.isEmpty() -> Text("You are not part of any groups.")
                    else -> {
                        LazyColumn {
                            items(groupItems) { groupItem ->
                                GroupListItem(groupItem, navController)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupListItem(groupItem: UserGroupListItem, navController: NavController) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { navController.navigate("groupDetails/${groupItem.id}") }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageUtils.getFullImageUrl(groupItem.groupImg),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .padding(end = 8.dp)
            )
            Column {
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
        }
    }
}