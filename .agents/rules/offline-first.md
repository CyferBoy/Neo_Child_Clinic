---
trigger: glob
globs: "**/core/sync/**, **/data/local/**, **/feature/sync/**"
---

# Offline-First Synchronization Rules

When working on persistence, outbox queuing, or sync workers:

1. **Local-First Single Source of Truth**:
   - The UI reads exclusively from Room via reactive Flow streams.
   - Screen state must NEVER depend on network round-trip completion.
2. **Atomic Mutations via TransactionRunner**:
   - All multi-table clinical mutations (visit + vaccination item + vial batch decrement + reminder update) MUST be wrapped in `TransactionRunner.runInTransaction { ... }`.
3. **Outbox Guarantee (`SyncQueueEntity`)**:
   - The sync outbox entry MUST be written within the identical Room transaction as the clinical mutation.
   - If an insert or update fails locally, no sync payload is queued.
4. **Idempotent Upserts & De-duplication**:
   - Remote sync pushes must use client-generated UUID primary keys and version timestamps.
   - Retried sync packets must never duplicate rows on Supabase.
5. **Session & Logout Safety**:
   - On user logout, in-memory caches must be cleared via `CacheRegistry.clearAll()`.
   - Pending `SyncQueueEntity` entries must remain securely preserved in Room across sessions.
