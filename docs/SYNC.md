# Synchronization model

How Neo Child Clinic keeps Room and Supabase consistent. Read this before changing any
refresh, queue, or Realtime code. Updated for the offline-first audit (Oct 2026).

## Ownership

| Layer | Owns |
|---|---|
| **Room** | Everything the UI reads. Local reads, offline writes, reactive updates, referential integrity, transaction boundaries, pending-sync state (`sync_queue`), filtering/sorting for display. |
| **Supabase** | Shared persisted data, other devices' changes, authorization (RLS), acknowledgement of pushes, server-generated fields (receipt numbers, `updated_at`). |
| **Repositories** | The only bridge: screens never choose between Room and a network response. Read path is always `UI → ViewModel → Repository → Room Flow → UI state`. |
| **`sync_queue` + `SyncUploader`** | The only write path Room → Supabase. Nothing else pushes. |
| **Realtime + `RefreshDataUseCase`** | Triggers for Supabase → Room. Events are never applied directly; they only cause a guarded re-pull that writes through repositories. |

## Outbound (local → cloud)

1. Business call runs inside a Room transaction (`TransactionRunner`); entity writes and
   `syncRepository.enqueue(...)` commit together — no partial state.
2. UI updates from Room immediately; nothing waits on the network.
3. `SyncRepositoryImpl.processNextItems()` drains the queue; a row is deleted **only
   after** its upload returns success (`markUploaded` + `deleteItem`). Failures keep the
   row (transient → `PENDING` with bounded retries, otherwise `FAILED`, visible and
   manually retryable on the Sync screen).
4. `SyncManagerImpl` schedules `SyncWorker` through WorkManager (network-constrained,
   periodic 15 min + on-enqueue one-shots).

**Invariants**

- Single-flight: one queue run at a time in-process (`SingleFlight` gate in
  `SyncRepositoryImpl`), so concurrent workers/callers can never upload the same row
  twice, and the 5-minute stale-`SYNCING` requeue only ever sees rows from a dead run.
- Groups are ordered by `transactionGroupId` (parent before children), so server-side
  FK order is respected.
- Server `upsert` on the natural PK + 404-tolerated DELETE make retried operations
  idempotent; queue rows are never dropped on a *local read error* (it rethrows into the
  retry classifier).

## Inbound (cloud → Room)

- Triggers: Realtime events (per-screen subscriptions, debounced), screen
  pull-to-refresh, and **`SyncReconciler`** — one app-level pass on app start and on
  every offline→online edge while authenticated (outbound first, then inbound).
- `RefreshDataUseCase` is `@Singleton` with its own `SingleFlight`: concurrent callers
  (any of the nine ViewModels + the reconciler) wait for the one in-flight 11-repository
  pipeline instead of running it twice.
- Every `refresh*()` is **merge, not replace**: a row is only written when it is absent
  or fully synced locally and has no pending `sync_queue` entry. A pull does not clear
  tables; since Phase 2 (`RemotePrune`, Batch 1) it *prunes* — synced rows that the
  remote set no longer lists and that no queue entry claims are deleted locally
  (`RemotePrune.computeBatches`; REMINDERS prunes via its `serverId` remote identity).
- Realtime payloads are treated as *invalidations*, not data: they trigger a re-pull
  through the authenticated PostgREST client.

## Conflict policy (deterministic, per record)

| Situation | Outcome |
|---|---|
| Remote row newer (`updated_at`) and local has **no** pending queue entry | Remote wins → written to Room |
| Local row has a pending queue entry | Local is authoritative until pushed; pull skips it (`isUnsynced` guard) |
| Local row has unknown age (entity has no `updated_at`, e.g. finance/vaccination items) | Local edit uploads (unknown age never loses to a timestamp) |
| Remote delete | Not auto-applied by refresh (pull is insert/update-only); hard deletes propagate through the explicit `DELETE` queue path from the deleting device |
| Soft delete | `reminders` keeps `is_deleted`; excluded from due/upcoming queries. Other tables were hard-deleted by migration 31→32 |
| Duplicate / out-of-order events | Re-pulls are idempotent upserts keyed by id; duplicates collapse |

There is **no** optimistic-lock column; `updated_at` + `sync_queue.isUnsynced()` is the
concurrency mechanism. Two devices editing the same record converge to the later
timestamp; the losing device's unpushed edit is preserved until the push happens (it
loses only on the timestamp comparison at upload time).

## UI loading contract

Lists must distinguish *loading* from *loaded-but-empty*:
`PatientHistoryUiState` (Patient Details) is the reference — `isLoaded` flips true only
after the first Room emission, and empty messages derive only from a successful query.
Never map a flow's `initial` value straight to "empty".

## Account isolation

`account_prefs.last_account_id` records which account the local database belongs to.

- **Sign out:** flush the queue (bounded, online only). If unacknowledged rows remain,
  sign-out is *blocked* until the user explicitly confirms "Erase & Sign Out" (dialog in
  `AppNavigation`). Otherwise local data is erased and the session ends. Navigation
  callbacks fire only after an actual sign-out.
- **Sign in as a different account:** with no unacknowledged rows, the previous cache is
  dropped automatically; with unacknowledged rows, sign-in is blocked until the user
  confirms the reset. Cancelled sign-ins clear the local session only.

## Known limitations

- No schema-level per-account scoping; isolation is enforced at the auth boundary above.
- `sync_queue.status = 'SYNCED'` is dead (rows are deleted after ack);
  `clearSynced()` clears terminal rows (`FAILED` and older `SYNCED`), never
  `PENDING`/`SYNCING` ones.
- `FAILED` rows are retried only manually (Sync screen) or by the sign-out flush gate.
- Remote rows deleted by another device leave this device on the next refresh
  (`RemotePrune` prune pass, Phase 2 Batch 1 — see `docs/SYNC.md` merge rules).
- Statistics aggregate whole tables in memory; pagination DAOs exist but are unused.
- A cancelled sign-out flush leaves rows `SYNCING` until the 5-minute stale requeue.
