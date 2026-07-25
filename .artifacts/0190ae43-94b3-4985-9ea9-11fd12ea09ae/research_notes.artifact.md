# Research Notes - Failed to get service from broker

## Error Analysis
**Error:** `java.lang.SecurityException: Unknown calling package name 'com.google.android.gms'`
**Tag:** `GoogleApiManager`

This error indicates a failure in the communication between the app and Google Play Services (GMS).

### Potential Causes
1. **Package Visibility (Android 11+):** The app targets API 35. On Android 11+ (API 30), apps must declare the packages they intend to interact with in a `<queries>` block in `AndroidManifest.xml`. While GMS is often exempt, certain APIs or configurations (like Play Integrity or App Check) may require explicit declaration.
2. **Firebase App Check Configuration:** The app uses `FirebaseAppCheck` with both `DebugAppCheckProviderFactory` (in `NeoChildApp`) and `PlayIntegrityAppCheckProviderFactory` (in `AdminViewModel`). Play Integrity requires GMS and might be failing if GMS visibility is restricted or if the device/emulator isn't supported.
3. **Manifest/Package Name Mismatch:** Although they seem to match (`com.clinic.neochild`), any discrepancy between the manifest, `build.gradle`, and `google-services.json` could cause authentication issues.
4. **Initialization Order:** Firebase is initialized before `super.onCreate()` in `NeoChildApp`. While not strictly forbidden, it's unconventional and could lead to issues if certain context-dependent services aren't ready.

## Proposed Fixes
1. Add `<queries>` for `com.google.android.gms` to `AndroidManifest.xml`.
2. Ensure consistent App Check initialization. The secondary app initialization in `AdminViewModel` might be better handled or checked for existence before re-initializing.
3. Verify if `com.google.android.gms.permission.AD_ID` or other GMS-related permissions are needed (though less likely for this specific error).

## Code Snippet for Manifest
```xml
<queries>
    <package android:name="com.google.android.gms" />
</queries>
```
