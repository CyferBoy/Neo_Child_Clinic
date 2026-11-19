# GitHub Actions Verification

Historical failures remain in GitHub history. A repair is verified only by a **new** run
with conclusion `success`. Statuses below are updated at the end of every Ralph iteration.

## Baseline (pre-repair, commit `1b8471d` on `main`)

| Workflow | Run ID | Conclusion |
|---|---|---|
| Android CI | `44460076114` | failure — lint |
| Android instrumented tests | `44460075999` | failure — D8 |
| Worker CI | `44460076139` | failure — TypeScript |
| Supabase SQL | `44460076014` | failure — `is_active_staff()` |
| CodeQL Advanced | `44460076026` | success |

## Verification runs

| Workflow | Trigger | Run ID | Conclusion | Notes |
|---|---|---|---|---|
| Android CI | PR to `main` | `44470946697` | success | lint, docs and unit tests pass |
| Android instrumented tests | PR to `main` | `44470946645` | failure | D8 fixed; 6 real DAO test failures now fixed locally |
| Worker CI | PR to `main` | `44470946559` | success | TypeScript and worker tests pass |
| Supabase SQL | PR to `main` (touching `supabase/**`) | `44470946764` | failure | SELECT policy missing in disposable schema; fixed locally |
| CodeQL Advanced | PR to `main` | `44470946591` | success | no new alerts |

### Second pass (commit `575d729`)

| Workflow | Run ID | Conclusion | Notes |
|---|---|---|---|
| Worker CI | `44472253447` | success | |
| Supabase SQL | `44472253562` | success | harness SELECT policy fixed |
| Android CI | `44472253408` | success | docs validator advisory: `setup-python@v5` targets deprecated Node 20 |
| Android instrumented tests | `44472253549` | failure | 1/70: reminder fixture key collision, fixed in iteration 4 |
| CodeQL Advanced | `44472253366` | success | |

### Final pass (commit `73aed1f`) — all applicable workflows green

| Workflow | Run ID | Conclusion |
|---|---|---|
| Android CI | `44473522999` | success |
| Android instrumented tests | `44473523129` | success (70 tests, 0 failures) |
| Worker CI | `44473523064` | success |
| Supabase SQL | `44473522804` | success |
| CodeQL Advanced | `44473523308` | success |
| Code scanning AI findings (dynamic) | `44473534306` | success |

### Not verifiable without external events

| Workflow | Trigger | Conclusion | Notes |
|---|---|---|---|
| Android release | tag / manual dispatch | — | no release being cut; signing secrets required |
| Notify app users about release | release published | — | no release being published; last runs `41392904735` etc. succeeded |

## Local checks performed

| Check | Result |
|---|---|
| `npx tsc --noEmit` (worker) | clean |
| `npm test` (worker) | 34 passed, 0 failed |
| `./gradlew assembleDebugAndroidTest --offline --no-daemon` | BUILD SUCCESSFUL |
| PyYAML parse of all seven workflow files | OK |
| `./gradlew lintDebug --offline --no-daemon` | BUILD SUCCESSFUL, 0 lint errors |
| `./gradlew testDebugUnitTest --offline --no-daemon` | 554 passed, 0 failed |
| `./gradlew assembleDebugAndroidTest --offline --no-daemon` | BUILD SUCCESSFUL |
| `python3 scripts/validate_docs.py --strict` | 0 errors |
