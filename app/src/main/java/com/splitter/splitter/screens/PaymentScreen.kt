package com.splitter.splitter.screens

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.splitter.splitter.components.GlobalFAB
import com.splitter.splitter.components.GlobalTopAppBar
import com.splitter.splitter.model.GroupMember
import com.splitter.splitter.model.Payment
import com.splitter.splitter.network.ApiService
import com.splitter.splitter.ui.theme.GlobalTheme
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
    var splitMode by remember { mutableStateOf("equally") }
    var paidByUser by remember { mutableStateOf("-") }

    // Retrieve transaction details from previous screen
    val transactionId = navController.currentBackStackEntry?.arguments?.getString("transactionId")
    val transactionAmount = navController.currentBackStackEntry?.arguments?.getString("amount")?.toDoubleOrNull()
    val transactionDescription = navController.currentBackStackEntry?.arguments?.getString("description")
    val transactionCreditorName = navController.currentBackStackEntry?.arguments?.getString("creditorName")
    val transactionCurrency = navController.currentBackStackEntry?.arguments?.getString("currency")
    val transactionBookingDateTime = navController.currentBackStackEntry?.arguments?.getString("bookingDateTime")

    Log.d("PaymentScreen", "Transaction details from previous screen: transactionId=$transactionId, amount=$transactionAmount, description=$transactionDescription, creditorName=$transactionCreditorName, currency=$transactionCurrency, bookingDateTime=$transactionBookingDateTime")

    fun updateEqualSplits(amount: Double, members: List<GroupMember>) {
        if (members.isNotEmpty()) {
            val totalAmount = (amount * 100).toInt() // Convert to cents
            val perPerson = totalAmount / members.size
            val remainder = totalAmount % members.size

            Log.d("PaymentScreen", "Total amount: $totalAmount cents, Per person: $perPerson cents, Remainder: $remainder cents")

            val splitList = MutableList(members.size) { perPerson }
            if (remainder != 0) {
                val random = Random()
                for (i in 0 until remainder) {
                    splitList[random.nextInt(members.size)] += 1
                }
            }

            splits = members.associate { it.userId to splitList[members.indexOf(it)] / 100.0 }
            Log.d("PaymentScreen", "Split amounts: $splits")
        }
    }

    // Fetch group members
    LaunchedEffect(groupId) {
        apiService.getMembersOfGroup(groupId).enqueue(object : Callback<List<GroupMember>> {
            override fun onResponse(call: Call<List<GroupMember>>, response: Response<List<GroupMember>>) {
                if (response.isSuccessful) {
                    groupMembers = response.body() ?: emptyList()
                    if (paymentId == 0) {
                        // Initialize splits for all members if creating a new payment
                        splits = groupMembers.associate { it.userId to 0.0 }
                        if (splitMode == "equally") {
                            updateEqualSplits(amount, groupMembers)
                        }
                    }
                    Log.d("PaymentScreen", "Fetched group members: $groupMembers")
                } else {
                    error = response.message()
                    Log.e("PaymentScreen", "Error fetching group members: $error")
                }
            }

            override fun onFailure(call: Call<List<GroupMember>>, t: Throwable) {
                error = t.message
                Log.e("PaymentScreen", "Failed to fetch group members: $error")
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
                            fetchPaymentSplits(apiService, paymentId) { fetchedSplits ->
                                // Ensure all group members have a split entry
                                splits = groupMembers.associate { it.userId to (fetchedSplits[it.userId] ?: 0.0) }
                                Log.d("PaymentScreen", "Fetched payment splits: $splits")
                            }
                        }
                    } else {
                        error = response.message()
                        Log.e("PaymentScreen", "Error fetching payment details: $error")
                    }
                }

                override fun onFailure(call: Call<Payment>, t: Throwable) {
                    error = t.message
                    Log.e("PaymentScreen", "Failed to fetch payment details: $error")
                }
            })
        } else {
            // Prefill with transaction details
            transactionAmount?.let { amount = it }
            transactionDescription?.let { description = it }
            transactionBookingDateTime?.let { paymentDate = it.split("T")[0] } // Extracting date part
            if (splitMode == "equally") {
                updateEqualSplits(amount, groupMembers)
            }
            Log.d("PaymentScreen", "Prefilled with transaction details: amount=$amount, description=$description, paymentDate=$paymentDate")
        }
    }

    GlobalTheme {
        Scaffold(
            topBar = {
                GlobalTopAppBar(title = { Text(if (paymentId == 0) "Add Payment" else "Edit Payment") })
            },
            floatingActionButton = {
                GlobalFAB(
                    onClick = {
                        val totalSplitAmount = splits.values.sum()
                        Log.d("PaymentScreen", "Total split amount: $totalSplitAmount, Payment amount: $amount")
                        if (totalSplitAmount == amount) {
                            if (paymentId == 0) {
                                Log.d("PaymentScreen", "Creating new payment")
                                if (userId != null) {
                                    createPayment(apiService, groupId, amount, description, notes, splits, paymentDate, userId, transactionId, splitMode) {
                                        navController.popBackStack()
                                    }
                                }
                            } else {
                                Log.d("PaymentScreen", "Updating existing payment")
                                if (userId != null) {
                                    updatePayment(apiService, paymentId, groupId, amount, description, notes, splits, paymentDate, userId, splitMode) {
                                        navController.popBackStack()
                                    }
                                }
                            }
                        } else {
                            showToast(context, "The total of the splits must equal the amount")
                        }
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = "Add") },
                    text = "Add Payment"
                )
            },
            content = { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    item {
                        if (error != null) {
                            Text("Error: $error", color = MaterialTheme.colorScheme.error)
                        }
                        TextField(
                            value = amount.toString(),
                            onValueChange = {
                                amount = it.toDoubleOrNull() ?: 0.0
                                if (splitMode == "equally") {
                                    updateEqualSplits(amount, groupMembers)
                                }
                            },
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

                        // Split Mode Dropdown
                        Text(
                            "Split Mode:",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { expanded = true }) {
                                Text(splitMode, color = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                DropdownMenuItem(
                                    onClick = {
                                        splitMode = "equally"
                                        updateEqualSplits(amount, groupMembers)
                                        expanded = false
                                    },
                                    text = { Text("Equally") }
                                )
                                DropdownMenuItem(
                                    onClick = {
                                        splitMode = "unequally"
                                        expanded = false
                                    },
                                    text = {Text("Unequally")}
                                )
                            }
                        }

                        // Display splits and allow overrides
                        Text(
                            "Splits:",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        groupMembers.forEach { member ->
                            val splitAmount = splits[member.userId] ?: 0.0
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "User ${member.userId}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                TextField(
                                    value = String.format("%.2f", splitAmount),
                                    onValueChange = {
                                        splits = splits.toMutableMap().apply {
                                            this[member.userId] = it.toDoubleOrNull() ?: 0.0
                                        }
                                    },
                                    modifier = Modifier.width(100.dp),
                                    enabled = splitMode == "unequally"
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}

fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
