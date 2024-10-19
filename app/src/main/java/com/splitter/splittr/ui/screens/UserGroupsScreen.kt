package com.splitter.splittr.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberImagePainter
import com.splitter.splittr.MyApplication
import com.splitter.splittr.ui.components.GlobalFAB
import com.splitter.splittr.ui.components.GlobalTopAppBar
import com.splitter.splittr.model.Group
import com.splitter.splittr.ui.theme.GlobalTheme
import com.splitter.splittr.ui.viewmodels.GroupViewModel
import com.splitter.splittr.utils.getUserIdFromPreferences

@Composable
fun UserGroupsScreen(navController: NavController) {
    val context = LocalContext.current
    val myApplication = context.applicationContext as MyApplication
    val groupViewModel: GroupViewModel = viewModel(factory = myApplication.viewModelFactory)

    val userId = getUserIdFromPreferences(context)
    val groups by groupViewModel.userGroups.collectAsStateWithLifecycle()
    val loading by groupViewModel.loading.collectAsStateWithLifecycle()
    val error by groupViewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(userId) {
        if (userId != null && userId > 0) {
            groupViewModel.loadUserGroups(userId)
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
            },
            content = { padding ->
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
                        groups.isEmpty() -> Text("You are not part of any groups.")
                        else -> {
                            LazyColumn {
                                items(groups) { group ->
                                    GroupItem(group, navController)
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun GroupItem(group: Group, navController: NavController) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { navController.navigate("groupDetails/${group.id}") }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberImagePainter(group.groupImg),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .padding(end = 8.dp)
            )
            Column {
                Text(group.name, fontSize = 20.sp, color = Color.Black)
                group.description?.let {
                    Text(it, fontSize = 16.sp, color = Color.Gray)
                }
            }
        }
    }
}