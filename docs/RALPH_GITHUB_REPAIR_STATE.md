# Ralph GitHub Repair State

Read this file at the start of every iteration; update it at the end. Never mark an issue
completed until a GitHub run confirms it.

## Current state

| Field | Value |
|---|---|
| Iteration | 4 |
| Working branch | `ci/gh-actions-repair` |
| Base commit | `1b8471d` (pushed to `main`) |
| Latest commit on branch | `575d729` (next commit pending) |
| Open PR | #21 |
| Total unresolved issues | 1 (instrumented re-verification) |
| Last successful run IDs | Android CI `44470946697`; Worker CI `44472253447`; CodeQL `44470946591`; Supabase SQL `44472253562` |
| Last failed run IDs | see [[GITHUB_ACTIONS_AUDIT]] |

## Completed repairs (verified)

- F1 Worker CI — run `44470946559` success.
- F3 Android CI lint — run `44470946697` success.
- F4 Supabase SQL — run `44472253562` success.
- CodeQL regression — run `44470946591` success.

## Completed repairs (local verification only)

- F2 Instrumented DEX class names — `assembleDebugAndroidTest` succeeds; DAO fixture fixes
  from iteration 3 await a new GitHub run (`44470946645` failed before them).
- F4 Supabase SQL step + harness — YAML parses; a new GitHub run is required
  (`44470946764` failed before the SELECT-policy fix).

## Remaining failures / work

- Push iteration 2/3 fixes and inspect the new Supabase SQL and instrumented runs.
- Confirm the final commit has a successful run for every applicable workflow.

## External blockers

- `Android release` requires a `vX.Y.Z` tag plus signing secrets; not exercised by this repair.
- `Notify app users about release` only runs on a published release.
- No local PostgreSQL server or Docker; Supabase SQL verification must happen on GitHub.

## History

- Iteration 1: audited seven workflows and 100 runs; fixed F1/F2/F3/F4; all local checks green.
- Iteration 2: Supabase SQL first verification run failed on a missing SELECT policy; harness fixed.
- Iteration 3: instrumented first verification run failed on 6 DAO fixtures; fixtures fixed.
- Iteration 4: one remaining reminder-key collision fixed; awaiting final instrumented run.
