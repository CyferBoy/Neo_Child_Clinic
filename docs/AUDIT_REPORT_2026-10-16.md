# Neo Child Clinic — Skills Integration & Read-Only Audit

Date: 2026-10-16 · Auditor: OpenCode (Space Bunny Free) + 5 read-only explorer agents
Scope: skills selection/installation, then a read-only audit of the app. **No application source, schema, or RLS policy was changed.**

---

## 1. Executive summary

**What this app does.** An offline-first Android clinic app (Kotlin + Compose + Room + Supabase). Staff record patients, vaccinations, vaccine stock, reminders, staff and money. The phone stores everything in an encrypted Room database and syncs to Supabase when online.

**Load assumed.** One clinic, a handful of staff (receptionist, nurse, doctor, admin), a few phones/tablets in the same building, intermittent internet, and a growing patient history (thousands of rows, not millions).

**Headline result.** The architecture is genuinely good: Room really is the single source of truth, DI wiring is complete and unambiguous, no `GlobalScope`, no destructive Room fallback, database-passphrase loss never wipes clinic data, and 200 JVM tests exist and pass. The problems are **not** structural. They are concentrated in three places:

1. **Stock maths at the edges** — borrowed stock that spans two batches comes back to the wrong batch, and deleting a patient puts administered doses back on the shelf.
2. **Sync bookkeeping** — one entity is pushed with a plain INSERT that can wedge, one queue item references an entity that does not exist, and "mark as synced" has no version check, so a concurrent pull can silently delete a newer local edit.
3. **Untested state machines** — the sync queue transitions and the stock restore maths have no tests at all, which is why the bugs above survived.

I read 26 of ~45 candidate findings in the final list below. The rest were smaller (style, naming, duplicated literals) and are summarised at the end.

**Verdict: fix stock integrity first, then sync bookkeeping, then write the regression tests that stop both from coming back.**

---

## 2. Skills selected and reasons

The collection has 2,687 skills. I filtered the machine-readable catalogue (`skills_index.json`) by Android/Kotlin/Compose/Room/Supabase/Postgres/MVVM/testing/performance/accessibility, then opened and read the actual `SKILL.md` of every plausible candidate before judging it.

### Selected (2 skills, both risk `safe`, both self-contained)

| Skill | Source path | What it actually does | Why it helps this app |
|---|---|---|---|
| `kotlin-coroutines-expert` | `skills/kotlin-coroutines-expert/SKILL.md` | 3.4 KB. Structured concurrency, `supervisorScope`, `StateFlow`/`SharedFlow` choice, `flowOn`, `TestScope`/`runTest` for testing suspend code, "don't catch `CancellationException`". | This app is Flow everywhere: Room `Flow` → ViewModel `StateFlow` → Compose. Two of the confirmed bugs are coroutine-shaped (an unbounded `Flow.first()` that hangs the screen, a push/pull race). Its testing half directly fills the biggest test gap (no `kotlinx-coroutines-test` in the project today). |
| `ui-states` | `skills/ui-states/SKILL.md` | 6.5 KB, MIT, from `uxkin/agent`. Enumerates the states apps forget: first use, empty, loading, error, offline, no results, long content, permission denied, success. | Finding 24: only 3 of 8 data screens have a real error+retry state; the rest degrade to a transient snackbar. This is exactly the checklist for that screen. |

### Explicitly rejected (and why)

| Skill | Reason for rejection |
|---|---|
| `brooks-test`, `brooks-debt` | **Broken in the catalogue.** Both `SKILL.md` Setup sections require `../_shared/common.md`, `../_shared/source-coverage.md`, `../_shared/decay-risks.md`. `skills/_shared/` does not exist in `sickn33/agentic-awesome-skills` **or** in the `arnoldwender` fork (verified via GitHub contents API on both). 2 of the 4 required documents would be missing after install. |
| `production-audit` | `risk: critical`, and it wraps a third-party engine: `npx commitshow@0.3.23 audit . --json`, writing `.commitshow/` sidecars. That uploads the repo to an external service. Not acceptable for a clinic app. |
| `architecture-patterns` | Its own "Do not use when" says small/localised refactors; it is backend/microservices-oriented and pitches a vendor framework (DBOS). The brief explicitly forbids refactoring to satisfy a pattern. |
| `five-axis-code-review`, `clean-code`, `andrej-karpathy` | Duplicates: `ponytail-review`/`ponytail-audit` (installed) and this repo's `AGENTS.md` already cover correctness/security/readability/test-coverage and the "smallest correct change" rule. |
| `database-security`, `mobile-reverse`, `apk-redteam-pipeline` | `risk: offensive` / red-team scope. A codebase read of your own app is not the job, and these can drift into active attack guidance. |
| `postgres-best-practices`, `postgresql` | `supabase-postgres-best-practices` (Supabase's own) is already installed globally. `postgresql` is `risk: critical` (it tells the agent to change schemas). |
| `android_ui_verification` | Needs a running emulator. There is an AVD (`Medium_Phone.avd`) but no `emulator` binary on PATH and no attached device, so I cannot verify it works here. Its examples are React Native. |
| `database-migrations-sql-migrations` | Relevant, but `risk: critical` and Phase 4 is read-only. Listed as a Phase 6 candidate if you want migration work done. |

**Honest summary of value:** this collection adds **one genuinely new capability** to your setup (`kotlin-coroutines-expert`). Everything else in your ten priority areas is already covered by skills you had installed *before* this task — `android-dev`, `android-jetpack-compose-expert`, `supabase-postgres-best-practices`, `security-audit`, `z-audit`, `ui-ux-pro-max`. **The collection has no Room, no offline-first sync, no Android dependency/build, and no Android-native testing skill** (searched by name and description). That is the real gap, and no install fills it.

---

## 3. Installation and compatibility status

**Compatibility — verified, not assumed.**
- OpenCode **v2.0.30** (from `opencode --version`). Official docs (`opencode.ai/docs/skills/`) list `.opencode/skills/`, `.claude/skills/` and **`.agents/skills/`** as project skill roots, plus the `~/.config/...` equivalents. The collection's documented OpenCode path (`.agents/skills`) is therefore genuinely supported by your version.
- Each `SKILL.md` needs YAML frontmatter with `name` + `description`, and `name` must match `^[a-z0-9]+(-[a-z0-9]+)*$` and the directory name. Both selected skills satisfy this.

**The repository you linked is a fork.** `arnoldwender/antigravity-awesome-skills` is a fork of `iradoweck/antigravity-awesome-skills`, whose README/CHANGELOG/npm package are the `agentic-awesome-skills` catalogue (canonical upstream `sickn33/agentic-awesome-skills`, v19.4.0, 2,687 skills). The fork has 0 stars and was last pushed 2026-10-14. The installer's own `REPO` constant points at `sickn33/agentic-awesome-skills.git`, so **installed content comes from upstream, not from the fork you linked.** Two consequences:
- `ui-states` does **not exist in the pinned 19.4.0 release** (it was added to `main` on 2026-10-12). To get it I used `--tag main`, which the installer itself flags: `--tag selects a mutable Git ref and skips npm release-identity verification`.
- The broken `brooks-*` skills are broken upstream too, so this is not a fork-only defect.

**Installer review (before running anything).** I read `tools/bin/install.js` (1,423 lines) and `package.json`. It:
- spawns only `git` (clone / ls-remote / rev-parse) and fetches npm registry metadata for release identity; it never executes content from a skill;
- writes only inside `--path`, guarded by `assertSafeDestinationPath`, via a staged temp dir + atomic rename;
- tracks what it wrote in `.antigravity-install-manifest.json` and only ever prunes entries listed in **its own** manifest — so it cannot delete `.claude/skills/security-audit` or any global skill;
- refuses symlinked destinations and refuses to open a symlinked manifest.

**Steps actually run, in order (all from the project root):**
1. `npm exec --yes --ignore-scripts --package=agentic-awesome-skills@19.4.0 -- agentic-awesome-skills audit --skills kotlin-coroutines-expert,ui-states` → rejected `ui-states` (not in 19.4.0), proving the pin matters.
2. `... audit --tag main --skills kotlin-coroutines-expert,ui-states` → 8 review signals, **all of them documentation URLs** (`[network]`). No scripts, no shell, no credential access, no file writes.
3. `... --path .agents/skills --tag main --skills kotlin-coroutines-expert,ui-states --dry-run` → plan: create `.agents/skills`, install 2 entries, **remove 0**, touch no other directory.
4. Same command without `--dry-run` → installed.

**Verification that it works.** After installing, OpenCode picked up both skills without a restart — they appeared in my available-skills list. On disk:

```
.agents/skills/.antigravity-install-manifest.json   (entries: kotlin-coroutines-expert, ui-states)
.agents/skills/kotlin-coroutines-expert/SKILL.md
.agents/skills/ui-states/SKILL.md
```

**Nothing else was touched.** `git status` shows `?? .agents/` as the only new item. The 112 modified files are your own pre-existing uncommitted work; the `.gitignore` diff is CRLF line endings only (verified: `file .gitignore` → CRLF), not an edit by me. Global `~/.config/opencode/skills/` (12 skills), `.claude/skills/security-audit`, `.opencode/opencode.json` and both plugins (`superpowers`, `ponytail`) are unchanged.

**How to remove.** `rm -rf .agents/skills` (or just the two skill folders). Nothing outside that directory was created, so removal cannot affect your global setup. To re-run or change the set, repeat the installer command with a different `--skills` list; it will prune only entries in its own manifest.

**Note on `ui-states`.** It advertises an optional UXKIN MCP server (`https://uxkin.com/mcp`) backed by a paid plan for screen search. **I did not connect it and you should not** — it would send your app screens to a third party. The skill works standalone.

---

## 4. Critical bugs and data-integrity risks

### 1. Borrowed stock that spans two batches is returned to the wrong batch — doses vanish from stock permanently · **CRITICAL (confirmed)**

- **Evidence.** `deductStock` spreads a deduction across batches, earliest-expiry-first: `feature/inventory/data/InventoryRepositoryImpl.kt:392-407` loops `for (batch in batches)` and calls `deductStockFromBatch(quantity = minOf(batch.remainingQuantity, remaining))`. `feature/borrowed/data/BorrowRepositoryImpl.kt:66-73` calls it with **no batch hint**. `data/local/entity/BorrowEntity.kt:35` has a single `@SerialName("batch_id") val batchId`. The return credits only that one: `feature/borrowed/data/BorrowRepositoryImpl.kt:211-218` → `returnBorrowedStock(originalBatchId, returnToBatchId = effectiveBatchId, ...)`, where `effectiveBatchId` comes from `record.batchId` (`ReturnVaccineDialog.kt:58`).
- **Root cause.** Deduction is batch-aware; the borrow record is not. There is no per-batch allocation table.
- **Repro.** Batch B1 has 3 doses (earliest expiry), B2 has 10. Borrow 5. Deduction takes 3 from B1 + 2 from B2. The record stores B1. Return all 5 → B1 becomes 5, B2 stays 8. **Stock is overstated by 2 doses forever.** Same corruption via `deleteBorrowedItem` (`:135-141`).
- **Fix.** Make `deductStock` return its per-batch split and persist one row per batch touched (or a `borrow_allocations` table); restore against that split instead of `record.batchId`.
- **Risk / modules.** Inventory + Borrow. Any clinic that borrows across a batch boundary silently loses track of real doses.
- **Regression test.** In-memory Room: two batches, borrow across the boundary, return in full → assert each batch's `remainingQuantity` equals its pre-borrow value exactly. Also test a partial return across the boundary.

### 2. The "insufficient stock" catch is wider than the stock write → dose gone, ledger says FAILED, no restore · **CRITICAL code path / suspected trigger**

- **Evidence.** `feature/vaccination/domain/ClinicalVaccinationService.kt:95-130`: the `try` wraps the whole `deductStockFromBatch` call and the `catch` writes a `status = "FAILED"` ledger row. Inside, `InventoryRepositoryImpl.kt:450-467` writes in this order: `updateBatch(...)` (stock moves) → `insertTransaction(...)` → `enqueueBatchStockChange(...)` (WorkManager). If anything throws after the first line, the catch swallows it and the outer `transactionRunner.run` **commits normally**.
- **Root cause.** The catch is meant for "stock was insufficient" (a precondition), but it also absorbs post-write failures.
- **Consequence.** `deleteVaccination` only reverses `status == "COMPLETED"` (`VaccinationRepositoryImpl.kt:398`) and `VaccinationEditEngine.applyInventoryDiff:141` caps the reversal at `completedByBatch`. A `FAILED` row is never restored. **The dose is lost and the visit reports `inventoryStatus = FAILED`.**
- **Repro / test.** Stub the sync scheduler to throw inside `deductStockFromBatch` after the batch update; assert either (a) the batch quantity is unchanged and no ledger row exists, or (b) a `COMPLETED` row exists and `deleteVaccination` restores the dose. Today it produces neither.
- **Fix.** Narrow the `try` to the stock pre-check; let any post-write exception propagate so the transaction rolls back.
- **Risk / modules.** Vaccination, Inventory. This is the one path where a rare infrastructure hiccup becomes permanent stock loss.

### 3. Deleting a patient puts every dose ever administered back into stock · **HIGH (confirmed)**

- **Evidence.** `feature/patient/data/PatientRepositoryImpl.kt:210-213` deletes each visit via `vaccinationRepository.get().deleteVaccination(it)`, and `feature/vaccination/data/VaccinationRepositoryImpl.kt:398-408` reverses every `COMPLETED` ledger row for those visits.
- **Repro.** A child has 20 recorded vaccinations (20 real doses administered). Staff delete the patient as a duplicate registration. All 20 are restored: `B.remainingQuantity += 20`.
- **Root cause.** Clinical-record deletion is routed through an inventory-reversal routine. There is no "remove the visit without touching stock" path.
- **Fix.** Patient deletion should hard-delete visits/reminders/finance **without** the inventory reversal (delete the `inventory_deductions` rows for those visits first, or call a delete variant that skips the reversal step).
- **Risk / modules.** Patient, Vaccination, Inventory — and every statistic derived from stock.
- **Regression test.** Seed 3 completed deductions for a patient, delete the patient, assert `remainingQuantity` is unchanged and the `inventory_transactions` ledger still holds the originals.

### 4. Editing a vaccination throws on insufficient stock and discards the whole clinical edit · **HIGH (confirmed)**

- **Evidence.** Create path tolerates a shortfall (`ClinicalVaccinationService.kt:117-130`). Edit path does not: `feature/vaccination/domain/VaccinationEditEngine.kt:123-139` calls `deductStockFromBatch`, which throws `IllegalStateException("Insufficient stock in Batch ...")` at `InventoryRepositoryImpl.kt:446-448`, propagating out of `transactionRunner.run` and discarding `addVaccination(updated)` (`:73`) and the finance update (`:76`).
- **Repro.** Visit has 1 dose of batch B, B has 1 left. Edit → tick *Vaccine & Batch* → quantity 3 → Save. "Insufficient stock in Batch B". The payment correction, date correction, doctor correction and reminder edits in the same save are all lost. The identical situation at creation time saves fine.
- **Fix.** Reuse the create path's semantics: pre-check the quantity in `applyInventoryDiff` and record a `FAILED` ledger row on shortfall instead of throwing.
- **Regression test.** `VaccinationEditEngineTest` already covers `inventoryDiff`; add an insufficient-stock case asserting the visit edit still applies and the ledger marks FAILED.

### 5. `doseSchedule` is stored and never read; no missed dose is ever rescheduled · **MEDIUM (confirmed, product decision)**

- **Evidence.** `data/local/entity/VaccineEntity.kt:26` and `domain/model/Vaccine.kt:13` declare `doseSchedule`. Grep across `app/src/main` finds **no reader**. Due dates come only from free-text staff input (`AddVaccinationViewModel.kt:370-376`). The only date arithmetic in the app is the statistics age-milestone table (`core/common/AgeUtils.kt:32-45`).
- **Consequence.** A dose skipped because the child was ill never reappears. `ReminderScheduler.kt:53-55` only re-runs the daily summary over whatever rows exist.
- **Decision needed.** Either parse `doseSchedule` into an interval and seed `reminders.dueDate` at record time, or **delete the unused column** so nobody assumes the schedule is honoured.
- **Regression test.** Only if you implement it: "dose skipped → appears in the Due list with the recomputed date".

---

## 5. Offline-first and synchronization findings

Room really is the single source of truth (see "checked healthy" below). The defects are in the bookkeeping around it.

### 6. "Mark as uploaded" has no version guard → a concurrent pull can erase a newer local edit · **HIGH (confirmed)**

- **Evidence.** `core/sync/SyncUploader.kt:366-378`:
  ```kotlin
  database.openHelper.writableDatabase.execSQL(
      "UPDATE ${descriptor.tableName} SET $syncedColumn = 1 WHERE ${descriptor.pkColumn} = ?",
      arrayOf(item.entityId))
  ```
  Keyed by id only. 13 of 16 pull paths also check `syncQueueDao.isUnsynced(...)` and are safe. Three do not:
  - `feature/finance/data/FinanceRepositoryImpl.kt:405-415` — `if (local == null || local.isSynced) { insert }`
  - `feature/doctor/data/DoctorAvailabilityRepositoryImpl.kt:147-156` — same shape
  - `feature/personalreminder/data/PersonalReminderRepositoryImpl.kt:175-180` — same shape
- **Root cause.** The dirty flag is cleared without asking "is this still the row I uploaded?" Push and pull are gated by *separate* `SingleFlight` instances (`SyncRepositoryImpl.kt:101` vs `RefreshDataUseCase.kt:47`), so they genuinely overlap.
- **Repro.** Save a finance edit → a WorkManager run uploads it → before `markUploaded` returns, save a second change to the same row → any pull (Statistics refresh, Sync screen "Refresh from cloud", reconnect pass) sees `isSynced == true` and overwrites with the server copy. **Edit 2 is then uploaded. It is gone locally and remotely, with no error.**
- **Smallest fix (choose one).** Either add the uploaded row's timestamp to the `WHERE` (`AND updated_at = ?`), or — cheaper and safer — add `&& !syncQueueDao.isUnsynced(...)` to those three pull guards, matching the other 13.
- **Regression test.** A fake DAO test: row is edited while an upload is in flight → assert the row stays `isSynced = false` and the pending queue item survives.

### 7. `"REMINDER_AUDIT"` is enqueued but is not a known sync entity → every patient merge permanently fails an item · **HIGH (confirmed, verified independently)**

- **Evidence.** `feature/patient/domain/MergePatientsUseCase.kt:68`: `syncRepository.enqueue("REMINDER_AUDIT", id.toString(), ...)`. `SYNC_ENTITY_REGISTRY` (`SyncUploader.kt:50-339`) contains 26 entities — `PATIENT`, `VACCINATION`, `VISIT`, `REMINDERS`, `AUDIT_LOG`, … — and **no `REMINDER_AUDIT`**. `SyncUploader.kt:423-424` then throws `IllegalArgumentException("Unknown entity: ...")`, which `isTransientSyncError` (`SyncRepositoryImpl.kt:585-603`) treats as permanent → `markFailed` → `SyncWorker` returns `Result.failure()`.
- **Repro.** Merge two duplicate patients → a FAILED row appears on the Sync screen every time; after 5 merges the app raises "Some changes could not be synced". The user cannot fix it from the UI.
- **Fix.** Delete the three enqueue lines — `audit_logs` has no client-side patientId rewrite to push, and `AuditLogger.log` (`core/logger/AuditLogger.kt:82`) already enqueues `AUDIT_LOG` itself.
- **Regression test.** A test asserting every entity name passed to `enqueue` exists in `SYNC_ENTITY_REGISTRY` would have caught this and would catch the next one.

### 8. Reminders push uses a bare INSERT, and an UPDATE with no server id is silently dropped as success · **HIGH (confirmed)**

- **Evidence.** `core/sync/SyncUploader.kt:458-472`:
  ```kotlin
  if (item.operation == SyncOperation.CREATE.name && localReminder.serverId == null) {
      postgrest.from(table).insert(localReminder.toRemote())
      database.dueReminderDao().updateServerId(localReminder.id, localReminder.id)
  } else if (localReminder.serverId != null || item.operation == SyncOperation.CREATE.name) { ... upsert ... }
  return
  ```
- **Bug A (wedges forever).** If the insert commits server-side but the response is lost, `serverId` stays null and the retry hits the same INSERT → 409 → classified permanent → FAILED, and `retryItem` re-fails identically. Every other entity uses idempotent `upsert`.
- **Bug B (silent data loss).** An `UPDATE` whose row still has `serverId == null` matches neither branch, falls through to `return`, and `processGroup` then runs `markUploaded` + `deleteItem` (`SyncRepositoryImpl.kt:306-307`). **The edit is reported as synced and never pushed.**
- **Fix.** Replace `insert` with `upsert` (the PK is already the local UUID), and handle the `serverId == null` + UPDATE case explicitly instead of returning.
- **Regression test.** Update a reminder that has never been pushed → assert a queue item is written and an upsert is attempted.

### 9. Dashboard "refresh" only pushes; it never pulls · **MEDIUM (confirmed)**

- **Evidence.** `feature/dashboard/presentation/DashboardViewModel.kt:421-432` calls `syncRepository.processNextItems()` only. Compare `PatientListViewModel.kt:120-134` and `StatisticsViewModel.kt:101-113`, which call `refreshDataUseCase()`.
- **Repro.** Doctor opens the Dashboard on device B, where a receptionist registered a patient on device A. Pull to refresh → nothing appears, no error, the spinner just stops.
- **Fix.** Call `refreshDataUseCase()` after `processNextItems()` (3 lines, matches the other screens).
- **Regression test.** Assert `refresh()` on each screen's ViewModel calls both paths (a shared helper test).

### 10. Add/Edit Vaccination hangs forever when no active doctor exists locally · **HIGH (confirmed, verified independently)**

- **Evidence.** `feature/vaccination/presentation/AddVaccinationViewModel.kt:159-161`:
  ```kotlin
  } else if (_uiState.value.allDoctors.isEmpty()) {
      uiState.filter { it.allDoctors.isNotEmpty() }.first()
  }
  ```
  The two sibling waits three lines above use `withTimeoutOrNull(5000)` with a comment explaining exactly this hang. This one was missed.
- **Root cause.** `allDoctors` comes from the local `profiles` table filtered to active doctors (`AddVaccinationViewModel.kt:229-232`). On a fresh install that table holds only the signed-in user (`AuthViewModel.kt:158`), and `RefreshDataUseCase.kt:34-46` does not include `ProfileRepository`, so `profiles` is never bulk-pulled.
- **Repro.** New phone → sign in → open a patient → Edit. `isVaccinationLoading` never clears; the screen spins forever.
- **Fix.** Wrap line 160 in `withTimeoutOrNull(5000)` — the same one-word change as its neighbours.
- **Regression test.** A ViewModel test with an empty doctor list asserting the state leaves loading within the timeout.

### 11. Profile write and enqueue are two separate statements, and `profiles` has no `isSynced` · **MEDIUM (confirmed)**

- **Evidence.** `feature/profile/data/ProfileRepositoryImpl.kt:74-82` writes then enqueues, not in a transaction. Compare `PatientRepositoryImpl.kt:143-174` and `VaccinationRepositoryImpl.kt:254-344`, which both wrap write + enqueue in one Room transaction.
- **Repro.** User edits their name in Profile Settings; the OS kills the app mid-save; the edit has no queue row, and the next pull (`ProfileRepositoryImpl.kt:65-72`) sees "not unsynced" and overwrites it. The edit silently reverts.
- **Fix.** Wrap the two lines in `transactionRunner.run { }`.
- **Regression test.** Assert that every repository write path either writes+enqueues in one transaction or is on an entity with an `isSynced` column. A grep-based test would prevent regressions cheaply.

### 12. `cloudRefresh` swallows failures, so the aggregated "refresh failed" message never fires for 8 of 11 steps · **MEDIUM (confirmed)**

- **Evidence.** `core/sync/CloudRefresh.kt:11-19` defaults `rethrow = false`. Only patients (`PatientRepositoryImpl.kt:91`), expenses (`ExpenseRepositoryImpl.kt:119`) and personal reminders (`PersonalReminderRepositoryImpl.kt:171`) pass `true`. Vaccinations, finance, reminders, inventory, doctor availability, todos and waste use the default, so `runRefreshStep` (`RefreshDataUseCase.kt:134-142`) records nothing. Several ViewModels then swallow even the aggregated error (`PatientViewModel.kt:173`, `FinanceDetailsViewModel.kt:65-66`, `VaccineInventoryViewModel.kt:68`).
- **Repro.** Supabase returns 500 for `patient_visits` during a Statistics pull-to-refresh → spinner stops, no error, the screen keeps showing old numbers as if current.
- **Fix.** Pass `rethrow = true` at the remaining 8 call sites — the callers already aggregate and surface the error.
- **Regression test.** A test with a throwing step asserting the aggregated error is non-null (partially exists: `RefreshStepTest`).

### 13. Audit-log screens read Supabase directly, so they are empty offline · **MEDIUM (confirmed, documented deviation)**

- **Evidence.** `feature/audit/data/AuditLogRepositoryImpl.kt:12-29` deliberately bypasses Room ("these paged reads deliberately hit Supabase directly"), and `StockHistoryViewModel.kt:129-137/167-175` passes `remoteOnly = true` unconditionally. `audit_logs` **is** synced into Room (`SyncUploader.kt:269-278`), so a local copy exists.
- **Repro.** Offline: staff record a vaccination (stock drops locally), then open Stock History → the deduction is invisible while the Inventory screen shows the lower stock. Two screens on the same money path disagree with no staleness hint.
- **Fix.** Merge local + remote by `transactionId`, or read locally whenever `sync_queue` has pending `BATCH`/`INVENTORY_TRANSACTION` rows — the `!remoteOnly` branch already exists.
- **Risk / modules.** Audit, Inventory. Do not "fix" this by removing the local `audit_logs` table.

### 14. `SingleFlight` makes a waiting caller report success · **MEDIUM (confirmed)**

- **Evidence.** `core/sync/SyncRepositoryImpl.kt:103-107` — `var anyFailed = false; flight.run { anyFailed = ... }; return anyFailed`. `SingleFlight.run` (`SingleFlight.kt:17-30`) skips the block when another caller holds the mutex, so `anyFailed` stays `false`, and `SyncWorker.kt:29-39` takes the success branch and **resets `failure_count` to 0**.
- **Repro.** A WorkManager run is uploading a group that permanently fails while the user taps "Sync now" → the manual run reports success and cancels the alert that was about to fire.
- **Fix.** Return the owner's result through a `CompletableDeferred`, or re-read the FAILED count after `flight.run` instead of trusting the return value.

---

## 6. Architecture and maintainability findings

### 15. Revenue is deduplicated for vaccination income on one screen and not on two others · **HIGH (confirmed, money display)**

- **Evidence.** `feature/statistics/domain/FinanceCalculator.kt:82-84, 308-314` keeps the newest transaction per `visitId` for category `VACCINATION`. `feature/statistics/presentation/FullReportViewModel.kt:196-207, 219` and `feature/statistics/presentation/OverviewTab.kt:137-149` sum both rows.
- **Root cause.** The duplication is a real historical failure mode documented at `feature/finance/data/FinanceRepositoryImpl.kt:34-47`; only one of three code paths knows about it.
- **Repro.** A visit with two `INCOME` rows, same `visitId`, category `VACCINATION` → *Financial Statistics* collapses them, *Full Report* and *Overview → Financial Trend* double-count. **Two different revenue totals for the same month on two screens.**
- **Fix.** Call `FinanceCalculator` from both paths instead of re-implementing the sum. `OverviewTab.kt:130-159` also runs this O(all transactions) loop inside `remember {}` on the Compose main thread.
- **Regression test.** One shared test over `FinanceCalculator` + a test that both ViewModels produce the same total for the same input.

### 16. `NeoChildApp` opens the encrypted database on the main thread at startup · **MEDIUM-HIGH (confirmed)**

- **Evidence.** `app/NeoChildApp.kt:24-37` injects `financeRepository`, `vaccinationRepository` and `syncReconciler` as **fields**; Hilt injects them in `onCreate()` before `super.onCreate()` (confirmed in the generated `Hilt_NeoChildApp.java`). Those reach `core/di/DatabaseModule.kt:35-37` → `AppDatabase.getDatabase(context)` → `SecurityUtils.getDatabasePassphrase(context)` (Android Keystore + EncryptedSharedPreferences) and a SQLCipher `openDatabase` round-trip (`AppDatabase.kt:442-463`).
- **Repro.** Cold start on a device with a slow Keystore (common right after reboot — there is already a `catch` for it at `AppDatabase.kt:449`): hundreds of ms to seconds of frozen UI, potentially an ANR.
- **Fix.** Move that initialisation off the main thread (a lazily-started `CoroutineScope(Dispatchers.IO).launch` in `onCreate`, or defer until first use). No new library needed.
- **Regression test.** Not unit-testable; verify with a manual cold-start timing or a macrobenchmark **only if** you add that dependency — otherwise just measure on a real cold boot.

### 17. Documented layering rules are broken in five places · **MEDIUM (confirmed)**

All five contradict `ARCHITECTURE.md:99-104`:

| Where | Rule broken | Evidence |
|---|---|---|
| `core/security/AuthViewModel.kt:27-40` | presentation → DAO/DB | injects `SyncQueueDao`, `AppDatabase` alongside repository *implementations* |
| `core/security/SessionManager.kt:4,10-13` | core → feature impl | injects `ProfileRepositoryImpl` although the `ProfileRepository` contract is bound at `core/di/RepositoryModule.kt:63`. Same at `AuthViewModel.kt:30-31`, `DailySummaryWorker.kt:8,22`, `NeoChildFirebaseMessagingService.kt:4,18`, `MainActivity.kt:31,66` |
| `feature/widget/data/WidgetWorker.kt:9-12,27-34` | feature → feature impl | injects `ReminderRepositoryImpl` + `PatientRepositoryImpl` + `AppDatabase`; also calls back into its own `presentation` package (`:66-68`) |
| `feature/sync/presentation/SyncViewModel.kt:5,18-24` | presentation → impl | injects `SyncRepositoryImpl` although `SyncRepository` is bound and used by 16 other files |
| `core/ui/Dialogs.kt:18-19,145-148` | core → feature presentation | renders `feature.audit.presentation.AuditLogItem` from the shared UI layer |

- **Why it matters.** Each one makes a unit test impossible without constructing a real Postgrest client, a Room DB or a Glance widget. None of the five files has a unit test today.
- **Fix.** Injected-type swaps only — no behaviour change. Cheap, and each one unblocks a test.
- **Regression test.** An ArchUnit-style test would be a new dependency; a cheaper guard is a simple check in CI that greps for `import com.neochildclinic.feature.*.data.` inside `core/` and `feature/*/presentation/`.

### 18. Two god classes own persistence, remote sync, audit and session logic · **MEDIUM (confirmed)**

- `feature/inventory/data/InventoryRepositoryImpl.kt` — 655 lines, 12 injected dependencies across 5 layers (Room CRUD, low-stock policy from user preferences, audit writing, raw PostgREST).
- `core/sync/SyncRepositoryImpl.kt` — 731 lines, plus 15 internal helpers and mutable per-run state.
- **Risk.** Changing the low-stock threshold means editing the file that also owns the SQL for six tables. No test constructs either.
- **Fix.** Do not restructure for its own sake. When you next touch one of these, extract the pure calculation into a sibling file next to it and add a test — the testability is the goal, not the layer count.

### Lean findings (delete over add)

| Finding | Evidence | Suggested action |
|---|---|---|
| `TransactionRunner` is a one-line pass-through with no behaviour and one implementation | `core/database/TransactionRunner.kt:3-5` vs `data/local/database/TransactionRunnerImpl.kt:12-14`; 13 injection sites | Keep. It is the seam that makes DAOs testable. Do **not** delete. |
| `PatientUtils` is a pure delegation facade that exists only to keep call sites compiling | `core/common/PatientUtils.kt:15-56`, 74 callers | Leave it. Its own comment says why. Deleting it is churn, not savings. |
| `DeviceRepository` contract sits in `profile` while its implementation is in `auth` | `feature/profile/domain/repository/DeviceRepository.kt:1-8` vs `feature/auth/data/DeviceRepositoryImpl.kt:23` | Move the contract to `auth`. 4 callers, 10-minute change, removes a confusing dependency. |
| `PatientRemoteDataSource` exposes `getStockHistoryPage` — inventory paging under a patient name | `data/remote/PatientRemoteDataSource.kt:9-21`, consumed only by `feature/inventory/data/InventoryRepositoryImpl.kt:274` | Rename/move to an inventory-owned source. |
| `BiometricLockManager` is a Kotlin `object` with unsynchronised mutable state | `core/security/BiometricLockManager.kt:11-13,21` | Not currently a bug (all writers are on the main thread) but there is **no** visibility guarantee. Add `@Volatile` or leave it — it has tests. |

**Honest lean estimate:** only ~2 small moves are worth doing. The rest of the codebase is leaner than its size suggests.

---

## 7. Security and database findings

No secret values were read or printed during this audit. Findings below name files and lines only.

### 19. 18 of 22 migration files are git-ignored — the RLS history cannot be reviewed or replayed · **HIGH (confirmed)**

- **Evidence.** `supabase/.gitignore:5-6` contains `migrations/`. `git ls-files supabase/migrations` returns only 4 files (`20261001_app_metadata_roles.sql`, `20261013120000_role_policy_carveouts.sql`, `20261013140000_stock_trigger_negative_guard.sql`, `20261013_profiles_privileged_fields_guard.sql`). The other 18 — including `20260929000000_security_baseline.sql` and `20260825_clinic_rls_and_accountability.sql` — are ignored.
- **Why it matters.** Nobody can diff a policy change or replay the database from a clean checkout. A fresh `supabase db reset` would apply 4 files against a hand-built schema. The repo already recommended un-ignoring at `supabase/migrations/20260909_expenses.sql:3-10` and it was never done.
- **Fix.** Delete the `migrations/` line from `supabase/.gitignore` and commit the files. One-line change, largest security-process win in this report.
- **Regression test.** CI check: `git ls-files supabase/migrations | wc -l` must equal the number of `.sql` files present.

### 20. No tenant boundary — every active staff role has full read/write on all patient PII · **HIGH (confirmed, single-clinic by design)**

- **Evidence.** There is no `clinic_id` column anywhere in the schema. `supabase/migrations/20260825_clinic_rls_and_accountability.sql:113-129` creates the same four policies on 14 tables gated only on `public.is_active_staff()`; the baseline repeats them at `20260929000000_security_baseline.sql:72-87`. The `profiles.role` default is `'nurse'`.
- **Consequence.** A receptionist's token can `select * from patients` (name, phone, address, DOB) and `finance_transactions` through PostgREST. This is documented and deliberate (`20260929000000_security_baseline.sql:3-7`), so it is a boundary decision, not a bug.
- **Fix / when.** Before a second clinic ever uses this code: add `clinic_id` **in Postgres**, scope the policies to it, and put any admin cross-clinic operation in a `SECURITY DEFINER` function. **Never filter by clinic in the client.**

### 21. Two tables/buckets holding patient data have no RLS or policy in version control · **HIGH (suspected — needs one live query to confirm)**

- **a. `public.todo_notification_events`.** Created by a migration that is absent from the repo (referenced only in a comment at `20260911_doctor_availability_slots.sql:147`), so the RLS sweep at `20260825_clinic_rls_and_accountability.sql:172-189` ran before it existed and the baseline's explicit list (`20260929000000_security_baseline.sql:40-61`) omits it. An in-repo `SECURITY DEFINER` trigger writes rows whose payload carries `patient_id`, patient `name` and `doctor_id` (`20260911_doctor_availability_slots.sql:164-166, 187-193`).
- **b. Storage bucket `patient-docs`.** The app uploads, overwrites (`upsert = true`), deletes and signs URLs using the **user session and the publishable key**: `feature/patient/data/DocumentRepositoryImpl.kt:17-37`. No migration creates the bucket or any `storage.objects` policy.
- **Confirm with:** `select relrowsecurity from pg_class where relname='todo_notification_events';` and a dump of `pg_policies` for `storage.objects`. If the bucket is public, this is an unauthenticated patient-PII leak — **Critical**.
- **Fix.** Version both as migrations: enable RLS and deny-all on the table (the writer is `SECURITY DEFINER`); create a **private** bucket with `storage.objects` policies scoped to `auth.role() = 'authenticated'`. `supabase/verify_security.sql:13-19` also needs the missing table names added to its hardcoded list.

### 22. `USING (true)` select policies survived the role carve-out · **MEDIUM (confirmed)**

- **Evidence.** `supabase/migrations/20260911_doctor_availability_slots.sql:46-50` and `:96-100`: `create policy ... for select to authenticated using (true)`. `20261013120000_role_policy_carveouts.sql:77-80` drops only the blanket insert/update/delete policies, and `doctor_weekly_slots_write_admin_or_own` is declared `for all` (`:84`), so its SELECT arm also OR's in. Permissive policies OR, so `using (true)` wins.
- **Consequence.** Any valid JWT — including one belonging to a staff account an admin just deactivated (`profiles.is_active = false`) — can read every doctor's schedule until the token expires (≤1 h).
- **Fix.** Rewrite as `using (public.is_active_staff())`, reusing the existing helper. Widen nothing else. `supabase/tests/rls_role_test.sql` has no case for this — add the deactivated-staff read assertion there.
- **Regression test.** Add the read case to `rls_role_test.sql`; it already exists for expenses/weekly-slot writes.

### 23. `audit_logs` is server-side append-only but the client writes it with `upsert()` · **MEDIUM (confirmed)**

- **Evidence.** `20260929000000_security_baseline.sql:96-102` explicitly drops the UPDATE and DELETE policies on `audit_logs` (SELECT + INSERT only — correct). `core/sync/SyncUploader.kt:537` does `postgrest.from(table).upsert(localData)`, which compiles to `INSERT ... ON CONFLICT DO UPDATE` and therefore needs the UPDATE policy the moment the id already exists (a retry after a lost response). Postgres raises `42501`, classified non-transient at `SyncRepositoryImpl.kt:593` → FAILED, audit entry lost.
- **Fix.** Fix the **client** (insert with `Prefer: resolution=ignore-duplicates`, or catch the conflict). **Never add an UPDATE policy to `audit_logs`.**

### 24. Inventory transaction deletes filter on a column that does not exist remotely · **MEDIUM (confirmed, verified independently)**

- **Evidence.** `SyncUploader.kt:159-173` sets `pkColumn = "transactionId"` for `inventory_transactions`, and `:445-450` uses that same value as the **PostgREST filter column**:
  ```kotlin
  postgrest.from(table).delete { filter { eq(descriptor.pkColumn, item.entityId) } }
  ```
  But the entity is `@PrimaryKey @SerialName("id") val transactionId` (`data/local/entity/VaccineEntity.kt:80`) — the remote column is `id`. The inline comment at `:445-447` asserts the opposite rationale.
- **Consequence.** PostgREST answers `PGRST100` (400, unknown column); the `catch` at `:451-453` only swallows 404, so the delete is rethrown and the queue item fails permanently. If PostgREST ever tolerated it, the delete would match nothing and silently succeed as a no-op. Either way, remote stock-transaction rows are never deleted.
- **Fix.** Split the two names: keep `pkColumn = "transactionId"` for the local `markUploaded` SQL, add a separate `remotePkColumn = "id"` for PostgREST filters.
- **Regression test.** A test asserting every descriptor's PostgREST filter column exists in the remote schema snapshot CSV that is already in the repo (`supabase/migrations/Supabase Snippet Full Schema Catalog (Tables, Keys, Indexes).csv`).

### 25. The negative-stock guard depends on a trigger this migration never creates · **MEDIUM (confirmed)**

- **Evidence.** `supabase/migrations/20261013140000_stock_trigger_negative_guard.sql:13-21, 66-68` versions the `update_batch_stock()` **function body only**, behind `if exists (select 1 from pg_proc …)`. It never issues `create trigger … on inventory_transactions`. The client assumes the trigger exists (`SyncUploader.kt:575-586`).
- **Consequence.** On any environment where it was applied by hand, `vaccine_batches.remaining_quantity` can still go negative with no server error.
- **Fix.** Add the idempotent `drop trigger if exists` + `create trigger … after insert or update or delete` to the same migration.
- **Regression test.** `supabase/tests/rls_role_test.sql` pattern: assert `pg_trigger` has the trigger after applying migrations to a clean database.

### 26. Filters the app actually issues have no indexes; natural keys its idempotency relies on have no UNIQUE constraint · **MEDIUM (confirmed)**

- `inventory_transactions`: filtered on `vaccine_id`, `batch_id`, `transaction_type`, ordered by `timestamp DESC` with `range()` pagination (`data/remote/PatientRemoteDataSourceImpl.kt:45-54`) — **zero indexes** on that table in any migration. Needs `(timestamp DESC)`, `(vaccine_id, timestamp)`, `(batch_id, timestamp)`, `(transaction_type)`.
- `audit_logs`: read with `eq("patient_id", …)` ordered by `timestamp` (`feature/audit/data/AuditLogRepositoryImpl.kt:24-28`) — no index. Also `timestamp` is `text` per the schema catalog, so the ordering is lexicographic.
- `profiles`: looked up by `eq("email", email)` (`feature/profile/data/ProfileRepositoryImpl.kt:38-40`) — no index. Needs a unique index on `email`.
- Missing UNIQUE natural keys: `reminders` is de-duplicated client-side by `(patient_id, original_visit_id, vaccine_name, type)` (`SyncUploader.kt:125-130`) with no unique index, so two devices create duplicates. `patients.patient_clinic_id` has no partial UNIQUE, so the clinic's registration number can be duplicated.
- **Fix.** Add the indexes and the two unique indexes as one migration. The unique indexes make the existing upserts conflict-safe, which removes the need for client-side dedup logic.
- **Already healthy:** partial UNIQUE on `patient_visits.receipt_number`, UNIQUE on `user_devices.fcm_token`, atomic `ON CONFLICT` in `neo_next_receipt_number`, `ON CONFLICT DO NOTHING` in the notification trigger.

### 27. The 14 refresh/read paths have no 401 handling · **LOW-MEDIUM (confirmed)**

- **Evidence.** Only the sync queue and backup client classify 401. `data/remote/PatientRemoteDataSourceImpl.kt:24-32` and 13 other `refresh*()` calls do `catch (e: Exception) { Log.e(...); null }`.
- **Consequence.** A staff member deactivated mid-session sees empty screens rather than a forced sign-out.
- **Fix.** Route these through the existing `requiresSessionRefresh`/`handleSessionRefreshOn401` classification in `SyncRepositoryImpl.kt:398-456`, or surface a session-expired event.

### Checked and found healthy (security)

- **No policy uses `to anon`, bare `auth.uid() IS NOT NULL`, or `auth.role() IS NOT NULL`.** Every client policy names `authenticated` and a real predicate.
- `audit_logs` has **no client UPDATE/DELETE policy** — a client delete is rejected by RLS, not by app code.
- `patients`, `patient_visits`, `vaccination_items`, `finance_transactions`, `reminders` all have RLS enabled with full CRUD for active staff.
- A fallback deny-by-default sweep enables RLS on every other `public` table (`20260825_clinic_rls_and_accountability.sql:172-189`); `receipt_sequences` and `api_write_rate_limits` have all privileges revoked and no policy.
- `created_by`/`updated_by` are stamped server-side from `auth.uid()`; the client's value is discarded. **Client-supplied accountability strings cannot be spoofed.**
- Privilege-escalation guard on `profiles` covers `role`, `is_active`, `is_deleted`, `email`; staff role changes go only through the service-role `manage-staff` edge function.
- Role comes from the signed JWT claim, not `user_metadata`.
- **The last commit's carve-out fix holds** for `expenses` and for `doctor_weekly_slots`/`doctor_slot_exceptions` writes, and for `user_devices` (so a deactivated account cannot self-reactivate). Only the SELECT residue (finding 22) remains.
- **No SQL injection surface.** Every dynamic identifier goes through `format(… %I …)` or `to_regclass` against hardcoded arrays; the Worker uses bound D1 statements; the one interpolated `execSQL` in the app hits local Room with column names from a static registry and a bound parameter.
- **Secrets hygiene is good.** The app module ships only `SUPABASE_URL`, the publishable key and the worker URL (`app/build.gradle.kts:46-51`) — no service-role key, no JWT secret. The service-role key is read only inside the edge functions; the Worker's signing material comes from `wrangler secret`. `.env.local` is untracked and ignored.
- Session handling is sound: live session read (no stale cache), Keystore-backed refresh token with a one-time plaintext migration, refresh races deferred to the SDK's own rotating refresh, backup client retries once on 401/403, sign-out flushes with a data-loss gate.
- Backup Worker: ownership always from the verified JWT `sub`, never from the URL or body; a foreign `backup_id` cannot be adopted on confirm; JWT verification pins the algorithm allowlist and checks `exp`/`aud`.

---

## 8. Testing gaps

Current state: **200 JVM unit tests across 30 files, 0 failures** (verified from the existing result XMLs in `app/build/test-results/testDebugUnitTest/`; see §10 for the fresh run). Five `androidTest` files use a real in-memory Room database and `MigrationTestHelper`.

| # | Gap | Severity | Evidence | Smallest fix |
|---|---|---|---|---|
| 28 | **Stock restore and borrow-return maths have zero tests.** Only the `deducted()` *extension* is tested. The functions that actually move stock on rollback are not: `reverseDeduction` (`InventoryRepositoryImpl.kt:530-537`), `returnBorrowedStock` (`:568-581`, cross-batch release `:603-630`), the multi-batch FEFO loop (`:383-410`), and the over-return cap (`BorrowRepositoryImpl.kt:182-186`). `grep -rn "Borrow" app/src/test app/src/androidTest` → 0 hits. | **CRITICAL** | as cited | Extract the pure `copy()` arithmetic into sibling extensions next to `InventoryStockMovement.kt`, add asserts to the existing `InventoryStockMovementTest`. ~30 lines of test. |
| 29 | **`DateClassifier` has zero tests** although it takes an injectable `todayStart` precisely so it is testable, and the whole Due tab depends on it (`core/common/DateClassifier.kt:25, 76-84`). The neighbouring `PatientUtils.getNextAgeMilestone` *is* tested, so the gap is inconsistent, not deliberate. | **HIGH** | as cited | 5 asserts: overdue(-3), today, tomorrow, future, unparseable → future. One small file. |
| 30 | **Sync queue state transitions untested.** `SyncOrderingTest`, `SyncGroupFailureTest`, `SyncErrorClassificationTest` cover grouping and error classification only. The actual transitions (`PENDING → SYNCING → deleted`, `retryCount+1`, `retryCount < 5 → PENDING`, `requeueStaleSyncingItems`) live at `SyncRepositoryImpl.kt:285,297,314,326,357,520,539` and are exercised by nothing. The `retryCount < 5` ceiling is a one-character change that would silently disable retries forever. | **HIGH** | as cited | Extract the transition decision into a pure function returning the next status, then table-test it. No Room needed. |
| 31 | **No tests for any remote data source or for the backup restore round-trip.** `BackupValidatorTest` proves the validator; nothing proves the thing it validates. | **MEDIUM** | `BackupCollector.kt`, `BackupRestorer.kt:50` | After §10's P2 fixes, add a restore round-trip on in-memory Room. |
| 32 | **`ExpenseMigrationTest` can never pass — it reports green only because nobody runs `androidTest`.** It calls `helper.createDatabase(testDbName, 22)` and `runMigrationsAndValidate(..., 23, ...)`, which read pre-packaged schemas from assets, but `app/schemas/.../AppDatabase/` contains exactly one file: `33.json`. `22.json`/`23.json` do not exist. The comment at `DateGivenIndexMigrationTest.kt:20-22` claiming the project "never configured `room.schemaLocation`" is itself wrong — `build.gradle.kts:29-31, 77-79` do configure it; only version 33 was ever built. | **HIGH (CI honesty)** | as cited | Either export the missing schemas or delete the test. Do not leave a permanently-unrunnable test in the tree. |
| 33 | **No Compose UI tests at all** (`createComposeRule` → 0 hits, no `ui-test` dependency) and **0 of 35 test files use in-memory Room** in the JVM suite. `app/build.gradle.kts` has no `testOptions` block, so the first unit test touching `Log`, `Uri` or `TextUtils` fails with a bare "not mocked". | **MEDIUM** | as cited | Add `testOptions { unitTests.isReturnDefaultValues = true }` (3 lines) **before** you write the next test. Don't add Robolectric just for this. |

---

## 9. Performance and UI/UX findings

### 34. N+1: one `isUnsynced()` query per row inside 8 reconciliation loops · **HIGH (confirmed)**

- **Evidence.** `data/local/dao/SyncQueueDao.kt:65-66` is a scalar `SELECT EXISTS` round-trip. 18 call sites; these sit inside `for` loops over **all** remote rows: `InventoryRepositoryImpl.kt:642-653` (two loops), `PatientRepositoryImpl.kt:95-119` (**three** queries per patient), `VaccinationRepositoryImpl.kt:192` and `:218` (twice per item and per visit).
- **Repro.** 5,000 patients in the clinic → a single `refreshPatients()` issues ~15,000 queries.
- **Fix.** One query returning a `Set<String>` fetched before the loop (`SELECT entityId FROM sync_queue WHERE entityName = :name AND status != 'SYNCED'`), plus one `SELECT id, patientClinicId FROM patients` map. Guard semantics unchanged, ~4 queries instead of 15,000.

### 35. Statistics and Dashboard materialise whole tables with no paging · **HIGH (confirmed)**

- **Evidence.** `StatisticsViewModel.kt:55-70` `combine`s `allPatients`, `allVaccinations`, `getAllTransactions()`, `getAllExpenses()` and more. The backing queries have no `LIMIT` (`PatientDao.kt:9-10`, `FinanceDao.kt:18-19`). `DashboardViewModel.kt:259` also pulls all patients for the picker. Paged DAO methods already exist (`FinanceDao.kt:39`, `ExpenseDao.kt:40`, `BorrowDao.kt:28`) — this path just does not use them.
- **Repro.** Any write anywhere invalidates the Room tables, so `combine` re-emits and re-runs `FinanceCalculator` over all patients × all visits × all items. Visible jank and OOM risk on low-end devices.
- **Fix.** Narrow `getAllTransactions()` to the selected period with the existing `getTransactionsInRangePage(limit, offset)`; page the visits feed. Keep full-list semantics only where a report truly needs them.

### 36. Date parsing uses the device timezone, then converts to IST → due dates shift by a day east of IST · **HIGH (confirmed)**

- **Evidence.** `core/common/DateUtils.kt:92` builds `SimpleDateFormat(format, Locale.ENGLISH)` with the **default (device) timezone**; every consumer then converts via `DateUtils.kt:26-27` (`atZone(ZoneId.of(IST_ZONE_ID))`). `feature/inventory/domain/InventoryUtils.kt:14-15` mixes zones explicitly: `expiryDate.toLocalDate().isBefore(LocalDate.now())` (IST vs device).
- **Repro.** Device set to Asia/Tokyo (UTC+9). A reminder due `"16 Oct 2026"` parses to `2026-10-15T15:00Z` → `2026-10-15T20:30` IST → `DateClassifier` returns `Overdue(1)`, not `Today`. Today's dose shows as overdue and drops out of the dashboard's due-today count. The same off-by-one hits `WidgetWorker.formatWidgetDate` (`:87-93`) and `InventoryUtils.isExpired` (a batch expiring "today" is treated as expired from 18:30 IST).
- **Fix.** Construct the `SimpleDateFormat`s with `TimeZone.getTimeZone(IST_ZONE_ID)` — the constant already exists at `DateUtils.kt:21` — and use `LocalDate.now(ZoneId.of(IST_ZONE_ID))` in `InventoryUtils`. This is a handful of lines and removes a whole class of off-by-one bugs.

### 37. `Vaccination.nextDueDate` compares `"d MMM yyyy"` strings lexicographically · **MEDIUM (confirmed, currently masked)**

- **Evidence.** `domain/model/Vaccination.kt:60`: `nextVaccinations.minByOrNull { it.dueDate }`. `Constants.DATE_FORMAT = "d MMM yyyy"` (`core/common/Constants.kt:8`) is not zero-padded, so `"16 Oct 2026" < "9 Oct 2026"` — the later date wins.
- **Why it is latent.** `ReminderDueListProcessor.kt:32-35` groups by `(patientId, dueDate)`, so every produced row has one date. Any caller that assembles `nextVaccinations` from more than one date — or a reminder imported from the server with two dates under one visit — gets the wrong "next" date, which then drives every Due/Overdue count.
- **Fix.** `minByOrNull { PatientUtils.parseDate(it.dueDate)?.time ?: Long.MAX_VALUE }`.
- **Regression test.** One assert with two dates where the string order and the date order disagree.

### 38. Due-list and dashboard queries omit `is_deleted = 0` while statistics includes it · **MEDIUM (confirmed inconsistency)**

- **Evidence.** Statistics filters: `DueReminderDao.kt:103` has `AND is_deleted = 0`. The Due-list queries do not: `DueReminderDao.kt:12`, `:24`, `:72`. `getAllReminders()` feeds `ReminderRepositoryImpl.getProcessedDueFlow()` (`:86-94`), which is the source for the **Due tab**, `DashboardViewModel.dueCount()` (`:411-417`) and `ClinicStatsManager.dueToday/overdue` (`:81-88`).
- **Why it is latent.** `isDeleted` is never set to `true` anywhere in `app/src/main` today, and `refreshReminders` filters server-side (`ReminderRepositoryImpl.kt:457`). The moment any path soft-deletes a reminder, Due counts and Upcoming counts diverge.
- **Fix.** Append `AND is_deleted = 0` to those three queries.

### 39. Three custom charts are invisible to TalkBack · **HIGH (confirmed)**

- **Evidence.** `feature/statistics/presentation/BarLineChart.kt:92` and `StatisticsTrendChart.kt:76` render on a raw `Canvas(modifier = Modifier.fillMaxSize())` with **no** `contentDescription` (`grep` → 0 hits). Same for `PatientsTab.kt:170`. The whole app has **zero** `Modifier.semantics` usage.
- **Consequence.** A screen-reader user gets nothing at all from the revenue and patient trend charts — the most important visuals in the app.
- **Fix.** One `Modifier.semantics { contentDescription = "…" }` per `Canvas`, reusing the totals already computed in the surrounding `remember`. ~6 lines total.
- **Regression test.** A Compose UI test asserting each chart node has a description — only worth adding once the project has any Compose test infrastructure at all.

### 40. Load-error + retry state exists on 3 of 8 data screens · **HIGH (confirmed)**

| Screen | loading | empty | error | retry |
|---|---|---|---|---|
| Patient list | ✅ skeleton | ✅ | ⚠️ snackbar only | ❌ |
| Patient details | ✅ | ✅ | ❌ | ❌ |
| Dashboard | ✅ rich skeleton | ❌ | ⚠️ snackbar | ❌ |
| Statistics | ✅ chart skeleton | ❌ (zeroed charts) | ⚠️ snackbar | ❌ |
| Inventory | ✅ | ✅ | ⚠️ snackbar | ❌ |
| Vaccination add/edit | ✅ button | n/a | ⚠️ snackbar | ❌ |
| Reminders (Due) | ✅ | ⚠️ hand-rolled `Text` | ❌ | ❌ |
| Audit log | ✅ | ✅ | ✅ `ErrorState` | ✅ |

- **Evidence.** `ErrorState` is used on 3 screens only (`ExpenseListScreen.kt:105`, `StockHistoryScreen.kt:103`, `FullAuditLogScreen.kt:66,114`). Two hand-rolled empties bypass `EmptyState` (`DueTab.kt:88`, `BorrowedScreen.kt:233-238`). `core/ui/States.kt:80-106` already has `ErrorState` **with** an `onRetry` parameter — wiring the five missing screens is a copy, not a new component. This is exactly what the newly installed `ui-states` skill is for.

### 41. Two icon-only buttons have no label · **MEDIUM (confirmed)**

- `feature/profile/presentation/ProfileScreen.kt:290` and `feature/staff/presentation/AddStaffScreen.kt:81` — both `IconButton` + `Icon(..., null)`. 49 of 51 `IconButton` sites are labelled; `LoginScreen.kt:113` does it correctly with `contentDescription = if (passwordVisible) "Hide password" else "Show password"`. Copy that line. Touch-target sizes are healthy otherwise.

### 42. Compose stability and a per-row O(n) lookup · **MEDIUM (confirmed)**

- **Evidence.** `grep -rn "@Immutable\|@Stable" app/src/main` → **1 hit in 349 files**. Domain data classes (`Patient`, `Vaccination`, `Reminder`) go straight into list items, so every Room emission can recompose the whole visible list. Separately, `feature/patient/presentation/PatientInfoComponents.kt:103` does `history.vaccinationCards.firstOrNull { … }` inside `itemsIndexed` → O(cards) per visible row, i.e. O(n²) on a long patient timeline.
- **Note.** This is "fewer skipped recompositions", not a correctness bug. The fix for the lookup is one line (`associateBy` in a `remember`); the stability work is optional and should not be done speculatively.

### 43. Nested scrollable-in-scrollable in two sheets · **LOW (confirmed)**

`WasteScreen.kt:171` (LazyColumn) + `:279` (inner `verticalScroll`); `BorrowedScreen.kt:241` + `:288`. Bounded cost, but the inner scroll swallows drags when the sheet content fits — a real gesture bug on those two screens.

### 44. ~350 hardcoded design literals remain · **LOW (confirmed, not urgent)**

Measured: `Spacing.*` token uses **639** across 191 feature files (81 import `core.designsystem`), against hardcoded `.dp` **253**, `.sp` **22**, `Color(0x…)` **82** (36 legitimately in `VaccineWidgetTheme.kt`, which cannot use `MaterialTheme`). Concentrated: `ExpenseListScreen.kt:122/176/253/265/284`, `DashboardCards.kt:37/40/43/57`, `AppUpdateDialog.kt:70` (13 literals), `StatisticsScreen.kt:259`. No dark-mode or large-font breakage follows. Fix opportunistically when you next edit a file.

---

## 10. Recommended implementation plan

**Nothing below has been applied.** This is the proposed order; tell me which items to implement and I will start there.

### P0 — Stock integrity (fix before anything else; these corrupt real-world data)

| # | Fix | Size | Regression test required |
|---|---|---|---|
| 2 | Narrow the `try` in `ClinicalVaccinationService.kt:95-130` to the stock pre-check so post-write failures roll back instead of committing a FAILED ledger row | small | failing scheduler stub → batch quantity unchanged, no orphan FAILED row |
| 3 | Patient deletion must not reverse inventory: delete `inventory_deductions` for those visits before `deleteVaccination`, or add a no-restore delete path | small | delete patient with 3 completed deductions → stock unchanged, ledger intact |
| 1 | Record the per-batch split on borrow and restore against it | **largest** — needs a schema migration for the allocation rows | borrow across a batch boundary, return in full and in part → each batch restored exactly |
| 4 | Make the vaccination edit path tolerate insufficient stock like the create path does | small | add to `VaccinationEditEngineTest` |

**Do 3 before 1** — 3 is smaller, has no schema change, and is a one-shot data-corruption fix.

### P1 — Sync correctness

| # | Fix | Size | Test required |
|---|---|---|---|
| 7 | Delete the three `REMINDER_AUDIT` enqueue lines in `MergePatientsUseCase.kt:67-69` | 3-line deletion | "every enqueued entity name exists in `SYNC_ENTITY_REGISTRY`" guard test |
| 8 | `insert` → `upsert` for reminders; handle the `serverId == null` UPDATE case instead of returning | small | update an unpushed reminder → queue item written, upsert attempted |
| 6 | Add `!syncQueueDao.isUnsynced(...)` to the 3 pull guards in `FinanceRepositoryImpl.kt:405`, `DoctorAvailabilityRepositoryImpl.kt:147`, `PersonalReminderRepositoryImpl.kt:175` | 3 lines | edit-during-upload → row stays dirty |
| 10 | Wrap `AddVaccinationViewModel.kt:160` in `withTimeoutOrNull(5000)` | 1 line | ViewModel test with an empty doctor list |
| 11 | Wrap `ProfileRepositoryImpl.kt:74-82` in `transactionRunner.run` | 2 lines | covered by the "write+enqueue in one transaction" guard test |
| 9 | Dashboard `refresh()` also calls `refreshDataUseCase()` | 3 lines | shared helper test |
| 12 | `rethrow = true` at the 8 remaining `cloudRefresh` call sites | 8 one-word edits | refresh error surfaces (extends `RefreshStepTest`) |
| 14 | `SingleFlight` must not report success to a coalesced caller | small | concurrent call returns the owner's result |

### P2 — Regression tests that lock the above down (write these with the fixes, not after)

- `testOptions { unitTests.isReturnDefaultValues = true }` in `app/build.gradle.kts` first (3 lines, prevents a confusing "not mocked" failure later).
- Stock restore / borrow-return / FEFO arithmetic (finding 28).
- `DateClassifier` (finding 29) and the timezone fix (finding 36) — these two belong together; the tests will fail before the fix and pass after, which is exactly what a regression test is for.
- Sync queue transitions extracted to a pure function and table-tested (finding 30).
- Resolve or delete `ExpenseMigrationTest` (finding 32).

### P3 — Backend / security

| # | Fix | Note |
|---|---|---|
| 19 | Remove `migrations/` from `supabase/.gitignore` and commit the 18 files | biggest security-process win; one line + a commit |
| 21 | Confirm `todo_notification_events` RLS state and the `patient-docs` bucket policy on the live project; version both as migrations | needs one live query I cannot run from here |
| 24 | Split `pkColumn` (local) from the PostgREST filter column (remote `id`) | small, verified defect |
| 23 | `audit_logs` push: insert-with-ignore-duplicates instead of `upsert` | **client-side only** — never add an UPDATE policy |
| 25 | Add the `create trigger` to the negative-stock migration | idempotent |
| 26 | One migration adding the 6 missing indexes + 2 unique natural keys | no client change; makes upserts conflict-safe |
| 22 | Rewrite the two `using (true)` select policies to `public.is_active_staff()`; add the deactivated-staff read case to `rls_role_test.sql` | narrow, does not widen anything |
| 27 | Route the 14 read paths through the existing 401 classification | small |

**Never** disable RLS, widen a policy to `true`, or move a service-role key into the app. Privileged operations belong in the Cloudflare Worker, an Edge Function, or a `SECURITY DEFINER` function.

### P4 — UI/UX and accessibility

Findings 39 (3 charts, ~6 lines), 40 (wire the existing `ErrorState`/`EmptyState` into 5 screens), 41 (2 `contentDescription` lines), 37 (one `minBy` line), 38 (three query predicates), 43 (two scroll containers).

### P5 — Performance and architecture cleanup

Findings 34 (N+1 — the highest value here), 35 (paging), 42 (the one-line `associateBy`), 15 (share `FinanceCalculator`), 16 (move DB init off the main thread), 17 (swap 5 injected types to their existing contracts), 18 (extract only when next touched).

### Build / test status — honest report

- `:app:testDebugUnitTest --offline --rerun` was executed during this audit. The first invocation was reported UP-TO-DATE and produced no new results, so I forced a real re-run. **`BUILD SUCCESSFUL in 25s`, `:app:testDebugUnitTest` executed, freshly written result XMLs at 12:42: 30 files, 200 tests, 0 skipped, 0 failures, 0 errors.** (Offline mode, so this proves the tests pass against cached dependencies, not that dependency resolution works from scratch.)
- I did **not** run instrumentation tests (`androidTest`) — they need a device, and `adb devices` shows none attached. No emulator binary is on PATH, though the AVD `Medium_Phone.avd` exists.
- I did **not** run lint, `assembleDebug`, or any Compose benchmark. All performance findings are static analysis, not measured frame timings.
- No migration was executed and the live Supabase project was not queried, so findings 21 and parts of 20/25 remain **suspected** and need one `select relrowsecurity …` plus a `storage.objects` policy dump to close.

### Findings not included in full

~19 lower-value findings were left out of the numbered list for length: hardcoded design literals per file, several unused/dead code paths, minor `remember` key improvements, and small naming inconsistencies. Say the word and I will expand any section.

---

**Files changed by this task: exactly one — `docs/AUDIT_REPORT_2026-10-16.md` (this file), plus the new untracked `.agents/skills/` directory. No application source, schema, migration or RLS policy was modified.**

---

# Phase 6 — Implementation record (authorised: "fix it all")

**Verification: `./gradlew :app:testDebugUnitTest --offline --rerun` → BUILD SUCCESSFUL, 38 result files, 252 tests, 0 skipped, 0 failures, 0 errors** (freshly executed; timestamps verified). `:app:compileDebugAndroidTestKotlin` also compiles. Was 200 tests before this work — 52 new tests added.

## Fixed (root cause, not symptom)

| # | Finding | Fix | Test |
|---|---|---|---|
| 1 | Borrow spanning two batches returned doses to the wrong batch | New pure `selectBorrowBatch` (FEFO, single covering batch) in `feature/borrowed/domain/BorrowBatchSelection.kt`; `saveBorrowedItem` now deducts from and records the *same* batch, and refuses with a clear message when no single batch can cover it. Edit-increase path must take the extra doses from the recorded batch | `BorrowBatchSelectionTest` (8) |
| 2 | Dose lost: post-write failure recorded as `FAILED` | New typed `InsufficientStockException`; `deductStockFromBatch`/`deductStock` throw it, and `ClinicalVaccinationService` now catches **only** it — every other failure propagates and rolls the transaction back | covered by the type + existing service tests |
| 3 | Deleting a patient restored every administered dose | `deletePatient` clears `inventory_deductions` for those visits first, so `deleteVaccination` reverses nothing. Rationale documented in-code: an over-counted shelf can re-issue a dose a child already received; an under-count is visible and reconcilable | — |
| 4 | Editing a vaccination discarded the whole clinical edit on a shortfall | `VaccinationEditEngine.applyInventoryDiff` catches `InsufficientStockException`, records the batch in `reconcileInventoryDeductions` as `FAILED` (so the reversal cap still holds) and completes the edit | `VaccinationEditEngineTest` extended |
| 5 | Non-positive quantity silently *added* stock | `require(quantity > 0)` in `VaccineBatchEntity.deducted`, the shared choke point every deduction passes through | `InventoryStockMovementTest` +2 (watched fail) |
| 6 | Concurrent pull erased a newer local edit | Added `syncQueueDao.isUnsynced(...)` to the three pull paths that trusted `isSynced` alone (finance, doctor weekly slots, doctor exceptions, personal reminders) | — |
| 7 | `REMINDER_AUDIT` failed permanently on every merge | Deleted the enqueue and the now-dead `auditIds` fetch | `SyncEntityRegistryTest` (2) — **watched fail**; now fails the build if any unregistered entity name is enqueued |
| 8 | Reminders push wedged / silently dropped edits | Reminders always `upsert` on the local UUID PK instead of CREATE-only `insert`; the UPDATE-with-no-serverId case is no longer a silent no-op | — |
| 9 | Dashboard refresh only pushed | Now also calls `refreshDataUseCase()` | — |
| 10 | Add/Edit Vaccination hung forever | The missed third wait is now bounded like its two siblings | — |
| 11 | Profile edit could be silently reverted | Write + enqueue wrapped in one Room transaction | — |
| 12 | Refresh failures swallowed at 8 of 11 steps | `rethrow = true` at all 10 remaining `cloudRefresh` call sites | `RefreshStepTest` (existing) |
| 14 | `SingleFlight` made a waiting caller report success, cancelling the failure alert | `SingleFlight.run` now returns the owner's result (and failure) via a shared `CompletableDeferred` | `SingleFlightResultTest` (4) — **watched fail** |
| 15 | Two screens showed different revenue totals | Extracted `FinanceCalculator.effectiveIncome`; Full Report and Overview Trend now share it instead of re-summing | `FinanceCalculatorTest` +3 — **watched fail** |
| 16 | SQLCipher DB opened on the main thread at startup | Six injected fields → `dagger.Lazy`, and `setupSync()` + `SyncReconciler.start()` moved into the existing IO scope. Only `HiltWorkerFactory` stays eager (needed synchronously, touches no I/O) | — |
| 19 | 18 of 22 migrations were git-ignored | Removed the ignore rule (verified no secrets in those files first) — 19 migration files are now visible to git | — |
| 21a/b | `todo_notification_events` and the `patient-docs` bucket had no versioned RLS | New migration enables RLS + deny-all on the table (guarded by `to_regclass`) and creates the bucket **private** with authenticated-staff policies | — |
| 22 | `using (true)` SELECT policies survived the carve-out | Rewritten to `public.is_active_staff()`; nothing widened | — |
| 23 | `audit_logs` upsert needed an UPDATE policy the server deliberately lacks | `upsert { ignoreDuplicates = true }` → verified in the library source (v3.8.0) to emit `Prefer: resolution=ignore-duplicates` → `ON CONFLICT DO NOTHING`. Append-only stays append-only | — |
| 24 | Inventory deletes filtered on `transactionId`, which does not exist remotely | New `remotePkColumn` on the descriptor; `deleteFilterColumn` used for PostgREST. `inventory_transactions` declares `id` | `SyncDescriptorRemoteColumnsTest` (2) — proves non-vacuous against the committed schema snapshot |
| 25 | Negative-stock trigger never installed by its migration | Migration now installs it under the same condition the function guard uses | — |
| 26 | Filters had no indexes; natural keys had no UNIQUE | One migration adds 5 indexes + 3 unique keys, each skipped with a loud notice if the data is not yet clean | — |
| 27 | 14 read paths swallowed 401 | Fixed in the one place they all funnel through: `cloudRefresh` always rethrows a session failure (offline still swallowed by default) | `CloudRefreshTest` (6) |
| 33 | `android.util.Log` threw "not mocked", making log-touching tests lie | `testOptions { unitTests.isReturnDefaultValues = true }` | — (found by a test failing for the wrong reason) |
| 34 | N+1 pull loops | `SyncQueueDao.unsyncedIdSet` fetched once per refresh; patient pull now batches its local lookups (`getPatientsByIds`) | — |
| 36 | Due dates/expiry shifted a day on devices east of IST | `parseDate` anchors to IST; `InventoryUtils` gained an injectable `today` defaulting to the clinic's today | `DateClassifierTest` (10), `InventoryUtilsExpiryTest` (9) — **watched fail** |
| 37 | `nextDueDate` compared `"d MMM yyyy"` as text | Compares parsed dates | `VaccinationNextDueDateTest` (6) — **watched fail** |
| 38 | Due-list counts ignored `is_deleted` while statistics honoured it | 4 DAO queries now filter `is_deleted = 0` | — |
| 39 | Charts invisible to TalkBack | `contentDescription` on both canvases, built from the same data they draw | — |
| 40 | 5 screens had no error/retry state | Patient list gets the existing `ErrorState`+retry; Due and Borrowed hand-rolled empty text replaced with `EmptyState` | — |
| 41 | 2 unlabelled icon buttons | `contentDescription` added, copying the pattern from `LoginScreen` | — |
| 42 | O(n) lookup per visible row | One `remember`ed map | — |

## Corrections to the audit (verified, not fixed)

- **Finding 43 (nested scrollable-in-scrollable) is a false positive.** Both hits (`WasteScreen.kt:279`, `BorrowedScreen.kt:288`) are `verticalScroll` modifiers inside an **`AlertDialog`** text slot, not nested inside the `LazyColumn`. That is correct Material behaviour for a tall form. No change made.
- **Finding 39's third claim was wrong.** `SimpleGenderChart` (`PatientsTab.kt`) draws no Canvas — it renders labelled text rows with percentages, so it was already accessible. Only the two real canvases were fixed.
- **Finding 36's `InventoryUtils` half needed a testability fix, not just a timezone fix.** The helpers had no injectable reference date, which is why a deterministic boundary test was impossible; `today` is now a defaulted parameter (all existing call sites unchanged).

## Deliberately not done

| Item | Why |
|---|---|
| 5 (`doseSchedule` never read) | A product decision: either implement interval-based rescheduling or delete the column. Both change behaviour; not mine to choose |
| 18 (two god classes) | Restructuring for its own sake. The fixes above landed without touching their structure |
| 35 (statistics paging) | Real N+1 fixed (34). Changing the statistics feeds to paged queries alters what the numbers contain — needs a product decision and manual verification of every report |
| 30 (sync queue state-machine tests) | The transitions live inside `SyncRepositoryImpl` behind DAO dependencies; extracting them is a refactor of the sync core, which deserves its own reviewed change. `SingleFlight`, the registry and the error classification are covered |
| `SyncViewModel`'s concrete injection | `SyncRepository`'s own doc comment deliberately exposes only `enqueue` + state. Widening it for one screen's queue management is a worse trade than the injection; reverted with the reason in code |
| 21a/b live verification | Still needs one `select relrowsecurity …` and a `storage.objects` policy dump against the hosted project — I cannot reach it from here. The migration is written and guarded, but **apply it only after checking both** |

## TDD honesty

Watched-fail-then-fix: findings 1, 5, 7, 14, 15, 36, 37, and the `testOptions` gap (33). Verified by compile + the defect's own evidence, without a unit test: 2, 3, 6, 8, 9, 10, 11, 16, 19, 22–26, 34, 38, 40–42 — these are Room/PostgREST/Hilt wiring changes that a JVM test would need 7 hand-written fakes or a new mocking dependency to reach, and I added no dependency.