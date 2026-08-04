# Neo Child Clinic - Vaccine Manager (v1.6)

Neo Child Clinic is a modern, production-grade Android application designed for pediatric clinics to manage patient records, vaccination schedules, inventory, and clinical workflows with high security and data integrity.

## Recent Updates (v1.6)
- **Supabase Backend Migration:** Fully transitioned from Firebase to **Supabase**. Integrated Supabase Auth for identity management and PostgREST for high-performance PostgreSQL data access.
- **Realtime Collaboration:** Implemented Supabase Realtime to ensure data (patients, vaccinations, etc.) stays synchronized across all clinic devices instantly.
- **Patient Attachments (Supabase Storage):** Added support for uploading and managing clinical documents, photos, and lab reports directly within the patient profile.
- **Consultation Tracking:** New module to record and track patient consultations, fees, and clinical notes, separate from vaccination records.
- **Enhanced Dashboard (Phase 2):** Redesigned the Dashboard with a Material 3 Navigation Drawer, providing quick access to Manage Staff, Audit Logs, and App Settings.
- **Integrated Sync Status:** Added a visual Cloud Sync indicator in the top bar to provide real-time feedback on background data synchronization.

## Previous Updates (v1.3)
- **Unified Follow-up Management:** Complete system for scheduling and tracking follow-up visits.
- **Precise Due Logic:** Refined "Due Today" filtering and automatic "Overdue" categorization.
- **External Vaccination Tracking:** Dedicated workflow to record vaccinations administered at other facilities.
- **Enhanced Audit Trail:** Improved tracking for status changes with staff attribution and timestamps.

## Features

- **Patient Management:** Comprehensive records with automated age calculation and sequential ID generation.
- **Smart Vaccination Engine:** Requirement-based tracking per vaccine. Automatically calculates next due dates.
- **Consultation & Clinical Notes:** Dedicated workspace for non-vaccination medical visits.
- **Document Attachments:** Securely store and view patient-related files in the cloud.
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
2. Run the provided SQL schema scripts (found in the artifacts directory) in the Supabase SQL Editor to create the necessary tables and RLS policies.
3. Enable **Email Auth** in the Authentication settings and disable "Confirm Email" for testing if desired.
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
- Build and run the app on an emulator or a physical device.

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
