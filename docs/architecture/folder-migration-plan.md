# Neo Child Clinic — Folder Migration Plan

## 1. Migration Strategy & Guidelines

This migration plan defines the structural reorganization of the Neo Child Clinic repository.
Key principles:
1. **Zero Runtime Disruption**: All Gradle builds, unit tests (344 tests), Room schemas (v33), and AndroidManifest declarations must remain fully working and intact.
2. **Standard CLI & Workflow Compliance**: `supabase/` and `cloudflare/backup-worker/` locations remain intact at root to preserve Supabase CLI, Cloudflare Wrangler, and GitHub Actions workflows (`.github/workflows/`).
3. **Knowledge Base Integrity**: `docs/Knowledge_Base/` remains untouched in place to maintain full compatibility with Obsidian, `scripts/validate_docs.py`, and 1,064 citations.
4. **Backward Compatibility**: Migrated root documentation files receive forwarding stubs to ensure any legacy links resolve seamlessly.

---

## 2. File-to-Destination Migration Mapping

| Current Path | Proposed Path | Action | Reason | Dependencies Affected |
|---|---|---|---|---|
| `docs/GETTING_STARTED.md` | `docs/getting-started/onboarding.md` | Move & add root pointer | Consolidates setup and onboarding in dedicated folder | Readme links, developer guides |
| `docs/SYNC.md` | `docs/architecture/offline-first-sync.md` | Move & add root pointer | Belongs in architecture documentation | KB notes, architecture docs |
| `docs/BACKUP_RESTORE.md` | `docs/operations/backup-restore.md` | Move & add root pointer | Operations and disaster recovery guide | Readme, operations guides |
| `docs/AUDIT_REPORT_2026-10-16.md` | `docs/quality/audit-report-2026-10-16.md` | Move | Belongs in quality and compliance archive | Audit documentation |
| `docs/SECURITY_AUDIT_2026-10-13.md` | `docs/quality/security-audit-2026-10-13.md` | Move | Belongs in quality and security archive | Security documentation |
| `CACHE_ARCHITECTURE.md` | `docs/architecture/cache-architecture.md` | Move & add root pointer | Belongs in architecture documentation | Architecture references |
| `docs/Screenshot/*` | `design/assets/screenshots/*` | Move | Consolidates UI design assets under `design/` | Readme images, UI specs |
| `docs/superpowers/plans/*` | `docs/architecture/specs/plans/*` | Move | Historical role metadata implementation plans | Architecture references |
| `docs/superpowers/specs/*` | `docs/architecture/specs/*` | Move | Role metadata specifications | Architecture references |
| `docs/superpowers/2026-10-01-app-metadata-roles-final-report.md` | `docs/architecture/specs/2026-10-01-app-metadata-roles-final-report.md` | Move | Role metadata fix report | Architecture references |
| `docs/bug-audit/*` (except deb) | `docs/quality/bug-audit/*` | Move | Consolidates historical bug and SQL audit records | Audit references |
| `docs/bug-audit/BitChord-1.8-linux-amd64.deb` | N/A | Delete | 70MB unrelated binary installer accidentally placed in docs | None (gitignored) |
| `tui.heapsnapshot`, `server.heapsnapshot` | N/A | Delete | Transient Node/Wrangler V8 heap dumps | None (gitignored) |

---

## 3. New Files to Create

| Path | Purpose | Content Summary |
|---|---|---|
| `docs/architecture/repository-inventory.md` | Comprehensive inventory | Complete breakdown of all directories and files |
| `docs/architecture/architecture-assessment.md` | Architecture analysis | Clean architecture, offline sync, boundaries review |
| `docs/architecture/folder-migration-plan.md` | Migration tracking | Exact mapping of all file operations |
| `docs/architecture/where-things-belong.md` | Navigation & placement guide | Detailed rules and examples for placing new code |
| `docs/features/overview.md` | Feature matrix | Comprehensive summary of all 20 clinic features |
| `docs/database/schema-overview.md` | Database architecture | Room v33 schema, Supabase sync tables, RLS rules |
| `docs/operations/release-management.md` | Operations procedures | APK signing, release checklist, rollback runbook |
| `CONTRIBUTING.md` | Developer contribution guide | Workflow, coding style, TDD rules, PR standards |
| `README.md` (Overhaul) | Master repository guide | Architecture, directory tree, getting started, features |

---

## 4. Retained Unchanged Elements

- **Android Module Source**: `app/src/main/java/com/neochildclinic/**` retains its clean package structure (`app`, `core`, `data`, `domain`, `feature`) to preserve Room v33 schema entity mappings and 344 unit tests.
- **Android Tests**: `app/src/test/**` and `app/src/androidTest/**` remain in place.
- **Gradle Configurations**: Root `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, and `app/build.gradle.kts` remain intact.
- **Supabase Root**: `supabase/config.toml`, `supabase/migrations/`, `supabase/functions/`, and `supabase/tests/` remain at root for CLI compatibility.
- **Cloudflare Worker**: `cloudflare/backup-worker/` remains at its path to maintain Wrangler and GitHub Actions `.github/workflows/worker-ci.yml`.
- **Knowledge Base Vault**: `docs/Knowledge_Base/**` remains in place to preserve Obsidian and `validate_docs.py` strict checks.
- **AI Tooling & Skills**: `.agents/`, `.claude/`, `.opencode/`, `.artifacts/` remain intact.
