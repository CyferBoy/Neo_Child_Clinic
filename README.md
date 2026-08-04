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
2. Run the provided SQL schema scripts (found in the artifacts directory) in the Supabase SQL Editor to create the necessary tables and RLS policies.
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
