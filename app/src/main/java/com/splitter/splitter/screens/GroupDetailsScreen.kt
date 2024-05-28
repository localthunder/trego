package com.splitter.splitter.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.rememberImagePainter
import com.splitter.splitter.model.Group
import com.splitter.splitter.model.GroupMember
import com.splitter.splitter.model.User
import com.splitter.splitter.model.Payment
import com.splitter.splitter.network.ApiService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun GroupDetailsScreen(navController: NavController, groupId: Int, apiService: ApiService) {
    var group by remember { mutableStateOf<Group?>(null) }
    var groupMembers by remember { mutableStateOf<List<GroupMember>>(emptyList()) }
    var usernames by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var payments by remember { mutableStateOf<List<Payment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

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

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(group?.name ?: "Group Details") })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Navigate to PaymentScreen for creating a new payment
                    navController.navigate("addExpense/$groupId")
                }
            ) {
                Text("+")
            }
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
                    Text("Error: $error", color = MaterialTheme.colors.error)
                } else {
                    group?.let { groupDetails ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = rememberImagePainter(groupDetails.groupImg),
                                contentDescription = null,
                                modifier = Modifier.size(80.dp).padding(bottom = 16.dp)
                            )
                            Text(groupDetails.name, fontSize = 24.sp, color = Color.Black)
                            groupDetails.description?.let {
                                Text(
                                    it,
                                    fontSize = 16.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                            }
                            Text(
                                "Members",
                                fontSize = 20.sp,
                                color = Color.Black,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                groupMembers.forEach { member ->
                                    val username = usernames[member.userId] ?: "Loading..."
                                    Text(username, fontSize = 18.sp, color = Color.Black)
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Payments",
                                fontSize = 20.sp,
                                color = Color.Black,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            LazyColumn {
                                items(payments) { payment ->
                                    PaymentItem(payment) {
                                        navController.navigate("paymentDetails/${groupId}/${payment.id}")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    )
}

fun fetchUsernames(apiService: ApiService, userIds: List<Int>, onResult: (Map<Int, String>) -> Unit) {
    val usernames = mutableMapOf<Int, String>()
    Log.d("GroupDetailsScreen", "Fetching usernames for userIds: $userIds")

    userIds.forEach { userId ->
        apiService.getUserById(userId).enqueue(object : Callback<User> {
            override fun onResponse(call: Call<User>, response: Response<User>) {
                if (response.isSuccessful) {
                    response.body()?.let { user ->
                        usernames[userId] = user.username
                        Log.d("GroupDetailsScreen", "Fetched username for userId $userId: ${user.username}")
                        onResult(usernames)
                    }
                } else {
                    Log.e("GroupDetailsScreen", "Error fetching username for userId $userId: ${response.message()}")
                }
            }

            override fun onFailure(call: Call<User>, t: Throwable) {
                Log.e("GroupDetailsScreen", "Failed to fetch username for userId $userId: ${t.message}")
            }
        })
    }
}

@Composable
fun PaymentItem(payment: Payment, onClick: () -> Unit) {
    val paymentDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
    val displayDateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())

    val parsedPaymentDate: String? = payment.paymentDate?.let {
        try {
            paymentDateFormat.parse(it.toString())?.let { date -> displayDateFormat.format(date) }
        } catch (e: Exception) {
            Log.e("PaymentItem", "Error parsing payment date: $it", e)
            null
        }
    }

    val parsedCreatedAt: String? = payment.createdAt?.let {
        try {
            dateTimeFormat.parse(it)?.let { date -> displayDateFormat.format(date) }
        } catch (e: Exception) {
            Log.e("PaymentItem", "Error parsing created_at date: $it", e)
            null
        }
    }

    val parsedUpdatedAt: String? = payment.updatedAt?.let {
        try {
            dateTimeFormat.parse(it)?.let { date -> displayDateFormat.format(date) }
        } catch (e: Exception) {
            Log.e("PaymentItem", "Error parsing updated_at date: $it", e)
            null
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Amount: ${payment.amount}", fontSize = 18.sp, color = Color.Black)
                Text("Description: ${payment.description}", fontSize = 14.sp, color = Color.Gray)
                parsedPaymentDate?.let {
                    Text("Payment Date: $it", fontSize = 14.sp, color = Color.Gray)
                }
                parsedCreatedAt?.let {
                    Text("Created At: $it", fontSize = 14.sp, color = Color.Gray)
                }
                parsedUpdatedAt?.let {
                    Text("Updated At: $it", fontSize = 14.sp, color = Color.Gray)
                }
                payment.notes?.let {
                    Text("Notes: $it", fontSize = 14.sp, color = Color.Gray)
                }
            }
        }
    }
}
