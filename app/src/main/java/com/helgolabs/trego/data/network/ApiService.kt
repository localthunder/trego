package com.helgolabs.trego.data.network

import android.net.Uri
import com.google.gson.annotations.SerializedName
import com.helgolabs.trego.data.local.dataClasses.ApiResponse
import com.helgolabs.trego.data.local.dataClasses.AuthResponse
import com.helgolabs.trego.data.local.dataClasses.CurrencyConversionResponse
import com.helgolabs.trego.data.local.dataClasses.GroupMemberResponse
import com.helgolabs.trego.data.local.dataClasses.GroupMemberWithGroupResponse
import com.helgolabs.trego.data.local.dataClasses.LoginRequest
import com.helgolabs.trego.data.local.dataClasses.MergeUsersRequest
import com.helgolabs.trego.data.local.dataClasses.MergeUsersResponse
import com.helgolabs.trego.data.local.dataClasses.PaymentSyncResponse
import com.helgolabs.trego.data.local.dataClasses.RegisterRequest
import com.helgolabs.trego.data.local.dataClasses.RequisitionRequest
import com.helgolabs.trego.data.local.dataClasses.RequisitionResponseWithRedirect
import com.helgolabs.trego.data.local.dataClasses.UploadResponsed
import com.helgolabs.trego.data.local.entities.GroupMemberEntity
import com.helgolabs.trego.data.repositories.TransactionRepository
import com.helgolabs.trego.data.model.BankAccount
import com.helgolabs.trego.data.model.Group
import com.helgolabs.trego.data.model.GroupMember
import com.helgolabs.trego.data.model.Institution
import com.helgolabs.trego.data.model.Payment
import com.helgolabs.trego.data.model.PaymentSplit
import com.helgolabs.trego.data.model.Requisition
import com.helgolabs.trego.data.model.Transaction
import com.helgolabs.trego.data.model.User
import com.helgolabs.trego.data.model.*
import com.helgolabs.trego.ui.screens.UserBalanceWithCurrency
import com.helgolabs.trego.ui.viewmodels.GroupViewModel
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

    @POST("users/merge")
    suspend fun mergeUsers(@Body request: MergeUsersRequest): MergeUsersResponse

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

    @POST("/api/auth/request-reset")
    suspend fun requestPasswordReset(@Body data: Map<String, String>): AuthResponse

    @POST("/api/auth/reset-password")
    suspend fun resetPassword(@Body data: Map<String, String>): AuthResponse

    @POST("api/auth/reset-token/authenticated")
    suspend fun getAuthenticatedResetToken(): AuthResponse

    @POST("api/notifications/register-device")
    suspend fun registerDeviceToken(@Body deviceToken: DeviceToken): DeviceToken

    @DELETE("api/notifications/unregister-device/{tokenId}")
    suspend fun unregisterDeviceToken(@Path("tokenId") tokenId: Int)

    @PUT("api/users/{userId}/username")
    suspend fun updateUsername(@Path("userId") userId: Int, @Body request: Map<String, String>): User

    @POST("/api/gocardless/requisition")
    suspend fun createRequisition(@Body requisitionRequest: RequisitionRequest): RequisitionResponseWithRedirect

    @POST("/api/gocardless/requisition/addRequisition")
    suspend fun addRequisition(@Body requisition: Requisition): Requisition

    @GET("/api/gocardless/accounts/{requisitionId}")
    suspend fun getBankAccounts(@Path("requisitionId") requisitionId: String): List<BankAccount>

    @POST("/api/gocardless/addAccount")
    suspend fun addAccount(@Body account: BankAccount): BankAccount

    @PUT("/api/gocardless/accounts/{accountId}")
    suspend fun updateAccount(@Path("accountId") accountId: String, @Body account: BankAccount): ApiResponse<BankAccount>

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

    @GET("/api/gocardless/transactions/account/{accountId}")
    suspend fun getAccountTransactions(@Path("accountId") accountId: String, ): List<Transaction>

    @GET("/api/gocardless/{userId}/accounts")
    suspend fun getUserAccounts(@Path("userId") userId: Int): List<BankAccount>

    @DELETE("/api/gocardless/accounts/{accountId}")
    suspend fun deleteBankAccount(@Path("accountId") accountId: String): Response<Unit>

    @POST("/api/groups")
    suspend fun createGroup(@Body group: Group): Group

    @GET("/api/groups/{id}")
    suspend fun getGroupById(@Path("id") id: Int): Group

    @PUT("/api/groups/{id}")
    suspend fun updateGroup(@Path("id") id: Int, @Body group: Group): Group

    @POST("api/groups/archive/{groupId}")
    suspend fun archiveGroup(
        @Path("groupId") groupId: Int,
        @Query("userId") userId: Int
    ): Unit

    @POST("api/groups/restore/{groupId}")
    suspend fun restoreGroup(
        @Path("groupId") groupId: Int,
        @Query("userId") userId: Int
    ): Unit

    @GET("api/groups/{groupId}/is-archived")
    suspend fun isGroupArchived(
        @Path("groupId") groupId: Int,
        @Query("userId") userId: Int
    ): Boolean

    @GET("api/groups/archives/{userId}")
    suspend fun getArchivedGroups(@Path("userId") userId: Int): List<Map<String, Any>>

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

    @GET("api/payments/currency-conversions/changes")
    suspend fun getCurrencyConversionsSince(
        @Query("since") timestamp: Long,
        @Query("userId") userId: Int
    ): CurrencyConversionResponse

    @GET("api/groups/default-splits/changes")
    suspend fun getGroupDefaultSplitsSince(
        @Query("since") timestamp: Long,
        @Query("userId") userId: Int
    ): List<GroupDefaultSplit>


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

    @DELETE("api/payments/{paymentId}/splits")
    suspend fun deleteAllSplitsForPayment(@Path("paymentId") paymentId: Int): Response<Unit>

    @DELETE("api/payments/{paymentId}/splits/{splitId}")
    suspend fun deletePaymentSplit(
        @Path("paymentId") paymentId: Int,
        @Path("splitId") splitId: Int
    ): Response<Unit>

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

    @PUT("/api/accounts/{accountId}/needs-reauthentication")
    suspend fun updateNeedsReauthentication(
        @Path("accountId") accountId: String,
        @Body needsReauthentication: Boolean
    ): Unit

    @GET("/api/accounts/reauthentication")
    suspend fun getAccountsNeedingReauthentication(): List<BankAccount>

    @GET("/api/accounts/{accountId}/reauthentication")
    suspend fun getNeedsReauthentication(@Path("accountId") accountId: String): Map<String, Boolean>

    @PUT("/api/accounts/{accountId}/reauth")
    suspend fun updateAccountAfterReauth(
        @Path("accountId") accountId: String,
        @Body newRequisitionId: Map<String, String>
    ): Response<Unit>

    @GET("/api/health")
    suspend fun healthCheck(): Response<Unit>

    @POST("/api/payments/currency-conversions")
    suspend fun createCurrencyConversion(@Body conversion: CurrencyConversion): CurrencyConversion

    @PUT("/api/payments/currency-conversions/{id}")
    suspend fun updateCurrencyConversion(@Path("id") id: Int, @Body conversion: CurrencyConversion): CurrencyConversion

    @DELETE("api/payments/currency-conversions/{id}")
    suspend fun deleteCurrencyConversion(@Path("id") id: Int): Response<Unit>

    @POST("api/groups/join/{inviteCode}")
    suspend fun joinGroupByInvite(@Path("inviteCode") inviteCode: String): Group

    @GET("api/groups/{groupId}/default-splits")
    suspend fun getGroupDefaultSplits(@Path("groupId") groupId: Int): List<GroupDefaultSplit>

    @POST("api/groups/{groupId}/default-splits")
    suspend fun createGroupDefaultSplit(@Path("groupId") groupId: Int, @Body defaultSplit: GroupDefaultSplit): GroupDefaultSplit

    @PUT("api/groups/{groupId}/default-splits/{splitId}")
    suspend fun updateGroupDefaultSplit(@Path("groupId") groupId: Int, @Path("splitId") splitId: Int, @Body defaultSplit: GroupDefaultSplit): GroupDefaultSplit

    @POST("api/groups/{groupId}/default-splits/batch")
    suspend fun createOrUpdateBatchGroupDefaultSplits(@Path("groupId") groupId: Int, @Body defaultSplits: List<GroupDefaultSplit>): List<GroupDefaultSplit>

    @DELETE("api/groups/{groupId}/default-splits")
    suspend fun deleteGroupDefaultSplits(@Path("groupId") groupId: Int): Response<Unit>

    @DELETE("api/groups/{groupId}/default-splits/{splitId}")
    suspend fun deleteGroupDefaultSplit(@Path("groupId") groupId: Int, @Path("splitId") splitId: Int): Response<Unit>
}