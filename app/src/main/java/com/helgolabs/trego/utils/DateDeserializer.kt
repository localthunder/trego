package com.helgolabs.trego.utils

import android.util.Log
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

        return try {
            // First try parsing as timestamp
            if (dateStr.matches(Regex("^\\d+$"))) {
                return dateStr.toLong()
            }

            // Then try parsing as ISO date string
            val localDateTime = try {
                LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME)
            } catch (e: Exception) {
                // Fallback to simpler date format if ISO parsing fails
                LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            }

            localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            Log.e("DateDeserializer", "Error parsing date: $dateStr", e)
            System.currentTimeMillis() // Fallback to current time if parsing fails
        }
    }
}

// Keep your existing Gson creation function
fun createGson(): Gson {
    return GsonBuilder()
        .registerTypeAdapter(Long::class.java, DateDeserializer())
        .create()
}