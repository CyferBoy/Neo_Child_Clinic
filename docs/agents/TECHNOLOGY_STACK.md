# Technology Stack Reference

**Project**: Neo Child Clinic  
**Verified From**: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `supabase/config.toml`, and environment audits  

---

## 1. Client-Side Android Stack

| Tier / Function | Technology | Version | Purpose & Notes |
|---|---|---|---|
| **Language** | Kotlin | 2.4.10 | Modern static typing, Coroutines, Flow, Kotlinx Serialization |
| **Android Tooling** | AGP (Android Gradle Plugin) | 9.5.0 | Application compilation, packaging, and linting |
| **Compiler Plugin** | Jetpack Compose Compiler | Bundled in Kotlin 2.4+ | Composable declarative UI generation |
| **Compiler Processor** | Google KSP | 2.3.11 | High-performance symbol processing for Room and Hilt |
| **Target SDK** | Android 15 (API 35) | 35 | Target runtime behavior |
| **Compile SDK** | Android 16 (API 37) | 37 | SDK compilation bindings |
| **Min SDK** | Android 10 (API 29) | 29 | Minimum supported clinical devices |
| **UI Framework** | Jetpack Compose | BOM 2026.08.00 | Pure reactive declarative UI |
| **Design System** | Material 3 (M3) | Compose M3 | Dynamic theming, clinic palette, accessibility |
| **Navigation** | Navigation Compose | 2.10.0 | Strongly-typed route navigation graphs |
| **Local Database** | Room Database | 2.8.5 | SQLite persistence with type-safe DAOs and schema exports |
| **Database Encryption**| SQLCipher Android | 4.19.0 | AES-256 full database encryption |
| **Key Management** | AndroidX Security Crypto | 1.1.0 | Android Keystore backed MasterKey encryption |
| **Dependency Injection**| Dagger Hilt | 2.60.1 | Compile-time dependency injection across ViewModels/Workers |
| **Background Sync** | AndroidX WorkManager | 2.11.2 | Periodic & one-time sync worker with constraints |
| **Home Widget** | AndroidX Glance | 1.2.0 | Compose-based home screen due-vaccination widget |
| **Biometrics** | AndroidX Biometric | 1.1.0 | Fingerprint / face unlock for staff sessions |
| **Push Notifications** | Firebase Cloud Messaging | BOM 34.19.0 | Remote alerts and clinic appointment reminders |
| **HTTP Engine** | Ktor Client CIO | 3.5.2 | Asynchronous I/O client for remote communication |

---

## 2. Cloud & Edge Backend Stack

| Component | Technology | Description |
|---|---|---|
| **Backend as a Service**| Supabase | PostgreSQL 15+, Auth, Realtime, Storage, Edge Functions |
| **Supabase Kotlin SDK** | io.github.jan-tennert:supabase-kt (3.8.0) | PostgREST, Auth, Realtime, Functions, Storage |
| **Database Migrations** | Tracked SQL Migrations (`supabase/migrations/`) | 38 migrations governing schema, triggers, and RLS policies |
| **Edge Functions** | Deno TypeScript (`supabase/functions/`) | `manage-staff` (secure staff administration), `notify-update` (FCM) |
| **Cloud Disaster Recovery**| Cloudflare Workers + Cloudflare R2 | TypeScript worker in `cloudflare/backup-worker/` for `.nccb` containers |
| **Edge Testing** | Vitest (`cloudflare/backup-worker/vitest.config.mts`)| Unit test suite for Cloudflare Worker JWT and R2 operations |

---

## 3. Tooling & Development Verification

| Category | Tool | Location / Config |
|---|---|---|
| **JVM Unit Testing** | JUnit 4.13.2 | `app/src/test/java/` (554 unit tests) |
| **Android Instrumentation**| AndroidX Test Runner 1.6.2 | `app/src/androidTest/` (MigrationTestHelper) |
| **Documentation Validator**| Python script | `scripts/validate_docs.py` (strict citation validator) |
| **Git Safety Config** | `git config` | `core.filemode=false`, `core.symlinks=false`, `safe.directory` |
