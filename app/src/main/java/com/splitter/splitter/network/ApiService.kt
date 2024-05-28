package com.splitter.splitter.network

import com.google.gson.annotations.SerializedName
import com.splitter.splitter.model.BankAccount
import com.splitter.splitter.model.Group
import com.splitter.splitter.model.GroupMember
import com.splitter.splitter.model.Institution
import com.splitter.splitter.model.Payment
import com.splitter.splitter.model.PaymentSplit
import com.splitter.splitter.model.Requisition
import com.splitter.splitter.model.Transaction
import com.splitter.splitter.model.User
import com.splitter.splitter.screens.LoginRequest
import com.splitter.splitter.screens.RegisterRequest
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

data class AuthResponse(val token: String?, val userId: Int, val success: Boolean = true, val message: String?, val refreshToken: String?)
data class RequisitionRequest(
    val baseUrl: String,
    @SerializedName("institution_id") val institutionId: String,
    val reference: String,
    @SerializedName("user_language") val userLanguage: String
)


data class RequisitionResponseWithRedirect(val id: String, val link: String, val redirectUrl: String)

interface ApiService {

    @GET("/api/users/{userId}")
    fun getUserById(@Path("userId") userId: Int): Call<User>

    @GET("/api/gocardless/institutions")
    fun getInstitutions(@Query("country") country: String): Call<List<Institution>>

    @POST("/api/auth/register")
    fun registerUser(@Body registerRequest: RegisterRequest): Call<AuthResponse>

    @POST("/api/auth/login")
    fun loginUser(@Body loginRequest: LoginRequest): Call<AuthResponse>

    @POST("/auth/refresh-token")
    fun refreshToken(@Body body: Map<String, String>): Call<AuthResponse>

    @POST("/api/gocardless/requisition")
    fun createRequisition(@Body requisitionRequest: RequisitionRequest): Call<RequisitionResponseWithRedirect>

    @GET("/api/gocardless/accounts/{requisitionId}")
    fun getBankAccounts(@Path("requisitionId") requisitionId: String): Call<List<BankAccount>>

    @POST("/api/gocardless/addAccount")
    fun addAccount(@Body account: BankAccount): Call<Void>

    @GET("/api/gocardless/requisition/{reference}")
    fun getRequisitionByReference(@Path("reference") reference: String): Call<Requisition>

    @GET("/api/gocardless/listUserAccounts")
    fun listUserAccounts(): Call<List<BankAccount>>

    @GET("/api/gocardless/transactions/{accountId}")
    fun getTransactions(@Path("accountId") accountId: Int): Call<List<Transaction>>
    @POST("/api/gocardless/transactions")
    fun createTransaction(@Body transaction: Transaction): Call<Transaction>
    @GET("/api/gocardless/{userId}/accounts")
    fun getUserAccounts(@Path("userId") userId: Int): Call<List<BankAccount>>
    @POST("/api/groups")
    fun createGroup(@Body group: Group): Call<Group>
    @GET("/api/groups/{id}")
    fun getGroupById(@Path("id") id: Int): Call<Group>
    @PUT("/api/groups/{id}")
    fun updateGroup(@Path("id") id: Int, @Body group: Group): Call<Group>
    @POST("api/groups/{groupId}/members")
    fun addMemberToGroup(@Path("groupId") groupId: Int, @Body groupMember: GroupMember): Call<GroupMember>

    @GET("api/groups/user/{userId}")
    fun getGroupsByUserId(@Path("userId") userId: Int): Call<List<Group>>

    @GET("api/groups/{groupId}/members")
    fun getMembersOfGroup(@Path("groupId") groupId: Int): Call<List<GroupMember>>

    @PUT("api/groups/{groupId}/members/{userId}")
    fun removeMemberFromGroup(@Path("groupId") groupId: Int, @Path("userId") userId: Int): Call<GroupMember>

    // Payments
    @POST("api/payments")
    fun createPayment(@Body payment: Payment): Call<Payment>

    @PUT("api/payments/{paymentId}")
    fun updatePayment(@Path("paymentId") paymentId: Int, @Body payment: Payment): Call<Payment>

    @GET("api/payments/{paymentId}")
    fun getPaymentById(@Path("paymentId") paymentId: Int): Call<Payment>

    @GET("api/payments/groups/{groupId}")
    fun getPaymentsByGroup(@Path("groupId") groupId: Int): Call<List<Payment>>

    // Payment Splits
    @POST("api/payments/{paymentId}/splits")
    fun createPaymentSplit(@Path("paymentId") paymentId: Int, @Body paymentSplit: PaymentSplit): Call<PaymentSplit>

    @PUT("api/payments/{paymentId}/splits/{splitId}")
    fun updatePaymentSplit(@Path("paymentId") paymentId: Int, @Path("splitId") splitId: Int, @Body paymentSplit: PaymentSplit): Call<PaymentSplit>

    @GET("api/payments/{paymentId}/splits")
    fun getPaymentSplitsByPayment(@Path("paymentId") paymentId: Int): Call<List<PaymentSplit>>

}
