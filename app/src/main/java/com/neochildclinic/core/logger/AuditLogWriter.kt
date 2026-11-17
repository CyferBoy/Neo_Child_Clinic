package com.neochildclinic.core.logger

import com.neochildclinic.domain.model.AuditLog

interface AuditLogWriter {
    suspend fun recordLog(auditLog: AuditLog, transactionGroupId: String? = null)
}
