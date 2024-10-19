package com.splitter.splittr.utils

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter


class DateDeserializer : JsonDeserializer<Long> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Long {
        val dateStr = json.asString
        // Parse the ISO 8601 date string into a Unix timestamp (Long)
        val localDateTime = LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME)
        return localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}

fun createGson(): Gson {
    return GsonBuilder()
        .registerTypeAdapter(Long::class.java, DateDeserializer()) // Custom deserializer for dates
        .create()
}