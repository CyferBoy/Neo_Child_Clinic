# Untangle decision log — pass 2, 2026-11-17

Extras chosen: Comments, Tests. Pass 1 is in `untangle-2026-11-17.md`.
This pass widened scope from `app/src` to `supabase/`, `cloudflare/`, `scripts/`,
`.github/` and the workflow prompts.

## Fixes, each above the problems it solves

### One source for each duplicated rule
- `"Asia/Kolkata"` was repeated 19 times across 13 files despite `DateUtils.IST_ZONE_ID`
  claiming to be the single source of truth. Replaced all 12 main-source copies with the
  shared constant; the only literal left is the constant definition. Deleted zero-caller
  `StatisticsDateUtils.currentISTYearMonth()`.
- Fiscal-year parsing/century inference/fallback was duplicated in four files and had
  already drifted (`?: 26` vs `?: 0`). `FinanceCalculator` now owns one parser
  (`parseFinancialYearStart`), one formatter (`formatFinancialYear`) and one fallback
  (`financialYearStartOrDefault`); canonical representation is `"26-27"` everywhere, so
  callers no longer prepend `"FY "` by hand.
- `calculateGrowth` and `calculateImprovement` shared a zero guard but differed in
  sign semantics (net profit can be negative, so `abs(previous)` is intended there).
  Kept both names, shared the guard via `percentageDelta`, and documented the divisor.
- `BAN_DURATION = "87600h"` was duplicated twice in manage-staff; now one constant.
- Role allowlist and the 16-table clinical list are duplicated across TypeScript/SQL/Kotlin;
  added cross-reference comments on each copy (Kotlin in `domain/model/Profile.kt`).

### Delete needless links and surplus
- `PersonalReminderNotificationWorker` injected raw `PersonalReminderDao`; now injects
  `PersonalReminderRepository`. `PatientClinicIdMigrationWorker` injected concrete
  `SyncRepositoryImpl`; now injects `SyncRepository`. No new abstractions or bindings.
- Stale "344 unit tests" in two workflow prompts removed; script-count docs updated to 5.
- Firebase config heredoc duplicated in three CI workflows; extracted
  `.github/scripts/create-firebase-config.sh` (verified byte-identical output).
- `worker-ci` `npm audit --omit=dev` on a zero-runtime-dependency package could never fail;
  now unscoped at high threshold. Duplicate docs-validation CI step collapsed to one.
- Self-demotion test used machine-specific absolute paths; now resolves from `import.meta.url`.
- `validate_docs.py` keyword-based citation excuse replaced with an explicit path allowlist;
  real-vault test now fails in CI instead of silently skipping.
- Removed zero-caller `StatisticsDateUtils.currentISTYearMonth()`.

### Comments that restate the code
- ~48 numbered test labels/section banners, 8 main-source "what" comments, numeric prefixes
  in `Spacing.kt` KDoc, three orphaned labels in `ConsultationRepositoryImplTest`, and
  stale symbol references (`DateUtils.IST`, "worker drives them through the impl") removed.
  External-constraint rationale (SQLCipher, NCC/BUG, `%20`, token rotation, retry/duplicate,
  reversal cap, cross-language "must match") kept.

### Tests that must fail on a regression
- Removed three tautological `latest > earliest` millis assertions, a duplicate
  `assertNull`, and a stale `delay` import. Fixed a doc claiming >80% when the assertion
  is >=75%. Kept the genuine lockout timing test (it asserts a real contract).

## Deliberately kept
- `cloudflare` `accountId = app_metadata.clinic_id || user.sub` was reverted to its original
  form: `challenger-edge-cases.spec.ts` is a live multi-tenant security test that depends on
  it. Deleting the branch would have been a behavior change, not an untangle.
- Existing SQL migrations were only comment-edited — never rewritten — because they are
  deployed history. The redundant policy drops / speculative alias tables inside them stay.
- `StatisticsDateUtils.IST` remains as a thin alias derived from `IST_ZONE_ID` (6 callers,
  no drift risk); removing it would touch six files for no correctness gain.
- Pass-1 deliberate keeps still stand (sync sentinels, `CloudRefresh.rethrow`, etc.).

## Verification
- `./gradlew testDebugUnitTest --offline` — 496 tests, 0 failures, 0 skipped.
- `python3 scripts/validate_docs.py --strict` — 0 errors, 0 warnings.
- `CI=true python3 scripts/test_validate_docs.py` — 37 tests, OK.
- `npm test` in `cloudflare/backup-worker` — 22 tests, 2 files, OK.
- Final read-only re-audit: hidden links / needless links / surplus / special cases /
  can't-fail tests all clean; remaining fixes were the comment cleanups above.
