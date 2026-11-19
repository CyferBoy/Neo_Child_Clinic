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
| Android CI | PR to `main` | pending | — | needs lint repair first |
| Android instrumented tests | PR to `main` | pending | — | D8 repaired locally |
| Worker CI | PR to `main` | pending | — | TypeScript repaired locally |
| Supabase SQL | PR to `main` (touching `supabase/**`) | pending | — | step reordered locally |
| CodeQL Advanced | PR to `main` | pending | — | regression check after code changes |
| Android release | tag / manual dispatch | not applicable | — | no release being cut; signing secrets required |
| Notify app users about release | release published | not applicable | — | no release being published; last runs `41392904735` etc. succeeded |

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
