package com.neochildclinic.core.model

enum class SyncOperation {
    CREATE,
    UPDATE,
    DELETE
}

enum class SyncStatus {
    PENDING,
    SYNCING,
    SYNCED,
    FAILED
}

enum class SyncPriority {
    LOW,
    MEDIUM,
    HIGH
}
