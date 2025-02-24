package com.helgolabs.trego.data.local.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.helgolabs.trego.data.local.dataClasses.TransactionStatus
import java.sql.Timestamp
import java.util.concurrent.atomic.AtomicInteger

class Converters {

    @TypeConverter
    fun fromString(value: String?): List<String>? {
        val listType = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromList(list: List<String>?): String? {
        return Gson().toJson(list)
    }

    @TypeConverter
    fun fromTimestamp(value: Timestamp?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toTimestamp(value: String?): Timestamp? {
        return value?.let { Timestamp.valueOf(it) }
    }

    @TypeConverter
    fun fromTransactionStatus(value: TransactionStatus?): String? {
        return value?.name
    }

    @TypeConverter
    fun toTransactionStatus(value: String?): TransactionStatus? {
        return if (value == null) null else try {
            TransactionStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            TransactionStatus.BOOKED  // Default to BOOKED for backward compatibility
        }
    }
}