package com.splitter.splitter.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberImagePainter
import com.splitter.splitter.model.Institution
import com.splitter.splitter.network.ApiService
import com.splitter.splitter.network.RequisitionRequest
import com.splitter.splitter.network.RequisitionResponseWithRedirect
import com.splitter.splitter.network.RetrofitClient
import com.splitter.splitter.utils.GocardlessUtils.getInstitutionLogoUrl
import downloadAndSaveImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@Composable
fun InstitutionsScreen(navController: NavController, context: Context) {
    var institutions by remember { mutableStateOf(listOf<Institution>()) }
    var loading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

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

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (loading) {
            CircularProgressIndicator()
        } else {
            Text("Available Institutions", style = MaterialTheme.typography.h6)
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn {
                items(institutions) { institution ->
                    InstitutionItem(institution) {
                        handleCreateRequisition(institution.id, context, )
                    }
                }
            }
        }
    }
}

@Composable
fun InstitutionItem(institution: Institution, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = rememberImagePainter(institution.logo),
                contentDescription = null,
                modifier = Modifier.size(40.dp).padding(end = 8.dp),
                contentScale = ContentScale.Crop
            )
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