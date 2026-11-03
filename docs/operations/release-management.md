# Neo Child Clinic — Release & Deployment Runbook

This guide covers building, signing, verifying, and distributing release builds of Neo Child Clinic.

---

## 1. Build Variants & Optimization

| Build Variant | Minification | Resource Shrinking | ProGuard Config | Debuggable | ABI Filters |
|---|---|---|---|---|---|
| **Debug** (`debug`) | No | No | None | Yes | `arm64-v8a`, `x86_64` |
| **Release** (`release`) | Yes (R8 full mode) | Yes | `proguard-rules.pro` | No | `arm64-v8a` |

---

## 2. Pre-Release Verification Checklist

Execute these verification gates before building production release binaries:

```bash
# 1. Run all unit tests
./gradlew testDebugUnitTest

# 2. Verify strict documentation citations and links
python3 scripts/validate_docs.py --strict
python3 scripts/test_validate_docs.py

# 3. Check for compilation warnings and lint
./gradlew compileReleaseKotlin
```

---

## 3. Building the Release Binary

### Unsigned Release APK
```bash
./gradlew :app:assembleRelease
```
The unsigned release APK is written to:
`app/build/outputs/apk/release/app-release-unsigned.apk`

### Signing with Production Key
Production APKs are signed using `apksigner` with the clinic release keystore:
```bash
apksigner sign --ks neochildclinic-release.jks \
  --ks-key-alias neochildclinic \
  --out app/build/outputs/apk/release/app-release-signed.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

> [!CAUTION]
> **Keystore Security**: Production keystores (`*.jks`) and passwords must NEVER be checked into Git. Store them securely in hardware tokens, environment secrets, or local developer keystore stores.

---

## 4. In-App Updates & Edge Function Deployment

1. **GitHub Releases Asset**: Upload `app-release-signed.apk` to the target GitHub release tag.
2. **Push Notification Fanout**: Trigger the `notify-update` Supabase Edge Function to alert active clinic devices:
   ```bash
   curl -X POST https://YOUR_PROJECT.supabase.co/functions/v1/notify-update \
     -H "Authorization: Bearer $UPDATE_NOTIFIER_SECRET" \
     -H "Content-Type: application/json" \
     -d '{"version": "1.0.1", "versionCode": 2, "notes": "Security updates and bug fixes"}'
   ```
3. **Client Verification**: Active devices download the update in the background via `AppUpdateManager` and verify the cryptographic signature before triggering package installer intent.
