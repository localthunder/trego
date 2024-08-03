package com.splitter.splitter.data.local.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.sql.Timestamp

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