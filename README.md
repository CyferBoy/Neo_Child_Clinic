# Neo Child Clinic — Vaccine Manager

[![GitHub release](https://img.shields.io/github/v/release/CyferBoy/Neo_Child_Clinic?label=latest%20release)](https://github.com/CyferBoy/Neo_Child_Clinic/releases/latest)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Status](https://img.shields.io/badge/status-active%20development-brightgreen)](#project-status)
[![Unit Tests](https://img.shields.io/badge/unit%20tests-554%20passed-success)](app/src/test/java/com/neochildclinic)

Neo Child Clinic is a robust, offline-first Android application designed for pediatric clinics and healthcare providers. It coordinates patient clinical records, multi-vial vaccination administration, doctor consultations, dynamic inventory/batch tracking, due reminders, and financial ledgers.

---

## 🧭 Developer Navigation & Documentation Map

| Area | Primary Guide | Description |
|---|---|---|
| **Code Placement** | 🧭 [Where Things Belong](docs/architecture/where-things-belong.md) | Practical guide: where to place screens, ViewModels, DAOs, migrations, and tests |
| **System Architecture** | 🏛️ [Architecture Overview](ARCHITECTURE.md) | High-level package boundaries, clean architecture, and dependency flow |
| **Getting Started** | 🚀 [Desktop Environment Setup](docs/agents/DESKTOP_SETUP.md) | Environment setup, JDK 17, Android Studio, `.env.local` keys, and first build |
| **Offline Synchronization** | 🔄 [Offline-First Sync](docs/architecture/offline-first-sync.md) | In-depth synchronization engine, queue mechanics, and conflict resolution |
| **Cache Architecture** | ⚡ [In-Memory Cache](docs/architecture/cache-architecture.md) | LRU cache specs, generation counters, and session boundary clearing |
| **Database Architecture** | 🗄️ [Room Database](docs/Knowledge_Base/Room%20Database.md) | Room schema 36 (library 2.8.5), 25 entities, 21 DAOs, foreign keys, cascade rules; Supabase RLS via the [policy matrix](docs/Knowledge_Base/Row%20Level%20Security.md) |
| **Features Overview** | 📋 [Project Hub](docs/Knowledge_Base/Neo%20Child%20Clinic.md) | Clinic capability overview and entry point to the feature notes |
| **Disaster Recovery** | 🛡️ [Backup & Restore](docs/Knowledge_Base/Backup%20and%20Restore.md) | `.nccb` AES-256-GCM container format, Cloudflare R2 worker, and safety rollback |
| **Releases & Deployment** | 📦 [Release Checklist](docs/Knowledge_Base/Release%20Checklist.md) | APK signing, ProGuard/R8 shrinking, in-app updates, and FCM alerts |
| **Contributing** | 🤝 [Contributing Guidelines](CONTRIBUTING.md) | Coding conventions, TDD requirements, and Git PR standards |
| **Security Policy** | 🔐 [Security Policy](SECURITY.md) | Responsible disclosure process and verified security controls |
| **Obsidian Vault** | 📚 [Knowledge Base](docs/Knowledge_Base/) | 66 interconnected notes verified with strict citation validators |

---

## 📱 Application Overview & Screenshots

- **Target SDK**: Android 15 (API 35) · **Compile SDK**: 35 · **Minimum SDK**: Android 10 (API 29)
- **Local Persistence**: Encrypted Room (schema 36, Room library 2.8.5, SQLite via SQLCipher) + Android Keystore
- **Cloud Backend**: Supabase PostgreSQL + Auth + Realtime + Storage
- **Edge Infrastructure**: Cloudflare Worker + R2 Storage for offsite encrypted backups

<table>
<tr>
<td align="center"><a href="design/assets/screenshots/dashboard.png" target="_blank"><img src="design/assets/screenshots/dashboard.png" width="220"><br><b>Clinic Dashboard</b></a></td>
<td align="center"><a href="design/assets/screenshots/patient%20details.png" target="_blank"><img src="design/assets/screenshots/patient%20details.png" width="220"><br><b>Patient Records</b></a></td>
<td align="center"><a href="design/assets/screenshots/due%20vaccination.png" target="_blank"><img src="design/assets/screenshots/due%20vaccination.png" width="220"><br><b>Due Vaccinations</b></a></td>
</tr>
<tr>
<td align="center"><a href="design/assets/screenshots/inventory.png" target="_blank"><img src="design/assets/screenshots/inventory.png" width="220"><br><b>Vial Inventory</b></a></td>
<td align="center"><a href="design/assets/screenshots/borrow%20vaccine.png" target="_blank"><img src="design/assets/screenshots/borrow%20vaccine.png" width="220"><br><b>Borrowed Vaccines</b></a></td>
<td align="center"><a href="design/assets/screenshots/app%20drawer.png" target="_blank"><img src="design/assets/screenshots/app%20drawer.png" width="220"><br><b>Navigation Drawer</b></a></td>
</tr>
</table>

---

## 📂 Repository Directory Tree

```text
Neo_Child_Clinic/
│
├── app/                                 # Android application module (:app)
│   ├── src/main/
│   │   ├── java/com/neochildclinic/
│   │   │   ├── app/                     # MainActivity, NavHost, Routes, work/ (SyncWorker), sync/
│   │   │   ├── core/                    # Shared infrastructure (cache, sync, security, ui, notifications)
│   │   │   ├── data/                    # Room DB (schema 36, library 2.8.5), 21 DAOs, 25 entities, backup
│   │   │   ├── di/                      # Hilt modules (DatabaseModule, RepositoryModule, SupabaseModule)
│   │   │   ├── domain/model/            # 35 Pure Kotlin business domain models (no Android imports)
│   │   │   └── feature/<name>/          # 20 Self-contained business features (patient, inventory, etc.)
│   │   ├── res/                         # Android drawables, colors, strings, themes, widget XML
│   │   └── AndroidManifest.xml          # Component declarations, permissions, auth deep links
│   ├── src/test/java/                   # 554 JVM-based unit tests
│   ├── src/androidTest/java/            # Android instrumentation tests (Room migrations, DAOs)
│   ├── schemas/                         # Room exported schema JSONs (33.json, 34.json, 35.json)
│   └── build.gradle.kts                 # Application build script and dependency configurations
│
├── supabase/                            # Supabase cloud backend
│   ├── migrations/                      # Tracked SQL schema, trigger arithmetic, & RLS migrations
│   ├── functions/                       # Deno Edge Functions (manage-staff, notify-update)
│   ├── tests/                           # SQL regression tests (e.g., stock_trigger_arithmetic.sql)
│   └── config.toml                      # Local Supabase CLI configuration
│
├── cloudflare/                          # Cloudflare edge infrastructure
│   └── backup-worker/                   # TypeScript Cloudflare Worker for R2 cloud backups & Vitest suite
│
├── design/                              # Design assets and design system
│   ├── design-system/                   # Design system documentation & contrast checkers
│   └── assets/screenshots/              # Full-resolution application screenshots
│
├── docs/                                # Documentation and Knowledge Base
│   ├── agents/                          # Multi-agent roles, coordination, and workstation setup
│   ├── architecture/                    # System architecture, sync models, placement guides
│   ├── audit/                           # Dated audit reports and remediation passes
│   ├── Knowledge_Base/                  # Linked Obsidian vault with 66 interconnected notes
│   └── SYNC.md                          # Synchronization quick reference
│
├── scripts/                             # Repository tooling & verification scripts
│   ├── validate_docs.py                 # Strict documentation and citation validator
│   └── test_validate_docs.py            # Regression test suite for documentation validation
│
├── .github/                             # GitHub Actions CI/CD workflows
├── README.md                            # Primary project overview and navigation guide
├── ARCHITECTURE.md                      # High-level architecture summary
├── CONTRIBUTING.md                      # Developer contribution workflow
└── SECURITY.md                          # Vulnerability reporting policy
```

---

## ⚡ Quick Start for Developers

```bash
# 1. Clone the repository
git clone https://github.com/CyferBoy/Neo_Child_Clinic.git
cd Neo_Child_Clinic

# 2. Configure credentials in .env.local
cat << 'EOF' > .env.local
SUPABASE_URL=https://YOUR_PROJECT_ID.supabase.co
SUPABASE_PUBLISHABLE_KEY=YOUR_SUPABASE_ANON_KEY
BACKUP_WORKER_URL=https://YOUR_BACKUP_WORKER.workers.dev
EOF

# 3. Build debug APK
./gradlew assembleDebug

# 4. Run unit tests (554 tests)
./gradlew testDebugUnitTest

# 5. Run documentation validation
python3 scripts/validate_docs.py --strict
```

See [Desktop Environment Setup](docs/agents/DESKTOP_SETUP.md) for full setup instructions.

---

## 🔒 Security & Privacy

This application handles sensitive medical, immunization, and financial data for children.
- Never commit credentials, signing keystores (`*.jks`), service-role keys, or `google-services.json`.
- Cloud access is governed by Supabase Row Level Security (RLS), but it is **not uniformly strict**: blanket `active staff` policies grant broad CRUD on many clinical/finance tables, with carve-outs for `expenses`, doctor schedules, `profiles` and `user_devices`, and deny-by-default on several tables. See the [RLS policy matrix and gaps](docs/Knowledge_Base/Row%20Level%20Security.md) for the exact per-table rules.
- Report any security vulnerabilities privately by following our [Security Policy](SECURITY.md).

---

## 📄 License

This project is licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
