# Implementation Plan - Fix GMS SecurityException

The application is experiencing a `java.lang.SecurityException: Unknown calling package name 'com.google.android.gms'` which is a known issue with `play-services-base:18.5.0`. Additionally, the project is missing the `google-services` plugin which is required for correct Firebase and Google Play Services configuration.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/gradle/libs.versions.toml)
- Update `playServicesBase` version from `18.5.0` to `18.10.0` (latest stable) to resolve the `SecurityException` bug.

#### [MODIFY] [build.gradle.kts](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/build.gradle.kts) (root)
- Apply the `google-services` plugin in the top-level build file.

#### [MODIFY] [app/build.gradle.kts](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/build.gradle.kts)
- Apply the `google-services` plugin to the app module.

### File Organization

#### [MOVE] `google-services.json`
- Move `google-services.json` from the project root to the `app/` directory where it is expected by the `google-services` plugin.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to ensure the project builds correctly with the new plugin and library version.

### Manual Verification
- Verify that the `google-services` plugin is correctly processing the JSON file by checking if `google-services.json` related resources are generated (internal check).
- The user should verify if the "Failed to get service from broker" error is resolved when running the app.
