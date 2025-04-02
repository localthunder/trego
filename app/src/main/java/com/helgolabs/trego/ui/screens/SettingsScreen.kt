package com.helgolabs.trego.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.helgolabs.trego.R
import com.helgolabs.trego.ui.components.GlobalTopAppBar
import java.util.Locale

@Composable
fun SettingsScreen(
    navController: NavController,
    userId: Int? = null
) {
    Scaffold(
        topBar = {
            GlobalTopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Personal details button
            SettingsButton(
                icon = Icons.Default.AccountCircle,
                title = "Personal details",
                onClick = {
                    if (userId != null) {
                        navController.navigate("personalDetails/$userId")
                    } else {
                        navController.navigate("personalDetails")
                    }
                }
            )

            // Connected accounts button
            SettingsButton(
                icon = Icons.Default.AccountBalance,
                title = "Connected accounts",
                onClick = {
                    navController.navigate("connectedAccounts")
                }
            )

            // Notifications button
            SettingsButton(
                icon = Icons.Default.Notifications,
                title = "Notifications",
                onClick = {
                    navController.navigate("notifications")
                }
            )

            // Appearance button
            SettingsButton(
                icon = Icons.Default.LightMode,
                title = "Appearance",
                onClick = {
                    navController.navigate("appearance")
                }
            )
        }
    }
}

@Composable
fun SettingsButton(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.Default.ArrowForwardIos,
                contentDescription = "Navigate",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}