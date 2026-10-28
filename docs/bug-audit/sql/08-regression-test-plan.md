# 08 — Regression Test Plan

**Goal:** one small, runnable check per material finding — no framework, no fixtures, same style as the existing SQL tests.
**Current state:** three SQL scripts; `.github/workflows/supabase-sql.yml` runs the stock and retention scripts, and now also the receipt and parent-delete checks. `rls_role_test.sql` still has no assertions.
**Status (2026-10-26):** the minimal first batch in §3 landed (stock moved-purchase case, CI replay steps, timestamp unit test, batch payload test). Open items remain listed in `14-fix-log.md §3`.

---

## 1. What exists today

| File | Assertions | Coverage | Gaps |
|---|---|---|---|
| `supabase/tests/stock_trigger_arithmetic.sql` | 12 `raise exception` checks | insert, retry, edit, move (negative row only), delete, negative guard, positive edit/delete, single-trigger count | **no** move of a positive row off an under-stocked batch (`SQL-006`); **no** delete of a positive row below zero (`SQL-007`); no concurrent sessions; no missing-batch case |
| `supabase/tests/inventory_reconciliation_test.sql` | 4 checks | view `security_invoker`, drift, opening balance, empty batch | inserts batches/transactions without `vaccine_id`; fails if the live column is `NOT NULL` (`SQL-034`); no RLS test on the view |
| `supabase/tests/rls_role_test.sql` | **none** | manual JWT simulation with placeholders | every expected result is a comment; no `DO` block; no deactivated-doctor slot test (`SQL-013`); no audit INSERT/UPDATE test (`SQL-019`); block 7 tests `is_admin()`, not the slot policy |

`supabase/verify_security.sql` is a read-only inspection script, not a test (`SQL-037`).

---

## 2. Test layers

### Layer A — Static checks (seconds, no database)

| Check | Catches | Implementation |
|---|---|---|
| every migration starts with `begin;` | `SQL-004` | shell/grep in CI |
| every migration ends with `commit;` | `SQL-004` | same |
| no `DROP FUNCTION … CASCADE` without an explicit policy list | `SQL-016` | grep + review rule |
| migration filenames use 14-digit timestamps | ordering hygiene | regex |
| no `create policy` on `audit_logs` for UPDATE/DELETE | `SQL-019` | grep |
| every synced entity’s table exists in the catalogue | `SQL-001`, `SQL-002` | parse CSV + entity files |

These are cheap and catch the classes that caused the largest gaps. They do not replace a database test.

### Layer B — Schema replay (empty Postgres, ~1 minute)

```bash
# CI service container: postgres:16
export DATABASE_URL=postgresql://postgres:postgres@localhost:5432/postgres
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f supabase/migrations/00000000000000_baseline_schema.sql
for f in supabase/migrations/2*.sql; do
  psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f "$f" || exit 1
done
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f supabase/verify_security.sql
```

This is the check that would have caught `SQL-001`, `SQL-003`, and `SQL-036`. It cannot pass until the baseline schema exists.

Post-replay assertions (as a `DO` block or `psql -tAc` comparisons):

- 24 business tables exist;
- every table has `relrowsecurity = true`;
- `audit_logs` has exactly SELECT + INSERT policies;
- `inventory_transactions` has exactly one trigger calling `update_batch_stock`;
- the three conditional unique indexes exist;
- `receipt_sequences` and `api_write_rate_limits` exist and are revoked from `authenticated`.

### Layer C — Function/trigger tests (disposable database, one transaction, rollback)

Extend `stock_trigger_arithmetic.sql` with the two missing cases:

```sql
-- SQL-006: move a positive purchase off a batch that no longer holds it.
-- A = 3 (opening), purchase +10 on A, move the row to B.
-- Expected: A = 3 and B = 50, or a typed check_violation. Never A = -7.
insert into public.vaccine_batches(id, remaining_quantity) values (a, 3), (b, 50);
insert into public.inventory_transactions(id, batch_id, quantity, transaction_type)
  values (purchase3, a, 10, 'PURCHASE');
update public.inventory_transactions set batch_id = b where id = purchase3;
select remaining_quantity into q from public.vaccine_batches where id = a;
if q <> 3 then raise exception 'moved purchase: expected A=3, got %', q; end if;
```

```sql
-- SQL-007: delete a positive row after the stock was consumed.
-- A = 3, purchase +10, then delete the purchase.
-- Expected: either A = -7 (audit-first) or a documented typed rejection.
```

Receipt test (`SQL-018`):

```sql
insert into public.patient_visits(id, date_given, receipt_number)
  values (gen_random_uuid(), '1 May 2026', 'FORGED');
-- assert the stored value matches ^NEO-[0-9]{4}/[0-9]{2}-[0-9]{6}$ and equals the next sequence value
```

Audit test (`SQL-019`):

```sql
insert into public.audit_logs(timestamp, "user", module, entity_type, entity_id, action)
  values ('2000-01-01 00:00:00+00', 'forged', 'X', 'X', 'X', 'X');
-- assert timestamp is now() (or the insert is rejected) and "user" is the caller label
```

Prune test (`SQL-020`):

```sql
insert into public.audit_logs(timestamp, "user", module, entity_type, entity_id, action)
  values ('2025-13-45 99:99:99', 'x', 'x', 'x', 'x', 'x');
select public.prune_audit_logs();   -- must not raise
```

### Layer D — RLS matrix (disposable database, JWT claims)

Replace `rls_role_test.sql` with a script that **fails** rather than printing expectations. Use `DO` blocks with `set local role authenticated` and `set local request.jwt.claims`, and `begin … exception when insufficient_privilege then …` to assert denials.

Matrix (role × table × operation), at minimum:

| Actor | Table | Op | Expected |
|---|---|---|---|
| active admin | `doctor_weekly_slots` | INSERT any doctor | success |
| active doctor | own slot | INSERT | success |
| active doctor | another doctor’s slot | INSERT | `42501` |
| **inactive doctor** | own slot | INSERT | `42501` (`SQL-013`) |
| **inactive admin** | any slot | INSERT | `42501` (`SQL-013`) |
| active nurse | `expenses` | UPDATE/DELETE | `42501` (`SQL-014`) |
| active staff | `audit_logs` | UPDATE/DELETE | `42501` (`SQL-019`) |
| active staff | `audit_logs` | INSERT | success |
| anon | any business table | SELECT | 0 rows / `42501` |
| active staff | `patient-docs` object for another patient | SELECT | **currently succeeds** — assert the intended policy (`SQL-015`) |
| service role | `profiles` | UPDATE role | success |

`rls_role_test.sql` cannot be fixed by adding assertions alone: it needs real UUIDs. Generate them inside the script (insert test profiles with `gen_random_uuid()` and matching `auth.users` rows is not possible without Supabase auth). Run it on a **staging clone** with seeded users; parameterise via `psql -v admin_uuid=…`.

### Layer E — Concurrency (two sessions, disposable database)

| Test | Finding |
|---|---|
| Session 1 moves a `+10` row off batch A; session 2 deducts `-2` from A | `SQL-006` |
| Session 1 and 2 both move rows off the same A | lost update (`SQL-006`) |
| Session 1 inserts a purchase; session 2 deletes the batch | `SQL-012` |
| 600+ writes in one minute from one user | `SQL-009` (rate-limit partial group) |

Use `psql` in two terminals with `BEGIN;` on both, or `pgbench` for the rate-limit test. No framework needed.

### Layer F — Android unit tests (JVM, no device)

| Test | Finding |
|---|---|
| `isoToLong("2026-08-22 03:48:08.105121+00")` equals `Instant.parse("2026-08-22T03:48:08.105121Z")` | `SQL-030` |
| `uploadBatch` JSON for CREATE has no `remaining_quantity` key (or an insert-only call) | `SQL-008` |
| `deletePatient` reminder queue id equals `serverId` when non-null | `SQL-024` |
| `resolveTransactionDate(null)` is non-null or throws before enqueue | `SQL-027` |
| `refreshTransactions` heal does not overwrite a pending id | `SQL-026` |
| `deleteVaccination` enqueues a ledger UPDATE with `visitId = null` | `SQL-023` |

These are plain JUnit tests against the mapper/uploader functions; no Android runtime is required for the JSON and date cases. The repository tests need a Room in-memory database (already used elsewhere in the project if `androidTest` exists; otherwise extract the pure functions).

### Layer G — Sync integration (staging or a PostgREST mock)

| Scenario | Finding |
|---|---|
| Create batch + purchase, drop the response after commit, retry | `SQL-008` |
| Three-item group, fail item 2, retry | `SQL-009` |
| Delete patient with visit, ledger, audit, todos | `SQL-021`–`SQL-025` |
| Delete visit with a deduction | `SQL-023` |
| Old-client soft-delete row, new-client pull | `SQL-028` |
| Two devices edit the same row within 5 hours | `SQL-030` |

A mock is enough for the ordering assertions; a staging clone is required for the FK/trigger assertions.

---

## 3. Minimal first batch (what to land before any fix)

Ponytail order — smallest checks that fail for the biggest bugs:

1. **Extend `stock_trigger_arithmetic.sql`** with the moved-purchase and delete-positive cases (`SQL-006`, `SQL-007`). One file, two blocks.
2. **Add a migration replay CI job** (`SQL-001`, `SQL-036`). It will fail until the baseline schema exists; that is the point.
3. **Add a failing RLS script** for the inactive-doctor slot write (`SQL-013`). One `DO` block on staging.
4. **Add the timestamp unit test** (`SQL-030`) — a table of six strings.
5. **Add the `uploadBatch` payload test** (`SQL-008`) — assert the CREATE payload does not contain `remaining_quantity`.

Everything else can follow the fixes. Do not build a test framework.

---

## 4. Finding → test map

| Finding | Layer | Test |
|---|---|---|
| SQL-001 | B | replay on empty DB |
| SQL-002 | A/B | entity↔catalogue diff |
| SQL-003 | B | assert three unique indexes |
| SQL-004 | A | `begin`/`commit` lint |
| SQL-005 | B | `information_schema.columns` type assertions |
| SQL-006 | C/E | moved-purchase test; two-session move |
| SQL-007 | C | delete-positive test |
| SQL-008 | F/G | payload test; lost-response retry |
| SQL-009 | G | fail-middle-of-group retry |
| SQL-010 | F | create→edit→retry |
| SQL-011 | G | ledger on A, delete batch on B |
| SQL-012 | C | delete batch with ledger |
| SQL-013 | D | inactive doctor slot insert |
| SQL-014 | D | nurse delete patient/finance |
| SQL-015 | D | cross-patient storage read |
| SQL-016 | B/D | policy snapshot |
| SQL-017 | D | self-update every `profiles` column |
| SQL-018 | C | forged receipt number |
| SQL-019 | C/D | forged timestamp/user; audit UPDATE/DELETE denied |
| SQL-020 | C | malformed timestamp prune |
| SQL-021 | G | patient delete with history |
| SQL-022 | G | todos after patient delete |
| SQL-023 | G | visit delete with deduction |
| SQL-024 | F | reminder `serverId` |
| SQL-025 | G | consultation visit delete |
| SQL-026 | F | pending heal / timezone |
| SQL-027 | F | null transaction date |
| SQL-028 | G | old-client soft delete |
| SQL-029 | C | text timestamp ordering |
| SQL-030 | F | parse table |
| SQL-031 | B | `EXPLAIN` snapshot / missing indexes |
| SQL-032 | C | cleanup old rate-limit rows |
| SQL-033 | C | the two missing stock cases (same as 006/007) |
| SQL-034 | C | view test on live nullability |
| SQL-035 | D | replace placeholder RLS script |
| SQL-036 | B | replay + drift diff |
| SQL-037 | A | run `verify_security.sql` in CI |

---

## 5. Environment

This audit environment has **no** `psql`, `pg_ctl`, `initdb`, Docker, or Supabase CLI. Nothing in this plan was executed. To run it, use either:

- a disposable Supabase project (recommended: it provides `auth`, `storage`, `pg_cron`, and `pg_net`), or
- a local `postgres:16` container plus stubs for `auth.uid()`, `auth.jwt()`, `storage.buckets`, `storage.objects`, and `net.http_post`.

The second option needs a small `test_shim.sql` (not written here) that creates those schemas/functions before the migrations. The first option tests the real thing and is the only way to validate RLS and storage policies.

**Safety:** every SQL test runs in `begin … rollback`; never point them at production during clinic hours. The stock test’s header already says this (`stock_trigger_arithmetic.sql:7-8`).
