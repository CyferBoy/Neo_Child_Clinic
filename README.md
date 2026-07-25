# Neo Child Clinic - Vaccine Manager (v1.3)

Neo Child Clinic is a modern, production-grade Android application designed for pediatric clinics to manage patient records, vaccination schedules, inventory, and clinical workflows with high security and data integrity.

## Recent Updates (v1.3)
- **Unified Follow-up Management:** Complete system for scheduling and tracking follow-up visits. Includes support for "Terminal States" such as **Marked as Done**, **Vaccinated Elsewhere**, and **Dismissed Reminder**.
- **Precise Due Logic:** Refined "Due Today" filtering to show only vaccinations due on the current date. Overdue items and grace-period reminders are automatically moved to the "Overdue" category for better focus.
- **External Vaccination Tracking:** Dedicated workflow to record vaccinations administered at other hospitals or government clinics, ensuring complete patient history without impacting local inventory.
- **Enhanced Audit Trail:** Improved tracking for reminder status changes, recording the staff member responsible, the reason for changes (e.g., dismissal reasons), and timestamps.
- **UI Architecture Cleanup:** Refactored reminder screens into specialized tabs (Due, Overdue, Completed/Dismissed) for a more organized administrative workflow.

## Previous Updates (v1.2)
- **Biometric Security Lock:** Integrated Android Biometric API for secure app access with configurable inactivity timeouts.
- **Full Clinic Audit Log:** App-wide historical tracking for every medical and administrative action.
- **Redesigned Expandable Settings:** Consolidated notification, inventory, backup, and security settings.
- **Sequential Clinic ID System:** Replaced random IDs with production-safe sequential system (`NEO-1000`).
- **Improved Room Schema Integrity:** Critical repair of database migration paths for 100% schema alignment.

## Features

- **Patient Management:** Comprehensive records with automated age calculation and sequential ID generation.
- **Smart Vaccination Engine:** Requirement-based tracking per vaccine. Automatically calculates next due dates based on clinic protocols.
- **Follow-up & Reminder System:** Schedule future visits, manage active/overdue reminders, and track completions or dismissals.
- **External Record Support:** Maintain a holistic view of patient health by recording vaccinations given elsewhere.
- **Inventory Management:** Track vaccine stock, batches, expiry dates, and low-stock alerts.
- **Secure Offline-First Architecture:** 
    - **Encryption:** 256-bit SQLCipher encryption for the local Room database.
    - **Offline Sync:** Robust background sync with Firebase Firestore via WorkManager.
- **Security & Privacy:** Biometric authentication and granular staff audit logs.
- **Clinical Tools:** Record consultations, generate digital receipts, and manage staff access.
- **Notification Suite:** Automated alerts for due vaccinations, overdue patients, and inventory warnings.
- **Home Screen Widgets:** Quick-access widgets for immediate visibility of today's tasks.

## Setup Instructions

To protect privacy and security, specific configuration files are not included in this repository. Follow these steps to set up the project:

### 1. Clone the Repository
```bash
git clone https://github.com/CyferBoy/Vaccine_Manager.git
```

### 2. Firebase Configuration
1. Create a new project in the [Firebase Console](https://console.firebase.google.com/).
2. Add an Android app to your Firebase project.
3. Download the `google-services.json` file.
4. Place the `google-services.json` file in the `app/` directory of the project.

### 3. Firebase Services Setup
Enable the following services in your Firebase console:
- **Authentication:** Email/Password provider.
- **Firestore Database:** Create a database in production mode.
- **Cloud Messaging:** To enable notifications.
- **App Check:** Recommended for production security.

### 4. Build and Run
- Open the project in Android Studio.
- Sync Gradle files.
- Build and run the app on an emulator or a physical device.

## Tech Stack

- **UI:** Jetpack Compose (Material 3)
- **Architecture:** MVVM + Clean Architecture principles
- **Database:** Room + SQLCipher (Encryption)
- **Backend:** Firebase (Firestore, Auth, FCM, App Check)
- **Background Tasks:** WorkManager
- **Dependency Injection:** Hilt

## License
This project is for demonstration purposes. Please contact the developer for licensing information.
