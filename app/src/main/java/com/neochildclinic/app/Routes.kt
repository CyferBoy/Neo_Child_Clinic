package com.neochildclinic.app

object Routes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val ADD_PATIENT = "add_patient"
    const val PATIENT_LIST = "patient_list"
    const val PATIENT_DETAILS = "patient_details/{patientId}"
    const val EDIT_PATIENT = "edit_patient/{patientId}"
    const val EDIT_VACCINATION = "edit_vaccination/{vaccinationId}"
    const val VACCINE_INVENTORY = "vaccine_inventory"
    const val STATISTICS = "statistics"
    const val MONTHLY_FINANCE_DETAILS = "monthly_finance_details/{monthKey}"
    const val MILESTONE_PATIENTS = "milestone_patients/{milestoneKey}"
    const val FULL_REPORT = "full_report"
    // vaccineId is an optional query arg: absent (or empty) means "whole vaccine type", which is
    // what tapping the type row passes. Present means one brand, keyed by the stable catalog
    // id rather than the free-text brand name.
    const val VACCINE_DETAIL = "vaccine_detail/{type}?vaccineId={vaccineId}"
    const val BORROWED = "borrowed"
    const val DUE = "due"
    const val WASTE = "waste"
    const val ADD_VACCINE_STOCK = "add_vaccine_stock"
    const val ADD_VACCINE_DEFINITION = "add_vaccine_definition"
    const val EDIT_VACCINE_DEFINITION = "edit_vaccine_definition/{vaccineId}"
    const val ADD_BATCH = "add_batch/{vaccineId}/{brandName}"
    const val EDIT_BATCH = "edit_batch/{batchId}?vaccineId={vaccineId}&brandName={brandName}"
    const val STOCK_HISTORY = "stock_history"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val NOTIFICATION_SETTINGS = "notification_settings"
    const val INVENTORY_SETTINGS = "inventory_settings"
    const val BACKUP_SETTINGS = "backup_settings"
    const val SECURITY_SETTINGS = "security_settings"
    const val HELP_SUPPORT = "help_support"
    const val PRIVACY_POLICY = "privacy_policy"
    const val TERMS_OF_SERVICE = "terms_of_service"
    const val APP_UPDATE = "app_update"
    const val SYNC = "sync"
    const val AUDIT_LOGS = "audit_logs"
    const val MANAGE_STAFF = "manage_staff"
    const val STAFF_DETAILS = "staff_details/{staffId}"
    const val ADD_STAFF = "add_staff"
    const val EDIT_STAFF = "edit_staff/{staffId}"
    const val SEARCH = "search"
    const val ADD_VACCINE_FOR_PATIENT = "add_vaccine/{patientId}"
    const val ADD_CONSULTATION = "add_consultation/{patientId}"
    const val EDIT_CONSULTATION = "edit_consultation/{consultationId}"
    const val TODAY_PATIENTS = "today_patients?tab={tab}&highlightId={highlightId}"
    const val PERSONAL_REMINDERS = "personal_reminders"
    const val ADD_PERSONAL_REMINDER = "add_personal_reminder?patientId={patientId}"
    const val EDIT_PERSONAL_REMINDER = "edit_personal_reminder/{reminderId}"
    const val EXPENSES = "expenses"
    const val DOCTOR_TIMINGS = "doctor_timings"
    const val ADD_EXPENSE = "add_expense"
    const val EDIT_EXPENSE = "edit_expense/{expenseId}"
}

/**
 * `brandName` is free text typed in AddVaccineScreen, so it contains spaces ("Serum Institute")
 * and sometimes reserved URI characters. Interpolating it straight into a route builds a URI
 * Navigation cannot match, so the destination silently never opens. Encode on the way out.
 *
 * [enc] is percent-strict: URLEncoder emits '+' for a space, which is ambiguous once Navigation
 * has percent-decoded a query parameter, so '+' is rewritten to %20. That keeps decoding
 * unambiguous, including for a brand that genuinely contains a '+'.
 */
internal fun enc(value: String): String =
    java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

/** Inverse of [enc]. Needed for path segments, which Navigation does not decode for us. */
internal fun dec(value: String): String = java.net.URLDecoder.decode(value, "UTF-8")

/** brandName is a path segment here, so the destination decodes it via [dec]. */
internal fun addBatchRoute(vaccineId: String, brandName: String): String =
    "add_batch/${enc(vaccineId)}/${enc(brandName)}"

/** brandName is a query param here, so Navigation decodes it for us - do not decode again. */
internal fun editBatchRoute(batchId: String, vaccineId: String, brandName: String): String =
    "edit_batch/${enc(batchId)}?vaccineId=${enc(vaccineId)}&brandName=${enc(brandName)}"

/**
 * Upcoming drill-down. [vaccineId] null = whole type; non-null = one brand.
 * `type` is a path segment so the destination decodes it via [dec]; `vaccineId` is a query
 * param so Navigation already decodes it - do not decode it twice.
 */
internal fun vaccineDetailRoute(type: String, vaccineId: String?): String =
    if (vaccineId.isNullOrBlank()) {
        "vaccine_detail/${enc(type)}"
    } else {
        "vaccine_detail/${enc(type)}?vaccineId=${enc(vaccineId)}"
    }
