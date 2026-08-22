package com.neochildclinic.data.local.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

/**
 * Adds vaccineName/batchNumber to vaccination_items so each row is a durable, self-contained
 * snapshot of what was actually given - independent of whether the referenced vaccine/batch is
 * later renamed or deleted from the catalog. Existing rows backfill with empty strings; they'll
 * be repaired the next time their parent vaccination is read (see
 * VaccinationRepositoryImpl.reconcileVisitFromItems), which only helps once patient_visits
 * itself still has the correct vaccineNames to fall back on for older data - going forward,
 * every new/edited row carries its own name and needs no reconciliation.
 *
 * An explicit migration (rather than relying on fallbackToDestructiveMigration) is required here
 * specifically because this database wipes and re-creates all local tables on an unhandled
 * version bump - which would force a full re-sync from the server and could resurrect exactly
 * the kind of stale/missing-catalog-reference issues this change exists to guard against.
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vaccination_items ADD COLUMN vaccineName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE vaccination_items ADD COLUMN batchNumber TEXT NOT NULL DEFAULT ''")
    }
}
