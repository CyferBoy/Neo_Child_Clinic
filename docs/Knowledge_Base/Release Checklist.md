# Release Checklist

> The gate before shipping. Every section is executable — no "consider whether".
> Companion: [[Release Notes]] · [[Documentation Maintenance]] · [[Quality/Bug Register]]

## How to use this

1. Work top to bottom. Sections 1-4 are automated; paste the output into the release notes.
2. Section 5 cannot be automated — a human runs it against a live database.
3. Section 6 is documentation; section 7 is the go/no-go.

---

## 1. Build

```bash
./gradlew clean
./gradlew assembleDebug --no-daemon
./gradlew assembleRelease --no-daemon     # if shipping
./gradlew lintDebug --no-daemon
```

- [ ] Debug APK assembles
- [ ] Release APK assembles **and is signed**
- [ ] `lintDebug` produces no new **Error**-severity issues
- [ ] `versionCode` incremented (`gradle/libs.versions.toml`)
- [ ] `versionName` set

⚠️ CI writes a temporary `app/google-services.json`. Locally, confirm yours is the real one
and is **not** staged: `git status --porcelain | grep google-services` must be empty.

## 2. Tests

```bash
./gradlew testDebugUnitTest --no-daemon
```

- [ ] Unit tests green — record the count and the commit
- [ ] **No new test was deleted or `@Ignore`d** — check `git diff HEAD~1 -- '*/test/*'`
- [ ] Every bug closed in this release has its regression test (see
      [[Documentation Maintenance]] § Verification evidence format)

```bash
./gradlew connectedDebugAndroidTest --no-daemon   # needs a device
```

- [ ] Instrumented tests run on a real device/emulator
- [ ] Room migrations 17→33 apply cleanly on an existing install
- [ ] `MigrationTestHelper` ran against a real exported schema

⚠️ **All 8 instrumented test classes (56 `@Test` methods) are currently in `androidTest` and
do NOT run in `./gradlew test`.** A green `test` task says nothing about DAO SQL. See [[Testing]].

## 3. Database migration compatibility

### Room (local)

- [ ] `DB_VERSION` incremented and `const val DB_VERSION` updated to match
      (`AppDatabase.kt:42` and `:79` — they are kept in sync by hand)
- [ ] `MIGRATIONS` array extended, contiguous, no duplicate start versions
- [ ] New migration is **non-destructive**, or its destructiveness is documented in the comment
- [ ] `AppDatabaseMigrationTest` asserts the new contiguity
- [ ] **Still no `fallbackToDestructiveMigration`** — verify it was not added
- [ ] Schema exported: `app/schemas/…/<version>.json` committed

⚠️ `migration31_32` is the precedent for a destructive migration: it hard-deletes rows
before dropping columns. Anything similar needs a release note, not just a code comment.

### Supabase (server)

- [ ] New migration added to `supabase/migrations/` with a **lexically sortable** timestamp
- [ ] ⚠️ Timestamp prefix sorts correctly — `20261013120000` sorts **after** `20261013_profiles`
      because `_` > digits. Rename to `20261013000000_profiles…` style
- [ ] Migration wrapped in `begin; … commit;` — ⚠️ 7 of 23 are currently bare DDL
- [ ] Migration is **idempotent** (`if exists`, `if not exists`, `to_regclass` guards)
- [ ] Any `SECURITY DEFINER` function sets `search_path`
- [ ] Existing triggers dropped before a replacement is installed — ⚠️ see [[Quality/Bug Register#NCC-003 — Legacy stock trigger never dropped; possible double-application|NCC-003]]
- [ ] Migration applied to **every** environment, not just one

```bash
git ls-files supabase/migrations/ | wc -l     # must equal the file count on disk
```

## 4. Documentation

```bash
python3 scripts/validate_docs.py --strict
```

- [ ] 0 errors, 0 warnings
- [ ] Notes affected by this release updated (see the table in
      [[Documentation Maintenance]])
- [ ] Bug register rows moved to **Fixed** **with quoted verification evidence**
- [ ] [[Architecture Decisions]] updated if a decision changed
- [ ] [[Source File Index]] counts regenerated if files were added or removed
- [ ] No secrets, tokens or patient data anywhere in the vault
- [ ] Mermaid diagrams render in Obsidian — ⚠️ the validator is heuristic, not a parser

## 5. Security checks (live database)

⚠️ **This section cannot be automated from the repository.** Run each query against the
hosted project. Results belong in the release notes.

### Grants — hard deadline

```sql
select table_name,
       has_table_privilege('authenticated', format('public.%I', table_name), 'select') as sel,
       has_table_privilege('authenticated', format('public.%I', table_name), 'insert') as ins,
       has_table_privilege('authenticated', format('public.%I', table_name), 'update') as upd
from information_schema.tables
where table_schema='public' order by table_name;
```

- [ ] **No table the app writes to has all three `false`** — ⚠️ [[Quality/Security Concerns#NSC-005 — No table-level `GRANT`; `auto_expose_new_tables` removed 2026-10-30|NSC-005]].
      `auto_expose_new_tables` is removed **2026-10-30**; after that, missing GRANTs break
      every write.

### Policies

```sql
select policyname, tablename, cmd, qual, with_check from pg_policies
where schemaname='public' and tablename in ('profiles','audit_logs')
order by tablename, policyname;
```

- [ ] No `WITH CHECK (true)` on `audit_logs` — ⚠️ [[Quality/Security Concerns#NSC-002 — Legacy `get_my_role()` policies may defeat the role migration|NSC-002]]
- [ ] No `FOR ALL` on `profiles` gated on `get_my_role()` — the same NSC-002
- [ ] `profiles` SELECT is not readable by a non-admin — ⚠️ [[Quality/Security Concerns#NSC-001 — `profiles` is readable by every active staff member|NSC-001]]

```sql
select proname, prosecdef, proconfig from pg_proc
where pronamespace='public'::regnamespace and prosecdef;
```

- [ ] Every `SECURITY DEFINER` function has a non-empty `proconfig` (`search_path` set) —
      ⚠️ [[Quality/Security Concerns#NSC-013 — `get_my_role()` is `SECURITY DEFINER` with no `search_path`|NSC-013]]

### Triggers

```sql
select tgname, tgenabled from pg_trigger
where tgrelid='public.inventory_transactions'::regclass and not tgisinternal;
```

- [ ] **Exactly one** stock trigger — ⚠️ [[Quality/Bug Register#NCC-003 — Legacy stock trigger never dropped; possible double-application|NCC-003]]; two means every movement is
      applied twice
- [ ] Stock trigger's `UPDATE` branch uses `- OLD.quantity + NEW.quantity` —
      ⚠️ [[Quality/Bug Register#NCC-002 — Postgres stock trigger corrupts stock on any retried upsert|NCC-002]]

```sql
select relname, relrowsecurity, relforcerowsecurity
from pg_class where relname='todo_notification_events';
```

- [ ] RLS enabled, `relforcerowsecurity` handled per [[Quality/Security Concerns#NSC-003 — `FORCE ROW LEVEL SECURITY` breaks its own writer|NSC-003]]

### Rate limits

```sql
select count(*) from public.api_write_rate_limits;
```

- [ ] Row count is bounded, not growing one row per user per minute forever — ⚠️
      [[Quality/Security Concerns#NSC-012 — Write rate limiter counts rows, not requests|NSC-012]]

### Column types the app writes strings into

```sql
select column_name, data_type from information_schema.columns
where table_name='vaccine_batches' and column_name='expiry_date';
```

- [ ] `expiry_date` is `text`, not `date` — ⚠️ [[Quality/Bug Register#NCC-026 — reclassified, not fixed|NCC-026]]. The app stores the
      date picker's display format (`d MMM yyyy`, e.g. `5 Jan 2026`) and uploads it as-is,
      with no ISO conversion. `vaccine_batches` has no DDL in the migrations, so this cannot
      be settled from the repository. A `date` here means every batch insert is rejected.

## 6. Data safety

- [ ] No migration drops or rewrites clinical or financial data without a release note
- [ ] No patient data, credentials or tokens in the vault, in code comments, or in commit
      messages
- [ ] `.env.local` still untracked: `git ls-files | grep -iE "env|secret|key"` is empty
- [ ] Backup restore rehearsed on a device with real-shaped data — ⚠️ `BackupRestorer` has
      **zero tests** (318 lines, the most destructive path in the app)
- [ ] Sign-out with unsynced work still blocks and prompts

## 7. Go / no-go

- [ ] Sections 1-4 green with output pasted into [[Release Notes]]
- [ ] Section 5 run and its results recorded
- [ ] No open **S1** bug introduced by this release — ⚠️ see
      [[Quality/Bug Register]] for the current list
- [ ] `Known Bugs.md` severity ordering still accurate
- [ ] [[Feature Roadmap]] updated to reflect what actually shipped
- [ ] Git tag matches the `versionName`, and the naming is consistent —
      ⚠️ currently mixed: `V0.5.0` vs `V.0.5.1`

### Reverting

- [ ] The previous tag is known and the APK is retained
- [ ] ⚠️ **A schema migration is not reverted by redeploying the app.** Record the reverse
      migration before applying a destructive one
- [ ] Rollback tested for the sign-out path, which erases local data

## Related

- [[Release Notes]] — the history this checklist feeds
- [[Quality/Bug Register]] — what must not be open at go/no-go
- [[Documentation Maintenance]] — the documentation sections above
- [[Testing]] — why section 2 has two parts
