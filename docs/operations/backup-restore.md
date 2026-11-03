# Neo Child Clinic — Backup & Disaster Recovery Architecture

This document covers local encrypted exports (`.nccb`), cloud backups via Cloudflare R2, automated scheduled backups, encryption architecture, and the rollback rules used during database restoration.

---

## 1. Architecture Overview

```
                         ┌─────────────────────────┐
                         │   BackupSettingsScreen   │  Compose UI
                         └────────────┬─────────────┘
                                      │
                         ┌────────────▼─────────────┐
                         │      BackupViewModel      │  Screen State Holder
                         └────────────┬─────────────┘
                                      │
                         ┌────────────▼─────────────┐
                         │   BackupRepositoryImpl    │  Orchestration
                         └───┬───────┬───────┬──────┘
             ┌───────────────┘       │       └───────────────┐
   ┌─────────▼────────┐   ┌──────────▼─────────┐   ┌──────────▼─────────┐
   │  BackupCollector  │   │   BackupRestorer    │   │    CloudBackupApi   │
   │  BackupValidator  │   │  SafetyBackupStore   │   │  (Ktor -> Worker)   │
   │  BackupSerializer │   └──────────┬──────────┘   └──────────┬─────────┘
   │  BackupCrypto     │              │                          │
   └─────────┬─────────┘   ┌──────────▼──────────┐               │
             │              │      BackupDao       │               │
             └─────────────►│  (data/local/dao)    │               │
                            └──────────┬──────────┘               │
                                       │                           │
                              AppDatabase (Room/SQLCipher)   Cloudflare Worker
                                                               │           │
                                                          R2 Bucket    D1 Metadata
```

- **Local Storage**: Room database (`AppDatabase.kt`) encrypted at rest via SQLCipher with keys in Android Keystore.
- **Backup Container**: `.nccb` encrypted binary package.
- **Cloud Backend**: Dedicated Cloudflare Worker (`cloudflare/backup-worker`) proxying encrypted payloads to Cloudflare R2 storage with metadata indexed in Cloudflare D1.

---

## 2. `.nccb` Container Format

A `.nccb` file is a portable, cryptographically authenticated binary package:

| Offset | Length | Field | Description |
|---|---|---|---|
| `0..3` | 4 bytes | Magic Header | ASCII `"NCCB"` |
| `4` | 1 byte | Version | Container format version (currently `1`) |
| `5` | 1 byte | Salt Length | $N$ bytes (16 bytes) |
| `6..6+N` | $N$ bytes | Salt | Cryptographic random salt for key derivation |
| Variable | 1 byte | IV Length | 12 bytes |
| Variable | 12 bytes | IV | AES-GCM Initialization Vector |
| Variable | 4 bytes | Iterations | PBKDF2 iteration count (210,000) |
| Variable | Remainder | Ciphertext | AES-256-GCM ciphertext + 16-byte auth tag |

### Decrypted JSON Payload
```json
{
  "backupVersion": 1,
  "backupId": "3c52fb4a-69fe-4929-b7ae-41dbe686bf71",
  "appName": "Neo Child Clinic",
  "appVersionName": "1.0.0",
  "appVersionCode": 1,
  "databaseVersion": 33,
  "createdAt": "2026-11-02T13:00:00.000Z",
  "createdByUserId": "staff-uuid",
  "checksum": "sha256-hex-digest",
  "data": {
    "patients": [ ... ],
    "vaccinations": [ ... ],
    "vaccine_batches": [ ... ]
  }
}
```

---

## 3. Included vs Excluded Tables

| Included Tables (Full Fidelity) | Excluded Tables (Transient / Derived) |
|---|---|
| `patients`, `vaccinations`, `vaccination_items`, `vaccines`, `vaccine_batches`, `consultations`, `reminders`, `personal_vaccine_reminders`, `borrow_records`, `borrow_returns`, `waste_records`, `inventory_transactions`, `inventory_deductions`, `finance_transactions`, `expenses`, `doctor_weekly_slots`, `doctor_slot_exceptions`, `patient_notes`, `consultation_todos`, `vaccination_todos`, `audit_logs`, `profiles` | `sync_queue` (rebuilt post-restore), `widget_due_cache` (ephemeral display cache), `backup_history` (metadata log) |

> [!SECURITY]
> **Stripped Secrets**: `ProfileEntity.fcmToken` is explicitly stripped prior to serialization. Passwords, Supabase service-role keys, and Keystore keys are never stored in Room and are never included in backups.

---

## 4. Restoration & Safety Rollback (`SafetyBackupStore`)

1. **Pre-Restore Snapshot**: Before altering any tables, `BackupRestorer` creates a local safety snapshot of the existing database via `SafetyBackupStore`.
2. **Atomic Ingestion**: Tables are populated inside a single database transaction. If deserialization or foreign-key constraints fail, the transaction rolls back completely.
3. **Queue Reseed**: Post-restore, all imported rows are treated as synchronized; `sync_queue` is reset to prevent pushing legacy snapshot data over fresh cloud mutations.
