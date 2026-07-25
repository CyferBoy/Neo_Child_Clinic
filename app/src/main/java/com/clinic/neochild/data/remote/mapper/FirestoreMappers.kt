package com.clinic.neochild.data.remote.mapper

import android.util.Log
import com.clinic.neochild.core.model.BorrowedVaccine
import com.clinic.neochild.data.local.entity.*
import com.clinic.neochild.domain.model.*
import com.google.firebase.firestore.DocumentSnapshot

object FirestoreMappers {
    private const val TAG = "FirestoreMappers"

    // --- Safe Retrieval Helpers ---

    private fun safeGetList(doc: DocumentSnapshot, primaryField: String, legacyField: String? = null): List<String> {
        val value = doc.get(primaryField)
        if (value is List<*>) {
            return value.mapNotNull { it?.toString() }
        }
        if (value is String) {
            return if (value.isBlank()) emptyList() else value.split(",").map { it.trim() }
        }
        // Try legacy field if primary is null/empty
        if (legacyField != null) {
            val legacyValue = doc.getString(legacyField)
            if (!legacyValue.isNullOrBlank()) return listOf(legacyValue)
        }
        return emptyList()
    }

    private fun safeGetDouble(doc: DocumentSnapshot, field: String): Double {
        return (doc.get(field) as? Number)?.toDouble() ?: 0.0
    }

    private fun safeGetLong(doc: DocumentSnapshot, field: String): Long {
        return (doc.get(field) as? Number)?.toLong() ?: 0L
    }

    private fun safeGetBoolean(doc: DocumentSnapshot, primaryField: String, legacyField: String? = null): Boolean {
        return doc.getBoolean(primaryField) ?: (if (legacyField != null) doc.getBoolean(legacyField) else null) ?: false
    }

    private fun safeGetString(doc: DocumentSnapshot, field: String, default: String = ""): String {
        return doc.get(field)?.toString() ?: default
    }

    // --- Mappers ---

    fun toStaff(doc: DocumentSnapshot): Staff? {
        return try {
            Staff(
                id = doc.id,
                email = safeGetString(doc, "email"),
                name = safeGetString(doc, "name"),
                role = safeGetString(doc, "role", "Staff"),
                fcmToken = doc.getString("fcmToken"),
                createdAt = safeGetLong(doc, "createdAt")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error mapping Staff ${doc.id}", e)
            null
        }
    }

    fun toWasteRecord(doc: DocumentSnapshot): WasteRecord? {
        return try {
            WasteRecord(
                id = doc.id,
                vaccineId = safeGetString(doc, "vaccineId"),
                batchId = safeGetString(doc, "batchId"),
                brandName = safeGetString(doc, "brandName"),
                batchNumber = safeGetString(doc, "batchNumber"),
                expiryDate = safeGetString(doc, "expiryDate"),
                dateWasted = safeGetString(doc, "dateWasted"),
                reason = safeGetString(doc, "reason"),
                quantity = safeGetLong(doc, "quantity").toInt()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error mapping WasteRecord ${doc.id}", e)
            null
        }
    }

    fun toBorrowedVaccine(doc: DocumentSnapshot): BorrowedVaccine? {
        return try {
            BorrowedVaccine(
                id = doc.id,
                doctorName = safeGetString(doc, "doctorName"),
                borrowedDate = safeGetString(doc, "borrowedDate"),
                vaccineName = safeGetString(doc, "vaccineName"),
                expiryDate = safeGetString(doc, "expiryDate"),
                batchNumber = safeGetString(doc, "batchNumber"),
                isReturned = safeGetBoolean(doc, "isReturned"),
                returnedDate = doc.getString("returnedDate"),
                type = safeGetString(doc, "type", "BY")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error mapping BorrowedVaccine ${doc.id}", e)
            null
        }
    }

    fun toPatient(doc: DocumentSnapshot): Patient? {
        return try {
            Patient(
                id = doc.id,
                patientClinicId = safeGetString(doc, "patientClinicId"),
                name = safeGetString(doc, "name"),
                parentName = safeGetString(doc, "parentName"),
                phone = safeGetString(doc, "phone"),
                alternatePhone = safeGetString(doc, "alternatePhone"),
                dob = safeGetString(doc, "dob"),
                gender = safeGetString(doc, "gender"),
                village = safeGetString(doc, "village"),
                address = safeGetString(doc, "address"),
                registrationDate = safeGetString(doc, "registrationDate"),
                isDeleted = safeGetBoolean(doc, "isDeleted")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error mapping Patient ${doc.id}", e)
            null
        }
    }

    fun toVaccine(doc: DocumentSnapshot): Vaccine? {
        return try {
            Vaccine(
                id = doc.id,
                type = safeGetString(doc, "type"),
                brandName = safeGetString(doc, "brandName"),
                companyName = safeGetString(doc, "companyName"),
                stock = safeGetLong(doc, "stock").toInt(),
                batchNumber = safeGetString(doc, "batchNumber"),
                expiryDate = safeGetString(doc, "expiryDate"),
                mrp = safeGetDouble(doc, "mrp"),
                netRate = safeGetDouble(doc, "netRate")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error mapping Vaccine ${doc.id}", e)
            null
        }
    }

    fun toVaccineEntity(doc: DocumentSnapshot): VaccineEntity? {
        return try {
            VaccineEntity(
                id = doc.id,
                type = safeGetString(doc, "type"),
                brandName = safeGetString(doc, "brandName"),
                companyName = safeGetString(doc, "companyName"),
                mrp = safeGetDouble(doc, "mrp"),
                netRate = safeGetDouble(doc, "netRate")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error mapping VaccineEntity ${doc.id}", e)
            null
        }
    }

    fun toVaccineBatchEntity(doc: DocumentSnapshot): VaccineBatchEntity? {
        return try {
            VaccineBatchEntity(
                batchId = doc.id,
                vaccineId = safeGetString(doc, "vaccineId"),
                batchNumber = safeGetString(doc, "batchNumber"),
                manufacturer = safeGetString(doc, "manufacturer"),
                purchaseDate = safeGetString(doc, "purchaseDate"),
                expiryDate = safeGetString(doc, "expiryDate"),
                purchaseQuantity = safeGetLong(doc, "purchaseQuantity").toInt(),
                remainingQuantity = safeGetLong(doc, "remainingQuantity").toInt(),
                supplier = safeGetString(doc, "supplier"),
                purchaseCost = safeGetDouble(doc, "purchaseCost"),
                sellingPrice = safeGetDouble(doc, "sellingPrice"),
                status = safeGetString(doc, "status", "ACTIVE")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error mapping VaccineBatchEntity ${doc.id}", e)
            null
        }
    }

    fun toVaccination(doc: DocumentSnapshot): Vaccination? {
        return try {
            val id = doc.id
            val patientId = safeGetString(doc, "patientId")
            
            // Critical Validation: Basic schema check
            if (patientId.isBlank()) {
                Log.w(TAG, "Skipping Vaccination $id: missing patientId")
                return null
            }

            // Backward Compatibility: Check legacy field names
            val vaccineNames = safeGetList(doc, "vaccineNames")
            val batchNumbers = safeGetList(doc, "batchNumbers", legacyField = "batchNumber")
            val expiryDates = safeGetList(doc, "expiryDates", legacyField = "expiryDate")
            val nxtVaccineNames = safeGetList(doc, "nxtVaccineNames", legacyField = "nextVaccineName")
            
            // Legacy boolean field 'done' vs new 'isDone'
            val isDone = safeGetBoolean(doc, "isDone", legacyField = "done")

            Vaccination(
                id = id,
                receiptNumber = safeGetString(doc, "receiptNumber"),
                patientId = patientId,
                vaccineNames = vaccineNames,
                vaccineIds = safeGetList(doc, "vaccineIds"),
                nxtVaccineNames = nxtVaccineNames,
                dateGiven = safeGetString(doc, "dateGiven"),
                nextDueDate = safeGetString(doc, "nextDueDate"),
                cost = safeGetDouble(doc, "cost"),
                cashAmount = safeGetDouble(doc, "cashAmount"),
                onlineAmount = safeGetDouble(doc, "onlineAmount"),
                totalPaid = safeGetDouble(doc, "totalPaid"),
                withFees = safeGetBoolean(doc, "withFees"),
                doctorsAcc = safeGetBoolean(doc, "doctorsAcc"),
                isDone = isDone,
                source = safeGetString(doc, "source", "CLINIC"),
                notes = safeGetString(doc, "notes"),
                rescheduleReason = safeGetString(doc, "rescheduleReason"),
                performedBy = safeGetString(doc, "performedBy"),
                batchNumbers = batchNumbers,
                expiryDates = expiryDates,
                inventoryStatus = safeGetString(doc, "inventoryStatus", "SKIPPED") // Default to SKIPPED for legacy
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error mapping Vaccination ${doc.id}", e)
            null
        }
    }

    fun toReminderEntity(doc: DocumentSnapshot): ReminderEntity? {
        return try {
            ReminderEntity(
                id = doc.id.toLongOrNull() ?: 0L,
                patientId = safeGetString(doc, "patientId"),
                originalVisitId = safeGetString(doc, "originalVisitId"),
                vaccineName = safeGetString(doc, "vaccineName"),
                dueDate = safeGetString(doc, "dueDate"),
                status = safeGetString(doc, "status", "ACTIVE"),
                reminderDate = doc.getString("reminderDate"),
                priority = safeGetString(doc, "priority", "NORMAL"),
                reminderEnabled = safeGetBoolean(doc, "reminderEnabled"),
                category = safeGetString(doc, "category", "VACCINATION"),
                notes = doc.getString("notes"),
                completionDate = doc.getLong("completionDate"),
                performedBy = doc.getString("performedBy"),
                dismissalDate = doc.getLong("dismissalDate"),
                dismissalReason = doc.getString("dismissalReason"),
                externalDate = doc.getString("externalDate"),
                source = doc.getString("source"),
                lastReminderTime = safeGetLong(doc, "lastReminderTime"),
                notificationSent = safeGetBoolean(doc, "notificationSent"),
                createdAt = safeGetLong(doc, "createdAt"),
                updatedAt = safeGetLong(doc, "updatedAt"),
                isSynced = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error mapping ReminderEntity ${doc.id}", e)
            null
        }
    }

    fun toFinanceEntity(doc: DocumentSnapshot): FinanceEntity? {
        return try {
            FinanceEntity(
                id = doc.id.toLongOrNull() ?: 0L,
                timestamp = safeGetLong(doc, "timestamp"),
                type = safeGetString(doc, "type", "INCOME"),
                category = safeGetString(doc, "category", "OTHER"),
                amount = safeGetDouble(doc, "amount"),
                currency = safeGetString(doc, "currency", "INR"),
                paymentMethod = safeGetString(doc, "paymentMethod", "CASH"),
                patientId = doc.getString("patientId"),
                visitId = doc.getString("visitId"),
                referenceNumber = doc.getString("referenceNumber"),
                remarks = doc.getString("remarks"),
                recordedBy = safeGetString(doc, "recordedBy", "SYSTEM"),
                isSynced = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error mapping FinanceEntity ${doc.id}", e)
            null
        }
    }

    fun toBorrowEntity(doc: DocumentSnapshot): BorrowEntity? {
        return try {
            BorrowEntity(
                id = doc.id,
                doctorName = safeGetString(doc, "doctorName"),
                vaccineId = safeGetString(doc, "vaccineId"),
                batchId = safeGetString(doc, "batchId"),
                borrowedDate = safeGetString(doc, "borrowedDate"),
                quantity = safeGetLong(doc, "quantity").toInt(),
                isReturned = safeGetBoolean(doc, "isReturned"),
                returnedDate = doc.getString("returnedDate"),
                type = safeGetString(doc, "type", "BY"),
                notes = doc.getString("notes"),
                isSynced = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error mapping BorrowEntity ${doc.id}", e)
            null
        }
    }

    fun toStaffEntity(doc: DocumentSnapshot): StaffEntity? {
        return try {
            StaffEntity(
                id = doc.id,
                name = safeGetString(doc, "name"),
                email = safeGetString(doc, "email"),
                role = safeGetString(doc, "role", "Staff"),
                department = doc.getString("department"),
                permissions = doc.getString("permissions"),
                fcmToken = doc.getString("fcmToken"),
                isActive = safeGetBoolean(doc, "isActive"),
                createdAt = safeGetLong(doc, "createdAt"),
                lastActive = safeGetLong(doc, "lastActive")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error mapping StaffEntity ${doc.id}", e)
            null
        }
    }

    fun toUserEntity(doc: DocumentSnapshot): UserEntity? {
        return try {
            UserEntity(
                id = doc.id,
                email = safeGetString(doc, "email"),
                name = safeGetString(doc, "name"),
                biometricEnabled = safeGetBoolean(doc, "biometricEnabled"),
                pinHash = doc.getString("pinHash"),
                lastLogin = safeGetLong(doc, "lastLogin"),
                fcmToken = doc.getString("fcmToken"),
                devices = doc.getString("devices"),
                securityStamp = safeGetString(doc, "securityStamp")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error mapping UserEntity ${doc.id}", e)
            null
        }
    }

    fun toPatientNoteEntity(doc: DocumentSnapshot): PatientNotesEntity? {
        return try {
            PatientNotesEntity(
                id = doc.id.toLongOrNull() ?: 0L,
                patientId = safeGetString(doc, "patientId"),
                content = safeGetString(doc, "content"),
                author = safeGetString(doc, "author"),
                timestamp = safeGetLong(doc, "timestamp"),
                isSynced = true,
                isDeleted = safeGetBoolean(doc, "isDeleted")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error mapping PatientNotesEntity ${doc.id}", e)
            null
        }
    }
}
