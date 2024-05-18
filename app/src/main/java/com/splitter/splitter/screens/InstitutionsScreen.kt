package com.splitter.splitter.screens

import android.content.Context
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
import com.splitter.splitter.network.RetrofitClient
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
                        handleCreateRequisition(institution.id, context)
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
    // Implement the logic to create a requisition
    Toast.makeText(context, "Create requisition for $institutionId", Toast.LENGTH_SHORT).show()
}
