package com.splitter.splittr.data.local.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.sql.Timestamp
import java.util.concurrent.atomic.AtomicInteger

object LocalIdGenerator {
    private val counter = AtomicInteger(99000000) // Start from 99 million

    fun nextId(): Int {
        return counter.getAndIncrement()
    }

    fun toLocalId(value: Int): Int {
        return if (value >= 99000000) value else nextId()
    }
}

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
}