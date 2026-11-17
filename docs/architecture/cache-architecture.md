# Neo Child Clinic — In-Memory Cache Architecture

> **Core Philosophy: Cache makes the app faster; Room makes the app correct.**

Room is always the local source of truth. `MemoryCache` is an in-memory LRU performance optimization for high-frequency reads. If the cache is empty, expired, or invalidated, reads seamlessly and safely fall back to Room.

```
UI -> ViewModel -> UseCase -> Repository -> PatientCache --hit--> return
                                                |miss
                                                v
                                              Room (source of truth) -> PatientCache.put -> return

Supabase -> Remote/Sync -> Room -> (Room InvalidationTracker) -> cache cleared -> UI
```

> [!IMPORTANT]
> **Reactive Flow Rule**: Room `Flow`s are never cached in `MemoryCache`. Only point-in-time entity lookups (e.g. `getPatientById`) leverage memory caching.

---

## 1. What is Cached

| Cache | Key Pattern | Stored Type | TTL | Reason | Invalidation Trigger |
|---|---|---|---|---|---|
| `PatientCache` | `PATIENT:<patientId>` | `Patient` | 5 min | `getPatientById` is accessed heavily during vaccination, consultation, and reminder flows | Synchronously on repo mutations (`addPatient`, `deletePatient`), Room `InvalidationTracker` on any `patients` table write, and on user logout |

---

## 2. What is Deliberately NOT Cached in Memory

| Candidate | Reason |
|---|---|
| **Patient search & lists** | Represent reactive Room `Flow`s; caching list emissions freezes UI updates and risks serving soft-deleted data. |
| **Inventory & batch lists** | Real-time stock counts must reflect immediate transaction decrements without caching delay. |
| **Statistics & financial totals** | Computed dynamically from Room tables using soft-delete exclusions. |
| **Widget data** | Backed by dedicated Room table `widget_due_cache` via `WidgetDueDao`. |
| **Auth & session state** | Owned directly by Supabase `Auth` client. |

---

## 3. Strict Caching Invariants

1. **Room Fallback**: Any cache exception or miss falls back to SQLite; cache operations never throw to UI callers.
2. **Deterministic Invalidation**: Cache entries are invalidated **after** the Room transaction commits, before returning.
3. **Generation Counter Guard**: `PatientCache.getOrLoad` increments a generation counter on every invalidation; a concurrent read that started before an invalidation is dropped and not saved back to cache.
4. **Session Boundary Purging**: `CacheRegistry.clearAll()` is executed inside a `finally` block on logout and login, preventing cross-user data leakage.
5. **MemoryCache Implementation**: Thread-safe with a single lock, bounded to `maxSize = 1000`, evicted by earliest expiration, with zero Android UI or Context references.
