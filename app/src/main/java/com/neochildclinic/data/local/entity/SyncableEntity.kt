package com.neochildclinic.data.local.entity

/**
 * Common abstraction for Room entities that participate in offline-first data synchronization.
 * Exposes whether the entity state is synced with the remote backend.
 */
interface SyncableEntity {
    val isSynced: Boolean
}
