package com.neochildclinic.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared wrapper for repository pull-from-Cloud refresh: run [block] on IO,
 * log failures, optionally rethrow (for callers that surface errors to UI).
 */
internal suspend fun cloudRefresh(tag: String, rethrow: Boolean = false, block: suspend () -> Unit) {
    withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: Exception) {
            Log.e(tag, "Refresh failed", e)
            if (rethrow) throw e
        }
    }
}
