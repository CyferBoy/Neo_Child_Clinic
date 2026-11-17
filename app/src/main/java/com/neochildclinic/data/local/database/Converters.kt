package com.neochildclinic.data.local.database

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun fromList(value: List<String>?): String? {
        return value?.let { Json.encodeToString(it) }
    }

    @TypeConverter
    fun toList(value: String?): List<String>? {
        // fromList stores null as SQL NULL, never the literal "null"; that string is only
        // legacy/foreign-data tolerance. Dropping this check would decode it as ["null"].
        if (value == null || value.trim() == "null") return null
        if (value.isBlank()) return emptyList()
        return runCatching {
            Json.decodeFromString<List<String>>(value)
        }.getOrElse {
            value.split(",").map(String::trim).filter(String::isNotEmpty)
        }
    }
}
