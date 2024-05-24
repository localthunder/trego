package com.splitter.splitter.screens

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.splitter.splitter.model.GroupMember
import com.splitter.splitter.model.Payment
import com.splitter.splitter.network.ApiService
import com.splitter.splitter.utils.PaymentUtils
import com.splitter.splitter.utils.PaymentUtils.createPayment
import com.splitter.splitter.utils.PaymentUtils.fetchPaymentSplits
import com.splitter.splitter.utils.PaymentUtils.updatePayment
import com.splitter.splitter.utils.getUserIdFromPreferences
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PaymentScreen(
    navController: NavController,
    groupId: Int,
    paymentId: Int,
    apiService: ApiService,
    context: Context
) {
    val userId = getUserIdFromPreferences(context)
    var payment: Payment? by remember { mutableStateOf(null) }
    var amount by remember { mutableStateOf(0.0) }
    var description by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var splits by remember { mutableStateOf(mapOf<Int, Double>()) }
    var groupMembers by remember { mutableStateOf<List<GroupMember>>(emptyList()) }
    var paymentDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var error by remember { mutableStateOf<String?>(null) }

    // Fetch group members
    LaunchedEffect(groupId) {
        apiService.getMembersOfGroup(groupId).enqueue(object : Callback<List<GroupMember>> {
            override fun onResponse(call: Call<List<GroupMember>>, response: Response<List<GroupMember>>) {
                if (response.isSuccessful) {
                    groupMembers = response.body() ?: emptyList()
                    if (paymentId == 0) {
                        // Initialize splits for all members if creating a new payment
                        splits = groupMembers.associate { it.userId to 0.0 }
                    }
                } else {
                    error = response.message()
                }
            }

            override fun onFailure(call: Call<List<GroupMember>>, t: Throwable) {
                error = t.message
            }
        })
    }

    // Fetch payment details if editing
    LaunchedEffect(paymentId) {
        if (paymentId != 0) {
            apiService.getPaymentById(paymentId).enqueue(object : Callback<Payment> {
                override fun onResponse(call: Call<Payment>, response: Response<Payment>) {
                    if (response.isSuccessful) {
                        response.body()?.let {
                            payment = it
                            amount = it.amount
                            description = it.description ?: ""
                            notes = it.notes ?: ""
                            paymentDate = it.paymentDate
                            PaymentUtils.fetchPaymentSplits(apiService, paymentId) { fetchedSplits ->
                                // Ensure all group members have a split entry
                                splits = groupMembers.associate { it.userId to (fetchedSplits[it.userId] ?: 0.0) }
                            }
                        }
                    } else {
                        error = response.message()
                    }
                }

                override fun onFailure(call: Call<Payment>, t: Throwable) {
                    error = t.message
                }
            })
        }
    }

    // UI
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (paymentId == 0) "Add Payment" else "Edit Payment") })
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top
            ) {
                if (error != null) {
                    Text("Error: $error", color = MaterialTheme.colors.error)
                }
                TextField(
                    value = amount.toString(),
                    onValueChange = { amount = it.toDoubleOrNull() ?: 0.0 },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = paymentDate,
                    onValueChange = { paymentDate = it },
                    label = { Text("Payment Date (yyyy-MM-dd)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Display splits and allow overrides
                Text("Splits:", fontSize = 20.sp, color = Color.Black, modifier = Modifier.padding(vertical = 8.dp))
                groupMembers.forEach { member ->
                    val splitAmount = splits[member.userId] ?: 0.0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("User ${member.userId}")
                        TextField(
                            value = splitAmount.toString(),
                            onValueChange = {
                                splits = splits.toMutableMap().apply {
                                    this[member.userId] = it.toDoubleOrNull() ?: 0.0
                                }
                            },
                            modifier = Modifier.width(100.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        val totalSplitAmount = splits.values.sum()
                        if (totalSplitAmount == amount) {
                            if (paymentId == 0) {
                                if (userId != null) {
                                    createPayment(apiService, groupId, amount, description, notes, splits, paymentDate, userId) {
                                        navController.popBackStack()
                                    }
                                }
                            } else {
                                if (userId != null) {
                                    updatePayment(apiService, paymentId, groupId, amount, description, notes, splits, paymentDate, userId) {
                                        navController.popBackStack()
                                    }
                                }
                            }
                        } else {
                            showToast(context, "The total of the splits must equal the amount")
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(if (paymentId == 0) "Add Payment" else "Save Payment")
                }
            }
        }
    )
}

fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
