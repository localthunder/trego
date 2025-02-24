package com.helgolabs.trego.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun DeepLinkTestScreen(
    navController: NavController
) {
    val context = LocalContext.current
    var resultText by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Test basic deep link
        Button(
            onClick = {
                try {
                    Log.d("DeepLinkTest", "Launching basic test deep link")
                    val uri = Uri.parse("trego://test")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        `package` = context.packageName  // Add this line
                    }
                    resultText = "Launching: $uri"
                    Log.d("DeepLinkTest", "Intent created: $intent")
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e("DeepLinkTest", "Error launching test deep link", e)
                    resultText = "Error: ${e.message}"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Test Basic Deep Link")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Test bank account deep link
        Button(
            onClick = {
                try {
                    Log.d("DeepLinkTest", "Launching bank account test deep link")
                    val uri = Uri.parse("trego://bankaccounts?reference=test_ref&returnRoute=test")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        `package` = context.packageName  // Add this line
                    }
                    resultText = "Launching: $uri"
                    Log.d("DeepLinkTest", "Intent created: $intent")
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e("DeepLinkTest", "Error launching bank account deep link", e)
                    resultText = "Error: ${e.message}"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Test Bank Account Deep Link")
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Show result text if any
        resultText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (it.startsWith("Error"))
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }

        Button(
            onClick = { navController.navigate("home") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Return to Home")
        }
    }
}