package com.helgolabs.trego.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Logout
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.helgolabs.trego.ui.components.GlobalTopAppBar
import com.helgolabs.trego.ui.components.LogoutDialog
import com.helgolabs.trego.utils.getUserIdFromPreferences

@Composable
fun HomeScreen(navController: NavController, context: Context) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    val userId = getUserIdFromPreferences(context)

    Scaffold(
        topBar = {
            GlobalTopAppBar(
                title = { Text("Home") },
                actions = {
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Logout"
                        )
                    }
                }
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
                Text(text = "Welcome to the Home Screen!")
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    navController.navigate("institutions")
                }) {
                    Text("Institutions")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    userId?.let {
                        navController.navigate("transactions/$it")
                    }
                }) {
                    Text("Transactions")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    userId?.let {
                        navController.navigate("userGroups/$it")
                    }
                }) {
                    Text("Groups")
                }
                Button(onClick = {
                    navController.navigate("test")
                }) {
                    Text("Deeplink test screen")
                }
            }
        }
    )

    LogoutDialog(
        showDialog = showLogoutDialog,
        onDismiss = { showLogoutDialog = false },
        navController = navController
    )
}
