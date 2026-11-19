# GitHub Actions Audit

Scope: all seven workflow files in `.github/workflows/`, every accessible workflow run
(`gh run list --limit 100`), and the failed-step logs of every actionable failure.
Baseline commit: `1b8471d` on `main`. Historical failures stay in GitHub history; repairs
are verified with new runs (see [[GITHUB_ACTIONS_VERIFICATION]]).

## Workflow inventory

| Workflow | Triggers | Permissions | Recent result (100 runs) |
|---|---|---|---|
| Android CI | push/PR to `main` | `contents: read` | 21 failure, 0 success — lint |
| Android instrumented tests | push/PR to `main` | `contents: read` | 19 failure, 2 cancelled, 0 success |
| CodeQL Advanced | push/PR to `main`, weekly cron | `security-events: write`, `packages: read`, `actions: read`, `contents: read` | 24 success, 0 failure |
| Worker CI | push/PR to `main` | `contents: read` | 6 failure, 16 success |
| Supabase SQL | push/PR touching `supabase/**`, `workflow_dispatch` | `contents: read` | 1 failure, 7 success |
| Android release | `v*` tag or manual dispatch with tag | `contents: read` | no recent runs; needs release signing secrets |
| Notify app users about release | `release: published` | `{}` | 4 success, 0 failure |

Cancelled instrumented runs are the workflow's own `cancel-in-progress` concurrency policy,
not failures. `Android release` and `Notify app users about release` have no run on the
current commit; they can only be exercised by a real release/tag, so they are tracked as
external blockers rather than verified here.

## Actionable failures investigated

| ID | Workflow | Run ID | Root cause | Status |
|---|---|---|---|---|
| F1 | Worker CI | `44460076139` | `test/jwt.spec.ts` typed the result of `crypto.subtle.generateKey()` as `CryptoKey \| CryptoKeyPair`, so `pair.publicKey` and `CryptoKeyPair` arguments failed `tsc --noEmit` | repaired, verified green |
| F1b | Worker CI | `43812837104` | Older, different failure: `src/index.ts` read `VerifiedUser.app_metadata` that the type did not declare. Already fixed before the baseline commit; re-check on new run | historical, fixed upstream |
| F2 | Android instrumented tests | `44460075999` | D8 rejected DEX class names generated from backtick Kotlin test names containing spaces (`BorrowDaoCountTest$empty table counts zero$1`) | repaired, verified green |
| F2b | Android instrumented tests | `44027097646` | Older failure at `:app:compileDebugAndroidTestKotlin`; current tree compiles and assembles androidTest locally | historical, fixed upstream |
| F3 | Android CI | `44460076114`, `44027097683`, `43779964630` | `lintDebug` fails on non-observable `Locale.getDefault()` reads inside composables (5 errors at baseline, 6 earlier). Long-standing blocker | repaired, verified green |
| F4 | Supabase SQL | `44460076014` | The child-refs step applied `20261118000000_access_hardening.sql` and its self-contained test to a minimal schema that lacks `is_active_staff()`/`auth.jwt()`; the test also drops and recreates the child-refs tables, invalidating the assertions that follow it | repaired, verified green |
| F5 | Android CI | `44027097683` | Gradle cache reservation warning (`ReserveCacheError`) appeared alongside the real lint failure; harmless, disappears when lint passes | no action needed |

## Non-findings

- CodeQL and the notify workflow have only successful runs; nothing to repair.
- The `Reject Kotlin assert()` guard in the instrumented workflow is a quality check, not a failure.
- No workflow uses `continue-on-error`; none of the repairs disable tests or quality checks.

## Repair log and state

See [[GITHUB_ACTIONS_REPAIR_LOG]], [[GITHUB_ACTIONS_VERIFICATION]] and
[[RALPH_GITHUB_REPAIR_STATE]].
