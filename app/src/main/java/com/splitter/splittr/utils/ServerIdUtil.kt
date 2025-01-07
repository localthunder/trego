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
            else -> {
                Log.w("ServerIdUtil", "Unknown entity type: $entityType")
                null
            }
        }
    }
}