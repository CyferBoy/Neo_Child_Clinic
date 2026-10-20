# Data Access Audit Summary — 2026-10-20

> Closing report of the Phase 1 **read-only** data-access audit of the Neo Child Clinic app.
> Companion reports: [[Data Access Architecture Audit 2026-10-20]] (architecture A–M),
> [[Data Access Findings Register 2026-10-20]] (all findings with evidence),
> [[Performance and Measurement Plan 2026-10-20]] (instrumentation/benchmarks),
> [[Phase 2 Implementation Plan 2026-10-20]] (ordered batches awaiting approval).

## Executive summary

The offline-first data-access architecture is **fundamentally sound and consistently built**:
Room-first reads paint every screen offline; writes are durable (Room + `sync_queue` committed
inside one transaction); uploads are session-gated, ordered, classified, idempotent, and
retry-bounded; pulls are server-side filtered and queue-guarded; caches are invalidated by
Room’s `InvalidationTracker`; SQLCipher + RLS + a deny-sweep security baseline protect both
stores; and the 2026-10-19 performance work (scoped refresh, SQL tile counts, batched todo
pull) is verified present at `main@c6db012`.

One systemic correctness gap, several efficiency gaps, and a handful of hygiene items were
confirmed — 15 new findings (DA-01…DA-15), of which **one is High**:

- **DA-01 (High, already tracked as NCC-023 “needs decision”)**: pulls are insert/update-only
  (`docs/SYNC.md:95`), so data deleted on one device persists on every other device forever,
  and a queued edit against a remotely-deleted row uploads as a silent no-op. Single-device
  behavior is unaffected; the clinic’s second tablet is where this bites.

The rest are Medium efficiency findings (DA-02 full-collection downloads with no projection or
delta cursor; DA-03 pull-loop N+1s; DA-04 in-memory statistics over full-table combines; DA-05
unindexable search scans; DA-06 widget reading the whole patients table; DA-10 unbounded
`audit_logs`), and Low items (dead code DA-09, queue unique-key DA-08, timeouts DA-07,
Logcat identifiers DA-11, SQLite journal mode DA-12, blockquote-level design records DA-13/14/15).

Bug Register defects re-verified (NCC-002 trigger Open awaiting live-DB confirmation;
NCC-025/027/028/029/030/031/036/038/039) are cross-referenced, not duplicated.

## Top priorities (Phase 2, in order)

1. **Approve a deletion-propagation approach** and implement Batch 1 (recommended: per-table
   `SELECT id` diff pruner guarded by the pending-queue set — no schema change).
2. **Batch 0 instrumentation** so every later batch ships with before/after numbers.
3. Batches 2–4: trivial hygiene (dead code, log-identifiers), payload trims + Room v34 dedup
   index + explicit request timeout, pull-loop N+1 removals — all small, low-risk.
4. Batches 5–8 (measured, gated): widget SQL join, incremental per-table cursors staged from
   patients, statistics SQL aggregates, then the paused UI items (`collectAsStateWithLifecycle`
   dependency decision).

## Risks needing evidence before action (registered, not actionable yet)

- PostgREST update-matches-zero-rows silence (DA-01 consequence 2) — verify in staging once.
- supabase-kt effective `requestTimeout` default (DA-07) — one command in a test harness.
- SQLCipher + WAL vs TRUNCATE, and cold-start verification-open cost (DA-12) — device benchmark.
- Live Supabase trigger state (NCC-002/003/007) — separate live-DB track, never attempted here.

## Decisions needing approval before Phase 2 starts

| Decision | Options | Recommendation |
|---|---|---|
| Deletion propagation | (a) server tombstones · (b) id-diff pruner · (c) soft-delete booleans | **(b)** — smallest, no schema change, guarded |
| `lifecycle-runtime-compose` dependency (item 5 of the earlier plan) | add dep vs manual lifecycle gating | add — single small dependency |
| `audit_logs` retention (DA-10) | keep-all vs windowed prune | windowed (90 d local / 1 y remote), needs sign-off |
| Manual refresh scope (DA-14) | full pipeline (status quo) vs per-screen scoped | keep status quo initially; measure first |

## Commands executed during Phase 1 (all read-only)

`git status --short` / `git branch --show-current` / `git log -1 --oneline` /
`git diff --stat` / `git diff --cached --stat` / `git status --ignored --short` /
`git rev-list --left-right --count origin/main...HEAD` / `git log` history checks •
`ls`/`find`/`wc -l` inventory sweeps • `grep` audits over `app/src/main/java`,
`supabase/migrations`, KB notes • `read` on ~30 source files (sync core, repositories,
DAOs, AppDatabase, workers, DI, backup stack, ViewModels) • two mid-audit false alarms on
`git log -- <path>` filters were re-checked and resolved (tree unchanged) •
`python3 scripts/validate_docs.py --strict` (final validation below). **No Gradle/test
execution was needed** — every claimed finding is pinned to source lines; no network
connections to any Supabase/production system were made; `.env.local` and
`app/google-services.json` were never read.

## Final state checks (audit close)

- `git branch --show-current` → `main`; `git log -1 --oneline` → `Perf: dashboard load path
  (Phase 2 items 1–4)`; `HEAD == origin/main` (`0 0` divergence); `stash@{0}` untouched.
- **Implementation untouched**: the only working-tree changes are the five new audit reports
  under `docs/Knowledge_Base/Quality/` (untracked). Zero modifications to Kotlin, SQL,
  Gradle, configuration, or existing documentation; nothing staged; no commits made.
- Doc validator `--strict` re-run at close — result and any fixes recorded in the session log;
  citations resolve to current files/lines.

## Confirmation

Phase 1 produced exactly five files (this one and its four companions). No code, schema,
build, or configuration file was created, modified, or reverted; no stash, reset, discard,
install, connect, fetch, or push took place. Phase 2 work starts only on explicit approval of
[[Phase 2 Implementation Plan 2026-10-20]] (batch-by-batch).

## Related reports

[[Data Access Architecture Audit 2026-10-20]] · [[Data Access Findings Register 2026-10-20]]
· [[Performance and Measurement Plan 2026-10-20]] · [[Phase 2 Implementation Plan 2026-10-20]]
· Related notes: [[Bug Register]], [[Known Bugs]], [[Offline Synchronization]], [[Technical Debt]]
