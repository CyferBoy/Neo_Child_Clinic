package com.neochildclinic.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.neochildclinic.core.model.BorrowReturnRecord
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single return transaction against a [BorrowEntity] borrowing record.
 *
 * A borrow record can have multiple of these (partial returns over time). The
 * original borrow record is never overwritten or deleted when a return is made -
 * this table is the append-only history, and status (Borrowed / Partially
 * Returned / Returned) is always derived by comparing the borrow record's
 * quantity against the SUM of these rows for it, never stored redundantly.
 */
@Serializable
@Entity(
    tableName = "borrow_returns",
    foreignKeys = [
        ForeignKey(
            entity = BorrowEntity::class,
            parentColumns = ["id"],
            childColumns = ["borrow_record_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = VaccineBatchEntity::class,
            parentColumns = ["batchId"],
            childColumns = ["batch_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("borrow_record_id"), Index("batch_id")]
)
data class BorrowReturnEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),

    @SerialName("borrow_record_id") @ColumnInfo(name = "borrow_record_id") val borrowRecordId: String,

    // The batch the returned stock physically lands in. May be the same batch it
    // was originally borrowed from ("Same Batch & Expiry") or a different one.
    @SerialName("batch_id") @ColumnInfo(name = "batch_id") val batchId: String,

    val quantity: Int,

    @SerialName("returned_date") @ColumnInfo(name = "returned_date") val returnedDate: String,

    val notes: String? = null,

    @SerialName("created_at") @ColumnInfo(name = "created_at") val createdAt: String = "",
    @SerialName("is_synced") @ColumnInfo(name = "is_synced") val isSynced: Boolean = false,
    @SerialName("created_by") @ColumnInfo(name = "created_by") val createdBy: String? = null,
    @SerialName("updated_by") @ColumnInfo(name = "updated_by") val updatedBy: String? = null
)

fun BorrowReturnEntity.toDomain() = BorrowReturnRecord(
    id = id,
    borrowRecordId = borrowRecordId,
    batchId = batchId,
    quantity = quantity,
    returnedDate = returnedDate,
    notes = notes,
    createdAt = createdAt,
    isSynced = isSynced
)

fun BorrowReturnRecord.toEntity(isSynced: Boolean = false) = BorrowReturnEntity(
    id = id,
    borrowRecordId = borrowRecordId,
    batchId = batchId,
    quantity = quantity,
    returnedDate = returnedDate,
    notes = notes,
    createdAt = createdAt ?: "",
    isSynced = isSynced
)
