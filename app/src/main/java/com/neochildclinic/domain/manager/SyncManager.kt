package com.neochildclinic.domain.manager

interface SyncManager {
    fun scheduleSync()
    fun scheduleImmediateSync()
    fun cancelAllSync()
}