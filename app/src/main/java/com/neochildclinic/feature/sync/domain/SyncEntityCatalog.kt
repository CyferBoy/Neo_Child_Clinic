package com.neochildclinic.feature.sync.domain

/**
 * Canonical set of entity names the sync queue may carry. Every name here must be registered
 * in SYNC_ENTITY_REGISTRY (data layer): enqueue() with an unknown name makes SyncUploader throw
 * "Unknown entity", the error is classified permanent, and the row can never be retried by the
 * user. REMINDER_AUDIT was such a case - a merge enqueued it while the registry never had it,
 * leaving a FAILED row on the Sync screen after every merge.
 *
 * Production source of truth for the registry test, replacing a regex scan of source files
 * that depended on the process working directory and silently passed when no files were found.
 *
 * shortcut: enqueue() still takes a raw String at call sites, so adding a name here does not make
 * it compiler-enforced; upgrade to a typed enum when those call sites are migrated.
 */
internal object SyncEntityCatalog {
    val ENQUEUED_ENTITY_NAMES: Set<String> = setOf(
        "PATIENT", "VACCINATION", "VISIT", "VACCINATION_ITEM", "WASTE", "REMINDERS",
        "VACCINE", "BATCH", "TRANSACTION", "INVENTORY_TRANSACTION", "PATIENT_NOTE",
        "FINANCE", "EXPENSE", "PROFILE", "STAFF", "BORROW", "BORROW_RETURN",
        "AUDIT_LOG", "CONSULTATION", "CONSULTATION_TODO", "VACCINATION_TODO",
        "PERSONAL_REMINDER", "DOCTOR_WEEKLY_SLOT", "DOCTOR_SLOT_EXCEPTION"
    )
}
