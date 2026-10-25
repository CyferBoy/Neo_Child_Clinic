# Due Vaccination

> Reminders, notifications, the home-screen widget, and personal follow-ups.
> Related: [[Vaccination Management]] · [[Statistics Dashboard]] · [[Offline-First Architecture]]

## Two parallel systems

| | Clinic due-vaccine | Personal follow-up |
|---|---|---|
| Table | `reminders` | `personal_vaccine_reminders` |
| Entity | `data/local/entity/ReminderEntity.kt` | `data/local/entity/PersonalReminderEntity.kt` |
| Created by | `ReminderRepositoryImpl.saveNextVaccination` | `PersonalReminderRepositoryImpl.createReminder` |
| Lifecycle | `ACTIVE` → `COMPLETED` / `DISMISSED` | `PENDING` → `READY` → `COMPLETED`, + `CANCELLED` |
| Notification shape | **one aggregated** summary with counts | **one per reminder row** |
| Worker | `DailySummaryWorker` | `PersonalReminderNotificationWorker` |

✅ They are deliberately independent — `PersonalReminderEntity.kt:15-23` states this in
KDoc, and `PersonalReminderRepositoryImpl.kt:78-80` adds that *"nothing in this repository
infers a status change from vaccination, payment, or inventory activity."*

⚠️ **Both workers share `settings.reminderTime`**, so a user can get **two notification
bursts at the same minute**. The channels differ, so it is not duplicate content for the same
fact — but it is not coordinated either.

## Triggering — WorkManager only

✅ **There is no `AlarmManager` and no boot receiver anywhere in the app.** Verified by
exhaustive grep. Periodic work survives reboot because WorkManager's own scheduler persists.

The app removes the default `WorkManagerInitializer` (`AndroidManifest.xml:88-97`) and
supplies its own `Configuration.Provider` + `HiltWorkerFactory`
(`app/NeoChildApp.kt:27-28`, `:53-56`).

`core/notification/ReminderScheduler.kt` — two unique periodic works, enqueued **only from
`NeoChildApp.onCreate`** (`:110-113`):

| Work | Period | Constraints | Backoff | Policy |
|---|---|---|---|---|
| `DailySummaryWorker` | **1 hour** (not daily — the dedupe lives in the worker, `:32`) | `NetworkType.CONNECTED` (`:31`) | `EXPONENTIAL`, 15 min | `UPDATE` (`:63`) |
| `PersonalReminderNotificationWorker` | 24 hours (`:81`) | **none** | — | `UPDATE` (`:84`) |

`runNow()` (`:87-93`) is a **non-unique** expedited `OneTimeWorkRequest<DailySummaryWorker>`.

⚠️ **Two consequences, one now resolved:**

1. **Was: changing the notification time in Settings had no effect until the app restarted.**
   `NotificationSettingsViewModel.updateSettings` (`:27-31`) wrote DataStore and never
   rescheduled — the two schedule methods' only call sites were `NeoChildApp.kt:111-112`.
   `updateSettings` now re-arms both (`:37-38`), and `ExistingPeriodicWorkPolicy.UPDATE`
   recomputes the delay, so the new time takes effect immediately (NCC-010).
2. **Every reminder mutation enqueues a fresh worker.** `triggerImmediateCheck()`
   (`ReminderRepositoryImpl.kt:520-523`) fires `runNow()` from seven call sites
   (`:271`, `:292`, `:312`, `:354`, `:368`, `:388`, `:409`).
   `DueViewModel.completeVaccination` (`:134-140`) loops over reminders, so completing a
   3-reminder row enqueues 3 workers — each recomputing `getClinicStats()` (a 4-way
   `combine` over full tables, `ClinicStatsManager.kt:40-45`) plus a full
   `getInventoryItems()` read.

## Notification channels

`core/notification/NotificationHelper.kt:43-79`, all created in `init`:

| Constant | Id | Importance |
|---|---|---|
| `CHANNEL_DAILY_SUMMARY` | `daily_summary` | `DEFAULT` |
| `CHANNEL_LOW_STOCK` | `low_stock_alerts` | `HIGH` |
| `CHANNEL_SYNC_BACKUP` | `sync_backup_alerts` | `HIGH` |
| `CHANNEL_APP_UPDATES` | `app_updates` | `HIGH` |
| `CHANNEL_PERSONAL_REMINDERS` | `personal_vaccine_reminders` | `HIGH` |

Fixed ids: `SUMMARY_ID=1001`, `LOW_STOCK_ID=1002`, `SYNC_ALERT_ID=1003`, `APP_UPDATE_ID=1004`,
`PERSONAL_REMINDER_BASE_ID=2000` (`:30-34`).

`POST_NOTIFICATIONS` is declared (`AndroidManifest.xml:7`) and requested at runtime in
`DashboardScreen.kt:186-191` and `MainActivity.kt:154-161`.

## State transitions

All transitions are DAO methods in `DueReminderDao.kt`, implemented as **insert-replace**
(not `UPDATE`) because of the unique composite index on
`[patientId, originalVisitId, dueDate, type, vaccineName]` (`ReminderEntity.kt:26`).

```kotlin
// DueReminderDao.kt:153-165
suspend fun moveDueToCompleted(reminder: ReminderEntity, completedBy: String, notes: String? = null) {
    val updated = reminder.copy(
        status = "COMPLETED",
        reminderEnabled = false,                     // ← the flag flip
        completionDate = getCurrentIsoTimestamp(),
        ...)
    insertReminder(updated)
}
```

So the invariant is **`status="COMPLETED" ⟺ reminderEnabled=false`** and
**`status="DISMISSED" ⟺ reminderEnabled=true`**.

| Action | Caller | Repository line |
|---|---|---|
| Schedule | Add/Edit Vaccination | `ReminderRepositoryImpl.kt:137-273` |
| Complete | `DueViewModel.completeVaccination` (`:134`) | `:275-294` |
| Dismiss | `DueViewModel.dismissReminder` (`:126`) | `:358-370` |
| Reschedule | `DueViewModel.rescheduleVaccination` (`:142`) | `:296-314` |
| Restore | `DueViewModel` / `CompletedDismissedViewModel` | `:372-390` |
| Cancel one vaccine | — | `:317-356` |
| Hard delete | — | `:392-411` |

⚠️ `DueViewModel`'s four mutations (`:126`, `:134`, `:142`, `:150`) are `viewModelScope.launch`
bodies with no `try`/`catch` — an uncaught exception there reaches the default handler and
**crashes the process**. NCC-036 re-measured the class of defect at **140 launches across 34
`*ViewModel.kt` files** (upper bound — the unprotected subset was not separated). See
[[Error Handling]].

## ReminderDueListProcessor — the authoritative projection

`feature/reminder/data/ReminderDueListProcessor.kt` is the single function every Due screen
depends on:

- `process()` (`:31`): active reminders are `status == "ACTIVE" && reminderEnabled`, grouped
  by `patientId to dueDate` (`:32`) → **one row per (patient, due date)** with all reminders
  attached. If no parent visit exists it **synthesises a shell** `Vaccination` with a random
  UUID (`:40-45`).
- Terminal rows (`:62-94`): matched by `(COMPLETED && !enabled) || (DISMISSED && enabled)`,
  grouped by `Triple(patientId, dueDate, status)`. Terminal rows with **no matching visit are
  dropped** (`:73-74`).
- `stats()` (`:99-117`): `dueToday` / `dueTomorrow` / `overdue` via `DateClassifier`;
  `completedToday` / `dismissedToday` via `isoToLong(...) >= todayStart` where
  `todayStart` is **IST** start-of-day (`DateClassifier.kt:18`, `DateUtils.kt:32-36`).

⚠️ **Zero tests.** This is the most load-bearing pure function in the reminder subsystem and
nothing covers its five lifecycle branches. See [[Testing]].

## Personal reminder selection

`core/notification/PersonalReminderNotificationWorker.kt:23-43`:

| Condition | Behaviour |
|---|---|
| `reminderDate` blank | notify, `undated = true` — **every run** |
| `== today` | "Today's follow-up reminder" |
| `< today` | "Overdue by N day(s)", `N.coerceAtLeast(1)` |
| `> today` | **no notification** |

`getActiveReminders()` = `status IN ('PENDING','READY')` (`PersonalReminderDao.kt:12`), no
date filter. ⚠️ A blank `reminderDate` therefore notifies on **every single run** — and
because `runNow()` fires from every mutation, that is frequent.

## Widget

`feature/widget/` — a Glance home-screen widget reading `widget_due_cache`.

- `WidgetWorker` → `WidgetLocalDataSource` → `WidgetDueDao.refreshCache` (a genuine
  two-statement transactional swap, `WidgetDueDao.kt:18-22`).
- ✅ Respects offline-first — the cache is populated from Room, never from the network.
- ✅ `WidgetWorker.kt:84-89` deliberately uses the **clinic's IST year**, not the device's,
  with an explicit comment.
- Glance cannot use `@AndroidEntryPoint`, so `VaccineWidget.kt:187-191` declares a
  legitimate `@EntryPoint` and resolves via `EntryPointAccessors.fromApplication` (`:49-51`).
  ✅ Correct pattern, not a DI bypass.

## Confirmed findings

See [[Known Bugs]] for full severity. Reminder-specific:

| # | Finding | Evidence |
|---|---|---|
| 1 | Notification-time changes did not take effect until process restart | Was: `ReminderScheduler.kt`'s 2 call sites, both `NeoChildApp.kt:111-112`; now re-armed from `NotificationSettingsViewModel.kt:37-38` (NCC-010) |
| 2 | Every reminder mutation enqueues a non-unique `DailySummaryWorker`; N reminders → N full stats recomputes | `ReminderRepositoryImpl.kt:520-523` (7 call sites), `DueViewModel.kt:134` |
| 3 | `ReminderRepositoryImpl.reschedule` silently discarded its `reminderDate` argument (the whole chain was dead) | Was `:296-314`; the argument, the dialog picker and both `@Transient` fields were deleted (NCC-032) |
| 4 | `ReminderDueListProcessor` drops rows matching neither terminal branch | `:62-65` |
| 5 | `notificationsSentToday` was **structurally always 0** — both fields `@Transient`, never persisted, no writer | Deleted, along with the unused `ReminderStats` field — no screen read it (NCC-033); the remaining stats block is `ReminderDueListProcessor.kt:99-116` |
| 6 | Personal reminder with a blank date notifies on every run | `PersonalReminderNotificationWorker.kt:26-28` |
| 7 | Scheduler uses **device** timezone; all statistics use **IST** | `ReminderScheduler.kt:39`, `DailySummaryWorker.kt:29` |
| 8 | ✅ Was: pull paths never applied tombstones — a row deleted on device B stayed on device A. Now both reminder pulls reconcile absence through `RemotePrune`. | `ReminderRepositoryImpl.kt:505`, `PersonalReminderRepositoryImpl.kt:201` |
| 9 | ✅ Was: `getAuditTrail` mapped audit fields to semantically wrong targets and had no consumer. The method and the unused `ReminderAudit` model were deleted. | `ReminderRepository.kt` (method removed) |

## Related

- [[Vaccination Management]] — where reminders are created
- [[Statistics Dashboard]] — `dueToday`, `overdue` share `DateClassifier`
- [[Room Database]] — the `reminders` table and its partial soft-delete filtering
- [[Error Handling]] — the unguarded `DueViewModel` mutations
