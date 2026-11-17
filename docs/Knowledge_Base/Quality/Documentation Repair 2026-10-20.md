# Documentation Repair — 2026-10-20

> Result pass of [[Quality/Audit Reconciliation]]'s method applied to the whole vault: every
> checkable claim in every note was re-derived against the source at `0972ed9` before
> anything was edited. Nothing here touches application code.
> Companion: [[Performance Diagnosis and Plan 2026-10-20]] (Phase 2).

## Method

1. The validator was extended first ([[Documentation Maintenance]] § What it checks) so every
   `path:line` citation written during the repair is machine-checked, not trusted.
2. Five read-only passes re-measured every countable claim (files, tests, migrations, DAOs,
   line counts, package tables) and re-read every cited line. Where two passes disagreed,
   the number was re-measured a third time before it moved.
3. Each note was corrected with targeted edits only; structure, markers and conventions are
   the note's own. Facts that depend on a live database are marked 🔍, not guessed.

## What was wrong, and what it changed

| Area | What the pass found | Notes touched |
|---|---|---|
| Renamed/stale code | `VaccinationEditEngineLedgerTest` never existed (real: `VaccinationEditEngineTest`, 8 tests); `StockDiffResult` never existed; `getConsultationsForPatientForPage` never existed | [[Quality/Bug Register]], [[Feature Roadmap]], [[Technical Debt]] |
| Counts (36 notes) | 352 `.kt` in main (not 351), 49 test files (41 unit + 8 instrumented), 277 + 56 `@Test`, 23 migrations (16 wrapped / 7 bare), 25 `@Entity` over 21 files, 21 DAOs (20 provided by Hilt), 34 `@HiltViewModel`, 51 destinations across 5 `NavGraphBuilder` files, 194 feature files / 21 packages, 72 Room index declarations | Architecture/[[Source File Index]]/[[Home]]/[[Testing]]/[[Database Schema]]/[[Feature Roadmap]] |
| Drifted citations | ~150 `path:line` references whose file resolves but whose line had moved (e.g. `PatientDao.kt:36-41` → :44-49; `DailySummaryWorker.kt:51` → :32; `DueReminderDao.kt:133-145` → :153-165) | most notes |
| Fixed-in-code shown as live | `SingleFlight` return value, `APPEND_OR_REPLACE`→`REPLACE`, reminders inside the transaction, `addStockBatch` transactional, realtime resubscribe, conflict branch reachable, size-bounded backup download, `BackupAutoScheduler` wired, error fields fed, statistics `.catch` | see [[Quality/Audit Reconciliation]] § 2026-10-19 pass |
| Schema history | "22 migrations, 7 of 22 bare, all replayable from a clone" → 23 `.sql`, 7 bare, and a clone still cannot recreate 18 of the tables (only 9 `CREATE TABLE` statements exist in git) | [[Database Schema]], [[Supabase]], [[Quality/Audit Reconciliation]] |
| Security notes | `get_my_role()` referenced by **14** legacy policies (was 10); four function-execution grants exist; several final policies *do* contain `is_deleted` (`profiles` at `20260816_security_hardening.sql:130,138`; expenses at `20261013120000_role_policy_carveouts.sql:43,52,66`); manage-staff is not the only `profiles` writer (`SyncUploader.kt:572`) | [[Supabase]], [[Row Level Security]], [[Quality/Security Concerns]], [[Known Bugs]] §4.7 |
| Cross-note contradictions | register index vs card (NCC-007, NCC-012), [[Known Bugs]] addendum vs register, "18 of 22" denominators, DAO-coverage counts (5 of 21 covered, 16 untested) | Registers and testing notes |

## Registers and reconciliation

- [[Quality/Bug Register]]: 42 cards now **29 ✅ Fixed · 9 ⚠️ Open · 2 Not a bug · 2 Needs decision**.
  Repairs: NCC-001's phantom test/type replaced with real symbols; NCC-006 counts corrected
  (16 filtered SELECTs in `DueReminderDao`, 17 predicates repo-wide); NCC-007 aligned with the
  Fixed + 🔍 live-DB precedent; NCC-012 card → Fixed with `MainActivity.kt:114-130` + 4 tests;
  NCC-036/041/042 corrections. NCC-002 re-opened (below).
- [[Quality/Hypotheses]]: HYP-001 **refuted by source** (`Patient.kt:3` typealiases
  `PatientEntity`, scalar columns only at `PatientEntity.kt:21-36`,
  `PatientRepositoryImpl.kt:89`); HYP-004's mechanism citation refreshed; HYP-005 rewritten
  around the landed `StockDeductionBlockedException`.
- [[Quality/Audit Reconciliation]]: the four "partial fix" audit claims (#14, #15, #38, #2)
  are closed with fresh evidence; net position 12 confirmed / 0 partial; §4.6 denominator
  corrected to 23 with the precise "clone cannot recreate the database" half preserved.

## New defect found by the pass (this was worth it)

**NCC-002 is not fixed.** `20261017_stock_trigger_duplicate_and_update_arithmetic.sql` is the
only definition of `public.update_batch_stock()` in the repository and its UPDATE branch still
nets `new - 2 * old`: the undo guard `v_delta <> old.quantity` (`:80`) fires for an idempotent
retry (delta 0 against a non-zero quantity), so it subtracts `OLD.quantity` and then applies
`v_delta = new.quantity - old.quantity` (`:71`, `:101`). A retried `VACCINATION` upsert (−2)
still invents 2 doses — the two examples in the migration's own comment (`:52-53`). The
client retries with the same id (`data/local/entity/VaccineEntity.kt:80`), so the retry *is*
an UPDATE. The register card carries the full arithmetic and the acceptance test; the
duplicate-trigger half (NCC-003) *is* fixed (`:26`). 🔍 Live confirmation needs the pgTAP
assertion in [[Release Checklist]] § 5.

## Validator

- New check: `path:line` citations must resolve to an existing file and to lines that exist;
  ambiguous basenames are reported; citations into deliberately untracked artifacts
  (`.artifacts/…`) produce an information note, not an error. Limitations honestly listed in
  [[Documentation Maintenance]]: a citation that resolves but no longer *means* something
  still passes — re-reading cited lines is the rule, not the tool.
- `scripts/test_validate_docs.py`: 28 → **37** regression tests.
- Final state: **0 errors, 0 warnings**, 670 citations, 25 mermaid blocks, 2 Dataview blocks,
  16 anchors, no orphans, 3 expected artifact info-notes.

## Obsidian functionality (§5 of the task)

Community plugins enabled (`dataview`, `obsidian-tasks-plugin`, `templater-obsidian`,
`obsidian-excalidraw-plugin`, `obsidian-git`) and `.obsidian/` config untouched; both Dataview
blocks validate; the vault contains **no ``​```tasks`` `` query blocks** (the Tasks plugin has
nothing to validate here); no broken wikilinks/anchors; no orphan or stub notes —
[[Welcome]] and `Templates/*.md` are intentional placeholders and were not "fixed"; no plugin
was installed or removed.

## What this pass did not change

- Application source, migrations, policies, tests, `.gitignore`, CI: **zero changes** (`git
  status` shows only the vault notes, `AGENTS.md`, `scripts/validate_docs.py`,
  `scripts/test_validate_docs.py`).
- `docs/AUDIT_REPORT_2026-10-16.md` and `docs/SECURITY_AUDIT_2026-10-13.md`: dated historical
  audits, preserved as-is (the reconciliation pages exist for exactly this).
- `README.md`, `ARCHITECTURE.md`, `CACHE_ARCHITECTURE.md`, `docs/SYNC.md`,
  `docs/BACKUP_RESTORE.md`, `docs/GETTING_STARTED.md`: checked against the corrected numbers;
  no contradictory count found, no change needed. `CACHE_ARCHITECTURE.md` is accurate
  (nanoTime clock, TTLs).

## Remaining / deliberately unresolved

- Live-database items stay 🔍 (trigger install, NCC-002 behaviour, GRANT fallout before
  2026-10-30, NSC rows in [[Release Checklist]] § 5).
- Instrumented tests were **run in CI on 2026-10-18 per the recorded results, not in this
  pass** — no instrumented claim here rests on an execution.
- Line counts are `wc -l` newline counts; `SyncUploader.kt` has no trailing newline (reads as
  678 lines in an editor, 677 by `wc -l`).
- One instruction from this pass was refuted and verified against source: `Known Bugs` §4.1's
  citation `20260929000000_security_baseline.sql:119-121` **is** the
  `create policy "active staff select profiles"` statement and was left as written.
- Approval requested, not made: `.opencode/` is gitignored (`.gitignore:82`), so the
  `docs-maintainer` agent config is untracked; `.artifacts/` (`.gitignore:30`) is why three
  citations carry info notes. Unignoring either is a tracking change that needs your call.

## Verification

```bash
python3 scripts/validate_docs.py --strict
python3 scripts/test_validate_docs.py            # 37 tests, OK
python3 scripts/validate_docs.py --changed
```
