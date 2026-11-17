package com.neochildclinic.core.common

/**
 * Centralized constants for the application to ensure consistency.
 */
object Constants {
    // Standard date format used across the entire app (e.g., 9 May 2026)
    const val DATE_FORMAT = "d MMM yyyy"

    // Cross-language floor - also hardcoded as MIN_PASSWORD_LENGTH in
    // supabase/functions/manage-staff/index.ts and minimum_password_length in supabase/config.toml.
    const val MIN_PASSWORD_LENGTH = 12
    val PASSWORD_MIN_LENGTH_ERROR = "Password must be at least $MIN_PASSWORD_LENGTH characters"

    // Predefined "Type" options for a Next Vaccination / Due Vaccination entry
    // (mandatory field on the Add Vaccination screen's Next Vaccination section).
    val DUE_VACCINATION_TYPES = listOf(
        "Routine", "Booster", "Catch-up", "Travel", "Other"
    )
}
