# Release Notes

> Version history reconstructed from git. Companion: [[Feature Roadmap]] · [[Known Bugs]]

## Current state

| | |
|---|---|
| Version | **0.5.1** (`versionCode 3`) — `gradle/libs.versions.toml:2-3` |
| Latest tag | `V.0.5.1`, 2026-09-04 |
| Branch | `main` |
| Commit count | 299 |
| First commit | 2026-07-08 |
| Room schema | **33** (16 migrations) |
| Unit tests | 252 passing, 0 failures (recorded 2026-10-16 14:33) |
| ⚠️ Working tree | **modified, uncommitted** — `main` has uncommitted changes across the app module |

⚠️ **Tag naming is inconsistent**: `V0.5.0` (no dot) vs `V.0.5.1` (leading dot). Fix before
the next release so tooling that parses tags does not miss one.

## Tags

| Tag | Date | Commit message |
|---|---|---|
| `V.0.5.1` | 2026-09-04 | Fix `RefreshDataUseCase` injection arguments in `UseCaseModule` |
| `V0.5.0` | 2026-08-15 | Updated app version to 0.5.0 and made sidebar version display dynamic |

## Recent work — post-0.5.1, in `main`

The most recent commits are **audit response work**, not feature work:

| Commit | What it fixed |
|---|---|
| `8da1412` | RLS role carve-outs, silent sync data loss, backup safety |
| `787b8e6` | fail-open notifier secret, weak staff passwords, cross-account backup confirm, unbounded PBKDF2 iterations |
| `fac0fd9` | false empty state on Patient Details; hardened offline-first sync |
| `a5d393c` | missing Room migrations, `deleted_at` type, silent DB wipe, patient `REPLACE` clobber, sync conflict drop |
| `ed3299c` | capture batch quantity diff before the write in `updateBatch` |
| `916cc79` | validate the Next Vaccination section for new records |
| `a3f4eb3` | encode brand names in routes; complete the upcoming drill-down |
| `0018992` | enqueue real sync deletes so hard-deleted rows stop resurrecting |
| `37508b8` | fix `isoToLong` dropping time-of-day from nano-precision instants |
| `0ad3879` | index `patient_visits.dateGiven`; enable Room schema export |
| `cf5d535` | batch patient price lookup and vaccination item reads |

✅ This is a healthy pattern — the team is closing audit findings and writing regression
tests alongside them (`CloudRefreshTest`, `SyncOrderingTest`, `EditReconcilerTest`,
`MemoryCacheTest` and others all trace to this wave).

⚠️ **But the open bugs in [[Known Bugs]] were not part of that wave.** The highest-severity
items — the stock ledger lying about a moved dose, the retried-upsert stock corruption, and
the reminder-loop duplicate-visit path — remain unfixed.

## What shipped in the current version

Reconstructed from the feature directories and migrations dated after 2026-09-04:

**Features**
- Expenses with integer-paise amounts, category and payment-method validation
- Doctor weekly availability slots and date exceptions
- Personal vaccine reminders (advance-paid follow-ups)
- Borrow and return of vaccine batches
- Consultation and vaccination "today's patients" queue with slot filtering
- Milestone patients statistics
- Widget configuration activity
- Shared design system (`core/designsystem/`)

**Data layer**
- Room 17 → 33: 16 migrations, three of them destructive
- 25 entities, 21 DAOs
- SQLCipher encryption at rest
- No destructive-migration fallback (deliberate — see [[Architecture Decisions]])

**Backend**
- Roles moved from `profiles.role` to `app_metadata.role`
- Write rate limiting (per-row, per-minute)
- Negative-stock guard trigger
- RLS index and trigger hardening
- `manage-staff` edge function as the only writer of `profiles`

## Upgrade notes for the next release

Before shipping, these are the things that will bite:

1. **Tag naming** — normalise `V.0.5.x` (currently mixed).
2. **The working tree is dirty.** `main` carries uncommitted modifications across the app
   module. Commit or stash before tagging.
3. **`20261016` migration may not be applied everywhere.** 🔍 Verify against every
   environment — `Known Bugs` §1.6 and §2.1 both depend on it.
4. **`auto_expose_new_tables` is removed on 2026-10-30.** 🔍 `Known Bugs` §4.5 — if no
   migration issues a `GRANT`, every write fails. This has a hard deadline.
5. ~~**17 migrations are untracked in git.**~~ ✅ **Resolved** — `f8e5d8a` tracked all
   `.sql` files (22 as of `20261017_…`). A clean clone can replay every migration in this
   repository, but still
   **cannot** recreate the ~12 legacy base tables (no initial migration exists for them) —
   see `Known Bugs` §4.6 and [[Quality/Audit Reconciliation]].

## Related

- [[Known Bugs]] — what should be fixed before the next release
- [[Feature Roadmap]] — suggested order of work
- [[Architecture Decisions]] — why things are the way they are
- [[Database Schema]] — the migration timeline
