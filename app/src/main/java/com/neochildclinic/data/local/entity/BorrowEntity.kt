package com.neochildclinic.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.neochildclinic.core.model.BorrowedVaccine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "borrow_records",
    foreignKeys = [
        ForeignKey(
            entity = VaccineEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaccineId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = VaccineBatchEntity::class,
            parentColumns = ["batchId"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("vaccineId"), Index("batchId")]
)
data class BorrowEntity(
    @PrimaryKey val id: String,
    @SerialName("doctor_name") val doctorName: String,
    @SerialName("vaccine_id") val vaccineId: String,
    @SerialName("batch_id") val batchId: String,
    @SerialName("borrowed_date") val borrowedDate: String,
    val quantity: Int = 1,
    @SerialName("is_returned") val isReturned: Boolean = false,
    @SerialName("returned_date") val returnedDate: String? = null,
    val type: String = "BY", // BY (Borrowed By us from someone), TO (Borrowed from us By someone)
    val notes: String? = null,
    @SerialName("is_synced") val isSynced: Boolean = false,
    @SerialName("created_by") @ColumnInfo(name = "created_by") val createdBy: String? = null,
    @SerialName("updated_by") @ColumnInfo(name = "updated_by") val updatedBy: String? = null
)

fun BorrowEntity.toDomain() = BorrowedVaccine(
    id = id,
    doctorName = doctorName,
    vaccineId = vaccineId,
    batchId = batchId,
    borrowedDate = borrowedDate,
    quantity = quantity,
    isReturned = isReturned,
    returnedDate = returnedDate,
    type = type,
    notes = notes,
    isSynced = isSynced
)

fun BorrowedVaccine.toEntity(isSynced: Boolean = false) = BorrowEntity(
    id = id,
    doctorName = doctorName,
    vaccineId = vaccineId,
    batchId = batchId,
    borrowedDate = borrowedDate,
    quantity = quantity,
    isReturned = isReturned,
    returnedDate = returnedDate,
    type = type,
    notes = notes,
    isSynced = isSynced
)
