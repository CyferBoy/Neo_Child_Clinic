# Known Constraints & Guardrails

**Project**: Neo Child Clinic  
**Scope**: Technical, Architectural, Security, and Cross-Environment Guardrails  

---

## 1. Cross-Environment Constraints (Desktop vs. Android Termux)

### 1.1 Shared Android Storage (`/storage/emulated/0`)
- **No POSIX Permissions**: The FUSE/SDCardFS emulation layer on `/storage/emulated/0` does not store Unix file permission bits (`chmod +x` is silently ignored).
  - *Guardrail*: Always invoke scripts explicitly via their interpreter (e.g., `bash ./gradlew ...` or `python3 scripts/...`). Never expect `./script.sh` to execute directly.
- **Symlink Limitations**: Shared storage cannot reliably create POSIX symlinks.
  - *Guardrail*: Use copy operations or direct relative paths rather than symlinks.
- **Git Dubious Ownership**: The directory owner is Android `media_rw` (UID 1023 / system), differing from the Termux user (`u0_a362`).
  - *Guardrail*: Maintain `git config --global --add safe.directory /storage/emulated/0/Neo-Child-Clinic` and `git config core.filemode false`.

### 1.2 Android 16 Mobile Constraints
- **Phantom Process Killer**: Android kills apps running more than 32 child processes or using excessive CPU in the background.
  - *Guardrail*: Keep Termux active in foreground or run `termux-wake-lock` during orchestration workflows.
- **No In-Termux Compilation**: Running `./gradlew assembleDebug` or instrumented tests in basic Termux is infeasible due to lack of the Android SDK build-tools, NDK, and memory limits.
  - *Guardrail*: Delegate build assembly and unit testing to Linux Mint Desktop or GitHub Actions CI.

---

## 2. Application Architecture Constraints

### 2.1 Clean Architecture Strictness
- **Zero DAO Leakage**: Presentation ViewModels must NEVER directly inject Room DAOs or Supabase clients. ViewModels communicate strictly via Domain Repositories.
- **Pure Domain**: Domain models under `domain/model/` must contain zero Android dependencies (`import android.*` is strictly forbidden).

### 2.2 Database & SQLCipher Constraints
- **Database Versioning**: The Room database is at version **34**. Any entity alteration requires an increment to `version = 35`, export of `35.json` schema, and an explicit `Migration(34, 35)` registered in `AppDatabase.kt`.
- **SQLCipher Key Management**: Database encryption relies on Android Keystore. The database passkey is never stored in plaintext or logged.
- **Atomic Transactions**: All mutations affecting multiple tables must execute within `TransactionRunner.runInTransaction { ... }`.

### 2.3 Supabase & Security Constraints
- **Row-Level Security (RLS)**: Client operations run under authenticated staff JWTs. RLS policies enforce clinic tenancy and active staff verification (`active_staff` claim).
- **Zero Production Secrets**: API keys, signing keystores (`*.jks`), service-role keys, and credentials must never be committed to Git. `.env.local` is strictly in `.gitignore`.

---

## 3. Antigravity Agent & Rules Constraints

### 3.1 Context & Token Budgets
- **Per-Rule Size Limit**: 24 KB (24,000 bytes) max per rule file.
- **Aggregate Rules Budget**: 20,000 tokens for all `always_on` rules. Over-budget rules are demoted from full inline text to file path pointers.
- *Guardrail*: Keep root `AGENTS.md` concise; put detailed guidelines in `.agents/rules/` with specific file `glob` triggers.

### 3.2 File Ownership & Conflict Prevention
- Two agents must NEVER modify the same file concurrently. The `project-manager` orchestrator enforces single-agent ownership before dispatching tasks.
