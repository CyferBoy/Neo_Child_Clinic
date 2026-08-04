package com.neochildclinic.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
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
    val doctorName: String,
    val vaccineId: String,
    val batchId: String,
    val borrowedDate: String,
    val quantity: Int = 1,
    val isReturned: Boolean = false,
    val returnedDate: String? = null,
    val type: String = "BY", // BY (Borrowed By us from someone), TO (Borrowed from us By someone)
    val notes: String? = null,
    val isSynced: Boolean = false
)
