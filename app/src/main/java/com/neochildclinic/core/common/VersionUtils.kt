package com.neochildclinic.core.common

object VersionUtils {
    /**
     * Compares [currentVersion] and [newVersion] semantically.
     * Returns true ONLY if [newVersion] is strictly newer than [currentVersion].
     * If [newVersion] is identical to or older than [currentVersion], returns false.
     */
    fun isNewerVersion(currentVersion: String, newVersion: String): Boolean {
        val curParts = parseVersionParts(currentVersion)
        val newParts = parseVersionParts(newVersion)

        if (curParts == null || newParts == null) {
            val cleanCur = normalizeVersion(currentVersion)
            val cleanNew = normalizeVersion(newVersion)
            return cleanCur.isNotBlank() && cleanNew.isNotBlank() && cleanCur != cleanNew
        }

        val maxLen = maxOf(curParts.size, newParts.size)
        for (i in 0 until maxLen) {
            val cur = curParts.getOrElse(i) { 0 }
            val new = newParts.getOrElse(i) { 0 }
            if (new > cur) return true
            if (new < cur) return false
        }
        return false // Exactly equal
    }

    fun normalizeVersion(rawVersion: String): String {
        var v = rawVersion.trim()
        while (v.startsWith("v", ignoreCase = true) || v.startsWith(".")) {
            v = v.substring(1).trim()
        }
        return v.substringBefore("-").substringBefore("+").trim()
    }

    fun parseVersionParts(version: String): List<Int>? {
        val clean = normalizeVersion(version)
        if (clean.isEmpty()) return null
        val parts = clean.split(".")
        val intParts = parts.mapNotNull { it.toIntOrNull() }
        if (intParts.isEmpty() || intParts.size != parts.size) return null
        return intParts
    }
}
