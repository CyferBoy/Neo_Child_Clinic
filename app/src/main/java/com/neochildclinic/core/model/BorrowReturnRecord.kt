package com.neochildclinic.core.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single return transaction against a [BorrowedVaccine] borrowing record.
 * Mirrors [BorrowedVaccine]'s direct-Postgrest DTO pattern used by
 * BorrowedViewModel. Multiple of these can exist per borrow record - each
 * partial return is its own row and none of them overwrite the original
 * borrow record or each other.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BorrowReturnRecord(
    val id: String = "",
    @SerialName("borrow_record_id") val borrowRecordId: String = "",
    @SerialName("batch_id") val batchId: String = "",
    val quantity: Int = 0,
    @SerialName("returned_date") val returnedDate: String = "",
    val notes: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("created_at") val createdAt: String? = null,
    @SerialName("is_synced") val isSynced: Boolean = true
)
