# Neo Child Clinic — Developer Onboarding & Setup

This guide explains how to set up **Neo Child Clinic - Vaccine Manager** for local Android development and how to prepare the required backend and cloud services.

> [!CAUTION]
> **Data Privacy Warning**: This project handles pediatric clinic and patient medical information. Do not use real patient data in a development environment until authentication, Row Level Security (RLS), Storage policies, database access, and backups have been thoroughly reviewed and tested.

---

## 1. Prerequisites & Environment Setup

Install the following before opening the project:
- **Android Studio**: Ladybug / Meerkat or newer
- **JDK**: Version 17 (e.g., OpenJDK 17 or Android Studio Embedded JBR 17)
- **Android SDK**: Platform 35 (`targetSdk = 35`, `compileSdk = 35`, minimum `minSdk = 29` / Android 10)
- **Git**: 2.30+
- **Supabase Account**: Project configured with PostgreSQL, Auth, and Storage
- **Firebase Account**: Project configured with Firebase Cloud Messaging (FCM)
- **Node.js** (Optional, for backup worker): v22+ if testing `cloudflare/backup-worker/`

### Project Configuration Matrix

| Component | Value | Defined In |
|---|---|---|
| Kotlin | 2.0.21 | `gradle/libs.versions.toml` |
| Android Gradle Plugin (AGP) | 8.7.1 | `gradle/libs.versions.toml` |
| Compile SDK | 35 | `app/build.gradle.kts` |
| Target SDK | 35 | `app/build.gradle.kts` |
| Minimum SDK | 29 (Android 10) | `app/build.gradle.kts` |
| Java Compatibility | Java 17 | `app/build.gradle.kts` |
| Room Database Schema | v33 | `AppDatabase.kt` / `app/schemas/` |
| Application ID | `com.neochildclinic` | `app/build.gradle.kts` |

---

## 2. Clone and Initial Sync

```bash
git clone https://github.com/CyferBoy/Neo_Child_Clinic.git
cd Neo_Child_Clinic
```

1. Open Android Studio and select **Open**, navigating to the cloned directory.
2. Allow Android Studio to download Gradle wrapper dependencies and complete the initial sync.
3. If prompted, confirm SDK platform 35 installation.

---

## 3. Configure Local Credentials

The Android application expects environment variables or a `.env.local` file at the root of the project:

```properties
SUPABASE_URL=https://YOUR_PROJECT_ID.supabase.co
SUPABASE_PUBLISHABLE_KEY=YOUR_SUPABASE_ANON_KEY
BACKUP_WORKER_URL=https://YOUR_BACKUP_WORKER.workers.dev
```

These values are injected at build time into `BuildConfig`:
- `BuildConfig.SUPABASE_URL`
- `BuildConfig.SUPABASE_ANON_KEY`
- `BuildConfig.BACKUP_WORKER_URL`

> [!WARNING]
> **Security Rule**: Never place Supabase `service_role` keys or private credentials into `.env.local` or Android application code. The Android app only ever uses the publishable/anon key with Row Level Security (RLS) enforcement.

---

## 4. Google Services (FCM) Configuration

Firebase Cloud Messaging is used for background vaccine reminders, update alerts, and daily clinic summaries.
1. Download `google-services.json` from the Firebase Console for package `com.neochildclinic`.
2. Place the file at `app/google-services.json`. (This file is gitignored to protect project credentials).

---

## 5. Building and Running Tests

### Assemble Debug Build
```bash
./gradlew assembleDebug
```
The resulting APK is located at `app/build/outputs/apk/debug/app-debug.apk`.

### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```
Runs the full suite of 344 unit tests verifying Room conversions, encryption, financial arithmetic, sync engines, and UI state processors.

### Run Documentation Strict Validation
```bash
python3 scripts/validate_docs.py --strict
```
Verifies all 66 Obsidian Knowledge Base notes, anchor links, Mermaid diagrams, and 1,064 exact line citations.

---

## 6. Offline-First Verification

When testing the app locally:
1. Log in with an active clinic staff account.
2. Put the device/emulator into Airplane Mode.
3. Add a patient, record a vaccination, or update inventory stock.
4. Verify the operation commits immediately to the local Room database (v33) and registers an entry in `sync_queue`.
5. Disable Airplane Mode.
6. Verify that `SyncWorker` or `SyncManager` uploads the queued operations to Supabase without duplication.
