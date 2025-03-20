package com.helgolabs.trego.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.dataClasses.RequisitionRequest
import com.helgolabs.trego.data.model.Institution
import com.helgolabs.trego.ui.viewmodels.InstitutionViewModel
import downloadAndSaveImage
import isLogoSaved
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstitutionSelectionScreen(
    navController: NavController,
    context: Context,
    bankName: String,
    returnRoute: String?
) {
    val myApplication = context.applicationContext as MyApplication
    val institutionViewModel: InstitutionViewModel = viewModel(factory = myApplication.viewModelFactory)
    val institutions by institutionViewModel.institutions.collectAsStateWithLifecycle()

    // Filter institutions by the bank name
    val filteredInstitutions = remember(institutions, bankName) {
        institutions.filter { institution ->
            // Match institutions with the provided bank name
            if (bankName == "HSBC" && institution.name.contains("HSBC", ignoreCase = true)) {
                true
            } else if (bankName == "Virgin Money" && institution.name.contains("Virgin Money", ignoreCase = true)) {
                true
            } else if (bankName == "Allied Irish Banks" && institution.name.contains("Allied Irish Banks", ignoreCase = true)) {
                true
            } else if (bankName == "Barclaycard" && institution.name.contains("Barclaycard", ignoreCase = true)) {
                true
            } else {
                val normalizedBankName = institution.name
                    .replace(Regex("(?i)\\s+(Personal|Business|Corporate|Commercial|Retail|Private|Online|Bankline|ClearSpend|net|Kinetic|Wealth)$"), "")
                    .replace(Regex("(?i)\\s+-\\s+sort\\s+code\\s+starts\\s+with.*$"), "")
                    .trim()

                normalizedBankName.equals(bankName, ignoreCase = true)
            }
        }.sortedBy { it.name } // Sort alphabetically
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = bankName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
        ) {
            Text(
                text = "Select Account Type",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn {
                items(filteredInstitutions) { institution ->
                    InstitutionItem(
                        institution = institution,
                        returnRoute = returnRoute
                    )
                }
            }
        }
    }
}

// Reuse the existing InstitutionItem with minor modifications
@Composable
fun InstitutionItem(
    institution: Institution,
    returnRoute: String?
) {
    val TAG = "InstitutionItem"
    val context = LocalContext.current
    val myApplication = context.applicationContext as MyApplication
    val institutionViewModel: InstitutionViewModel = viewModel(factory = myApplication.viewModelFactory)
    var shouldCreateRequisition by remember { mutableStateOf(false) }

    // State for logo and colors
    var logoFile by remember { mutableStateOf<File?>(null) }
    var dominantColors by remember { mutableStateOf(listOf<Color>()) }
    var logoExists by remember { mutableStateOf(false) }

    // First check local storage, then download if needed
    LaunchedEffect(institution.id) {
        if (institution.id != null) {
            val logoFilename = "${institution.id}.png"

            // Check if logo exists locally
            val file = File(context.filesDir, logoFilename)
            logoExists = isLogoSaved(context, institution.id)
            Log.d(TAG, "Local logo check: exists=${file.exists()}, size=${file.length()} bytes for ${institution.id}")

            if (logoExists) {
                // Logo exists locally, load it
                logoFile = file
                Log.d(TAG, "Using local logo from: ${file.absolutePath}")

                try {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        Log.d(TAG, "Loaded local bitmap: ${bitmap.width}x${bitmap.height}")

                        // Extract colors
                        try {
                            val colors = com.helgolabs.trego.utils.GradientBorderUtils.getDominantColors(bitmap).map { Color(it) }
                            dominantColors = if (colors.size < 2) {
                                val averageColor = Color(com.helgolabs.trego.utils.GradientBorderUtils.getAverageColor(bitmap))
                                listOf(averageColor, averageColor.copy(alpha = 0.7f))
                            } else {
                                colors
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error extracting colors", e)
                            dominantColors = listOf(Color.Gray, Color.LightGray)
                        }
                    } else {
                        Log.e(TAG, "Failed to decode local bitmap, file might be corrupted")
                        // If we can't decode the bitmap, the file might be corrupted
                        logoExists = false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading local logo", e)
                    logoExists = false
                }
            }

            // If no valid local logo, try to download
            if (!logoExists) {
                try {
                    Log.d(TAG, "No valid local logo, fetching URL for ${institution.id}")
                    val logoUrl = institutionViewModel.getInstitutionLogoUrl(institution.id)

                    if (logoUrl != null) {
                        Log.d(TAG, "Got URL: $logoUrl for ${institution.id}")
                        try {
                            // Try to download the logo
                            val downloadedFile = downloadAndSaveImage(context, logoUrl, logoFilename)
                            if (downloadedFile != null) {
                                Log.d(TAG, "Downloaded logo to: ${downloadedFile.absolutePath}")
                                logoFile = downloadedFile
                                logoExists = true

                                // Extract colors from the downloaded image
                                try {
                                    val bitmap = BitmapFactory.decodeFile(downloadedFile.absolutePath)
                                    if (bitmap != null) {
                                        val colors = com.helgolabs.trego.utils.GradientBorderUtils.getDominantColors(bitmap).map { Color(it) }
                                        dominantColors = if (colors.size < 2) {
                                            val averageColor = Color(com.helgolabs.trego.utils.GradientBorderUtils.getAverageColor(bitmap))
                                            listOf(averageColor, averageColor.copy(alpha = 0.7f))
                                        } else {
                                            colors
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error extracting colors from downloaded image", e)
                                    dominantColors = listOf(Color.Gray, Color.LightGray)
                                }
                            } else {
                                Log.e(TAG, "Failed to download logo")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error downloading logo", e)
                        }
                    } else {
                        Log.e(TAG, "No logo URL available")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting logo URL", e)
                }
            }
        }
    }

    // Requisition handling code
    LaunchedEffect(shouldCreateRequisition) {
        if (shouldCreateRequisition) {
            try {
                val encodedReturnRoute = Uri.encode(returnRoute ?: "home")
                val baseUrl = "trego://bankaccounts"

                val requisitionRequest = RequisitionRequest(
                    baseUrl = baseUrl,
                    institutionId = institution.id,
                    userLanguage = "EN",
                    returnRoute = encodedReturnRoute
                )

                val result = institutionViewModel.createRequisition(requisitionRequest)
                result.onSuccess { response ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(response.link))
                    context.startActivity(intent)
                }.onFailure { error ->
                    Toast.makeText(context, "Failed to create requisition: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Unexpected error: ${e.message}", Toast.LENGTH_SHORT).show()
            }

            shouldCreateRequisition = false
        }
    }

    // Create image bitmap from file
    val logoImage = remember(logoFile, logoExists) {
        if (logoExists && logoFile != null) {
            try {
                val bitmap = BitmapFactory.decodeFile(logoFile?.absolutePath)
                bitmap?.asImageBitmap()
            } catch (e: Exception) {
                Log.e(TAG, "Error creating bitmap for display", e)
                null
            }
        } else {
            null
        }
    }

    // Create card with logo
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { shouldCreateRequisition = true },
        border = BorderStroke(2.dp, Brush.linearGradient(dominantColors.ifEmpty {
            listOf(Color.Gray, Color.LightGray)
        }))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (logoImage != null) {
                    Image(
                        bitmap = logoImage,
                        contentDescription = "Institution Logo",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                } else {
                    // Placeholder with institution initial
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.LightGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = institution.name.firstOrNull()?.toString() ?: "?",
                            color = Color.White
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))

            // Display just the account type (Personal, Business, etc.) or distinctive qualifier
            val displayName = remember(institution.name) {
                // For Virgin Money with sort code details, keep that part
                if (institution.name.contains("sort code starts with", ignoreCase = true)) {
                    institution.name.substringAfter("Virgin Money").trim()
                }
                // For Allied Irish Banks (NI), keep the NI part
                else if (institution.name.contains("Allied Irish Banks (NI)", ignoreCase = true)) {
                    "(NI)"
                }
                // For Allied Irish Banks variants with suffixes
                else if (institution.name.contains("Allied Irish Banks", ignoreCase = true) &&
                    !institution.name.equals("Allied Irish Banks", ignoreCase = true)) {
                    val regex = Regex("(?i)(Personal|Business|Corporate|Commercial|Retail|Private|Online|Bankline|ClearSpend|Kinetic)$")
                    val match = regex.find(institution.name)
                    if (match != null) {
                        match.value.trim()
                    } else {
                        institution.name.substringAfter("Allied Irish Banks").trim()
                    }
                }
                // For Barclaycard variants
                else if (institution.name.contains("Barclaycard", ignoreCase = true) &&
                    !institution.name.equals("Barclaycard", ignoreCase = true)) {
                    if (institution.name.contains("Commercial Payments", ignoreCase = true)) {
                        "Commercial Payments"
                    } else if (institution.name.contains("UK", ignoreCase = true)) {
                        "UK"
                    } else {
                        institution.name.substringAfter("Barclaycard").trim()
                    }
                }
                // For HSBCnet or other HSBC variants, extract the specific name
                else if (institution.name.contains("HSBC", ignoreCase = true) &&
                    !institution.name.equals("HSBC", ignoreCase = true)) {
                    if (institution.name.contains("net", ignoreCase = true)) {
                        "HSBCnet"
                    } else {
                        val regex = Regex("(?i)(Personal|Business|Corporate|Commercial|Retail|Private|Online|Bankline|ClearSpend|Kinetic)$")
                        val match = regex.find(institution.name)
                        if (match != null) {
                            "HSBC ${match.value.trim()}"
                        } else {
                            institution.name
                        }
                    }
                }
                // Normal handling for most institutions
                else {
                    val regex = Regex("(?i)(Personal|Business|Corporate|Commercial|Retail|Private|Online|Bankline|ClearSpend)$")
                    val match = regex.find(institution.name)
                    if (match != null) {
                        match.value.trim()
                    } else {
                        institution.name
                    }
                }
            }

            Text(displayName)
        }
    }
}