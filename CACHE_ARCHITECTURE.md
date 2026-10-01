# Cache Architecture

> **Cache makes the app faster; Room makes the app correct.**

Room is the local source of truth. `MemoryCache` is a temporary, bounded, in-memory
performance optimization. An empty, expired or cleared cache is always safe: every read
falls back to Room.

```
UI -> ViewModel -> UseCase -> Repository -> PatientCache --hit--> return
                                                |miss
                                                v
                                              Room (source of truth) -> PatientCache.put -> return

Supabase -> Remote/Sync -> Room -> (Room InvalidationTracker) -> cache cleared -> UI
```

Room `Flow`s are never cached: `Room Flow -> Repository -> UseCase -> ViewModel -> UI`.

## What is cached

| Cache | Key | Value | TTL | Why | Invalidation |
|---|---|---|---|---|---|
| Patient detail (`PatientCache`) | `PATIENT:<patientId>` | `Patient` | 5 min | `getPatientById` is called repeatedly by the vaccination, consultation and reminder screens | Repo mutation (`addPatient`, `deletePatient`) invalidates that id synchronously; **any** write to the `patients` table (sync pull/upload, backup restore, clinic-ID migration worker, `refreshPatients`) clears the cache through a Room `InvalidationTracker` observer; logout / login clear it |

## What is deliberately NOT cached

| Candidate | Decision | Reason |
|---|---|---|
| Patient search / patient list | Not cached | They are Room `Flow`s. The old list cache returned `flowOf(cached)`, which froze the screen until the next invalidation and never matched the soft-delete / sync state. `PATIENT_SEARCH:<query>` therefore does not exist |
| Inventory item / list | Removed | Was invalidated but never read or populated (dead code) |
| Vaccination item / list | Removed | Provided by DI, never injected anywhere |
| Profile | Removed | Provided by DI, never injected anywhere |
| Statistics, finance aggregates | Not cached | Derived from Room; soft-deleted rows stay excluded. Profile before adding a cache |
| Widget | Not cached | Uses the Room table `widget_due_cache` via `WidgetDueDao`; independent of `MemoryCache` |
| Auth / session | Not cached | Owned by the Supabase `Auth` client |

## Rules

1. **Room first.** Cache failure (`get`/`put` throwing) falls back to Room and never breaks a read.
2. **TTL is not consistency.** It only stops entries living in memory forever. Mutations
   invalidate immediately.
3. **Deterministic invalidation.** Repository mutations invalidate *after* the Room write
   succeeds, in the same coroutine, before the suspend function returns. No
   `scope.launch { cache.invalidate(...) }`. If the Room write throws, the cache is untouched.
4. **Stale-write guard.** `PatientCache.getOrLoad` bumps a generation counter on every
   invalidation; a read that began before an invalidation does not write its result back.
5. **Writers that bypass the repository** are covered by the `InvalidationTracker` observer
   registered in `PatientRepositoryImpl` (fires after the transaction commits; the repository
   also invalidates synchronously for its own writes).
6. **Soft delete.** `deletePatient` invalidates the detail entry after the Room write, so a
   deleted patient is never served from memory.

## Logout / session isolation

- `MemoryCache` implements `ClearableCache`; `PatientCache` registers with `CacheRegistry`
  (singleton). Any new user-scoped cache must register the same way.
- `AuthViewModel.logout()` calls `cacheRegistry.clearAll()` in a `finally` block (runs even if
  device deactivation or sign-out throws). `login()` also clears first, covering sessions that
  ended without `logout()` (revoked/expired token).
- Isolation is by clearing, not by TTL. Keys are not user-prefixed because the cache is emptied at
  every session boundary.

## `MemoryCache` contract

- Thread-safe (single lock), generic, `get/put/remove/invalidate/invalidateWhere/contains/clear/size`.
- Hard `maxSize` (default 1000). When full, expired entries are dropped first, then the entry with
  the **earliest expiry** is evicted (not LRU, not "oldest").
- Lazy expiration, no cleanup coroutine. Monotonic clock (`System.nanoTime`) for TTL.
- Holds only the values passed in: no Context, View, ViewModel, DB or network client.

## How to add a cache

1. Only for repeated, non-reactive reads with a measurable benefit.
2. Wrap a `MemoryCache` in a small per-feature class, register it with `CacheRegistry`.
3. Invalidate after every successful Room write, including writers outside the repository.
4. Add tests for hit, miss, invalidation, logout.
