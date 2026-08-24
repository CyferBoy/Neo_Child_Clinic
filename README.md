# Neo Child Clinic - Vaccine Manager (v0.5.0)

Neo Child Clinic is a modern, production-grade Android application designed for pediatric clinics to manage patient records, vaccination schedules, inventory, and clinical workflows with high security and data integrity.

## Features (v0.5.0)

- **Staff Accountability Tracking:** Added `created_by` and `updated_by` tracking across all major tables. Every record now saves the human-readable name (display name or email) of the staff member who created or last modified it.
- **Supabase Backend Integration:** Fully integrated with **Supabase**. Uses Supabase Auth for identity management and PostgREST for high-performance PostgreSQL data access.
- **Realtime Collaboration:** Uses Supabase Realtime to ensure data (patients, vaccinations, etc.) stays synchronized across all clinic devices instantly.
- **App Update System:** Automated checking for new releases via GitHub Releases with support for mandatory and optional updates.
- **IST Timezone Standardization:** All timestamps and clinical logic are locked to **Indian Standard Time (IST, UTC+05:30)** to ensure consistency across the clinic.
- **Patient Attachments:** Support for uploading and managing clinical documents, photos, and lab reports via Supabase Storage.
- **Consultation Tracking:** Dedicated module to record and track patient consultations, fees, and clinical notes.
- **Enhanced Dashboard:** Navigation hub with a Material 3 Navigation Drawer, providing quick access to all clinical modules.
- **Smart Vaccination Engine:** Requirement-based tracking per vaccine. Automatically calculates next due dates.
- **Patient Management:** Comprehensive records with automated age calculation and sequential ID generation.
- **Follow-up & Reminder System:** Complete system for scheduling and tracking follow-up visits with specific "Due" and "Overdue" logic.
- **Inventory Management:** Track vaccine stock, batches, expiry dates, and low-stock alerts.
- **Secure Offline-First Architecture:** 
    - **Encryption:** 256-bit SQLCipher encryption for the local Room database.
    - **Offline Sync:** Robust background sync with Supabase via WorkManager.
- **Security & Privacy:** Biometric authentication and granular role-based access control (RLS).
- **Home Screen Widgets:** Quick-access widgets for immediate visibility of today's tasks.

## Setup Instructions

### 1. Clone the Repository
```bash
git clone https://github.com/CyferBoy/Neo_Child_Clinic.git
```

### 2. Supabase Configuration
1. Create a new project on [Supabase](https://supabase.com/).
2. Enable **Email Auth** in the Authentication settings.
3. Add `neochild://auth-callback` to your Redirect URLs.
4. Create a storage bucket named `patient-docs`.
5. Set the project timezone to `UTC` (The app handles IST conversion locally).

### 3. App Configuration
1. Open `SupabaseModule.kt` in the `di` package.
2. Replace `YOUR_SUPABASE_URL` and `YOUR_SUPABASE_ANON_KEY` with your project credentials.

### 4. Firebase Messaging (For Notifications & Updates)
1. Add your `google-services.json` to the `app/` directory.
2. Obtain a Firebase Service Account JSON for the notification pipeline.

### 5. Build and Run
- Open the project in Android Studio.
- Sync Gradle files.
- Build and run the app.

## App Update System

The application automatically checks for updates on startup and provides a manual check in **Settings → Support → Check for Updates**.

### Creating a Release
To publish an update:
1. Create a new **GitHub Release**.
2. Attach the signed release APK as an asset.
3. In the Release description, include the following metadata:
   ```text
   version-code: 2
   update-type: mandatory
   minimum-version-code: 2
   ```
   - `version-code`: Must match the `versionCode` in `build.gradle.kts`.
   - `update-type`: Either `mandatory` or `optional`.
   - `minimum-version-code`: (Optional) Forces updates for all versions below this code.

### Update Automation
The project includes a GitHub Action (`notify-update.yml`) that triggers when a release is published. It notifies all registered devices via FCM about the new version.

#### 1. GitHub Secrets
Configure these in GitHub (Settings → Secrets and variables → Actions):
- `SUPABASE_URL`: Your project URL.
- `UPDATE_NOTIFIER_SECRET`: A secure shared secret between GitHub and your Edge Function.

#### 2. Supabase Configuration
- **Edge Function**: Deploy the `notify-update` function using the Supabase CLI.
- **Secrets**: Set these in Supabase (`supabase secrets set`):
  - `UPDATE_NOTIFIER_SECRET`: Same value as in GitHub.
  - `FIREBASE_SERVICE_ACCOUNT`: The content of your Firebase Service Account JSON.

## Tech Stack

- **UI:** Jetpack Compose (Material 3)
- **Architecture:** MVVM + Clean Architecture principles
- **Database:** Room + SQLCipher (Local) / Supabase PostgreSQL (Remote)
- **Authentication:** Supabase Auth
- **Storage:** Supabase Storage
- **Realtime:** Supabase Realtime
- **Dependency Injection:** Hilt

## License
This project is licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for details.

---
**Note:** This repository is intended for clinical use. Ensure all Supabase RLS policies and Edge Function authorizations are properly configured before deploying with real patient data.
