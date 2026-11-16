package com.neochildclinic.core.sync

/**
 * Interface abstraction allowing repositories and ViewModels to trigger sync
 * without depending on concrete WorkManager schedulers or worker classes in core.
 */
interface SyncScheduler {
    fun scheduleSync()
    fun scheduleImmediateSync()
}
