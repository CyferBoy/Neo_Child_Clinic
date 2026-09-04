package com.neochildclinic.core.utils

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Supabase's Auth SDK exposes `user_metadata` values as kotlinx.serialization [JsonElement]s.
 * Calling `.toString()` directly on a JsonElement re-serializes it back to JSON source text -
 * a string value like `admin` becomes the literal text `"admin"` **including the quote
 * characters**, not the plain content.
 *
 * That corrupted, still-quoted string then:
 *  - fails `UserRole.valueOf(...)` (no enum constant is named `"admin"` with quotes), which
 *    silently falls back to the nurse role via the surrounding try/catch, and
 *  - shows up with stray literal quote marks around names shown in the UI.
 *
 * Use this instead of `.toString()` anywhere a `user_metadata` value needs to be read as a
 * plain Kotlin String.
 */
fun JsonElement?.metadataString(): String? = (this as? JsonPrimitive)?.contentOrNull
