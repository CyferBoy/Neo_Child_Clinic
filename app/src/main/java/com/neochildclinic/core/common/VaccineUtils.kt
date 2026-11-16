package com.neochildclinic.core.common

object VaccineUtils {
    /**
     * Removes parentheses from vaccine names if they exist (e.g., "Hepatitis B (HepB)" -> "HepB").
     */
    fun cleanVaccineName(name: String): String =
        if (name.contains("(") && name.endsWith(")")) {
            name.substringAfter("(").substringBeforeLast(")").trim()
        } else {
            name
        }
}
