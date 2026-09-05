# Neo Child Clinic - Vaccine Manager

[![GitHub release](https://img.shields.io/github/v/release/CyferBoy/Neo_Child_Clinic?label=latest%20release)](https://github.com/CyferBoy/Neo_Child_Clinic/releases/latest)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Status](https://img.shields.io/badge/status-active%20development-brightgreen)](#project-status)

Neo Child Clinic - Vaccine Manager is an Android application for pediatric clinics to manage patients, vaccinations, consultations, vaccine inventory, reminders, staff, and financial records.

The application uses an offline-first architecture with an encrypted local Room database and Supabase synchronization.

## Table of Contents

- [Overview](#overview)
- [Screenshots](#screenshots)
- [Features](#features)
- [Technology Stack](#technology-stack)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
- [Backend & Configuration](#backend--configuration)
- [Releases & Updates](#releases--updates)
- [Data Safety](#data-safety)
- [Security & Privacy](#security--privacy)
- [Contributing](#contributing)
- [Support](#support)
- [License](#license)
- [Disclaimer](#disclaimer)

---

## Overview

- **Version:** 0.5.1
- **Version Code:** 3
- **Minimum Android:** 7.0 (API 24)
- **Target Android:** 15 (API 35)
- **Status:** 🟢 Active Development

Neo Child Clinic - Vaccine Manager is actively developed and maintained. Features, database structures, and backend services may change between releases.

---

## Screenshots

| | |
|---|---|
| ![Dashboard](Screenshot/dashboard.png) Dashboard | ![Patient Details](Screenshot/patient%20details.png) Patient Details |
| ![Due Vaccination](Screenshot/due%20vaccination.png) Due Vaccination | ![Inventory](Screenshot/inventory.png) Inventory |
| ![Borrow Vaccine](Screenshot/borrow%20vaccine.png) Borrow Vaccine | ![App Drawer](Screenshot/app%20drawer.png) Navigation Drawer |

---

## Features

### 👶 Patient Management

- Create, edit, search, and manage patient records.
- Automatic patient ID generation and age calculation.
- Vaccination and consultation history.
- Patient notes and todos.
- Patient merge support.
- Patient document, photo, and lab-report attachments.
- Search using patient details, vaccine names, and receipt numbers.

### 💉 Vaccination

- Add and edit vaccination visits.
- Multiple vaccines per visit.
- Vaccine and batch selection.
- Automatic next-vaccine calculation.
- Individual next-vaccine management.
- Stored next-vaccine IDs for reliable tracking.
- Vaccination receipts.
- Cash and online/UPI payment tracking.
- With Fees and Doctor's Account options.
- Inventory deduction and reconciliation when vaccination records are edited.

### 🩺 Consultation

- Add and edit consultations.
- Doctor selection.
- Consultation fees and clinical notes.
- Follow-up dates.
- Consultation todos.
- Consultation receipts.

### 📦 Vaccine Inventory

- Vaccine and batch management.
- Stock and expiry tracking.
- Inventory transactions.
- Low-stock monitoring.
- Vaccine wastage management.
- Borrowed and lent vaccine tracking.
- Partial vaccine returns.
- Return to existing or newly created batches.

### 🔄 Borrowed Vaccines & Returns

- Track vaccines borrowed from or lent to other doctors/clinics.
- Offline-first borrowed vaccine management.
- Partial returns.
- Multiple return transactions.
- Return vaccines to an existing batch.
- Create a new batch during return with batch number, expiry date, and pricing.
- Detailed return history and status tracking.

### 🔔 Reminders & Notifications

- Vaccination reminders.
- Consultation follow-ups.
- Personal vaccine reminders.
- Overdue, Today, Tomorrow, and Upcoming classification.
- Completed and cancelled reminder states.
- Daily clinic summary.
- Low-stock notifications.
- Background notifications using WorkManager.
- Configurable notification settings.

Personal vaccine reminders support both saved patients and walk-in/non-saved patients, including advance-payment tracking.

### 📊 Dashboard & Statistics

- Today's consultations and vaccinations.
- Upcoming and overdue work.
- Low-stock information.
- Vaccination statistics.
- Consultation statistics.
- Financial statistics.
- Monthly financial information.
- Vaccine usage statistics.
- Patient milestones and clinic metrics.

### 💰 Financial Management

- Vaccination and consultation financial records.
- Cash and online/UPI payments.
- Vaccine cost/COGS tracking.
- Financial summaries and monthly details.
- Receipt generation and printing.

#### Receipt Numbering

Receipt numbers are generated server-side using a shared clinic sequence.

- Financial year: April to March.
- Financial year is determined from the visit/transaction date.
- Receipt numbers are generated atomically to prevent duplicates.
- Receipt numbers are not reused after deletion.

Example:

> 1 April 2026 - 31 March 2027 = Financial Year 26/27

### 👨‍⚕️ Staff Management

Supported roles:

- Admin
- Doctor
- Receptionist
- Nurse
- Inventory Manager

Administrators can manage staff accounts, roles, and account status.

### 🔐 Security

- Supabase Authentication.
- Role-based access control.
- Supabase Row Level Security (RLS).
- SQLCipher-encrypted Room database.
- Android Keystore-backed secure storage.
- Biometric application lock.
- Configurable inactivity protection.
- Staff accountability.
- Audit logging.
- Server-side authorization for privileged operations.

### ☁️ Offline & Synchronization

- Offline-first local database.
- Encrypted local storage.
- Background synchronization with Supabase.
- WorkManager-based sync.
- Automatic retry for failed synchronization.
- Sync queue and status tracking.
- Manual synchronization.
- Realtime synchronization for supported data.

### 📎 Patient Documents

Patient clinical documents can be stored using Supabase Storage.

Supported attachments include:

- Documents
- Photos
- Lab reports

### 📱 Home Screen Widget

Provides quick access to upcoming vaccination information directly from the Android home screen with refresh support.

### 🔄 Application Update System

The built-in update system uses GitHub Releases and supports:

- Optional updates.
- Mandatory updates.
- Re-update of the installed version.
- Downgrade where supported.
- APK download and installation.
- Download progress and cancellation.
- Release notes.
- Minimum-version enforcement.
- Startup update notifications.

When a new update is detected, a small temporary notification can appear when the app opens. Tapping it opens the update interface. The notification is separate from downloading, re-update, and downgrade handling.

---

## Technology Stack

- Kotlin
- Jetpack Compose
- Material 3
- MVVM / Clean Architecture
- Hilt
- Room
- SQLCipher
- WorkManager
- Supabase
- Firebase Cloud Messaging
- GitHub Releases

---

## Architecture

The application follows a layered architecture:

```
UI
│
├── Jetpack Compose
├── ViewModels
└── Navigation
      │
      ▼
Domain
│
├── Use Cases
├── Domain Models
└── Repository Interfaces
      │
      ▼
Data
│
├── Repositories
├── Room / SQLCipher
├── Sync Manager
└── Supabase
      │
      ├── PostgreSQL
      ├── Auth
      ├── Realtime
      └── Storage
```

Background tasks such as synchronization and reminders are handled using WorkManager.

---

## Getting Started

### 1. Prerequisites

Install:

- Android Studio
- JDK 17
- Android SDK 35
- Git

A Supabase project and Firebase project are required for full functionality.

### 2. Clone the Repository

```bash
git clone https://github.com/CyferBoy/Neo_Child_Clinic.git
cd Neo_Child_Clinic
```

Open the project in Android Studio and allow Gradle to sync.

### 3. Configure Supabase

Configure:

```
NEXT_PUBLIC_SUPABASE_URL
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY
```

These can be provided through `.env.local` or the build environment.

Apply the required migrations from:

```
supabase/migrations/
```

### 4. Configure Firebase

Configure Firebase Cloud Messaging and add the required Android Firebase configuration.

FCM is required for:

- Vaccination reminders.
- Personal vaccine reminders.
- Low-stock notifications.
- Daily summaries.
- Application update notifications.

### 5. Configure Storage

Configure the patient document storage bucket:

```
patient-docs
```

Apply appropriate Storage policies.

### 6. Configure Edge Functions

Deploy:

```
supabase/functions/manage-staff/
supabase/functions/notify-update/
```

Configure the required server-side secrets before deployment.

### 7. Build

```bash
./gradlew assembleDebug
```

Or press Run ▶ in Android Studio.

Run tests:

```bash
./gradlew testDebugUnitTest
```

Run lint:

```bash
./gradlew lintDebug
```

### 8. Initial Setup

After installation:

1. Sign in with an authorized staff account.
2. Configure clinic settings.
3. Add staff and doctors.
4. Configure vaccines and inventory batches.
5. Configure notification preferences.
6. Perform an initial synchronization.
7. Verify patient, vaccination, inventory, and financial synchronization.

> **Important:** Before using real patient data, verify Authentication, RLS, Storage policies, Edge Function authorization, Firebase configuration, and backup/recovery settings.

---

## Backend & Configuration

### Backend

Supabase is used for:

- Authentication
- PostgreSQL database
- Row Level Security
- Realtime
- Storage
- Edge Functions

The project includes Edge Functions for privileged staff management and application-release notifications.

### Configuration & Secrets

Required application configuration:

```
NEXT_PUBLIC_SUPABASE_URL
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY
```

Firebase configuration is required for notification functionality.

Never commit:

- Supabase service-role keys.
- Firebase service-account credentials.
- Private signing keys.
- Production secrets.
- Other sensitive credentials.

The Supabase publishable key is a client-side key and should not be treated as a server secret. Backend access must be protected using authentication and RLS.

### Database

The application uses:

- Room + SQLCipher for encrypted local storage.
- Supabase PostgreSQL for cloud storage.
- Room migrations for supported local database upgrades.
- Supabase migrations for backend schema and security changes.

Current Room database version:

```
22
```

Supabase migrations are stored in:

```
supabase/migrations/
```

---

## Releases & Updates

### Latest Release

The latest stable APK is available from the GitHub Releases page.

[View Releases](https://github.com/CyferBoy/Neo_Child_Clinic/releases)

Production releases should use a properly signed APK.

### Publishing a New Release

To publish an application update:

1. Create a new GitHub Release.
2. Add the appropriate version tag.
3. Upload the signed APK.
4. Add release notes.
5. Add update metadata when required.

Example:

```
version-code: 4
update-type: mandatory
minimum-version-code: 4
```

The release workflow can notify registered devices through Firebase Cloud Messaging using the Supabase `notify-update` Edge Function.

### Changelog

Release-specific changes are documented in GitHub Releases.

[View Changelog](https://github.com/CyferBoy/Neo_Child_Clinic/releases)

---

## Data Safety

### Backup & Recovery

The application uses local storage and Supabase synchronization to maintain clinic data.

For production use, administrators should maintain a reliable database backup and recovery strategy.

Important considerations:

- Verify that synchronization is working.
- Monitor failed synchronization tasks.
- Configure Supabase database backups.
- Maintain a recovery plan.
- Test restoration procedures periodically.

Do not consider device-local data alone as a complete backup.

### Permissions

The application may request Android permissions required for features such as:

- Notifications.
- Camera or image capture.
- Access to selected files/documents.
- Network access.
- Background processing.
- Firebase Cloud Messaging.

Permissions are used only for the corresponding application features.

---

## Security & Privacy

If you discover a security vulnerability, please follow the instructions in [SECURITY.md](SECURITY.md). Do not publicly disclose security vulnerabilities before they have been reviewed.

The application may handle sensitive clinic information including patient, vaccination, consultation, financial, and staff data.

Production deployments should properly configure:

- Supabase RLS.
- Storage policies.
- Authentication.
- Edge Function secrets.
- Staff permissions.
- Database backup and recovery.

The application provides local database encryption and application security features, but secure deployment also depends on correct backend configuration.

---

## Contributing

Contributions, bug reports, and suggestions are welcome.

Before submitting changes:

1. Check existing issues and pull requests.
2. Keep changes focused and documented.
3. Run unit tests.
4. Run lint checks.
5. Verify database changes include the required migrations.
6. Test synchronization-related changes carefully.
7. Do not commit secrets or production credentials.

---

## Support

For application support:

neochildclinic.sbg@gmail.com

---

## License

This project is licensed under the Apache License, Version 2.0.

See [LICENSE](LICENSE) for the complete license.

---

## Disclaimer

Neo Child Clinic - Vaccine Manager is clinic-management software and does not replace professional medical judgment.

Clinic administrators are responsible for:

- Correct patient data entry.
- Correct vaccination schedules.
- Appropriate staff permissions.
- Data protection.
- Backup and recovery.
- Compliance with applicable healthcare and privacy requirements.

Always verify clinical and financial information before relying on it for patient care or accounting.
