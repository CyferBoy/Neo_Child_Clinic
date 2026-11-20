# Agent Responsibilities & Boundaries

This document defines the roles, capabilities, and boundaries for all 12 specialized engineering agents in the Neo Child Clinic ecosystem.

---

## Agent Directory & Responsibilities

| Agent ID | Title | Core Mission | May Modify | Must Never Touch |
|---|---|---|---|---|
| **`project-manager`** | Orchestrator | Task decomposition, dependency sequencing, verification enforcement | Task records, summaries | Application source code directly |
| **`software-architect`** | Architect | Clean Architecture, MVVM boundaries, ADRs, module coupling | `docs/architecture/adr/**` | UI implementation details, quick hacks |
| **`android-developer`** | Android Dev | Kotlin 2.4, Compose M3, ViewModels, Room DAOs, Coroutines | `app/src/main/**`, UI components | Database migrations without DB review |
| **`backend-engineer`** | Backend Dev | Supabase, PostgreSQL triggers, Edge Functions, Cloudflare Worker | `supabase/**`, `cloudflare/**` | Relaxing RLS policies for convenience |
| **`database-engineer`** | DB Engineer | Room schema 36 (library 2.8.5) SQLite, migrations, SQLCipher, PostgreSQL schema | `data/local/**`, `schemas/**` | Released historical migration scripts |
| **`qa-engineer`** | QA Engineer | Unit tests, integration tests, offline-first verification | `app/src/test/**`, `androidTest/**`| Production source code |
| **`security-auditor`** | Security Auditor | RLS audit, Keystore encryption, secret scan, vulnerability report | Security reports, test scripts | Modifying live credentials / configs |
| **`documentation-engineer`** | Doc Engineer | Architecture documentation, Knowledge Base, validator | `docs/**` strictly | Application code, database schema |
| **`code-reviewer`** | Code Reviewer | Independent peer reviews, blast radius analysis, maintainability | Read-only | Entire workspace (Read-only) |
| **`performance-engineer`** | Perf Engineer | Startup time, memory footprint, Room query optimization | Profiling configs, targeted opts | Speculative micro-optimizations |
| **`release-engineer`** | Release Eng | Gradle build scripts, ProGuard/R8, CI/CD, APK packaging | `build.gradle.kts`, `.github/**` | Committing keystores or passwords |
| **`research-agent`** | Researcher | Official Android/Supabase docs, API compatibility | Read-only research briefs | Entire workspace (Read-only) |

---

## Invariant Delegation Rules
1. **The Orchestrator coordinates; it does not code.** The `project-manager` ensures work packages are clean and assigns them to the appropriate specialist.
2. **Specialists own their layers.** The `android-developer` never alters database migrations; the `documentation-engineer` never edits Kotlin code.
3. **Double Verification.** Any non-trivial implementation authored by `android-developer` or `backend-engineer` must be peer-reviewed by `code-reviewer` and verified by `qa-engineer`.
