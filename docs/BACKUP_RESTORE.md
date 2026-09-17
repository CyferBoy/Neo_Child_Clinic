# Backup & Restore

Settings -> Backup & Restore. Covers local export/import, cloud backup/restore (Cloudflare
R2 via a Worker), automatic backup, encryption, and the conflict/rollback rules used during
restore.

## Architecture

```
                         ┌─────────────────────────┐
                         │   BackupSettingsScreen   │  Compose UI
                         └────────────┬─────────────┘
                                      │
                         ┌────────────▼─────────────┐
                         │      BackupViewModel      │
                         └────────────┬─────────────┘
                                      │
                         ┌────────────▼─────────────┐
                         │   BackupRepositoryImpl    │  orchestration
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
                                                          R2 binding   D1 (metadata)
```

Everything under `data/backup/` is new. `BackupDao` is a second, additive Room DAO over the
app's *existing* tables (see `data/local/dao/BackupDao.kt`) - nothing about the existing
Patient/Consultation/Vaccination/Sync repositories changed, and no second database was
introduced.

## Backup file format (`.nccb`)

A `.nccb` file is a binary container:

```
4 bytes   magic "NCCB"
1 byte    container format version
1 byte    salt length + N bytes salt
1 byte    IV length + N bytes IV
4 bytes   PBKDF2 iteration count
N bytes   AES-256-GCM ciphertext (includes the 16-byte auth tag)
```

Decrypting the ciphertext yields a UTF-8 JSON **envelope**:

```json
{
  "backupVersion": 1,
  "backupId": "…uuid…",
  "appName": "Neo Child Clinic",
  "appVersionName": "…",
  "appVersionCode": 0,
  "databaseVersion": 26,
  "createdAt": "2026-09-11T16:30:00.000Z",
  "createdByUserId": "…supabase user id…",
  "deviceInfo": { "platform": "Android", "osVersion": "…", "model": "…" },
  "recordCounts": { "patients": 120, "consultations": 340, "...": 0 },
  "checksum": "…sha256 hex of the JSON-encoded data block…",
  "data": { "patients": [ … ], "consultations": [ … ], "...": [] }
}
```

`data` reuses the app's existing `@Serializable` Room entities directly (see
`data/backup/BackupModels.kt`) - the JSON field names are the same `@SerialName`s already
used for the Supabase sync payloads, so there is exactly one documented shape, not two.

**Versioning / migration.** `backupVersion` is checked on read: a value greater than the
app's `CURRENT_BACKUP_VERSION` fails with *"This backup was created by a newer version of
Neo Child Clinic. Please update the app before restoring it."* before anything is touched.
`BackupMigrator` (`data/backup/BackupSerializer.kt`) is the extension point for future
version bumps - it currently only handles `backupVersion == 1` and is where a `1 -> 2`
mapping would be added later. Because `Json { ignoreUnknownKeys = true }` plus default
values on every field are already used, most additive field changes never need a version
bump at all.

## Backup data mapping

| Included (with local Room table)                                          | Excluded |
|-----------------------------------------------------------------------------|----------|
| profiles, vaccines, vaccine_batches, doctor_weekly_slots, doctor_slot_exceptions, patients, patient_visits (vaccinations), vaccination_items, consultations, reminders, personal_vaccine_reminders, borrow_records, borrow_returns, waste_records, inventory_transactions, inventory_deductions, finance_transactions, expenses, patient_notes, consultation_todos, vaccination_todos, audit_logs | `sync_queue` (transient outbox, rebuilt after restore), `widget_due_cache` (derived display cache, regenerated on refresh), `backup_history` (describes backups, isn't clinic data) |

**Always stripped, never exported:** `ProfileEntity.fcmToken` (device push token), and of
course nothing that was never in the local database to begin with - passwords, Supabase
service-role keys, R2 credentials, and auth/device tokens live in Supabase Auth / Keystore /
Worker secrets, never in a Room table, so they cannot appear in a backup by construction.

**Known fidelity limitation:** a few fields on `InventoryTransactionEntity` (`status`,
`failureReason`, `processedAt`, `processedBy`) are marked `@Transient` in the entity itself
(pre-existing, "Local Only" per that file's own comments) and several `ReminderEntity`
fields are marked `@Transient` as derived/local-only - these are not included in the backup
and fall back to their defaults on restore. This mirrors how those fields already behave
with respect to the app's own remote sync, not a new gap introduced by backup.

## Encryption

Two independent encryption schemes are used, deliberately kept separate:

1. **Local database at rest** (unchanged): SQLCipher, key from Android Keystore-backed
   `EncryptedSharedPreferences` (`SecurityUtils.kt`). Device-bound, never leaves the device.
2. **Portable backups** (`.nccb` files, local export and cloud): password-based AES-256-GCM,
   key derived with PBKDF2WithHmacSHA256 (210,000 iterations, 256-bit key, 16-byte salt,
   12-byte IV) - see `BackupCrypto.kt`. This has to be independent of (1) because a backup
   must be restorable on a different device/install.

**Password handling.** Neo Child Clinic never stores the password for a manual Export or
Cloud "Backup Now" - it is typed fresh every time and held only in memory for the duration
of that operation. **Automatic Backup is the one exception**: since it runs unattended
(a nightly WorkManager job with nobody present to type a password), the password given when
Automatic Backup is turned on is stored via the same Keystore-wrapped
`EncryptedSharedPreferences` mechanism already used for the database passphrase
(`SecurityUtils.saveBackupPassword`/`getBackupPassword`). Turning Automatic Backup off
erases it immediately. **If a password is lost, the backup cannot be recovered** - this is
by design (there is no server-side escrow) and is stated in the UI.

A wrong password fails via AES-GCM's own authentication tag check
(`AEADBadTagException`) before any JSON is even parsed, and decryption only ever operates
on the input file in memory - existing data is never touched until decryption, checksum
verification, and relationship validation have all already succeeded.

## Restore behavior

Every restore (local or cloud) follows the same path in `BackupRepositoryImpl.performRestore`:

1. **Safety backup** - the current database is collected and written to app-private storage,
   encrypted with an Android Keystore key (`SafetyBackupStore`, no password needed since it
   never leaves the device). If this step fails, the restore is aborted before anything
   destructive happens.
2. **Restore** (`BackupRestorer`, inside one `Room` transaction - if any step throws, the
   whole transaction rolls back and existing data is left exactly as it was):
   - **Replace**: every included table is cleared (children-first) and re-inserted
     (parents-first), preserving original IDs - no ID remapping, since the app's own sync
     architecture already treats client-generated UUIDs as canonical (see
     `SyncRepositoryImpl`'s upsert-by-id).
   - **Merge**: applied per table with a documented, deterministic rule (see the class doc
     on `BackupRestorer.kt`):
     - Tables with a reliable "last updated" column (patients, consultations,
       vaccinations, vaccines, vaccine batches, doctor slots/exceptions, reminders,
       personal reminders, expenses, todos, waste records, profiles): **last-write-wins**,
       reusing the exact rule `SyncRepositoryImpl` already uses against Supabase, rather
       than inventing a second one.
     - Append-only ledgers/logs with no reliable timestamp (borrow records/returns,
       patient notes, finance transactions, inventory transactions/deductions, audit
       logs): **insert-if-absent only** - an existing row is never overwritten.
     - `vaccination_items` follow their parent visit: re-applied only when that visit was
       itself inserted/updated in this merge.
3. **Resync** - every restored row whose `isSynced` is `false` is re-enqueued onto the
   existing `sync_queue` (`SyncOperation.UPDATE`), and `SyncManager.scheduleImmediateSync()`
   is called once. The existing `SyncWorker`/`SyncRepositoryImpl` then pushes it through the
   same upsert-by-id path used for every normal local edit - this is what prevents restore
   from ever creating a duplicate Supabase row. Tables whose entity has no `isSynced` column
   at all (profiles, vaccines, vaccine batches, vaccination items) are pure local-database
   operations with nothing to resync.

**Known edge case:** `PatientEntity.patientClinicId` has a `UNIQUE` index. Room's
`OnConflictStrategy.REPLACE` respects *any* unique constraint, not just the primary key, so
in the rare case where a Merge-mode backup and the existing database both used the same
`patientClinicId` for two different patient rows, the existing row would be replaced rather
than a constraint error being raised. Replace-mode restores are unaffected (the table is
fully cleared first). This is flagged here rather than silently ignored - a pre-merge
`patientClinicId` collision check would close this gap if it ever becomes a real issue.

**Known limitation: Merge mode can resurrect a hard-deleted row.** Most tables in this app
delete rows with a real `DELETE FROM ...` (e.g. `WasteRepositoryImpl.deleteWaste()`,
`WasteDao.deleteWaste()`) rather than a soft-delete flag, so once a row is deleted there is
no tombstone left behind to compare against. If a Merge-mode restore is run from a backup
taken *before* that deletion, `BackupRestorer` has no way to tell "never existed" apart from
"existed and was deleted since" - it sees no local row with that id and inserts the
backup's copy, undoing the deletion. `waste_records` is a clean example of this (no
`isDeleted` column at all), but the same is true of patients, consultations, vaccinations,
vaccination items, borrow records/returns, patient notes, finance/inventory transactions,
audit logs, reminders, and personal reminders - i.e. every included table except the two
that already use a soft-delete flag with its own `updated_at` bump: `expenses.isDeleted` and
`doctor_slot_exceptions.is_deleted`. For those two, last-write-wins already does the right
thing, because the soft-delete *is* an update with a newer timestamp.
Replace-mode restores are unaffected by this (the table is fully cleared before the backup's
rows are inserted, so there's nothing to "undo"). Closing this gap for the hard-delete
tables would need a tombstone (e.g. a `deleted_records` table recording id + deleted-at) that
Merge mode checks before re-inserting a row absent from the local database - out of scope
here, called out so it isn't silently assumed away.

## Sync integration

- Local Restore only ever touches the local database and (per the resync step above) queues
  unsynced rows for the *existing* sync pipeline - it never talks to Supabase directly.
- Cloud Restore is explicitly separate from "restoring Supabase" - it downloads/decrypts an
  R2 object and then runs the exact same local-restore path. Nothing in this feature ever
  writes to Supabase directly or deletes/overwrites remote rows outside the normal
  upsert-by-id sync path.
- Because backup content already carries each row's original `isSynced` flag, a restore
  never mistakes an already-synced row for a pending one (no spurious re-uploads) and never
  loses a genuinely pending offline edit either.

## Cloudflare Worker / R2 (cloud backup)

See `cloudflare/backup-worker/` for the full source. Endpoints (all require a valid Supabase
access token as `Authorization: Bearer <token>`):

| Method & path                        | Purpose |
|---------------------------------------|---------|
| `PUT /backups/:id`                    | Upload the encrypted `.nccb` bytes |
| `POST /backups/:id/confirm`           | Verify the R2 object exists with the claimed size, then persist metadata to D1 |
| `GET /backups`                        | List the caller's own backups |
| `GET /backups/:id/download`           | Download the encrypted bytes |
| `DELETE /backups/:id`                 | Delete one backup |
| `POST /backups/retention { keep }`    | Delete the caller's oldest backups beyond `keep`, never the newest |

**Authentication & isolation.** The Worker verifies the Supabase JWT itself (HS256, using
the `SUPABASE_JWT_SECRET` Worker secret - zero-dependency, via the Workers-native
`crypto.subtle`, see `src/jwt.ts`) and derives the account id from the token's `sub` claim.
Every read/write looks the target backup up by id in D1 and checks its stored `account_id`
against the verified caller - **never** from anything the client supplied in the URL or
body. This is what stops one clinic from reading another clinic's backup by changing an id
in the request (verified by the ownership-isolation test in `test/backup-worker.spec.ts`).

**R2 access.** The Worker uses a native R2 **binding** (`env.BACKUP_BUCKET`), not the
S3-compatible API with an access key/secret. This means there is no R2 access key or secret
access key anywhere in this project at all - not in the Android app (which was the hard
requirement), and not even as a Worker secret, since a binding needs none. The R2 bucket
itself has no public access enabled.

**R2 object paths:** `backups/{accountId}/{backupId}/backup.nccb` - no patient names or
other patient data anywhere in the path.

**Metadata (D1):** `backup_id, account_id, storage_path, size_bytes, app_version,
backup_version, created_at, confirmed_at, status` - see `schema.sql`. No clinic/patient data
is ever stored in D1.

### Deployment

```bash
cd cloudflare/backup-worker
npm install
wrangler d1 create neochildclinic-backups     # copy the returned id into wrangler.toml
npm run db:migrate:remote
wrangler r2 bucket create neochildclinic-backups
wrangler secret put SUPABASE_JWT_SECRET       # from Supabase Project Settings -> API -> JWT Settings
npm run deploy
```

Then set the deployed Worker's URL as `BACKUP_WORKER_URL` for the Android app's build
(same `.env.local`/CI-secret mechanism already used for `SUPABASE_URL` /
`SUPABASE_PUBLISHABLE_KEY` - see `getEnv()` in `app/build.gradle.kts`). If
`BACKUP_WORKER_URL` is left empty, Cloud Backup/Restore are simply unavailable in that build
and the screen says so - Local Export/Import and Automatic (local-only) Backup are
unaffected.

### Required environment variables / secrets

| Name | Where | Notes |
|------|-------|-------|
| `BACKUP_WORKER_URL` | Android build env (`.env.local` / CI) | Public URL of the deployed Worker |
| `SUPABASE_JWT_SECRET` | Worker secret (`wrangler secret put`) | Verifies the same tokens Supabase Auth already issues; never touches the Android app |
| D1 `database_id` | `wrangler.toml` | From `wrangler d1 create` |
| R2 bucket name | `wrangler.toml` | From `wrangler r2 bucket create` |

## Error handling

Every failure surfaces as one of the typed `BackupException` subclasses in
`data/backup/BackupErrors.kt`, each with a fixed, user-facing message (never a raw stack
trace) and a separate, sanitized `logDetail` for `Log.e` that never includes patient data,
backup contents, passwords, encryption keys, or credentials:

- *"This backup is corrupted or incomplete. Your current data has not been changed."*
- *"Incorrect backup password. Your current data has not been changed."*
- *"This backup was created by a newer version of Neo Child Clinic. Please update the app before restoring it."*
- *"Backup failed. Please try again."* (generic fallback)
- Network/auth-specific messages for no internet, timeout, expired session, unauthorized,
  upload/download failure, server error, insufficient storage, interrupted.

## Testing

- `app/src/test/java/com/neochildclinic/data/backup/BackupCryptoTest.kt` - pure-JVM: correct
  round trip, wrong password fails safely (no partial decrypt), tampering is detected.
- `app/src/test/java/com/neochildclinic/data/backup/BackupValidatorTest.kt` - referential
  integrity checks (missing patient/vaccine/batch references, duplicate ids) against
  in-memory payloads.
- `cloudflare/backup-worker/test/backup-worker.spec.ts` - JWT verification (missing/expired/
  invalid-signature tokens rejected) and cross-account ownership isolation.

**Not exercised in this environment:** this repo was inspected and edited in a sandbox with
no network access and no Android SDK/Gradle available, so none of the above could actually
be *run* here (no `./gradlew test`, no `npm install && npm test`, no instrumented Room/DAO
tests, no end-to-end restore-then-sync test across two devices). Everything above was
written to compile against the real entity/DAO signatures found in this codebase and was
checked by hand field-by-field against `data/local/entity/*.kt`, but **please run the full
test suite and a real device restore before shipping**, especially:
- A large-database export/import (thousands of rows) for performance and the single-Room-
  transaction size in Replace mode.
- The `patientClinicId` unique-index edge case noted above.
- The Worker tests, and a real deploy against a Supabase project's actual JWT secret format
  (HS256 is Supabase's default; a project configured for RS256/ES256 JWT signing would need
  `src/jwt.ts` extended to fetch and verify against Supabase's JWKS endpoint instead).
- `CloudBackupApi.kt`'s assumption that `io.github.jan.supabase.auth.Auth` exposes
  `currentSessionOrNull()?.accessToken` and a suspend `refreshCurrentSession()` - both are
  long-standing public supabase-kt APIs, but this project's exact supabase-kt version
  wasn't available to check against directly in this environment.

## Security considerations

- No R2 access key/secret anywhere in the repo (native binding, see above).
- No Supabase service-role key anywhere in the Android app or the Worker (the Worker only
  verifies tokens the app already has; it never calls Supabase's admin API).
- Backups are encrypted client-side before upload - the Worker and R2 never see plaintext
  clinic data, only opaque ciphertext.
- Per-account isolation is enforced server-side from the verified JWT, never from a
  client-supplied id.
- The R2 bucket has no public access enabled.
- Nothing sensitive is logged: see `BackupErrors.kt`'s `logDetail` fields and the explicit
  "never log" list this documentation itself calls out (patient data, backup contents,
  passwords, keys, credentials, access tokens).
