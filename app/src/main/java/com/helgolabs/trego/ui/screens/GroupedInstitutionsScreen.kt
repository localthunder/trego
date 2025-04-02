package com.helgolabs.trego.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.model.Institution
import com.helgolabs.trego.ui.viewmodels.InstitutionViewModel
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import com.helgolabs.trego.data.local.dataClasses.InstitutionGroup
import com.helgolabs.trego.data.local.dataClasses.RequisitionRequest
import com.helgolabs.trego.ui.components.GlobalFAB
import com.helgolabs.trego.ui.components.GlobalTopAppBar
import com.helgolabs.trego.ui.components.SectionHeader
import com.helgolabs.trego.ui.theme.GlobalTheme
import com.helgolabs.trego.ui.viewmodels.PaymentsViewModel.PaymentAction
import isLogoSaved
import java.io.File

@Composable
fun GroupedInstitutionsScreen(
    navController: NavController,
    context: Context,
    returnRoute: String? = null
) {
    val myApplication = context.applicationContext as MyApplication
    val institutionViewModel: InstitutionViewModel = viewModel(factory = myApplication.viewModelFactory)
    val institutions by institutionViewModel.institutions.collectAsStateWithLifecycle()
    val loading by institutionViewModel.loading.collectAsStateWithLifecycle()
    val error by institutionViewModel.error.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    val groupedInstitutions = remember(institutions, searchQuery) {
        val filteredInstitutions = institutions.filter {
            it.name.contains(searchQuery, ignoreCase = true)
        }

        // List of popular UK banks (you can adjust this list as needed)
        val popularBanks = setOf(
            "Barclays", "HSBC", "Lloyds", "NatWest", "Santander",
            "Monzo", "Revolut", "Starling", "Metro Bank", "First Direct",
            "Nationwide", "Halifax", "Royal Bank of Scotland", "TSB Bank",
            "Chase", "Barclaycard", "Bank of Scotland", "American Express"
        )

        // Create a mapping to identify main institution names (stripped of qualifiers)
        val institutionGroups = filteredInstitutions.groupBy { institution ->
            // Extract the main bank name by removing qualifiers like "Personal", "Business", etc.
            val baseName = institution.name
                .replace(Regex("(?i)\\s+(Personal|Business|Corporate|Commercial|Retail|Private|Online|Bankline|ClearSpend|net|Kinetic|Wealth|International)$"), "")
                .replace(Regex("(?i)\\s+-\\s+sort\\s+code\\s+starts\\s+with.*$"), "")  // Handle Virgin Money variants
                .trim()

            // Special handling for specific bank groups
            when {
                institution.name.contains("HSBC", ignoreCase = true) -> "HSBC"
                institution.name.contains("Virgin Money", ignoreCase = true) -> "Virgin Money"
                institution.name.contains("Allied Irish Banks", ignoreCase = true) -> "Allied Irish Banks"
                institution.name.contains("Barclaycard", ignoreCase = true) -> "Barclaycard"
                else -> baseName
            }
        }

        // Convert to InstitutionGroup objects
        institutionGroups.map { (mainName, groupInstitutions) ->
            // Determine if this is a popular bank
            val isPopular = popularBanks.any { popularBank ->
                mainName.contains(popularBank, ignoreCase = true)
            }

            // Choose the "main" institution from the group - prioritize personal accounts
            val mainInst = groupInstitutions.find { it.name.contains("Personal", ignoreCase = true) }
                ?: groupInstitutions.first()

            InstitutionGroup(
                mainName = mainName,
                institutions = groupInstitutions,
                mainInstitution = mainInst,
                isPopular = isPopular
            )
        }.sortedWith(
            compareByDescending<InstitutionGroup> { it.isPopular }
                .thenBy { it.mainName }
        )
    }

    GlobalTheme {
        Scaffold(
            topBar = {
                GlobalTopAppBar(
                    title = { Text("Available Banks") },
                )
            },
            content = { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    if (loading) {
                        CircularProgressIndicator()
                    } else if (error != null) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                    } else {

                        // Search Bar
                        TextField(
                            value = searchQuery,
                            onValueChange = { newQuery -> searchQuery = newQuery },
                            placeholder = { Text("Search banks") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Section headers
                        if (groupedInstitutions.any { it.isPopular }) {
                            LazyColumn {
                                // Popular banks section
                                item {
                                    SectionHeader(title = "Popular Banks")
                                }

                                // List popular banks
                                items(groupedInstitutions.filter { it.isPopular }) { group ->
                                    InstitutionGroupItem(
                                        institutionGroup = group,
                                        navController = navController,
                                        returnRoute = returnRoute
                                    )
                                }

                                // Other banks section (if there are any non-popular banks)
                                if (groupedInstitutions.any { !it.isPopular }) {
                                    item {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        SectionHeader(title = "Other Banks")
                                    }

                                    // List other banks
                                    items(groupedInstitutions.filter { !it.isPopular }) { group ->
                                        InstitutionGroupItem(
                                            institutionGroup = group,
                                            navController = navController,
                                            returnRoute = returnRoute
                                        )
                                    }
                                }
                            }
                        } else {
                            // If no popular banks match the search, just show all results
                            LazyColumn {
                                items(groupedInstitutions) { group ->
                                    InstitutionGroupItem(
                                        institutionGroup = group,
                                        navController = navController,
                                        returnRoute = returnRoute
                                    )
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
fun InstitutionGroupItem(
    institutionGroup: InstitutionGroup,
    navController: NavController,
    returnRoute: String?
) {
    val TAG = "InstitutionGroupItem"
    val context = LocalContext.current
    val myApplication = context.applicationContext as MyApplication
    val institutionViewModel: InstitutionViewModel = viewModel(factory = myApplication.viewModelFactory)

    // State to trigger requisition creation for single-institution groups
    var shouldCreateRequisition by remember { mutableStateOf(false) }

    // State for logo and colors
    var logoFile by remember { mutableStateOf<File?>(null) }
    var dominantColors by remember { mutableStateOf(listOf<Color>()) }
    var logoExists by remember { mutableStateOf(false) }

    // Use the main institution's ID for logo
    val institutionId = institutionGroup.mainInstitution.id

    // First check local storage, then download if needed
    LaunchedEffect(institutionId) {
        if (institutionId != null) {
            val logoFilename = "${institutionId}.png"

            // Check if logo exists locally
            val file = File(context.filesDir, logoFilename)
            logoExists = isLogoSaved(context, institutionId)

            if (logoExists) {
                // Logo exists locally, load it
                logoFile = file

                try {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        // Extract colors (using existing utility from your code)
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
                        logoExists = false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading local logo", e)
                    logoExists = false
                }
            }

            // If logo doesn't exist, we'll use a placeholder
            if (!logoExists) {
                dominantColors = listOf(Color.Gray, Color.LightGray)
            }
        }
    }

    // Requisition handling for single institutions
    LaunchedEffect(shouldCreateRequisition) {
        if (shouldCreateRequisition && institutionGroup.institutions.size == 1) {
            try {
                val institution = institutionGroup.institutions.first()
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable {
                // If there's only one institution in the group, create requisition directly
                if (institutionGroup.institutions.size == 1) {
                    shouldCreateRequisition = true
                } else {
                    // Navigate to institution selection screen
                    navController.navigate("institution_selection/${institutionGroup.mainName}?returnRoute=${returnRoute ?: "home"}")
                }
            },
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
                        contentDescription = "Bank Logo",
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
                            text = institutionGroup.mainName.firstOrNull()?.toString() ?: "?",
                            color = Color.White
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = institutionGroup.mainName,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (institutionGroup.institutions.size > 1) {
                    Text(
                        text = "${institutionGroup.institutions.size} options",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}