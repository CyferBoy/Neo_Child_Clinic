# GitHub Actions Repair Log

One logically related fix per iteration. Nothing is marked completed until its local check
and a GitHub run confirm it; run IDs go in [[GITHUB_ACTIONS_VERIFICATION]].

## Iteration 1 — build/compile failures (Worker + instrumented) and SQL step conflict

Branch: `ci/gh-actions-repair` (no direct push to protected `main`).

### F1 — Worker CI TypeScript

`cloudflare/backup-worker/test/jwt.spec.ts`: asserted the key-generation result to
`CryptoKeyPair`, which is the overload's actual return type when the algorithm literal is
 recognised. No test weakened; the JWKS/kid tests are unchanged.

Local validation: `npx tsc --noEmit` clean; `npm test` all worker test files passing.

### F2 — Instrumented DEX name

`app/src/androidTest/java/com/neochildclinic/data/local/dao/BorrowDaoCountTest.kt`: renamed
two backtick test names containing spaces to JVM-safe identifiers. Assertions unchanged.

Local validation: `./gradlew assembleDebugAndroidTest --offline --no-daemon` — BUILD
SUCCESSFUL (D8 no longer rejects generated class names).

### F4 — Supabase SQL step ordering

`.github/workflows/supabase-sql.yml`: the child-refs test step no longer applies
`20261118000000_access_hardening.sql` or runs its test script. Those belong to a separate
final step: `supabase/tests/access_hardening_test.sql` is self-contained and applies the
migration itself with a relative include. This removes the missing `is_active_staff()`
failure and stops the access test from dropping the tables the child-refs assertions need.
The test harness also attaches `neo_stamp_accountability()` to `vaccine_batches`, which the
real schema does in an earlier migration and its deleted_by assertion requires.

Local validation: all workflow YAML parsed with PyYAML; no local PostgreSQL server is
available, so the SQL conclusion comes from the GitHub run.

### F3 — Android CI lint

Four composables read `Locale.getDefault()`; replaced with the lint-recommended observable
`LocalLocale.current.platformLocale` (`FinanceTab`, `FinanceTable`, `StatisticsSummaryCard`).
The loading skeleton used `BoxWithConstraints` without reading the scope; now a plain `Box`
(`DashboardScreen`), while the real responsive usage remains `BoxWithConstraints`.

Local validation: full unit suite and `lintDebug` in progress.

### Pending in this iteration
