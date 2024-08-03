package com.splitter.splitter.screens

import android.app.DatePickerDialog
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.splitter.splitter.components.GlobalDatePickerDialog
import com.splitter.splitter.components.GlobalFAB
import com.splitter.splitter.components.GlobalTopAppBar
import com.splitter.splitter.model.GroupMember
import com.splitter.splitter.model.Payment
import com.splitter.splitter.model.User
import com.splitter.splitter.data.network.ApiService
import com.splitter.splitter.ui.theme.GlobalTheme
import com.splitter.splitter.utils.CurrencyUtils
import com.splitter.splitter.utils.FormattingUtils.formatPaymentAmount
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
    var user by remember { mutableStateOf<User?>(null) }
    var payment: Payment? by remember { mutableStateOf(null) }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var splits by remember { mutableStateOf(mapOf<Int, Double>()) }
    var groupMembers by remember { mutableStateOf<List<GroupMember>>(emptyList()) }
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var paymentDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var error by remember { mutableStateOf<String?>(null) }
    var splitMode by remember { mutableStateOf("equally") }
    var paidByUser by remember { mutableStateOf(userId) }
    var paidToUser by remember { mutableStateOf<Int?>(null) } // New state for paid to user
    var currency by remember { mutableStateOf("GBP") }
    var institutionName by remember { mutableStateOf<String?>(null) }
    var isTransaction by remember { mutableStateOf(false) }
    var paymentType by remember { mutableStateOf("spent") }
    val focusRequesterDescription = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var expandedPaidByUserList by remember { mutableStateOf(false) }
    var expandedPaidToUserList by remember { mutableStateOf(false) } // New state for expanded paid to user list
    var expandedPaymentTypeList by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }



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
            for (i in 0 until kotlin.math.abs(remainder)) {
                if (remainder > 0) {
                    splitList[i] += 1
                } else {
                    splitList[i] -= 1
                }
            }

            splits = members.mapIndexed { index, member -> member.userId to splitList[index] / 100.0 }.toMap()
            Log.d("PaymentScreen", "Split amounts: $splits")
        }
    }

    fun updateTransferSplits(amount: Double, members: List<GroupMember>, paidToUser: Int?) {
        if ( paymentType == "transferred" ) {
            splitMode = "unequally"
        }
        if (paidToUser != null) {
            splits = members.associate { it.userId to if (it.userId == paidToUser) amount else 0.0 }
            Log.d("PaymentScreen", "Transfer split amounts: $splits")
        }
    }

    // Fetch user details and set default currency only if not already set
    LaunchedEffect(userId) {
        if (userId != null && currency == "GBP") { // Assuming GBP is the default currency code
            apiService.getUserById(userId).enqueue(object : Callback<User> {
                override fun onResponse(call: Call<User>, response: Response<User>) {
                    if (response.isSuccessful) {
                        user = response.body()
                        currency = user?.defaultCurrency ?: "GBP"
                        Log.d("PaymentScreen", "User: $user , Currency: $currency")
                    } else {
                        error = response.message()
                        Log.e("PaymentScreen", "Error fetching user: $error")
                    }
                }

                override fun onFailure(call: Call<User>, t: Throwable) {
                    error = t.message
                    Log.e("PaymentScreen", "Failed to fetch user: $error")
                }
            })
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
                            updateEqualSplits(amount.toDoubleOrNull() ?: 0.0, groupMembers)
                        }
                    }
                    Log.d("PaymentScreen", "Fetched group members: $groupMembers")

                    // Fetch user details for these group members
                    val userIds = groupMembers.map { it.userId }
                    Log.d("PaymentScreen", "Fetching users with IDs: $userIds")
                    apiService.getUsersByIds(userIds).enqueue(object : Callback<List<User>> {
                        override fun onResponse(call: Call<List<User>>, response: Response<List<User>>) {
                            if (response.isSuccessful) {
                                users = response.body() ?: emptyList()
                                Log.d("PaymentScreen", "Fetched users: ${users.map { it.username }}")
                            } else {
                                error = response.message()
                                Log.e("PaymentScreen", "Error fetching users: $error")
                            }
                        }

                        override fun onFailure(call: Call<List<User>>, t: Throwable) {
                            error = t.message
                            Log.e("PaymentScreen", "Failed to fetch users: $error")
                        }
                    })
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
                            amount = kotlin.math.abs(it.amount).toString()
                            description = it.description ?: ""
                            notes = it.notes ?: ""
                            institutionName = it.institutionName
                            paymentDate = it.paymentDate
                            paidByUser = it.paidByUserId
                            splitMode = it.splitMode
                            paymentType = it.paymentType ?: "spent"
                            isTransaction = it.transactionId != null
                            fetchPaymentSplits(apiService, paymentId) { fetchedSplits ->
                                // Ensure all group members have a split entry
                                splits = groupMembers.associate { it.userId to kotlin.math.abs(fetchedSplits[it.userId] ?: 0.0) }
                                Log.d("PaymentScreen", "Fetched payment splits: $splits")

                                // If paymentType is "transferred", set paidToUser to the user with the largest absolute split amount
                                if (paymentType == "transferred") {
                                    val maxSplitUser = splits.maxByOrNull { kotlin.math.abs(it.value) }?.key
                                    paidToUser = maxSplitUser
                                    Log.d("PaymentScreen", "Paid to user set to: $paidToUser")
                                }
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
            transactionAmount?.let {
                amount = kotlin.math.abs(it).toString()
                paymentType = if (it < 0) "spent" else "received"
            }
            transactionDescription?.let { description = it }
            transactionBookingDateTime?.let { paymentDate = it.split("T")[0] } // Extracting date part
            if (splitMode == "equally") {
                updateEqualSplits(amount.toDoubleOrNull() ?: 0.0, groupMembers)
            }
            if (transactionId != null) {
                isTransaction = true
            }
            Log.d("PaymentScreen", "Prefilled with transaction details: amount=$amount, description=$description, paymentDate=$paymentDate")
        }
    }


    // Observe changes to paymentType
    LaunchedEffect(paymentType) {
        if (paymentType == "transferred" && groupMembers.isNotEmpty()) {
            updateTransferSplits(amount.toDoubleOrNull() ?: 0.0, groupMembers, paidToUser)
        } else if (splitMode == "equally") {
            updateEqualSplits(amount.toDoubleOrNull() ?: 0.0, groupMembers)
        }
    }

    // Observe changes to amount
    LaunchedEffect(amount) {
        if (paymentType == "transferred") {
            updateTransferSplits(amount.toDoubleOrNull() ?: 0.0, groupMembers, paidToUser)
        } else if (splitMode == "equally" && paymentType != "transferred") {
            updateEqualSplits(amount.toDoubleOrNull() ?: 0.0, groupMembers)
        }
    }

    // Observe changes to splitMode
    LaunchedEffect(splitMode) {
        if (splitMode == "equally" && paymentType != "transferred") {
            updateEqualSplits(amount.toDoubleOrNull() ?: 0.0, groupMembers)
        }
    }

    // Observe changes to paidToUser
    LaunchedEffect(paidToUser) {
        if (paymentType == "transferred" && groupMembers.isNotEmpty()) {
            updateTransferSplits(amount.toDoubleOrNull() ?: 0.0, groupMembers, paidToUser)
        }
    }

    // Handle result from currency selection screen
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    savedStateHandle?.getLiveData<String>("currency")?.observe(navController.currentBackStackEntry!!) { selectedCurrency ->
        Log.d("PaymentScreen", "Selected currency from backstack: $selectedCurrency")
        currency = selectedCurrency
    }

    fun getCurrencySymbol(currencyCode: String): String {
        return CurrencyUtils.currencySymbols[currencyCode] ?: currencyCode
    }

    val handlePaymentCreationOrUpdate = handlePaymentCreationOrUpdate@{
        val totalSplitAmount = splits.values.sum()
        Log.d("PaymentScreen", "Total split amount: $totalSplitAmount, Payment amount: ${amount.toDoubleOrNull() ?: 0.0}")
        if (kotlin.math.abs(totalSplitAmount - (amount.toDoubleOrNull() ?: 0.0)) < 0.01) { // Allow a small tolerance for floating-point precision issues
            val paymentAmount = kotlin.math.abs(amount.toDoubleOrNull() ?: 0.0)

            val splitValues = splits.map { (userId, splitAmount) ->
                userId to kotlin.math.abs(splitAmount)
            }.toMap()

            val finalSplitMode = if (paymentType == "transferred") "unequally" else splitMode

            val finalSplits = if (paymentType == "transferred" && paidToUser != null) {
                users.associate { it.userId to if (it.userId == paidToUser) paymentAmount else 0.0 }
            } else {
                splits.map { (userId, splitAmount) ->
                    userId to kotlin.math.abs(splitAmount)
                }.toMap()
            }

            if (paidToUser == paidByUser) {
                showToast(context, "You cannot save transfers of money to and from the same person")
                return@handlePaymentCreationOrUpdate
            }

            Log.d("PaymentScreen", "Split Values: $splitValues")

            val finalDescription = if (paymentType == "transferred" && paidByUser != null && paidToUser != null) {
                "${users.firstOrNull { it.userId == paidByUser }?.username} transferred $paymentAmount to ${users.firstOrNull { it.userId == paidToUser }?.username} outside this app."
            } else {
                description
            }

            if (paymentId == 0) {
                Log.d("PaymentScreen", "Creating new payment")
                if (userId != null) {
                    createPayment(apiService, groupId, paymentAmount, finalDescription, notes, finalSplits, paymentDate, userId, transactionId, finalSplitMode, institutionName, paymentType, currency, paidByUser) {
                        navController.popBackStack()
                    }
                }
            } else {
                Log.d("PaymentScreen", "Updating existing payment")
                if (userId != null) {
                    updatePayment(apiService, paymentId, groupId, paymentAmount, finalDescription, notes, finalSplits, paymentDate, userId, finalSplitMode, institutionName, paymentType, currency, paidByUser) {
                        navController.popBackStack()
                    }
                }
            }
        } else {
            showToast(context, "The total of the splits must equal the amount")
        }
    }

    val handleArchivePayment = {
        apiService.archivePayment(paymentId).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    navController.popBackStack()
                } else {
                    showToast(context, "Error archiving payment: ${response.message()}")
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                showToast(context, "Error archiving payment: ${t.message}")
            }
        })
    }

    GlobalTheme {
        Scaffold(
            topBar = {
                GlobalTopAppBar(
                    title = { Text(if (paymentId == 0) "Add Payment" else "Edit Payment") },
                    actions = {
                        if (paymentId == 0) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.Filled.Close, contentDescription = "Discard Payment", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        } else {
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Archive Payment",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                GlobalFAB(
                    onClick = { handlePaymentCreationOrUpdate() },
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
                        // Top row with payer's name, "spent", and amount text box
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (paidByUser == userId) "I" else users.find { it.userId == paidByUser }?.username ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .clickable { expandedPaidByUserList = true }
                                    .padding(end = 8.dp)
                            )

                            DropdownMenu(
                                expanded = expandedPaidByUserList,
                                onDismissRequest = { expandedPaidByUserList = false }
                            ) {
                                // Use derivedStateOf to ensure we are reacting to changes in the users list
                                val userMap by remember { derivedStateOf { users.associateBy { it.userId } } }

                                groupMembers.forEach { member ->
                                    val user = userMap[member.userId]
                                    Log.d("PaymentScreen", "User ID: ${user?.userId}, Username: ${user?.username}, Fallback: ${member.userId}")
                                    val username = user?.username ?: member.userId.toString()
                                    DropdownMenuItem(
                                        text = { Text(text = if (member.userId == userId) "I" else username) },
                                        onClick = {
                                            paidByUser = member.userId
                                            expandedPaidByUserList = false
                                        }
                                    )
                                }
                            }

                            TextButton(
                                onClick = { expandedPaymentTypeList = true },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(paymentType, color = MaterialTheme.colorScheme.primary)
                            }

                            DropdownMenu(
                                expanded = expandedPaymentTypeList,
                                onDismissRequest = { expandedPaymentTypeList = false }
                            ) {
                                listOf("spent", "received", "transferred").forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type) },
                                        onClick = {
                                            paymentType = type
                                            if (type == "spent") {
                                                amount = (amount.toDoubleOrNull()?.let { kotlin.math.abs(it) } ?: 0.0).toString()
                                            } else if (type == "received") {
                                                amount = (amount.toDoubleOrNull()?.let { kotlin.math.abs(it) } ?: 0.0).toString()
                                            }
                                            expandedPaymentTypeList = false
                                        }
                                    )
                                }
                            }

                            // Currency Selector
                            Text(
                                text = CurrencyUtils.currencySymbols[currency] ?: currency,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .clickable {
                                        navController.navigate("currencySelection")
                                    }
                                    .padding(end = 8.dp)
                            )

                            if (isTransaction) {
                                Text(
                                    text = formatPaymentAmount(amount),
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                OutlinedTextField(
                                    value = formatPaymentAmount(amount),
                                    onValueChange = {
                                        val sanitizedInput = it.filter { char -> char.isDigit() || char == '.' }
                                        if (sanitizedInput.count { char -> char == '.' } <= 1 && sanitizedInput.length <= 10) {
                                            amount = sanitizedInput
                                            if (splitMode == "equally") {
                                                updateEqualSplits(amount.toDoubleOrNull() ?: 0.0, groupMembers)
                                            }
                                        }
                                    },
                                    label = { Text("Amount") },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isTransaction,
                                    keyboardOptions = KeyboardOptions.Default.copy(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Next // Show "Next" button
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onNext = {
                                            focusRequesterDescription.requestFocus()
                                        }
                                    ),
                                )
                            }
                        }

                        if (paymentType == "transferred") {
                            Text(
                                text = if (paidToUser == userId) {
                                    "me"
                                } else {
                                    users.find { it.userId == paidToUser }?.username ?: users.find { it.userId != userId }?.username ?: ""
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .clickable { expandedPaidToUserList = true }
                                    .padding(end = 8.dp)
                            )
                            DropdownMenu(
                                expanded = expandedPaidToUserList,
                                onDismissRequest = { expandedPaidToUserList = false }
                            ) {
                                // Use derivedStateOf to ensure we are reacting to changes in the users list
                                val userMap by remember { derivedStateOf { users.associateBy { it.userId } } }

                                groupMembers.forEach { member ->
                                    val user = userMap[member.userId]
                                    val username = user?.username ?: member.userId.toString()
                                    DropdownMenuItem(
                                        text = { Text(text = if (member.userId == userId) "I" else username) },
                                        onClick = {
                                            paidToUser = member.userId
                                            expandedPaidToUserList = false
                                        }
                                    )
                                }
                            }
                        } else {
                            TextField(
                                value = description,
                                onValueChange = { description = it },
                                label = { Text("Description") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequesterDescription),
                                enabled = !isTransaction,
                                keyboardOptions = KeyboardOptions.Default.copy(
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                    }
                                )
                            )
                        }

                        TextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notes") },
                            modifier = Modifier
                                .fillMaxWidth(),
                            keyboardOptions = KeyboardOptions.Default.copy(
                                imeAction = ImeAction.Done // Show "Done" button
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                }
                            )
                        )
                        GlobalDatePickerDialog(
                            date = paymentDate,
                            onDateChange = { newDate ->
                                paymentDate = newDate
                            }
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
                                        updateEqualSplits(amount.toDoubleOrNull() ?: 0.0, groupMembers)
                                        expanded = false
                                    },
                                    text = { Text("equally") }
                                )
                                DropdownMenuItem(
                                    onClick = {
                                        splitMode = "unequally"
                                        expanded = false
                                    },
                                    text = { Text("unequally") }
                                )
                            }
                        }

                        // Display splits and allow overrides
                        groupMembers.forEach { member ->
                            val splitAmount = splits[member.userId] ?: 0.0
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = users.find { it.userId == member.userId }?.username ?: "User ${member.userId}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                TextField(
                                    value = splitAmount.toString(),
                                    onValueChange = {
                                        splits = splits.toMutableMap().apply {
                                            this[member.userId] = kotlin.math.abs(it.toDoubleOrNull() ?: 0.0)
                                        }
                                    },
                                    modifier = Modifier.width(100.dp),
                                    enabled = splitMode == "unequally",
                                    leadingIcon = { Text(getCurrencySymbol(currency)) },
                                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                                )
                            }
                        }
                    }
                }
                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        title = { Text("Delete Payment") },
                        text = { Text("Are you sure you want to delete this payment?") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (paymentId != 0) {
                                        handleArchivePayment()
                                        showDeleteDialog = false
                                    }
                                }
                            ) {
                                Text("Delete")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        )
    }
}

fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
