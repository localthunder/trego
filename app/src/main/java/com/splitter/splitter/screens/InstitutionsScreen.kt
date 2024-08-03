package com.splitter.splitter.screens

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.splitter.splitter.model.Institution
import com.splitter.splitter.data.network.ApiService
import com.splitter.splitter.data.network.RequisitionRequest
import com.splitter.splitter.data.network.RequisitionResponseWithRedirect
import com.splitter.splitter.data.network.RetrofitClient
import com.splitter.splitter.utils.GocardlessUtils.getInstitutionLogoUrl
import com.splitter.splitter.utils.GradientBorderUtils
import downloadAndSaveImage
import isLogoSaved
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstitutionsScreen(navController: NavController, context: Context, apiService: ApiService) {
    var institutions by remember { mutableStateOf(listOf<Institution>()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val apiService = RetrofitClient.getInstance(context).create(ApiService::class.java)
        apiService.getInstitutions("gb").enqueue(object : Callback<List<Institution>> {
            override fun onResponse(call: Call<List<Institution>>, response: Response<List<Institution>>) {
                if (response.isSuccessful) {
                    institutions = response.body() ?: listOf()
                } else {
                    Toast.makeText(context, "Failed to fetch institutions", Toast.LENGTH_SHORT).show()
                }
                loading = false
            }

            override fun onFailure(call: Call<List<Institution>>, t: Throwable) {
                Toast.makeText(context, "Error fetching institutions", Toast.LENGTH_SHORT).show()
                loading = false
            }
        })
    }

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
        } else {
            Text("Available Institutions", style = MaterialTheme.typography.displaySmall)
            Spacer(modifier = Modifier.height(16.dp))

            // Search Bar
            TextField(
                value = searchQuery,
                onValueChange = { newQuery -> searchQuery = newQuery },
                placeholder = { Text("Search institutions") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.textFieldColors(
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
                        context = context,
                        apiService = apiService,
                        onClick = { handleCreateRequisition(institution.id, context) }
                    )
                }
            }
        }
    }
}

@Composable
fun InstitutionItem(
    institution: Institution,
    borderSize: Dp = 2.dp, // Default border size
    borderBrush: Brush = Brush.linearGradient(listOf(Color.Gray, Color.LightGray)),
    context: Context,
    apiService: ApiService,
    onClick: () -> Unit
) {
    var logoFile by remember { mutableStateOf<File?>(null) }
    var dominantColors by remember { mutableStateOf(listOf<Color>()) }

    val institutionId = institution.id

    LaunchedEffect(institutionId) {
        if (institutionId != null) {
            Log.d("InstitutionItem", "Processing institution ID: $institutionId")
            val logoFilename = "$institutionId.png"
            val logoSaved = isLogoSaved(context, institutionId)

            if (!logoSaved) {
                Log.d("InstitutionItem", "Logo not saved locally, fetching from API")
                val logoUrl = withContext(Dispatchers.IO) {
                    getInstitutionLogoUrl(apiService, institutionId)
                }
                logoUrl?.let {
                    Log.d("InstitutionItem", "Downloading logo from URL: $it")
                    withContext(Dispatchers.IO) {
                        downloadAndSaveImage(context, it, logoFilename)?.let { file ->
                            Log.d("InstitutionItem", "Logo downloaded and saved at: ${file.path}")
                            logoFile = file
                            val bitmap = BitmapFactory.decodeFile(file.path)
                            if (bitmap != null) {
                                Log.d("InstitutionItem", "Bitmap width: ${bitmap.width}, height: ${bitmap.height}")
                                dominantColors = GradientBorderUtils.getDominantColors(bitmap).map { Color(it) }
                                if (dominantColors.size < 2) {
                                    // Use the average color and a slightly different shade of it to create a gradient
                                    val averageColor = Color(GradientBorderUtils.getAverageColor(bitmap))
                                    dominantColors = listOf(averageColor, averageColor.copy(alpha = 0.7f))
                                }
                                Log.d("InstitutionItem", "Dominant colors: $dominantColors")
                            } else {
                                Log.e("InstitutionItem", "Failed to decode image file: ${file.path}")
                            }
                        }
                    }
                }
            } else {
                Log.d("InstitutionItem", "Logo already saved locally")
                logoFile = File(context.filesDir, logoFilename)
                val bitmap = BitmapFactory.decodeFile(logoFile?.path)
                if (bitmap != null) {
                    Log.d("InstitutionItem", "Bitmap width: ${bitmap.width}, height: ${bitmap.height}")
                    dominantColors = GradientBorderUtils.getDominantColors(bitmap).map { Color(it) }
                    if (dominantColors.size < 2) {
                        // Use the average color and a slightly different shade of it to create a gradient
                        val averageColor = Color(GradientBorderUtils.getAverageColor(bitmap))
                        dominantColors = listOf(averageColor, averageColor.copy(alpha = 0.7f))
                    }
                    Log.d("InstitutionItem", "Dominant colors: $dominantColors")
                } else {
                    Log.e("InstitutionItem", "Failed to decode image file: ${logoFile?.path}")
                }
            }
        } else {
            Log.d("InstitutionItem", "Institution ID is null")
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
            .clickable(onClick = onClick),
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


fun handleCreateRequisition(institutionId: String, context: Context) {
    val baseUrl = "splitter://bankaccounts"
    val apiService = RetrofitClient.getInstance(context).create(ApiService::class.java)
    val requisitionRequest = RequisitionRequest(
        baseUrl = baseUrl,
        institutionId = institutionId,
        reference = "ref_${System.currentTimeMillis()}",
        userLanguage = "EN"
    )

    apiService.createRequisition(requisitionRequest).enqueue(object : Callback<RequisitionResponseWithRedirect> {
        override fun onResponse(call: Call<RequisitionResponseWithRedirect>, response: Response<RequisitionResponseWithRedirect>) {
            if (response.isSuccessful) {
                val requisitionResponse = response.body()
                // Launch the GoCardless URL to redirect the user
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(requisitionResponse?.link))
                context.startActivity(intent)

                // Download and save the logo
                CoroutineScope(Dispatchers.IO).launch {
                    val logoUrl = getInstitutionLogoUrl(apiService, institutionId)
                    if (logoUrl != null) {
                        downloadAndSaveImage(context, logoUrl, "$institutionId.png")
                    }
                }
            } else {
                Toast.makeText(context, "Failed to create requisition", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onFailure(call: Call<RequisitionResponseWithRedirect>, t: Throwable) {
            Toast.makeText(context, "Error creating requisition", Toast.LENGTH_SHORT).show()
        }
    })
}