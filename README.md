# Neo Child Clinic - Vaccine Manager (1.0)

Neo Child Clinic is a modern, production-grade Android application designed for pediatric clinics to manage patient records, vaccination schedules, inventory, and clinical workflows with high security and data integrity.

## Features (1.0)

- **Supabase Backend Integration:** Fully integrated with **Supabase**. Uses Supabase Auth for identity management and PostgREST for high-performance PostgreSQL data access.
- **Realtime Collaboration:** Uses Supabase Realtime to ensure data (patients, vaccinations, etc.) stays synchronized across all clinic devices instantly.
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
git clone https://github.com/CyferBoy/Vaccine_Manager.git
```

### 2. Supabase Configuration
1. Create a new project on [Supabase](https://supabase.com/).
3. Enable **Email Auth** in the Authentication settings.
4. Add `neochild://auth-callback` to your Redirect URLs.
5. Create a storage bucket named `patient-docs`.

### 3. App Configuration
1. Open `SupabaseModule.kt` in the `di` package.
2. Replace `YOUR_SUPABASE_URL` and `YOUR_SUPABASE_ANON_KEY` with your project credentials.

### 4. Firebase Messaging (Optional for Notifications)
1. Add your `google-services.json` to the `app/` directory if you wish to use FCM for push notifications.

### 5. Build and Run
- Open the project in Android Studio.
- Sync Gradle files.
- Build and run the app.

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

## Public GitHub release notes

This repository contains the Vaccine Manager Android application.

### Supabase
The Android client uses the Supabase URL and publishable/anon client key required by the application. Do not commit Supabase service-role keys, database passwords, or other server-side secrets.

### Push notifications
Firebase Cloud Messaging (FCM) is used for push notifications. Device FCM tokens are associated with authenticated users through the application's backend. Firebase Authentication is not required for application authentication.

### Building a release
1. Open the project in Android Studio.
2. Configure your private release signing key locally; never commit the keystore or signing credentials.
3. Update `versionCode` and `versionName` for each release.
4. Build a signed release APK/AAB.
5. Publish the release artifact separately in GitHub Releases.

### GitHub Releases
The source repository and release APKs are separate concerns. Do not commit APK/AAB files to the source tree. Release APKs should be attached to GitHub Releases.

### Security
Before deploying with real patient data, review Supabase Row Level Security (RLS), Edge Function authorization, and all server-side access controls.

### In-app update releases

The app checks the latest GitHub Release and downloads the APK from its release assets. Each release should attach one `.apk` file.

Recommended release body metadata:

```text
version-code: 2
update-type: mandatory
minimum-version-code: 2
```

For an optional release:

```text
version-code: 3
update-type: optional
```

`version-code` must match the Android `versionCode`. `update-type: mandatory` prevents dismissal. `minimum-version-code` can force an older installed build to update even when the newest release itself is optional.

The app remembers a dismissed optional version and will not show it again until a newer release is available.

### Update Automation

The project includes a GitHub Action (`notify-update.yml`) that triggers when a release is published. It notifies all registered devices via FCM about the new version.

#### 1. GitHub Secrets
Configure these in GitHub (Settings → Secrets and variables → Actions):
- `SUPABASE_URL`: Your project URL (e.g., `https://xyz.supabase.co`).
- `UPDATE_NOTIFIER_SECRET`: A long random string to authenticate GitHub to your Edge Function.

#### 2. Supabase Configuration
- **Edge Function**: Deploy the `notify-update` function using the Supabase CLI.
- **Secrets**: Set these in Supabase (`supabase secrets set`):
  - `UPDATE_NOTIFIER_SECRET`: Same value as in GitHub.
  - `FIREBASE_SERVICE_ACCOUNT`: The content of your Firebase Service Account JSON.
