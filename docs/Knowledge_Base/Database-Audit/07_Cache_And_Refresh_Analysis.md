# Cache, Refresh and Invalidation Analysis

## 1. Cache layers

| Layer | Scope | Lifetime | Invalidation | Verdict |
| --- | --- | --- | --- | --- |
| Room (25 tables) | persistent local source of truth | until local delete/restore/logout | business writes; pull upserts | sound, but pull is not a full cache reconciliation |
| `PatientCache` | one-shot patient lookups / list hydration | process memory | Room `patients` observer + explicit `invalidate(id)` + generation guard | sound |
| `DateUtils.parseDate` cache | parsed date strings | process, cap 5,000 entries, wholesale clear at cap | none (immutable mapping) | sound |
| `MemoryCache` / `CacheRegistry` | user-scoped in-memory caches | session | `clearAll()` on logout/session end | sound |
| `widget_due_cache` | widget rows | persistent | rebuilt by `WidgetWorker` after writes/refresh | can be stale between triggers |
| Compose `stateIn(WhileSubscribed(5000))` | screen state | 5 s after last collector | Room Flow | sound |
| Finance visit-income mapping | deterministic UUID from visit id | permanent | not a cache; identity rule | sound |

No HTTP response cache or TTL exists for Supabase reads. “Freshness” is whatever the last successful pull wrote.

## 2. Refresh triggers

| Trigger | Scope | Coalescing |
| --- | --- | --- |
| App start / offline→online | `SyncReconciler`: push, then full pull | push single-flight; pull single-flight |
| Periodic `SyncWorker` | **upload only** | upload single-flight |
| Dashboard init | todos refresh + realtime | per-VM `_isRefreshing`; todo realtime debounced |
| Patient list init | full `RefreshDataUseCase` | global single-flight |
| Patient list realtime | full pipeline after 500 ms debounce | debounce + `_isRefreshing` |
| Due/completed reminders screen | `refreshReminders()` | none across screens |
| Borrowed screen | `refreshBorrows()`; manual refresh also `refreshInventory()` | none across screens |
| Waste screen | waste + inventory refresh | none across screens |
| Inventory screens | `refreshInventory()` | none across screens |
| Weekly doctor slots | availability + profiles refresh | none across screens |
| Personal reminders | `refresh()` | none across screens |
| Profile screen | `refreshProfiles()` | none across screens |
| Audit screen | remote page load | pagination guard only |
| Widget writes | `WidgetUtils.updateWidget()` unique work `REPLACE` | WorkManager unique name |

## 3. Single-flight scope

`SingleFlight` is used in exactly two places:

- `RefreshDataUseCase` — coalesces full pipeline runs.
- `SyncRepositoryImpl.processNextItems()` — coalesces upload runs.

It is **not** applied to repository-level refreshes. A screen calling `refreshInventory()` while `RefreshDataUseCase` is in its inventory step will issue a second network request, and both will upsert the same rows. Likewise, two screens that refresh the same table can run concurrently.

This is a performance/consistency issue, not a data-corruption issue, because pulls are idempotent upserts. It becomes a correctness issue where a pull has side effects: the finance self-heal enqueues UPDATES (DBA-03) and duplicate concurrent pulls can enqueue duplicates.

## 4. Verified duplicate-request scenarios

1. **App start:** `SyncReconciler` pulls all 13 steps; `PatientListViewModel.init` also invokes the full pipeline. These two are coalesced by the same `RefreshDataUseCase` flight, so this pair is safe.
2. **Borrowed screen:** `init` calls `refreshBorrows()`; pulling to refresh calls `refreshBorrows()` **and** `refreshInventory()`. Neither is part of the global flight. Opening then refreshing fetches borrows twice; inventory is fetched here and again by the pipeline. Borrows are not in the global pipeline at all, so they refresh only when this screen opens.
3. **Reminders:** due/completed/dismissed screens each call `refreshReminders()`; the pipeline also pulls reminders. No shared flight.
4. **Waste:** screen refresh calls `refreshWaste()` + `refreshInventory()`; both are also pipeline steps.
5. **Doctor slots:** screen refresh pulls availability + profiles; availability is also a pipeline step, profiles is not.
6. **Realtime:** patient-list changes trigger a **full 13-step pipeline**, not just a patients/visits pull. A burst is debounced 500 ms, but each burst still re-downloads every table.
7. **Stock history:** every page/refresh is a fresh PostgREST request with no local cache.

## 5. Staleness paths

| Path | What the user sees | Root cause |
| --- | --- | --- |
| Remote delete on another device | deleted patient/visit/finance still visible, counted in stats | upsert-only pull, no tombstones (DBA-06) |
| Non-auth pull failure | old local data, no banner (most repos swallow) | `cloudRefresh(rethrow=false)` default |
| Auth/session failure | error surfaces | explicit rethrow of session failures |
| Finance date mismatch | local row overwritten and re-uploaded | self-heal branch bypasses pending guard (DBA-03) |
| Stock history offline | empty/error screen | remote-only read (DBA-07) |
| Audit history offline | empty/error screen | remote-only by design |
| Widget after background save | old widget content until worker runs | asynchronous cache rebuild |
| Partial restore failure | restore rolled back; queue may retain old rows | restore does not clear `sync_queue` |

## 6. Positive findings

- `PatientCache` does not serve stale rows after a Room change: it observes `patients` and uses a generation token to discard an in-flight reload that started before invalidation.
- Session teardown clears all memory caches and `clearAllTables()`; logout is blocked while sync items are unacknowledged.
- The date parse cache is bounded and cleared wholesale instead of growing without limit.
- Widget refreshes are unique-work `REPLACE`, so they do not queue unbounded refreshes.

## 7. Recommendations (target, not implemented)

- Route every repository refresh through a named `SingleFlight` key (or through `RefreshDataUseCase`) and expose one “last successful pull” timestamp per table.
- Make pull failures visible as a non-blocking stale-data banner (at least for manual refreshes).
- Add tombstone reconciliation (`updated_at` + `deleted_at`, or full ID diff with pending-queue guard) before claiming offline-first convergence.
- Cache stock-history pages locally or pull the ledger; do not make a primary inventory screen network-only.
- Remove pull-time side effects such as the finance heal enqueue; repairs belong in an explicit maintenance task.

## 8. Findings

- **DBA-03** finance self-heal overwrites pending local edits.
- **DBA-06** no delete convergence.
- **DBA-07** remote-only stock history.
- **DBA-11** uncoalesced duplicate pulls.
- **DBA-14** system-timezone “today” causes cache/UI date drift.
- **DBA-15** remote-only audit history (deliberate limitation).
