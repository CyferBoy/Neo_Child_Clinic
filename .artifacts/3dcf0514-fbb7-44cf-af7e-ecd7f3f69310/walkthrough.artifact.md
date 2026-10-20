# Walkthrough - Resolved GMS SecurityException

The `java.lang.SecurityException: Unknown calling package name 'com.google.android.gms'` error was caused by a bug in `play-services-base:18.5.0`. This has been resolved by upgrading the library and correctly configuring the `google-services` plugin.

## Changes

### 1. Library Upgrade
Updated `playServicesBase` from `18.5.0` to `18.10.0` in `libs.versions.toml`. Version `18.5.0` has a known internal bug that causes package name verification failures in some environments.

### 2. Plugin Configuration
- Added `alias(libs.plugins.google.services) apply false` to the root [build.gradle.kts](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/build.gradle.kts).
- Applied `alias(libs.plugins.google.services)` to the app [build.gradle.kts](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/build.gradle.kts).
- This ensures that the application is correctly identified by Google Play Services and that the `google-services.json` file is processed.

### 3. File Organization
- Moved `google-services.json` from the project root to the [app/](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/) directory. The `google-services` plugin requires this file to be in the module directory to generate the necessary configuration resources.

## Verification Results

- **Gradle Sync**: Successful.
- **Build**: `./gradlew :app:assembleDebug` completed successfully.
