# Ralph GitHub Repair State

Read this file at the start of every iteration; update it at the end. Never mark an issue
completed until a GitHub run confirms it.

## Current state

| Field | Value |
|---|---|
| Iteration | 1 |
| Working branch | `ci/gh-actions-repair` |
| Base commit | `1b8471d` (pushed to `main`) |
| Latest commit on branch | pending |
| Open PR | none yet |
| Total unresolved issues | 5 (all fixes local-green; awaiting GitHub verification) |
| Last successful run IDs | CodeQL `44460076026`; notify `41392904735` |
| Last failed run IDs | see [[GITHUB_ACTIONS_AUDIT]] |

## Completed repairs (local verification only)

- F1 Worker CI TypeScript (typecheck clean, all worker tests pass).
- F2 Instrumented DEX class names (`assembleDebugAndroidTest` succeeds).
- F3 Android CI lint (`lintDebug` BUILD SUCCESSFUL, 0 errors).
- F4 Supabase SQL step conflict (YAML parses; GitHub run required).

## Remaining failures / work

- Push the branch, open a PR into `main`, and inspect every triggered run.
- Retrieve logs for any new failure, then iterate.

## External blockers

- `Android release` requires a `vX.Y.Z` tag plus signing secrets; not exercised by this repair.
- `Notify app users about release` only runs on a published release.
- No local PostgreSQL server or Docker; Supabase SQL verification must happen on GitHub.

## History

- Iteration 1 started: audited seven workflows and 100 runs; fixed F1/F2/F3/F4; all local
  checks green (unit tests 554/0, lint 0 errors, androidTest assembles, worker tests pass).
