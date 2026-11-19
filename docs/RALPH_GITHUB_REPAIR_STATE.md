# Ralph GitHub Repair State

Read this file at the start of every iteration; update it at the end. Never mark an issue
completed until a GitHub run confirms it.

## Current state

| Field | Value |
|---|---|
| Iteration | 5 (final verification) |
| Working branch | `ci/gh-actions-repair` |
| Base commit | `1b8471d` (pushed to `main`) |
| Latest commit on branch | `73aed1f` (final docs commit pending) |
| Open PR | #21 |
| Total unresolved issues | 0 |
| Last successful run IDs | Android CI `44473522999`; instrumented `44473523129`; Worker `44473523064`; Supabase SQL `44473522804`; CodeQL `44473523308` |
| Last failed run IDs | see [[GITHUB_ACTIONS_AUDIT]] |

## Completion

`RALPH_LOOP_COMPLETE` for all applicable workflows: the five push/PR workflows and the
dynamic code-scanning run have successful verification runs on `73aed1f`. The two release
workflows are external-event-only (tag plus signing secrets / published release) and are
documented as not verifiable here, not as successes.

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

- None on the current tree. A release/tag run is still unverified by design (external blocker).

## External blockers

- `Android release` requires a `vX.Y.Z` tag plus signing secrets; not exercised by this repair.
- `Notify app users about release` only runs on a published release.
- No local PostgreSQL server or Docker; Supabase SQL verification must happen on GitHub.

## History

- Iteration 1: audited seven workflows and 100 runs; fixed F1/F2/F3/F4; all local checks green.
- Iteration 2: Supabase SQL first verification run failed on a missing SELECT policy; harness fixed.
- Iteration 3: instrumented first verification run failed on 6 DAO fixtures; fixtures fixed.
- Iteration 4: one remaining reminder-key collision fixed; final pass green on all workflows.
- Iteration 5: documented final run IDs; no repairs left.
