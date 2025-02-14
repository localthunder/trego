package com.splitter.splittr.utils

import android.content.Context
import android.util.Log
import com.splitter.splittr.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

object ServerIdUtil {
    suspend fun getServerId(localId: Int, entityType: String, context: Context): Int? {
        val database: AppDatabase by lazy { AppDatabase.getDatabase(context) }
        return when (entityType) {
            "groups" -> database.groupDao().getGroupByIdSync(localId)?.serverId
            "users" -> database.userDao().getUserByIdDirect(localId)?.serverId
            "group_members" -> database.groupMemberDao().getGroupMemberByIdSync(localId)?.serverId
            "payments" -> database.paymentDao().getPaymentById(localId).firstOrNull()?.serverId
            "payment_splits" -> database.paymentSplitDao().getPaymentSplitById(localId)?.serverId
            "currency_conversions" -> database.currencyConversionDao().getConversionByIdSync(localId)?.serverId
            else -> {
                Log.w("ServerIdUtil", "Unknown entity type: $entityType")
                null
            }
        }
    }

    suspend fun getLocalId(serverId: Int, entityType: String, context: Context): Int? {
        val database: AppDatabase by lazy { AppDatabase.getDatabase(context) }
        return when (entityType) {
            "groups" -> database.groupDao().getAllGroups().first()
                .find { it.serverId == serverId }?.id
            "users" -> database.userDao().getAllUsers().first()
                .find { it.serverId == serverId }?.userId
            "group_members" -> database.groupMemberDao().getGroupMemberByServerId(serverId)?.id
            "payments" -> database.paymentDao().getPaymentByServerId(serverId)?.id
            "payment_splits" -> database.paymentSplitDao().getPaymentSplitByServerId(serverId)?.id
            "currency_conversions" -> database.currencyConversionDao().getConversionByServerId(serverId)?.id
            else -> {
                Log.w("ServerIdUtil", "Unknown entity type: $entityType")
                null
            }
        }
    }

    suspend fun saveIdMapping(localId: Int, serverId: Int, entityType: String, context: Context) {
        val database: AppDatabase by lazy { AppDatabase.getDatabase(context) }
        try {
            when (entityType) {
                "groups" -> {
                    val group = database.groupDao().getGroupByIdSync(localId)
                    group?.let {
                        database.groupDao().updateGroup(it.copy(serverId = serverId))
                    }
                }
                "users" -> {
                    val user = database.userDao().getUserByIdDirect(localId)
                    user?.let {
                        database.userDao().updateUserDirect(it.copy(serverId = serverId))
                    }
                }
                "group_members" -> {
                    val member = database.groupMemberDao().getGroupMemberByIdSync(localId)
                    member?.let {
                        database.groupMemberDao().updateGroupMember(it.copy(serverId = serverId))
                    }
                }
                "payments" -> {
                    val payment = database.paymentDao().getPaymentById(localId).firstOrNull()
                    payment?.let {
                        database.paymentDao().updatePayment(it.copy(serverId = serverId))
                    }
                }
                "payment_splits" -> {
                    val split = database.paymentSplitDao().getPaymentSplitById(localId)
                    split?.let {
                        database.paymentSplitDao().updatePaymentSplit(it.copy(serverId = serverId))
                    }
                }
                else -> Log.w("ServerIdUtil", "Unknown entity type: $entityType")
            }
        } catch (e: Exception) {
            Log.e("ServerIdUtil", "Error saving ID mapping for $entityType", e)
            throw e
        }
    }
}