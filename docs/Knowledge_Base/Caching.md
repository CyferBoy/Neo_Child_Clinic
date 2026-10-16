# Caching

> The three in-memory cache layers, their invalidation rules, and what each one gets wrong.
> Related: [[Offline-First Architecture]] · [[Room Database]] · [[Known Bugs]]

## The three layers

| Layer | File | Holds | Bound / TTL | Invalidated by |
|---|---|---|---|---|
| `MemoryCache<K,V>` | `core/cache/MemoryCache.kt:35-93` | generic KV | **5 min** default, **1000** entries | `remove`, `clear` |
| `CacheRegistry` | `core/cache/CacheRegistry.kt:15-25` | `CopyOnWriteArrayList<ClearableCache>` | — | `clearAll()` |
| `PatientCache` | `feature/patient/data/PatientCache.kt:20-60` | `PATIENT:<id>` → `Patient` | 5 min, 1000 entries | `invalidate(id)`, `clear()` |
| Room (`neochild_db`, v33, SQLCipher) | `data/local/database/AppDatabase.kt` | the real source of truth | — | `clearAllTables()` on sign-out |

`Room` is not a cache in the eviction sense — it is the source of truth, and every screen
reads from it. The three above sit in front of it.

## MemoryCache

`core/cache/MemoryCache.kt` is the best-tested class in the app (10 tests in
`app/src/test/.../core/cache/MemoryCacheTest.kt`, including an 8-thread concurrency test).

Verified properties:

- Uses `SystemClock.elapsedRealtime()`, not wall clock — so a clock change cannot expire
  entries early (`:83-93`).
- Lock-guarded; `maxSize` eviction removes the **earliest-expiring** entry (`:60-68`).
- Expiry is **lazy** — checked on `get` and `put`, not on a timer. An entry that is never
  read again is never evicted; only `put` triggers a size trim (`:90-93`).
- Overwriting an existing key does **not** count against `maxSize` — correct, since it
  replaces rather than adds.

## CacheRegistry

A list of `ClearableCache` that can be bulk-cleared. Only **two** call sites, both in
`core/security/AuthViewModel.kt`:

```kotlin
cacheRegistry.clearAll()   // :212 — after login
cacheRegistry.clearAll()   // :341 — after sign-out
```

Account isolation on logout is a genuine strength of this codebase. `PatientCacheTest.kt`
has a dedicated test asserting *"logout clears so next user can't see user A's data"*.

## PatientCache

`feature/patient/data/PatientCache.kt` is the only domain-object cache. It maps
`PATIENT:<id>` to the `Patient` model with a 5-minute TTL and a **generation guard**
(`:29-48`): a read that started before an invalidation will not write its now-stale result
back into the cache. This is the correct way to avoid the classic
invalidate-during-load race, and it is tested.

Invalidation triggers:

- `patientCache.invalidate(patient.id)` after every write — `PatientRepositoryImpl.kt:190`.
- `clear()` on **Room invalidation of table `patients`** — `PatientRepositoryImpl.kt:66-68`.
- `clear()` at the end of `refreshPatients` — `:139`.

⚠️ The Room-triggered clear is scoped to `patients` only. Editing a **vaccination** does
not clear it, so a `Patient` object cached with a stale embedded vaccination count or
history can survive a vaccination edit until the 5-minute TTL expires.

## DI wiring

All three are wired by **constructor injection**, not by an explicit `@Provides`:
`CacheRegistry` and `PatientCache` carry `@Singleton`/`@Inject` constructors, so Hilt
resolves them implicitly. `grep` over `core/di/*.kt` finds **zero** mentions of any of
them — the wiring is implicit, not missing.

`MemoryCache` is **not injectable**: `PatientCache` constructs it directly with the
defaults (`PatientCache.kt:25`), so TTL and size cannot be tuned per environment or test.

## Why reads feel instant

The cache is a **nice-to-have, not the reason reads are fast**. Every list screen reads a
live Room `Flow`:

- `PatientRepositoryImpl.kt:86` — `allPatients` is a Room `Flow`.
- `PatientRepositoryImpl.kt:254-255` — `searchPatients` likewise.

Room queries on a local SQLite file never block on the network, so the offline-first
experience is provided by the persistence layer, not by these caches. The caches exist to
avoid re-running a query for a detail screen that was just on the list.

## Confirmed findings

| # | Finding | Severity | Evidence |
|---|---|---|---|
| 1 | `MemoryCache` lazy expiry means read-never entries are never evicted — bounded by `maxSize` but never TTL-swept | Low | `MemoryCache.kt:90-93` |
| 2 | `MemoryCache` TTL/size hardcoded; not injectable | Low | `PatientCache.kt:25` |
| 3 | `PatientCache` not invalidated on vaccination edit — stale patient object for up to 5 min | Medium | Room invalidation filter is `patients`-only, `PatientRepositoryImpl.kt:66-68` |
| 4 | `CacheRegistry` clear only on login/logout — a stale cache survives a mid-session account switch until `clearAllTables` | Low | `AuthViewModel.kt:212`, `:341` |

## Related

- [[Offline-First Architecture]] — the sync pipeline that feeds these caches
- [[Room Database]] — the layer the caches actually accelerate
- [[Known Bugs]] — the confirmed-defect list
- [[Technical Debt]] — dead code and duplication across the cache layer
