# Remove Firebase and Google Services Plugin

The project currently uses Firebase (FCM) for push notifications, but since you are moving to Supabase and want "nothing to do with Firebase," I will remove the Google Services plugin and all Firebase-related code and dependencies. Supabase does **not** require the Google Services plugin or `google-services.json`.

## User Review Required

> [!WARNING]
> Removing Firebase Messaging (FCM) will disable push notifications in the app. Supabase does not currently provide a direct alternative for mobile push notifications without using a provider like FCM or OneSignal.

## Proposed Changes

I will remove the Google Services plugin, Firebase dependencies, and refactor the code to remove Firebase references.

### Build Configuration

#### [MODIFY] [build.gradle.kts (Project)](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/build.gradle.kts)
- Remove `alias(libs.plugins.google.services) apply false`.

#### [MODIFY] [app/build.gradle.kts](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/build.gradle.kts)
- Remove `alias(libs.plugins.google.services)`.
- Remove Firebase dependencies (`firebase-bom`, `firebase-messaging`).

### Code Refactoring

#### [MODIFY] [NeoChildApp.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/app/NeoChildApp.kt)
- Remove `Firebase.initialize(context = this)`.
- Remove Firebase imports.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/app/MainActivity.kt)
- Remove `FirebaseMessaging` injection.
- Remove `fetchAndStoreFcmToken()` and its call.
- Remove related imports and permission request logic if no longer needed.

#### [MODIFY] [NotificationModule.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/di/NotificationModule.kt)
- Remove `provideFirebaseMessaging()` provider.

#### [DELETE] [MyFirebaseMessagingService.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/notification/MyFirebaseMessagingService.kt)
- Delete this file as it is a Firebase service.

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/AndroidManifest.xml)
- Remove the `.notification.MyFirebaseMessagingService` declaration.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to verify that the project builds successfully without the Google Services plugin.

### Manual Verification
- Verify that the app launches and functions correctly without Firebase initialization errors in Logcat.
