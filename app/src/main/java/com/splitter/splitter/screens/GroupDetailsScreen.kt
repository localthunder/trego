package com.splitter.splitter.screens

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberImagePainter
import com.splitter.splitter.components.AddMembersDialog
import com.splitter.splitter.components.GlobalFAB
import com.splitter.splitter.components.GlobalTopAppBar
import com.splitter.splitter.components.PaymentItem
import com.splitter.splitter.model.Group
import com.splitter.splitter.model.GroupMember
import com.splitter.splitter.model.Payment
import com.splitter.splitter.network.ApiService
import com.splitter.splitter.ui.theme.GlobalTheme
import com.splitter.splitter.utils.GroupUtils.fetchUsernames
import com.splitter.splitter.utils.ImageUtils
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun GroupDetailsScreen(navController: NavController, groupId: Int, apiService: ApiService) {
    val context = LocalContext.current
    var group by remember { mutableStateOf<Group?>(null) }
    var groupMembers by remember { mutableStateOf<List<GroupMember>>(emptyList()) }
    var usernames by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var payments by remember { mutableStateOf<List<Payment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAddMembersDialog by remember { mutableStateOf(false) }
    var groupImage by remember { mutableStateOf<String?>(null) }

    // Handle image upload
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }

    // Function to upload image
    fun uploadImage(context: Context, apiService: ApiService, groupId: Int, uri: Uri) {
        isUploading = true
        ImageUtils.uploadGroupImage(apiService, context, groupId, uri) { success, path, error ->
            isUploading = false
            if (success) {
                Log.d("GroupDetailsScreen", "Image uploaded successfully: $path")
                groupImage = path // Update the image path if needed
            } else {
                Log.e("GroupDetailsScreen", "Image upload failed: $error")
                uploadError = error
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            imageUri = it
            uploadImage(context, apiService, groupId, it)
        }
    }

    LaunchedEffect(groupId) {
        Log.d("GroupDetailsScreen", "Fetching group details for groupId: $groupId")

        apiService.getGroupById(groupId).enqueue(object : Callback<Group> {
            override fun onResponse(call: Call<Group>, response: Response<Group>) {
                if (response.isSuccessful) {
                    group = response.body()
                    Log.d("GroupDetailsScreen", "Fetched group details: $group")
                } else {
                    error = response.message()
                    Log.e("GroupDetailsScreen", "Error fetching group details: $error")
                }
            }

            override fun onFailure(call: Call<Group>, t: Throwable) {
                error = t.message
                Log.e("GroupDetailsScreen", "Failed to fetch group details: $error")
            }
        })

        apiService.getMembersOfGroup(groupId).enqueue(object : Callback<List<GroupMember>> {
            override fun onResponse(call: Call<List<GroupMember>>, response: Response<List<GroupMember>>) {
                if (response.isSuccessful) {
                    groupMembers = response.body() ?: emptyList()
                    Log.d("GroupDetailsScreen", "Fetched group members: $groupMembers")
                    fetchUsernames(apiService, groupMembers.map { it.userId }) { usernamesResult ->
                        usernames = usernamesResult
                        Log.d("GroupDetailsScreen", "Fetched usernames: $usernames")
                    }
                } else {
                    error = response.message()
                    Log.e("GroupDetailsScreen", "Error fetching group members: $error")
                }
                loading = false
            }

            override fun onFailure(call: Call<List<GroupMember>>, t: Throwable) {
                error = t.message
                loading = false
                Log.e("GroupDetailsScreen", "Failed to fetch group members: $error")
            }
        })

        apiService.getPaymentsByGroup(groupId).enqueue(object : Callback<List<Payment>> {
            override fun onResponse(call: Call<List<Payment>>, response: Response<List<Payment>>) {
                if (response.isSuccessful) {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    payments = response.body()?.sortedByDescending { payment ->
                        dateFormat.parse(payment.paymentDate)
                    } ?: emptyList()
                    loading = false
                } else {
                    error = response.message()
                    loading = false
                }
            }

            override fun onFailure(call: Call<List<Payment>>, t: Throwable) {
                error = t.message
                Log.e("GroupDetailsScreen", "Failed to fetch payments: $error")
            }
        })
    }

    GlobalTheme {
        Scaffold(
            topBar = {
                GlobalTopAppBar(title = { Text(group?.name ?: "Group Details") })
            },
            floatingActionButton = {
                GlobalFAB(
                    onClick = {
                        // Navigate to PaymentScreen for creating a new payment
                        navController.navigate("addExpense/$groupId")
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = "Add") },
                    text = "Add Expense"
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
                        Text("Error: $error", color = MaterialTheme.colorScheme.error)
                    } else {
                        group?.let { groupDetails ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Display the group image if available
                                Box(
                                    modifier = Modifier
                                        .size(128.dp)
                                        .clickable {
                                            launcher.launch("image/*")
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isUploading) {
                                        // Show loading indicator
                                        Text("Uploading...", style = MaterialTheme.typography.displaySmall)
                                    } else if (uploadError != null) {
                                        // Show error message
                                        Text("Error: $uploadError", style = MaterialTheme.typography.displaySmall)
                                    } else if (groupImage != null) {
                                        // Display uploaded image
                                        val imagePath = ImageUtils.getImageFile(context, groupImage!!).absolutePath
                                        Log.d("GroupDetailsScreen", "Displaying image: $imagePath")
                                        Image(
                                            painter = rememberImagePainter(imagePath),
                                            contentDescription = "Group Image",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        // Placeholder text
                                        Text("Upload Image", style = MaterialTheme.typography.displaySmall)
                                    }
                                }
                                Text(
                                    groupDetails.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                groupDetails.description?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )
                                }
                                Text(
                                    "Members",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    groupMembers.forEach { member ->
                                        val username = usernames[member.userId] ?: "Loading..."
                                        Text(
                                            username,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Button(
                                        onClick = { showAddMembersDialog = true }
                                    ) {
                                        Text("Add People")
                                    }
                                    Button(
                                        onClick = { navController.navigate("groupBalances/$groupId") }
                                    ) {
                                        Text("Balances")
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Payments",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                LazyColumn {
                                    items(payments) { payment ->
                                        PaymentItem(payment, apiService, context) {
                                            navController.navigate("paymentDetails/${groupId}/${payment.id}")
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
                if (showAddMembersDialog) {
                    AddMembersDialog(
                        onDismissRequest = { showAddMembersDialog = false },
                        onCreateInviteLinkClick = { /* Handle create invite link */ },
                        onInviteMembersClick = { navController.navigate("inviteMembers/$groupId") }
                    )
                }
            }
        )
    }
}
