package com.neochildclinic.domain.repository

import com.neochildclinic.domain.model.AutoBackupSettings

/** Automatic-backup scheduling operations used by BackupViewModel. */
interface BackupAutoScheduler {
    fun schedule(settings: AutoBackupSettings)
    fun cancel()
}
