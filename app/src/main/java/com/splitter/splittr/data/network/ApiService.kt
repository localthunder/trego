package com.splitter.splittr.data.network

import android.net.Uri
import com.google.gson.annotations.SerializedName
import com.splitter.splittr.data.local.dataClasses.AuthResponse
import com.splitter.splittr.data.local.dataClasses.GroupMemberResponse
import com.splitter.splittr.data.local.dataClasses.GroupMemberWithGroupResponse
import com.splitter.splittr.data.local.dataClasses.LoginRequest
import com.splitter.splittr.data.local.dataClasses.PaymentSyncResponse
import com.splitter.splittr.data.local.dataClasses.RequisitionRequest
import com.splitter.splittr.data.local.dataClasses.RequisitionResponseWithRedirect
import com.splitter.splittr.data.local.dataClasses.UploadResponsed
import com.splitter.splittr.data.local.entities.GroupMemberEntity
import com.splitter.splittr.data.repositories.TransactionRepository
import com.splitter.splittr.data.model.BankAccount
import com.splitter.splittr.data.model.Group
import com.splitter.splittr.data.model.GroupMember
import com.splitter.splittr.data.model.Institution
import com.splitter.splittr.data.model.Payment
import com.splitter.splittr.data.model.PaymentSplit
import com.splitter.splittr.data.model.Requisition
import com.splitter.splittr.data.model.Transaction
import com.splitter.splittr.data.model.User
import com.splitter.splittr.data.model.*
import com.splitter.splittr.ui.screens.RegisterRequest
import com.splitter.splittr.ui.screens.UserBalanceWithCurrency
import com.splitter.splittr.ui.viewmodels.GroupViewModel
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @GET("/api/users/{userId}")
    suspend fun getUserById(@Path("userId") userId: Int): User

    @POST("/api/users/batch")
    suspend fun getUsersByIds(@Body userIds: List<Int>): List<User>

    @POST("/api/users/create")
    suspend fun createUser(@Body user: User): User

    @PUT("/api/users/{userId}")
    suspend fun updateUser(@Path("userId") userId: Int, @Body user: User): User

    @GET("/api/gocardless/institutions")
    suspend fun getInstitutions(@Query("country") country: String): List<Institution>

    @GET("/api/gocardless/institution/{institutionId}")
    suspend fun getInstitutionById(@Path("institutionId") institutionId: String): Institution

    @POST("/api/gocardless/insertInstitution")
    suspend fun insertInstitution(@Body institution: Institution): Institution

    @POST("/api/auth/register")
    suspend fun registerUser(@Body registerRequest: RegisterRequest): AuthResponse

    @POST("/api/auth/login")
    suspend fun loginUser(@Body loginRequest: LoginRequest): AuthResponse

    @POST("/auth/refresh-token")
    suspend fun refreshToken(@Body body: Map<String, String>): AuthResponse

    @POST("/api/gocardless/requisition")
    suspend fun createRequisition(@Body requisitionRequest: RequisitionRequest): RequisitionResponseWithRedirect

    @POST("/api/gocardless/requisition/addRequisition")
    suspend fun addRequisition(@Body requisition: Requisition): Requisition

    @GET("/api/gocardless/accounts/{requisitionId}")
    suspend fun getBankAccounts(@Path("requisitionId") requisitionId: String): List<BankAccount>

    @POST("/api/gocardless/addAccount")
    suspend fun addAccount(@Body account: BankAccount): BankAccount

    @PUT("accounts/{accountId}")
    suspend fun updateAccount(@Path("accountId") accountId: String, @Body account: BankAccount): BankAccount

    @GET("/api/gocardless/requisition/{reference}")
    suspend fun getRequisitionByReference(@Path("reference") reference: String): Requisition

    @GET("/api/gocardless/requisition/user/{userId}")
    suspend fun getRequisitionsByUserId(@Path("userId") userId: Int): List<Requisition>

    @GET("/api/gocardless/listUserAccounts")
    suspend fun listUserAccounts(): List<BankAccount>

    @GET("/api/gocardless/transactions/{userId}")
    suspend fun getTransactionsByUserId(@Path("userId") userId: Int): TransactionRepository.TransactionsApiResponse

    @GET("api/transactions/{transactionId}")
    suspend fun getTransactionById(@Path("transactionId") transactionId: String): Transaction

    @GET("/api/gocardless/transactions/recent/{userId}")
    suspend fun getRecentTransactions(
        @Path("userId") userId: Int,
        @Query("date_from") dateFrom: String
    ): List<Transaction>

    @GET("/api/gocardless/transactions/non-recent/{userId}")
    suspend fun getNonRecentTransactions(
        @Path("userId") userId: Int,
        @Query("date_to") dateTo: String
    ): List<Transaction>

    @POST("/api/gocardless/transactions")
    suspend fun createTransaction(@Body transaction: Transaction): Transaction

    @GET("/api/gocardless/{userId}/accounts")
    suspend fun getUserAccounts(@Path("userId") userId: Int): List<BankAccount>

    @POST("/api/groups")
    suspend fun createGroup(@Body group: Group): Group

    @GET("/api/groups/{id}")
    suspend fun getGroupById(@Path("id") id: Int): Group

    @PUT("/api/groups/{id}")
    suspend fun updateGroup(@Path("id") id: Int, @Body group: Group): Group

    @POST("api/groups/{groupId}/members")
    suspend fun addMemberToGroup(
        @Path("groupId") groupId: Int,
        @Body groupMember: GroupMember
    ): GroupMember

    @PUT("/api/groups/{groupId}/members/{memberId}")  // Changed path to be more RESTful
    suspend fun updateGroupMember(
        @Path("groupId") groupId: Int,
        @Path("memberId") memberId: Int,
        @Body groupMember: GroupMember
    ): GroupMemberResponse

    @GET("api/groups/{groupId}/invite-link")
    suspend fun getGroupInviteLink(@Path("groupId") groupId: Int): Map<String, String>

    @GET("api/groups/user/{userId}")
    suspend fun getGroupsByUserId(@Path("userId") userId: Int): List<Group>

    @GET("users/{userId}/group-memberships")
    suspend fun getAllGroupMembershipsForUser(@Path("userId") userId: Int): List<GroupMember>

    @GET("api/gocardless/changes")
    suspend fun getAccountsSince(
        @Query("since") timestamp: Long,
        @Query("userId") userId: Int
    ): List<BankAccount>

    @GET("api/groups/changes")
    suspend fun getGroupsSince(
        @Query("since") timestamp: Long,
        @Query("userId") userId: Int
    ): List<Group>

    @GET("api/groups/members/changes")
    suspend fun getGroupMembersSince(
        @Query("since") timestamp: Long,
        @Query("userId") userId: Int
    ): List<GroupMemberWithGroupResponse>

    @GET("api/payments/changes")
    suspend fun getPaymentsSince(
        @Query("since") timestamp: Long,
        @Query("userId") userId: Int
    ): PaymentSyncResponse

    @GET("api/gocardless/requisition/changes")
    suspend fun getRequisitionsSince(
        @Query("since") timestamp: Long,
        @Query("userId") userId: Int
    ): List<Requisition>

    @GET("api/gocardless/transaction/changes")
    suspend fun getTransactionsSince(
        @Query("since") timestamp: Long,
        @Query("userId") userId: Int
    ): List<Transaction>

    @GET("api/users/changes")
    suspend fun getUsersSince(
        @Query("since") timestamp: Long,
        @Query("userId") userId: Int
    ): List<User>


    @GET("api/groups/{groupId}/members")
    suspend fun getMembersOfGroup(@Path("groupId") groupId: Int): List<GroupMember>

    @PUT("api/groups/members/{memberId}")
    suspend fun removeMemberFromGroup(@Path("memberId") memberId: Int): GroupMember

    @GET("api/groups/{groupId}/balances")
    suspend fun getGroupBalances(@Path("groupId") groupId: Int): List<UserBalanceWithCurrency>

    @POST("api/payments")
    suspend fun createPayment(@Body payment: Payment): Payment

    @PUT("api/payments/{paymentId}")
    suspend fun updatePayment(@Path("paymentId") paymentId: Int, @Body payment: Payment): Payment

    @GET("api/payments/{paymentId}")
    suspend fun getPaymentById(@Path("paymentId") paymentId: Int): Payment

    @GET("api/payments/{transactionId}")
    suspend fun getPaymentByTransactionId(@Path("transactionId") transactionId: String): Payment

    @GET("api/payments/groups/{groupId}")
    suspend fun getPaymentsByGroup(@Path("groupId") groupId: Int): List<Payment>

    @POST("api/payments/{paymentId}/splits")
    suspend fun createPaymentSplit(
        @Path("paymentId") paymentId: Int,
        @Body paymentSplit: PaymentSplit
    ): PaymentSplit

    @PUT("api/payments/{paymentId}/splits/{splitId}")
    suspend fun updatePaymentSplit(
        @Path("paymentId") paymentId: Int,
        @Path("splitId") splitId: Int,
        @Body paymentSplit: PaymentSplit
    ): PaymentSplit

    @GET("api/payments/{paymentId}/splits")
    suspend fun getPaymentSplitsByPayment(@Path("paymentId") paymentId: Int): List<PaymentSplit>

    @POST("api/payments/{paymentId}/archive")
    suspend fun archivePayment(@Path("paymentId") paymentId: Int)

    @POST("api/payments/{paymentId}/restore")
    suspend fun restorePayment(@Path("paymentId") paymentId: Int)

    @Multipart
    @POST("/api/storage/uploadGroupImage/{groupId}")
    suspend fun uploadGroupImage(
        @Path("groupId") groupId: Int,
        @Part image: MultipartBody.Part
    ): UploadResponsed

    @PUT("accounts/{accountId}/needs-reauthentication")
    suspend fun updateNeedsReauthentication(
        @Path("accountId") accountId: String,
        @Body needsReauthentication: Boolean
    ): Unit

    @GET("accounts/reauthentication")
    suspend fun getAccountsNeedingReauthentication(): List<BankAccount>

    @GET("accounts/{accountId}/reauthentication")
    suspend fun getNeedsReauthentication(@Path("accountId") accountId: String): Map<String, Boolean>

    @PUT("accounts/{accountId}/reauth")
    suspend fun updateAccountAfterReauth(
        @Path("accountId") accountId: String,
        @Body newRequisitionId: Map<String, String>
    ): Response<Unit>
}