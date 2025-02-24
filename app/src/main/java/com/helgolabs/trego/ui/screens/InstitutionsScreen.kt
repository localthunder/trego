package com.helgolabs.trego.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.local.dataClasses.RequisitionRequest
import com.helgolabs.trego.data.model.Institution
import com.helgolabs.trego.ui.viewmodels.InstitutionViewModel
import com.helgolabs.trego.utils.GradientBorderUtils
import downloadAndSaveImage
import isLogoSaved
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstitutionsScreen(
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

    // Filter institutions based on the search query
    val filteredInstitutions = institutions.filter {
        it.name.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        if (loading) {
            CircularProgressIndicator()
        } else if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
        } else {
            Text("Available Institutions", style = MaterialTheme.typography.displaySmall)
            Spacer(modifier = Modifier.height(16.dp))

            // Search Bar
            TextField(
                value = searchQuery,
                onValueChange = { newQuery -> searchQuery = newQuery },
                placeholder = { Text("Search institutions") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(16.dp))
            // Display filtered institutions
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

@Composable
fun InstitutionItem(
    institution: Institution,
    returnRoute: String?
) {
    val context = LocalContext.current
    val myApplication = context.applicationContext as MyApplication
    val institutionViewModel: InstitutionViewModel = viewModel(factory = myApplication.viewModelFactory)
    var logoFile by remember { mutableStateOf<File?>(null) }
    var dominantColors by remember { mutableStateOf(listOf<Color>()) }
    var shouldCreateRequisition by remember { mutableStateOf(false) }

    LaunchedEffect(institution.id) {
        if (institution.id != null) {
            val logoFilename = "${institution.id}.png"
            val logoSaved = isLogoSaved(context, institution.id)

            if (!logoSaved) {
                val logoUrl = institutionViewModel.getInstitutionLogoUrl(institution.id)
                logoUrl?.let {
                    val file = downloadAndSaveImage(context, it.toString(), logoFilename)
                    file?.let { savedFile ->
                        logoFile = savedFile
                        val bitmap = BitmapFactory.decodeFile(savedFile.path)
                        if (bitmap != null) {
                            dominantColors = GradientBorderUtils.getDominantColors(bitmap).map { Color(it) }
                            if (dominantColors.size < 2) {
                                val averageColor = Color(GradientBorderUtils.getAverageColor(bitmap))
                                dominantColors = listOf(averageColor, averageColor.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            } else {
                logoFile = File(context.filesDir, logoFilename)
                val bitmap = BitmapFactory.decodeFile(logoFile?.path)
                if (bitmap != null) {
                    dominantColors = GradientBorderUtils.getDominantColors(bitmap).map { Color(it) }
                    if (dominantColors.size < 2) {
                        val averageColor = Color(GradientBorderUtils.getAverageColor(bitmap))
                        dominantColors = listOf(averageColor, averageColor.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }

    LaunchedEffect(shouldCreateRequisition) {
        if (shouldCreateRequisition) {
            try {
                val encodedReturnRoute = Uri.encode(returnRoute ?: "home")
                // Make sure the return URL matches exactly what GoCardless will use
                val baseUrl = "trego://bankaccounts"

                Log.d("InstitutionItem", "Creating requisition with baseUrl: $baseUrl")
                Log.d("InstitutionItem", "Return route: $returnRoute")

                val requisitionRequest = RequisitionRequest(
                    baseUrl = baseUrl,
                    institutionId = institution.id,
                    userLanguage = "EN",
                    returnRoute = encodedReturnRoute
                )

                val result = institutionViewModel.createRequisition(requisitionRequest)
                result.onSuccess { response ->
                    Log.d("InstitutionItem", "Requisition created successfully: $response")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(response.link))
                    context.startActivity(intent)
                }.onFailure { error ->
                    Log.e("InstitutionItem", "Failed to create requisition", error)
                    Toast.makeText(context, "Failed to create requisition: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("InstitutionItem", "Error creating requisition", e)
                Toast.makeText(context, "Unexpected error: ${e.message}", Toast.LENGTH_SHORT).show()
            }

            shouldCreateRequisition = false
        }
    }

    val logoImage = logoFile?.let {
        BitmapFactory.decodeFile(it.path)
    }?.asImageBitmap()

    val borderSize = 2.dp
    val borderBrush = if (dominantColors.size >= 2) {
        Brush.linearGradient(dominantColors)
    } else {
        Brush.linearGradient(listOf(Color.Gray, Color.LightGray))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { shouldCreateRequisition = true },
        border = BorderStroke(borderSize, borderBrush)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
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
                Spacer(modifier = Modifier.size(40.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(institution.name)
        }
    }
}