# Neo Child Clinic — Getting Started

This guide explains how to set up **Neo Child Clinic - Vaccine Manager** for local Android development and how to prepare the required backend services.

> **Important:** This project handles clinic and patient information. Do not use real patient data in a development environment until authentication, Row Level Security (RLS), Storage policies, database access, and backups have been reviewed and tested.

---

## 1. What You Need

Install the following before opening the project:

- **Android Studio** with a recent stable Android SDK setup
- **JDK 17**
- **Android SDK Platform 35** or a compatible installed SDK required by the project
- **Git**
- A **Supabase** project
- A **Firebase** project for Firebase Cloud Messaging (FCM)

### Project requirements

| Component | Current project value |
|---|---|
| Kotlin | 2.4.10 |
| Android Gradle Plugin | 9.3.2 |
| Compile SDK | 37 |
| Target SDK | 35 |
| Minimum SDK | 29 (Android 10) |
| Java | 17 |
| Room database | 22 |
| Application ID | `com.neochildclinic` |

The exact dependency versions are maintained in `gradle/libs.versions.toml`.

---

## 2. Get the Source Code

Clone the repository:

```bash
git clone https://github.com/CyferBoy/Neo_Child_Clinic.git
cd Neo_Child_Clinic
```

Open the project directory in Android Studio.

Allow Android Studio to:

1. Detect the Gradle project.
2. Download Gradle dependencies.
3. Sync the project.
4. Install any missing Android SDK components requested by the project.

Do not change dependency versions just to make the first sync succeed. Resolve the actual Gradle/SDK error first.

---

## 3. Configure Supabase

The Android application reads these values from `.env.local` or environment variables:

```text
SUPABASE_URL=
SUPABASE_PUBLISHABLE_KEY=
```

### Recommended local setup

Create `.env.local` in the project root:

```properties
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_PUBLISHABLE_KEY=YOUR_PUBLISHABLE_KEY
```

The application maps these values into the Android `BuildConfig` as:

- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`

### Where to find them

In your Supabase project, obtain:

- The project URL.
- The publishable/client key intended for the Android application.

**Never put a Supabase service-role key in the Android application's configuration.**

The publishable key is used by the client, while authorization must be enforced with Supabase Authentication and RLS.

---

## 4. Configure the Supabase Database

The application depends on the Supabase PostgreSQL schema, authentication configuration, RLS policies, Realtime configuration, and Storage configuration.

Before running the application against a new Supabase project, make sure the required backend schema and policies have been deployed.

The repository currently contains Supabase configuration and Edge Functions under:

```text
supabase/
├── config.toml
└── functions/
    ├── manage-staff/
    └── notify-update/
```

If your database migrations/schema are maintained separately from this repository, apply the matching database version before using the application.

> **Do not create an incompatible database schema manually.** The Android Room/Supabase models and server-side schema must remain compatible.

---

## 5. Configure Firebase Cloud Messaging

Firebase Cloud Messaging is used for application notifications such as vaccination reminders, personal vaccine reminders, low-stock notifications, daily summaries, and update notifications.

### Firebase Android configuration

The Android application expects Firebase configuration for the package:

```text
com.neochildclinic
```

Use the Firebase project's Android configuration and ensure the correct `google-services.json` is available to the app module when building locally.

The file is intentionally ignored by Git in the normal development workflow:

```text
app/google-services.json
```

If your repository copy does not contain a valid configuration file, download the Android configuration from Firebase Console and place it at that location.

> Do not commit private Firebase service-account credentials or other server-side secrets.

---

## 6. Configure Patient Document Storage

Patient documents and related attachments use Supabase Storage.

The application expects the Storage bucket:

```text
patient-docs
```

The Storage bucket and its policies must allow only properly authenticated and authorized users to access permitted clinic data.

Supported attachment types include:

- Patient documents
- Photos
- Lab reports
- Expense receipt attachments using the existing document system

Test upload, download, authorization, and deletion/soft-delete behavior before using real data.

---

## 7. Deploy Supabase Edge Functions

The project contains two Edge Functions:

```text
supabase/functions/manage-staff/
supabase/functions/notify-update/
```

### `manage-staff`

Used for privileged staff-management operations such as creating/managing staff and password-related actions.

### `notify-update`

Used by the application update notification workflow to notify registered devices through FCM.

Deploy these functions through the Supabase CLI or your established deployment pipeline.

The functions use server-side environment values such as:

```text
SUPABASE_URL
SUPABASE_SERVICE_ROLE_KEY
```

The **service-role key must remain server-side** and must never be embedded in the Android application.

For update notifications, configure the additional secret required by the deployment workflow:

```text
UPDATE_NOTIFIER_SECRET
```

---

## 8. Local Environment Checklist

Before building, verify:

```text
[ ] JDK 17 is installed
[ ] Android SDK is installed
[ ] Project opens successfully in Android Studio
[ ] Gradle sync completes successfully
[ ] .env.local contains SUPABASE_URL
[ ] .env.local contains SUPABASE_PUBLISHABLE_KEY
[ ] Firebase Android configuration is available
[ ] Supabase database schema is deployed
[ ] Authentication is configured
[ ] RLS policies are enabled and tested
[ ] patient-docs Storage bucket and policies are configured
[ ] Required Edge Functions are deployed
```

---

## 9. Build the Debug APK

From the project root:

### Linux/macOS

```bash
./gradlew assembleDebug
```

### Windows

```bat
gradlew.bat assembleDebug
```

The debug APK is normally generated under:

```text
app/build/outputs/apk/debug/
```

You can also select the `app` configuration in Android Studio and press **Run ▶**.

---

## 10. Run Unit Tests

Run debug unit tests with:

```bash
./gradlew testDebugUnitTest
```

On Windows:

```bat
gradlew.bat testDebugUnitTest
```

Run lint with:

```bash
./gradlew lintDebug
```

The project is configured not to abort the build on lint errors in the current configuration, so review lint output rather than assuming a successful build means there are no lint findings.

---

## 11. First Launch

After installing the debug build:

1. Open the application.
2. Sign in using an authorized staff account.
3. Confirm the correct clinic/account context is loaded.
4. Configure clinic settings.
5. Add staff and assign appropriate roles.
6. Configure doctors and vaccine data.
7. Add inventory and vaccine batches.
8. Configure notification preferences.
9. Perform an initial synchronization.
10. Verify that local and Supabase data remain consistent.

### Recommended first tests

Test these workflows with non-production data:

- Create and edit a patient.
- Add a vaccination.
- Add/edit a next vaccination.
- Add a consultation.
- Add vaccine stock and batches.
- Test stock deduction and reconciliation.
- Test borrowed vaccine and return workflows.
- Test expenses and financial calculations.
- Test reminders and notifications.
- Test patient document upload/download.
- Test offline changes followed by synchronization.
- Test staff role restrictions.

---

## 12. Offline-First Development

The application uses a local Room database protected with SQLCipher and synchronizes supported data with Supabase.

When testing offline behavior:

1. Start with a synchronized account/device.
2. Disable network access.
3. Create or edit test records.
4. Confirm the application continues to operate using local data.
5. Restore network access.
6. Allow the synchronization process to run.
7. Confirm the changes reach Supabase correctly.
8. Check for conflicts, failed sync items, or duplicate records.

Do not assume that a locally saved record has reached the server until synchronization has completed successfully.

---

## 13. Database and Encryption Notes

### Local database

The Android application uses:

- Room
- SQLCipher
- Android security facilities for key protection

The current Room database version is:

```text
22
```

When changing entities or database structure, add the appropriate Room migration and update the database version according to the project's migration strategy.

### Cloud database

Supabase provides the PostgreSQL backend. Changes to the cloud schema must remain compatible with:

- Room entities
- Domain models
- Repositories
- Synchronization logic
- RLS policies
- Realtime behavior

---

## 14. Security Rules for Developers

Never commit or expose:

```text
SUPABASE_SERVICE_ROLE_KEY
Firebase service-account private keys
Android signing private keys
Keystores and passwords
Production credentials
Production patient data
Other server-side secrets
```

Client-side configuration must never be used as a substitute for server-side authorization.

Always verify:

- Supabase Authentication
- Row Level Security (RLS)
- Storage policies
- Edge Function authorization
- Staff roles and permissions
- Secure local key handling
- Production backup/recovery procedures

---

## 15. Common Setup Problems

### Gradle sync fails

Check:

- JDK 17 is being used by Gradle.
- Android Studio has the required SDK installed.
- The project has network access to download dependencies.
- You did not accidentally change Kotlin/AGP/KSP versions.

### Supabase connection fails

Check:

- `.env.local` is in the project root.
- `SUPABASE_URL` is correct.
- `SUPABASE_PUBLISHABLE_KEY` is correct.
- The application was rebuilt after changing environment values.
- Authentication and RLS are configured correctly.

### Notifications do not arrive

Check:

- Firebase is configured for `com.neochildclinic`.
- FCM registration is working.
- Android notification permission/settings are enabled where required.
- Supabase/Edge Function notification configuration is correct.
- Device network access is available.

### Patient documents fail to upload

Check:

- The `patient-docs` bucket exists.
- Storage policies are correctly configured.
- The signed-in user has the required authorization.
- Supabase configuration is correct.

---

## 16. Before Production Use

Complete this checklist before connecting the application to real clinic data:

```text
[ ] Production Supabase project verified
[ ] Database schema verified
[ ] RLS policies tested
[ ] Storage policies tested
[ ] Edge Functions deployed securely
[ ] Server-side secrets configured
[ ] Firebase production configuration verified
[ ] Notifications tested
[ ] Authentication and staff roles tested
[ ] Offline sync tested
[ ] Backup strategy configured
[ ] Recovery/restore procedure tested
[ ] Release APK signed correctly
[ ] Production update process tested
```

Do not use the debug build as the production release.

---

## 17. Useful Project Locations

| Purpose | Location |
|---|---|
| Android application | `app/` |
| Gradle version catalog | `gradle/libs.versions.toml` |
| Supabase configuration | `supabase/` |
| Staff Edge Function | `supabase/functions/manage-staff/` |
| Update notification function | `supabase/functions/notify-update/` |
| Screenshots | `docs/Screenshot/` |
| Architecture/design diagrams | `docs/designs/` |
| Security documentation | `SECURITY.md` |
| Main project README | `README.md` |

---

## 18. Quick Start

For an experienced developer, the shortest path is:

```bash
git clone https://github.com/CyferBoy/Neo_Child_Clinic.git
cd Neo_Child_Clinic
```

Create `.env.local`:

```properties
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_PUBLISHABLE_KEY=YOUR_PUBLISHABLE_KEY
```

Then:

```bash
./gradlew assembleDebug
```

Install the generated APK, sign in with an authorized account, and verify the backend/synchronization setup before entering real data.

---

## Need More Documentation?

See the main [`README.md`](../README.md) for the complete feature list, architecture, security, releases, and project information.
