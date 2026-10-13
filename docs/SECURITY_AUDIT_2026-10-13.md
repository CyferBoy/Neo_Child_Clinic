# 🔐 Z-Audit Security Report

**Target:** Neo Child Clinic / vaccine_manager_app (local repo)
**Date:** 2026-10-13
**Stack Detected:** Android (Kotlin 379 files, Compose, SQLCipher, supabase-kt) + Supabase (Postgres RLS, 2 Edge Functions) + Cloudflare Worker (R2 + D1) + GitHub Actions CI

---

## Executive Summary

Posture is good for a project of this size: server-side RLS baseline with a defense-in-depth "enable RLS on every table" loop, service-role credentials kept server-side, SQLCipher + Android Keystore for local data, AES-256-GCM backups with PBKDF2-SHA256 (210k), `allowBackup=false`, and ownership-checked backup APIs. **No critical finding.** The one real issue is a fail-open auth check in `notify-update`; everything else is hardening. The largest residual risk is deployment, not code: `SECURITY.md` itself notes the RLS migrations must be applied to the live project before any of these protections count.

---

## 🟠 High Findings

### H1: `notify-update` fails open if the secret env var is unset
- **Location:** `supabase/functions/notify-update/index.ts:17-24`
- **Issue:** `const secret = Deno.env.get('UPDATE_NOTIFIER_SECRET')` — if the secret is missing/renamed in the deployed function, `secret` is `undefined`, the comparison becomes `authHeader !== "Bearer undefined"`, and sending literally `Authorization: Bearer undefined` passes.
- **Impact:** Anyone on the internet could push notification content to every registered clinic device (and burn FCM quota). `manage-staff` fails closed by comparison (`getUser("")` errors), so this is an inconsistency, not a pattern.
- **Evidence:** workflow `.github/workflows/notify-update.yml:19` guards the *caller* side only; the server does not.
- **Remediation:**
  ```ts
  const secret = Deno.env.get("UPDATE_NOTIFIER_SECRET");
  if (!secret || !authHeader || authHeader !== `Bearer ${secret}`) return json401;
  ```

---

## 🟡 Medium Findings

### M1: `profiles_update_own` policy survives the security baseline, contradicting it
- **Location:** `supabase/migrations/20260816_security_hardening.sql:135` vs `20260929000000_security_baseline.sql:123`
- **Issue:** The baseline's comment says "No authenticated INSERT/UPDATE/DELETE policy is intentionally created for profiles", but it only drops two *select* policies. The 20260816 `profiles_update_own` policy is still there (permissive policies are additive).
- **Impact today:** bounded — the `protect_profile_privileged_fields` trigger blocks `id/role/is_active/is_deleted/deleted_by/email` for non-admins. A staff member can still self-update any *unguarded* column (e.g. `deleted_at`, and any future column someone forgets to add to the trigger allowlist). This is a policy/trigger allowlist-drift risk that becomes privilege escalation the day a privileged column is added without touching the trigger.
- **Remediation:** `drop policy if exists "profiles_update_own" on public.profiles;` in a follow-up migration, then let the app's profile-edit path use an explicit guarded update (or extend the trigger guard to "only whitelisted columns may change").

### M2: No server-side password strength rule in staff creation
- **Location:** `supabase/functions/manage-staff/index.ts:45-53`
- **Issue:** `CREATE` only checks presence of `password`. Strength depends entirely on GoTrue's project-level minimum (default 6 chars).
- **Impact:** an admin can provision staff (and doctors/admins) with a trivial password on a system holding patient records.
- **Remediation:** `if (password.length < 12) throw new Error("Password must be at least 12 characters")` before `createUser`.

### M3: Backup `confirm` can re-point another account's row if a `backup_id` is known
- **Location:** `cloudflare/backup-worker/src/index.ts:177-184` + `schema.sql:6`
- **Issue:** `backup_id` is the global `PRIMARY KEY`, and `ON CONFLICT(backup_id) DO UPDATE` does not check `account_id`. A caller who learns a victim's backup id (app-generated UUID) can overwrite `storage_path`/`size_bytes` for that row while `account_id` stays the victim's.
- **Impact:** limited — the attacker can't read the victim's object (GCM tag + ownership checks), and guessing a UUID is impractical; result would be the victim's metadata pointing at a dead object (backup effectively lost). DoS, not disclosure.
- **Remediation:** guard the upsert — read the row first, `if (row && row.account_id !== accountId) return 409;` (same helper already exists: `getOwnedBackup`).

### M4: PBKDF2 iteration count is read from the container with no bounds
- **Location:** `app/src/main/java/com/neochildclinic/data/backup/BackupCrypto.kt` (`decrypt` → `iterations = buffer.int`)
- **Issue:** A crafted `.nccb` file can declare e.g. `2_000_000_000` iterations; the app derives the key on the main restore path and appears hung (or thermally DoS'd) before GCM ever rejects the file. Tiny values are also accepted.
- **Impact:** client-side denial of service / UX freeze on restore; no data exposure (GCM still authenticates).
- **Remediation:** clamp before deriving: `require(iterations in 10_000..1_000_000) else throw Corrupted(...)` — legitimate files use 210_000, so nothing breaks.

### M5: Supabase access/refresh tokens stored in plaintext SharedPreferences
- **Location:** `app/src/main/java/com/neochildclinic/core/di/SupabaseModule.kt:48-50` (`autoSaveToStorage = true`, no custom `sessionStorage`)
- **Issue:** supabase-kt's default session storage is unencrypted SharedPreferences, while the *database* passphrase deliberately goes through EncryptedSharedPreferences/Keystore.
- **Impact:** on a rooted device or via a non-backup-blocked exfiltration path, long-lived refresh tokens are readable in the clear. `allowBackup=false` and RLS reduce the practical risk — this is consistency hardening.
- **Remediation:** supply a custom `SessionStorage` backed by the existing `EncryptedSharedPreferences` helper in `SecurityUtils`.

---

## 🟢 Low Findings / Recommendations

- **L1 — Raw internal errors returned to callers:** `manage-staff` returns `errorMessage(error)` with status 400 (DB/PostgREST messages, config errors); `notify-update` returns `error.message`. Map to generic messages server-side; keep detail in logs. Also return 401/403 rather than 400 for auth failures.
- **L2 — `Access-Control-Allow-Origin: *` on both Edge Functions:** acceptable (bearer-token auth, no cookies), but pin to the app's origins if any web client ever exists.
- **L3 — Worker JWT verify has no `iss` check** (`cloudflare/backup-worker/src/jwt.ts:168-176`): `aud` and `exp` are checked; add `iss === "<project-ref>"` (or drop the HS256 fallback once JWKS is confirmed deployed, so only asymmetric keys are accepted).
- **L4 — Update APK hash verified only "when a digest was available"** (`AppUpdateManager.kt:282-303`): unsigned-digest GitHub assets install without a hash check. Android's same-signer requirement still prevents a rogue APK, so LOW — make a missing digest a hard failure if you want belt-and-braces.
- **L5 — PII-adjacent log lines:** `ProfileRepositoryImpl.kt:42` logs an email; patient logs are IDs only (good). Drop the email from the log message.
- **L6 — Deep link `neochild://auth-callback` on an exported activity:** any app can fire a crafted intent into `handleDeeplinks`. Worst case is session-swap/login-CSRF style, not token theft. Verify the PKCE `state`/`code` binding if you ever see odd logins.
- **L7 — No `npm audit` coverage** for `cloudflare/backup-worker` (npm unavailable in this environment). Run it in CI.
- **L8 — Shared bearer secret on a public Edge Function** with no server-side rate limit: ensure `UPDATE_NOTIFIER_SECRET` is ≥128 bits of entropy.
- **INFO — Git history:** contains a Supabase **anon** (publishable) key — public by design, ship-in-APK class, no rotation needed — and an old `password = "password"` Compose placeholder. `.env.local`, `google-services.json`, keystores, and `*.heapsnapshot` are correctly untracked.

---

## ✅ What's Secure

- **RLS baseline + defense-in-depth loop** (`20260929000000_security_baseline.sql`, `20260825_clinic_rls_and_accountability.sql`): every public table gets RLS even if not explicitly opened; all business policies are `to authenticated` and gated on `is_active_staff()`.
- **Audit logs are append-only** from the client (no update/delete policy) with server-authoritative `created_by` stamping — client-supplied labels are overridden by `SECURITY DEFINER` triggers.
- **Authorization roles live in `app_metadata` only** (`20261001_app_metadata_roles.sql`); `user_metadata` (client-writable) can never authorize.
- **Database write rate limit** enforced by trigger server-side (600/mutating user/min), service-role bypass only — correctly recognizes that RLS ≠ rate limiting.
- **Service-role key never in the client** (`SUPABASE_SERVICE_ROLE_KEY` only in Deno env); Android ships only the publishable key, read from `.env.local` via `buildConfigField`.
- **Backup Worker:** JWT-verified (JWKS/ES256 preferred), ownership derived from stored `account_id` never from the URL, size ceilings (200 MB), no R2 access keys anywhere (native binding), retention can never delete the newest backup.
- **Local data:** SQLCipher with a Keystore-backed passphrase in EncryptedSharedPreferences; `allowBackup="false"`; release build minified + resource-shrunk.
- **Backups at rest:** AES-256-GCM, PBKDF2-HMAC-SHA256 210k, random salt/IV, container self-describes iterations, checksum verified before the live DB is touched; password never stored.
- **No secrets in docs, migrations, gradle files, tracked build outputs, or the repo index.**

---

## 📋 Prioritized Action Plan

1. **Today:** H1 fail-closed secret check in `notify-update` (3-line change), M2 password length in `manage-staff`.
2. **This week:** M3 ownership guard before the confirm upsert; M4 clamp PBKDF2 iterations; **run `supabase/verify_security.sql` against the live project and apply pending migrations** — the code-level RLS guarantees are theoretical until deployed.
3. **This month:** M1 drop the stale `profiles_update_own` policy (or tighten the trigger to a column allowlist); M5 encrypted session storage; L1/L5 log+error hygiene.
4. **Backlog:** L2/L3/L4/L7/L8.

---

## Appendix: Tools Used

- Z-Audit skill; static review of 379 Kotlin files, 17 SQL migrations, 2 Deno Edge Functions, 1 Cloudflare Worker, GitHub Actions workflows; git-history secret sweep; targeted greps for hardcoded credentials, SQL concatenation, exported components and PHI logging.
- **Not run:** live endpoint probing (no network egress from this environment), `npm audit` (npm not installed), `verify_security.sql` (needs your Supabase project).

---

## Fix Log — 2026-10-13

| Finding | Fix | Verified |
|---|---|---|
| H1 fail-open notifier secret | `notify-update/index.ts` now rejects every caller when `UPDATE_NOTIFIER_SECRET` is unset | code review |
| M2 weak staff passwords | `manage-staff/index.ts` enforces a 12-character minimum before `createUser` | code review |
| M3 confirm upsert cross-account | `backup-worker/src/index.ts` returns 404 before the upsert when `backup_id` belongs to another account; new spec case in `test/backup-worker.spec.ts` | CI only (no node in this sandbox) |
| M4 unbounded PBKDF2 iterations | `BackupCrypto.decrypt` rejects containers outside 10k–1M iterations; new test in `BackupCryptoTest.kt` | `./gradlew :app:testDebugUnitTest --tests "*BackupCryptoTest*"` ✅ |
| M1 stale `profiles` policy | **Not dropped** — the app's own `SyncUploader` pushes profile updates through that policy, so dropping it would break profile edits in the field. Instead: new migration `20261013_profiles_privileged_fields_guard.sql` closes the real gap (`deleted_at` was unguarded) and documents that the trigger, not the policy, is the allowlist to extend | code review |

Still open (not fixed here): M5 encrypted session storage, L1–L8 hardening items, and **deploying the migrations + running `verify_security.sql` against the live project**.
