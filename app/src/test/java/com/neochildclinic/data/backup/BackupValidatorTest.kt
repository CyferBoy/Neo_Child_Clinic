package com.neochildclinic.data.backup

import com.neochildclinic.data.local.entity.PatientEntity
import com.neochildclinic.data.local.entity.VaccinationItemEntity
import com.neochildclinic.data.local.entity.VaccineBatchEntity
import com.neochildclinic.data.local.entity.VaccineEntity
import com.neochildclinic.data.local.entity.VisitEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupValidatorTest {

    private fun envelope(payload: BackupPayloadV1) = BackupEnvelope(
        backupId = "test-backup-id",
        appVersionName = "1.0-test",
        appVersionCode = 1,
        databaseVersion = 26,
        createdAt = "2026-01-01T00:00:00.000Z",
        checksum = "irrelevant-for-this-test", // BackupValidator never checks this - BackupSerializer does
        recordCounts = payload.recordCounts(),
        data = payload
    )

    private fun consistentPayload(): BackupPayloadV1 {
        val patient = PatientEntity(id = "patient-1", name = "Test Patient", phone = "555-0100", dob = "2020-01-01", gender = "F")
        val vaccine = VaccineEntity(id = "vaccine-1", type = "IPV", brandName = "Brand", companyName = "Company")
        val batch = VaccineBatchEntity(
            batchId = "batch-1", vaccineId = "vaccine-1", batchNumber = "B1", manufacturer = "Mfr",
            purchaseDate = "2026-01-01", expiryDate = "2027-01-01", purchaseQuantity = 10, remainingQuantity = 9
        )
        val visit = VisitEntity(id = "visit-1", patientId = "patient-1", dateGiven = "2026-01-02")
        val item = VaccinationItemEntity(id = "item-1", vaccinationId = "visit-1", vaccineId = "vaccine-1", batchId = "batch-1")

        return BackupPayloadV1(
            patients = listOf(patient),
            vaccines = listOf(vaccine),
            vaccineBatches = listOf(batch),
            visits = listOf(visit),
            vaccinationItems = listOf(item)
        )
    }

    @Test
    fun `a fully consistent payload validates and produces a matching restore summary`() {
        val summary = BackupValidator.validate(envelope(consistentPayload()))

        assertEquals("test-backup-id", summary.backupId)
        assertEquals(1, summary.recordCounts["patients"])
        assertEquals(1, summary.recordCounts["vaccinationItems"])
    }

    @Test(expected = BackupException.BrokenRelationships::class)
    fun `a visit referencing a patient that does not exist is rejected`() {
        val payload = consistentPayload()
        val orphanVisit = VisitEntity(id = "visit-2", patientId = "no-such-patient", dateGiven = "2026-01-02")

        BackupValidator.validate(envelope(payload.copy(visits = payload.visits + orphanVisit)))
    }

    @Test(expected = BackupException.BrokenRelationships::class)
    fun `a vaccination item referencing a batch that does not exist is rejected`() {
        val payload = consistentPayload()
        val orphanItem = VaccinationItemEntity(id = "item-2", vaccinationId = "visit-1", vaccineId = "vaccine-1", batchId = "no-such-batch")

        BackupValidator.validate(envelope(payload.copy(vaccinationItems = payload.vaccinationItems + orphanItem)))
    }

    @Test(expected = BackupException.BrokenRelationships::class)
    fun `duplicate patient ids in the same payload are rejected`() {
        val payload = consistentPayload()
        val duplicate = payload.patients.first().copy(name = "Different Name, Same Id")

        BackupValidator.validate(envelope(payload.copy(patients = payload.patients + duplicate)))
    }

    @Test
    fun `an empty payload is valid (nothing to restore, nothing inconsistent)`() {
        val summary = BackupValidator.validate(envelope(BackupPayloadV1()))
        assertTrue(summary.recordCounts.values.all { it == 0 })
    }
}
