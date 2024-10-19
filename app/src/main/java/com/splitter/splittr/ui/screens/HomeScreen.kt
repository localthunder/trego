package com.splitter.splittr.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.splitter.splittr.ui.components.GlobalTopAppBar
import com.splitter.splittr.utils.getUserIdFromPreferences

@Composable
fun HomeScreen(navController: NavController, context: Context) {
    val userId = getUserIdFromPreferences(context)

    Scaffold(
        topBar = {
            GlobalTopAppBar(
                title = { Text("Home") }
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
            }
        }
    )
}
