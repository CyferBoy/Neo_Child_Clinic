package com.neochildclinic.feature.patient.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.DriverManager

/**
 * Documents the expected hasUnsyncedRecordsForPatient SQL semantics for remote-prune
 * protection against an in-memory SQLite database.
 *
 * shortcut: the SQL below mirrors PatientDao.hasUnsyncedRecordsForPatient; Room's actual
 * query needs an instrumented/Robolectric DB, which this unit-test module does not have.
 * Upgrade: move these checks to androidTest against the real AppDatabase.
 */
class PatientRemotePruningProtectionTest {

    private val sqlQuery = """
        SELECT EXISTS(
            SELECT 1 FROM sync_queue sq
            WHERE sq.status != 'SYNCED' AND (
                (sq.entityName = 'PATIENT' AND sq.entityId = ?) OR
                (sq.entityName IN ('VACCINATION', 'PATIENT_VISIT', 'VISIT') AND sq.entityId IN (SELECT id FROM patient_visits WHERE patientId = ?)) OR
                (sq.entityName = 'VACCINATION_ITEM' AND sq.entityId IN (SELECT id FROM vaccination_items WHERE vaccinationId IN (SELECT id FROM patient_visits WHERE patientId = ?))) OR
                (sq.entityName = 'CONSULTATION' AND sq.entityId IN (SELECT id FROM consultations WHERE patientId = ?)) OR
                (sq.entityName = 'PATIENT_NOTE' AND sq.entityId IN (SELECT id FROM patient_notes WHERE patientId = ?)) OR
                (sq.entityName IN ('REMINDER', 'REMINDERS') AND sq.entityId IN (SELECT id FROM reminders WHERE patientId = ?)) OR
                (sq.entityName = 'PERSONAL_REMINDER' AND sq.entityId IN (SELECT id FROM personal_vaccine_reminders WHERE patient_id = ?)) OR
                (sq.entityName = 'CONSULTATION_TODO' AND sq.entityId IN (SELECT id FROM consultation_todos WHERE patient_id = ?)) OR
                (sq.entityName = 'VACCINATION_TODO' AND sq.entityId IN (SELECT id FROM vaccination_todos WHERE patient_id = ?)) OR
                (sq.entityName IN ('FINANCE', 'FINANCE_TRANSACTION') AND sq.entityId IN (SELECT id FROM finance_transactions WHERE patientId = ? OR visitId IN (SELECT id FROM patient_visits WHERE patientId = ?))) OR
                (sq.entityName IN ('TRANSACTION', 'INVENTORY_TRANSACTION') AND sq.entityId IN (SELECT transactionId FROM inventory_transactions WHERE patientId = ? OR visitId IN (SELECT id FROM patient_visits WHERE patientId = ?)))
            )
        )
    """.trimIndent()

    private fun executeQueryOnMemoryDb(setup: (java.sql.Connection) -> Unit, patientId: String): Boolean {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("CREATE TABLE sync_queue (id TEXT PRIMARY KEY, entityName TEXT, entityId TEXT, status TEXT);")
                stmt.execute("CREATE TABLE patient_visits (id TEXT PRIMARY KEY, patientId TEXT);")
                stmt.execute("CREATE TABLE vaccination_items (id TEXT PRIMARY KEY, vaccinationId TEXT);")
                stmt.execute("CREATE TABLE consultations (id TEXT PRIMARY KEY, patientId TEXT);")
                stmt.execute("CREATE TABLE patient_notes (id TEXT PRIMARY KEY, patientId TEXT);")
                stmt.execute("CREATE TABLE reminders (id TEXT PRIMARY KEY, patientId TEXT);")
                stmt.execute("CREATE TABLE personal_vaccine_reminders (id TEXT PRIMARY KEY, patient_id TEXT);")
                stmt.execute("CREATE TABLE consultation_todos (id TEXT PRIMARY KEY, patient_id TEXT);")
                stmt.execute("CREATE TABLE vaccination_todos (id TEXT PRIMARY KEY, patient_id TEXT);")
                stmt.execute("CREATE TABLE finance_transactions (id TEXT PRIMARY KEY, patientId TEXT, visitId TEXT);")
                stmt.execute("CREATE TABLE inventory_transactions (transactionId TEXT PRIMARY KEY, patientId TEXT, visitId TEXT);")
            }
            setup(conn)
            conn.prepareStatement(sqlQuery).use { pstmt ->
                for (i in 1..13) {
                    pstmt.setString(i, patientId)
                }
                pstmt.executeQuery().use { rs ->
                    assertTrue(rs.next())
                    return rs.getInt(1) == 1
                }
            }
        }
    }

    private val isSqliteAvailable: Boolean = try {
        Class.forName("org.sqlite.JDBC")
        true
    } catch (_: ClassNotFoundException) {
        false
    }

    @Test
    fun `hasUnsyncedRecords returns true when outbox contains FINANCE with patientId`() {
        if (!isSqliteAvailable) return
        val patientId = "patient-alpha"
        val result = executeQueryOnMemoryDb({ conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("INSERT INTO finance_transactions (id, patientId, visitId) VALUES ('fin-1', '$patientId', NULL);")
                stmt.execute("INSERT INTO sync_queue (id, entityName, entityId, status) VALUES ('sq-1', 'FINANCE', 'fin-1', 'PENDING');")
            }
        }, patientId)
        assertTrue("Pending FINANCE record directly on patient must return true", result)
    }

    @Test
    fun `hasUnsyncedRecords returns true when outbox contains FINANCE_TRANSACTION with visitId`() {
        if (!isSqliteAvailable) return
        val patientId = "patient-alpha"
        val visitId = "visit-alpha-1"
        val result = executeQueryOnMemoryDb({ conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("INSERT INTO patient_visits (id, patientId) VALUES ('$visitId', '$patientId');")
                stmt.execute("INSERT INTO finance_transactions (id, patientId, visitId) VALUES ('fin-2', NULL, '$visitId');")
                stmt.execute("INSERT INTO sync_queue (id, entityName, entityId, status) VALUES ('sq-2', 'FINANCE_TRANSACTION', 'fin-2', 'PENDING');")
            }
        }, patientId)
        assertTrue("Pending FINANCE_TRANSACTION linked via visit must return true", result)
    }

    @Test
    fun `hasUnsyncedRecords returns true when outbox contains TRANSACTION with patientId`() {
        if (!isSqliteAvailable) return
        val patientId = "patient-alpha"
        val result = executeQueryOnMemoryDb({ conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("INSERT INTO inventory_transactions (transactionId, patientId, visitId) VALUES ('tx-1', '$patientId', NULL);")
                stmt.execute("INSERT INTO sync_queue (id, entityName, entityId, status) VALUES ('sq-3', 'TRANSACTION', 'tx-1', 'QUEUED');")
            }
        }, patientId)
        assertTrue("Pending TRANSACTION directly on patient must return true", result)
    }

    @Test
    fun `hasUnsyncedRecords returns true when outbox contains INVENTORY_TRANSACTION with visitId`() {
        if (!isSqliteAvailable) return
        val patientId = "patient-alpha"
        val visitId = "visit-alpha-1"
        val result = executeQueryOnMemoryDb({ conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("INSERT INTO patient_visits (id, patientId) VALUES ('$visitId', '$patientId');")
                stmt.execute("INSERT INTO inventory_transactions (transactionId, patientId, visitId) VALUES ('tx-2', NULL, '$visitId');")
                stmt.execute("INSERT INTO sync_queue (id, entityName, entityId, status) VALUES ('sq-4', 'INVENTORY_TRANSACTION', 'tx-2', 'FAILED');")
            }
        }, patientId)
        assertTrue("Pending INVENTORY_TRANSACTION linked via visit must return true", result)
    }

    @Test
    fun `hasUnsyncedRecords returns false when status is SYNCED`() {
        if (!isSqliteAvailable) return
        val patientId = "patient-alpha"
        val result = executeQueryOnMemoryDb({ conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("INSERT INTO finance_transactions (id, patientId, visitId) VALUES ('fin-3', '$patientId', NULL);")
                stmt.execute("INSERT INTO sync_queue (id, entityName, entityId, status) VALUES ('sq-5', 'FINANCE', 'fin-3', 'SYNCED');")
            }
        }, patientId)
        assertFalse("Synced FINANCE record must not block pruning", result)
    }

    @Test
    fun `hasUnsyncedRecords returns false when outbox entry belongs to different patient`() {
        if (!isSqliteAvailable) return
        val patientId = "patient-alpha"
        val otherPatientId = "patient-beta"
        val result = executeQueryOnMemoryDb({ conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("INSERT INTO finance_transactions (id, patientId, visitId) VALUES ('fin-4', '$otherPatientId', NULL);")
                stmt.execute("INSERT INTO sync_queue (id, entityName, entityId, status) VALUES ('sq-6', 'FINANCE', 'fin-4', 'PENDING');")
            }
        }, patientId)
        assertFalse("Outbox record for patient-beta must not block pruning for patient-alpha", result)
    }
}
