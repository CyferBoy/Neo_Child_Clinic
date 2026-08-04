package com.neochildclinic.core.constants

/**
 * Centralized constants for the application to ensure consistency.
 */
object Constants {
    // Admin email addresses for managing staff
    val ADMIN_EMAILS = listOf(
        "anjumnadeem580@gmail.com"
    )

    // Standard date format used across the entire app (e.g., 9 May 2026)
    const val DATE_FORMAT = "d MMM yyyy"
    
    // Pre-defined list of common vaccine brands for suggestions in the Add Vaccination screen
    val COMMON_VACCINES = listOf(
        "Pentaxim", "Hexaxim", "Rotavac", "Rotasiil", "MMR", "Varilrix", "Pneumosil", "Synflorix", 
        "SII BCG", "Hepatitis B", "OPV", "IPV", "Penta", "Rotavirus", "PCV", "fIPV", "Vitamin A", 
        "DPT Booster", "Measles-Rubella (MR)", "JE", "TD", "Flu (Influenza)", "Typhoid", 
        "Chickenpox", "Hepatitis A", "HPV", "Meningococcal", "Cholera", "Rabies"
    )
}
