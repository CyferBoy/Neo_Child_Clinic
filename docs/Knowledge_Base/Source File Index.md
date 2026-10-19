# Source File Index

> Every source root, what it contains, and which files are load-bearing.
> Counts generated from the tree at commit `4d11a32`.
> Companion: [[Architecture Map]] · [[Testing]]

## Source roots

| Root | Files | Role |
|---|---|---|
| `app/src/main/java/com/neochildclinic/` | 352 | production Kotlin |
| `app/src/test/java/com/neochildclinic/` | 41 | JVM unit tests |
| `app/src/androidTest/java/com/neochildclinic/` | 6 | instrumented tests |
| `app/src/main/res/` | — | resources; `res/xml/` holds only `vaccine_widget_info.xml` |
| `app/schemas/` | 1 | `…/AppDatabase/33.json` — the only exported Room schema |
| `supabase/migrations/` | 23 | SQL migrations, now git-tracked |
| `supabase/functions/` | 2 | `manage-staff`, `notify-update` (Deno/TS) |
| `cloudflare/` | — | backup Worker + R2 binding |
| `scripts/` | 2 | `validate_docs.py` — documentation integrity; `test_validate_docs.py` — its tests (both 2026-10-18) |

## Entry points

| File | Annotation | Role |
|---|---|---|
| `app/NeoChildApp.kt` | `@HiltAndroidApp` (`:24`), `Configuration.Provider` | Application; starts `SyncReconciler`, `ReminderScheduler`, `BackupAutoScheduler`, the screen-off receiver, COGS migration |
| `app/MainActivity.kt` | `@AndroidEntryPoint` (`:44`) | Single activity; hosts `NavHost`, `FLAG_SECURE`, biometric lock overlay |
| `app/Navigation.kt` | — | `NavHost` + 5 graph builders, 51 destinations |
| `feature/widget/presentation/VaccineWidget.kt` | `@EntryPoint` (`:187`) | Glance widget; the **only** legitimate `@EntryPoint` (Glance cannot be `@AndroidEntryPoint`) |
| `feature/widget/presentation/VaccineWidgetReceiver.kt` | — | widget broadcast receiver |
| `feature/widget/presentation/VaccineWidgetConfigurationActivity.kt` | — | widget setup |

## `app/` — 9 files

`NeoChildApp.kt`, `MainActivity.kt`, `Navigation.kt`, `Routes.kt`, plus
`NavigationDashboardGraph.kt` (22 destinations), `NavigationPatientGraph.kt` (10),
`NavigationInventoryGraph.kt` (9), `NavigationReminderGraph.kt` (5),
`NavigationStatisticsGraph.kt` (5).

## `core/` — 52 files

| Sub-package | Files | Notable |
|---|---|---|
| `cache/` | 2 | `MemoryCache.kt` (10 tests), `CacheRegistry.kt` |
| `common/` | 7 | `DateUtils.kt` (**most carefully documented function in the app**), `AgeUtils`, `PatientUtils`, `DateClassifier`, `Constants` |
| `database/` | 1 | `TransactionRunner` interface |
| `designsystem/` | 6 | `Theme`, `Color`, `Spacing`, `Type`, `Shape`, `Elevation` |
| `di/` | 4 | `DatabaseModule`, `SupabaseModule`, `RepositoryModule`, `RemoteDataSourceModule` |
| `logger/` | 1 | `AuditLogger.kt` — dual API, a known footgun |
| `network/` | 1 | `NetworkMonitor.kt` |
| `notification/` | 5 | `ReminderScheduler`, `DailySummaryWorker`, `PersonalReminderNotificationWorker`, `NotificationHelper` (5 channels), FCM service |
| `preferences/` | 2 | `PreferenceManager`, `NotificationSettingsManager` |
| `security/` | 6 | `AuthViewModel`, `SessionManager`, `SecurityUtils`, `BiometricLockManager`, `BiometricAuthenticator`, `CurrentUserProvider` |
| `sync/` | 7 | `SyncRepositoryImpl`, `SyncUploader` (674 lines), `SyncManagerImpl`, `SyncReconciler`, `SyncWorker`, `CloudRefresh`, `RealtimeChangeSubscriptions`, `SingleFlight` |
| `ui/` | 9 | `States.kt` (**only 4 of 44 screens use `ErrorState`**), `Skeleton.kt` (21 screens), `LockScreen`, `AppBackground` |

## `data/` — 61 files

| Sub-package | Files | Notable |
|---|---|---|
| `local/dao/` | **21** | 4 have any test; all DAO tests are instrumented |
| `local/entity/` | 22 files / **25 `@Entity`** | `ReminderEntity` is the only soft-delete table |
| `local/database/` | 3 | `AppDatabase.kt` (v33, 16 migrations), `Converters.kt` (17 lines, 1 converter), `TransactionRunnerImpl.kt` |
| `remote/` | 2 | `PatientRemoteDataSource(+Impl)` |
| `backup/` | 13 | `BackupCrypto` (best crypto in repo), `BackupRestorer` (318 lines, untested), `BackupValidator`, `SafetyBackupStore`, `CloudBackupApi` |

## `domain/model/` — 30 files

Pure Kotlin, no Android imports. Includes `Vaccination.kt` (whose `nextDueDate` documents
the `"d MMM yyyy"` non-padding hazard), `ClinicStats.kt`, `SyncEnums.kt`, `InventoryItem.kt`.

## `feature/` — 193 files across 26 packages

| Package | Files | ViewModels |
|---|---|---|
| `patient` | 24 | 2 |
| `inventory` | 17 | 5 |
| `statistics` | 15 | 5 |
| `settings` | 12 | 2 |
| `dashboard` | 9 | 1 |
| `vaccination` | 9 | 1 |
| `reminder` | 9 | 2 |
| `borrowed` | 6 | 1 |
| `personalreminder` | 6 | 1 |
| `widget` | 9 | — (Glance) |
| `finance` | 8 | 3 |
| `consultation` | 3 | 1 |
| `waste` | 3 | 1 |
| `doctor` | 6 | 1 |
| `staff` | 5 | 1 |
| `audit` | 3 | 1 |
| `profile`, `auth`, `search`, `sync`, `update` | 3–4 each | 1 each |

### The seven pure engines — best tested code in the repo

| File | What it decides |
|---|---|
| `feature/vaccination/domain/VaccinationEditEngine.kt` | `inventoryDiff` — stock deltas by batch |
| `feature/vaccination/domain/EditReconciler.kt` | `classifyItems` / `classifyReminders` — row-level edit plan |
| `feature/inventory/data/InventoryStockMovement.kt` | `deducted` — the single deduction funnel |
| `feature/inventory/domain/InventoryUtils.kt` | 4 expiry predicates, IST-anchored |
| `feature/borrowed/domain/BorrowBatchSelection.kt` | FEFO single-batch picker |
| `feature/reminder/data/ReminderDueListProcessor.kt` | Due-list projection — **0 tests** |
| `feature/vaccination/presentation/AddVaccinationLogic.kt` | FEFO pick, date revalidation, next-group validation |

## Backend

| Path | Files |
|---|---|
| `supabase/migrations/*.sql` | 21 — 16 no-op? no: 16 wrapped, 5 bare; **4 wrapped in `begin`/`commit`, 17 are bare DDL** |
| `supabase/functions/manage-staff/index.ts` | 257 lines — the only writer of `profiles` under the service role |
| `supabase/functions/notify-update/index.ts` | 166 lines — FCM fan-out, secret-gated |
| `supabase/verify_security.sql`, `supabase/tests/rls_role_test.sql` | live-DB scripts, **never executed from this repo** |
| `cloudflare/` | backup Worker + R2 binding |

## Configuration

| File | Contents |
|---|---|
| `app/build.gradle.kts` | `versionCode 3`, `versionName 0.5.1`, Room schema location (`:29-31`), `isReturnDefaultValues = true` (`:95`) |
| `gradle/libs.versions.toml` | version catalogue — Room 2.8.5, SQLCipher 4.19.0 |
| `app/src/main/AndroidManifest.xml` | no backup rules (`allowBackup=false`), `POST_NOTIFICATIONS`, no boot receiver, WorkManager initializer removed (`:88-97`) |
| `supabase/config.toml` | per-function JWT config; `auto_expose_new_tables` commented out — **removal 2026-10-30** |

## Files that carry the most risk

| File | Lines | Why |
|---|---|---|
| `feature/inventory/data/InventoryRepositoryImpl.kt` | 660 | every stock mutation; lost-update race |
| `feature/vaccination/presentation/AddVaccinationViewModel.kt` | 707 | `saveVaccination` is 220 lines |
| `core/sync/SyncUploader.kt` | 674 | 22 descriptors; crosses every layer |
| `feature/dashboard/presentation/DashboardViewModel.kt` | 439 | 4-level `combine`, 13 deps |
| `data/local/dao/BackupDao.kt` | 272 | second DAO over 20 tables |
| `data/backup/BackupRestorer.kt` | 318 | most destructive path, **0 tests** |
| `core/security/AuthViewModel.kt` | 369 | the sign-out data-erase gate |

## Load-bearing single-purpose files worth knowing

| File | Contract |
|---|---|
| `core/sync/CloudRefresh.kt` | always rethrows session failures, even when asked to swallow |
| `data/backup/BackupCrypto.kt` | PBKDF2 210k + AES-256-GCM, iteration bounds checked |
| `core/cache/MemoryCache.kt` | monotonic clock, bounded, thread-safe |
| `core/security/SecurityUtils.kt` | DB passphrase, backup password, encrypted prefs |
| `core/common/DateUtils.kt` | the IST anchor for the whole app |

## Related

- [[Architecture Map]] — how these files form layers
- [[Testing]] — which of these are covered
- [[Source File Index]] ← you are here
- [[Documentation Maintenance]] — when to update this index
