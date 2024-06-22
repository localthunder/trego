package com.splitter.splitter.utils

import android.content.Context
import android.util.Log
import com.splitter.splitter.model.Payment
import com.splitter.splitter.model.PaymentSplit
import com.splitter.splitter.network.ApiService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PaymentUtils {

    fun createPayment(
        apiService: ApiService,
        groupId: Int,
        amount: Double,
        description: String,
        notes: String,
        splits: Map<Int, Double>,
        paymentDate: String,
        userId: Int,
        transactionId: String?,
        splitMode: String,
        institutionName: String?,
        onComplete: () -> Unit
    ) {
        val payment = Payment(
            id = 0,  // New payment will get its ID from the backend
            groupId = groupId,
            paidByUserId = userId,
            transactionId = transactionId,
            amount = amount,
            description = description,
            notes = notes,
            paymentDate = paymentDate,
            createdBy = userId,
            updatedBy = userId,
            createdAt = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                Locale.getDefault()
            ).format(Date()),
            updatedAt = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                Locale.getDefault()
            ).format(Date()),
            splitMode = splitMode,
            institutionName = institutionName
        )

        apiService.createPayment(payment).enqueue(object : Callback<Payment> {
            override fun onResponse(call: Call<Payment>, response: Response<Payment>) {
                if (response.isSuccessful) {
                    val createdPayment = response.body()
                    createdPayment?.id?.let { paymentId ->
                        splits.forEach { (userId, splitAmount) ->
                            createPaymentSplit(apiService, paymentId, userId, splitAmount, userId)
                        }
                    }
                    onComplete()
                } else {
                    Log.e("PaymentUtils", "Error creating payment: ${response.message()}")
                }
            }

            override fun onFailure(call: Call<Payment>, t: Throwable) {
                Log.e("PaymentUtils", "Failed to create payment: ${t.message}")
            }
        })
    }

    fun updatePayment(
        apiService: ApiService,
        paymentId: Int,
        groupId: Int,
        amount: Double,
        description: String,
        notes: String,
        splits: Map<Int, Double>,
        paymentDate: String,
        userId: Int,
        splitMode: String,
        institutionName: String?,
        onComplete: () -> Unit
    ) {
        val payment = Payment(
            id = paymentId,
            groupId = groupId,
            paidByUserId = userId,
            transactionId = null,
            amount = amount,
            description = description,
            notes = notes,
            paymentDate = paymentDate,
            createdBy = 0,  // This field is not used for update, but must be set
            updatedBy = userId,
            createdAt = "",  // This field is not used for update, but must be set
            updatedAt = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                Locale.getDefault()
            ).format(Date()),
            splitMode = splitMode,
            institutionName = institutionName
        )

        apiService.updatePayment(paymentId, payment).enqueue(object : Callback<Payment> {
            override fun onResponse(call: Call<Payment>, response: Response<Payment>) {
                if (response.isSuccessful) {
                    splits.forEach { (userId, splitAmount) ->
                        // Fetch the split ID for each userId before updating
                        apiService.getPaymentSplitsByPayment(paymentId)
                            .enqueue(object : Callback<List<PaymentSplit>> {
                                override fun onResponse(
                                    call: Call<List<PaymentSplit>>,
                                    response: Response<List<PaymentSplit>>
                                ) {
                                    if (response.isSuccessful) {
                                        response.body()?.forEach { paymentSplit ->
                                            if (paymentSplit.userId == userId) {
                                                updatePaymentSplit(
                                                    apiService,
                                                    paymentId,
                                                    paymentSplit.id,
                                                    userId,
                                                    splitAmount,
                                                    userId
                                                )
                                            }
                                        }
                                    } else {
                                        Log.e(
                                            "PaymentUtils",
                                            "Error fetching payment splits: ${response.message()}"
                                        )
                                    }
                                }

                                override fun onFailure(
                                    call: Call<List<PaymentSplit>>,
                                    t: Throwable
                                ) {
                                    Log.e(
                                        "PaymentUtils",
                                        "Failed to fetch payment splits: ${t.message}"
                                    )
                                }
                            })
                    }
                    onComplete()
                } else {
                    Log.e("PaymentUtils", "Error updating payment: ${response.message()}")
                }
            }

            override fun onFailure(call: Call<Payment>, t: Throwable) {
                Log.e("PaymentUtils", "Failed to update payment: ${t.message}")
            }
        })
    }

    fun createPaymentSplit(
        apiService: ApiService,
        paymentId: Int,
        userId: Int,
        amount: Double,
        createdBy: Int
    ) {
        val currentDateTime =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Date())
        val paymentSplit = PaymentSplit(
            id = 0,
            paymentId = paymentId,
            userId = userId,
            amount = amount,
            createdAt = currentDateTime,
            createdBy = createdBy,
            updatedAt = currentDateTime,
            updatedBy = createdBy
        )

        apiService.createPaymentSplit(paymentId, paymentSplit)
            .enqueue(object : Callback<PaymentSplit> {
                override fun onResponse(
                    call: Call<PaymentSplit>,
                    response: Response<PaymentSplit>
                ) {
                    if (!response.isSuccessful) {
                        Log.e("PaymentUtils", "Error creating payment split: ${response.message()}")
                    }
                }

                override fun onFailure(call: Call<PaymentSplit>, t: Throwable) {
                    Log.e("PaymentUtils", "Failed to create payment split: ${t.message}")
                }
            })
    }

    fun updatePaymentSplit(
        apiService: ApiService,
        paymentId: Int,
        splitId: Int,
        userId: Int,
        amount: Double,
        updatedBy: Int
    ) {
        val currentDateTime =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Date())
        val paymentSplit = PaymentSplit(
            id = splitId,
            paymentId = paymentId,
            userId = userId,
            amount = amount,
            createdAt = "",  // This field is not used for update, but must be set
            createdBy = 0,  // This field is not used for update, but must be set
            updatedAt = currentDateTime,
            updatedBy = updatedBy
        )

        apiService.updatePaymentSplit(paymentId, splitId, paymentSplit)
            .enqueue(object : Callback<PaymentSplit> {
                override fun onResponse(
                    call: Call<PaymentSplit>,
                    response: Response<PaymentSplit>
                ) {
                    if (!response.isSuccessful) {
                        Log.e("PaymentUtils", "Error updating payment split: ${response.message()}")
                    }
                }

                override fun onFailure(call: Call<PaymentSplit>, t: Throwable) {
                    Log.e("PaymentUtils", "Failed to update payment split: ${t.message}")
                }
            })
    }

    fun fetchPaymentSplits(
        apiService: ApiService,
        paymentId: Int,
        onResult: (Map<Int, Double>) -> Unit
    ) {
        apiService.getPaymentSplitsByPayment(paymentId)
            .enqueue(object : Callback<List<PaymentSplit>> {
                override fun onResponse(
                    call: Call<List<PaymentSplit>>,
                    response: Response<List<PaymentSplit>>
                ) {
                    if (response.isSuccessful) {
                        val splits =
                            response.body()?.associate { it.userId to it.amount } ?: emptyMap()
                        onResult(splits)
                    } else {
                        Log.e(
                            "PaymentUtils",
                            "Error fetching payment splits: ${response.message()}"
                        )
                    }
                }

                override fun onFailure(call: Call<List<PaymentSplit>>, t: Throwable) {
                    Log.e("PaymentUtils", "Failed to fetch payment splits: ${t.message}")
                }
            })
    }
}
