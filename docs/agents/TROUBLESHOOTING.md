# Troubleshooting & Recovery Guide

Common issues across Desktop and Termux environments and their documented resolutions.

---

## 1. Termux & Android Shared Storage Issues

### Symptom: `fatal: detected dubious ownership in repository`
- **Cause**: Android media UID differs from Termux application UID.
- **Fix**:
  ```bash
  git config --global --add safe.directory /storage/emulated/0/Neo-Child-Clinic
  ```

### Symptom: Git reports every file modified after a pull
- **Cause**: Android shared storage does not preserve POSIX filemode flags (`0644` vs `0755`).
- **Fix**:
  ```bash
  git -C /storage/emulated/0/Neo-Child-Clinic config core.filemode false
  git -C /storage/emulated/0/Neo-Child-Clinic checkout -- .
  ```

### Symptom: Permission Denied when writing files from CLI
- **Cause**: Storage permission revoked or workspace not marked trusted.
- **Fix**:
  1. Run `termux-setup-storage` and grant permission.
  2. Verify `/storage/emulated/0/Neo-Child-Clinic` is in `"trustedWorkspaces"` in `~/.gemini/antigravity-cli/settings.json`.

### Symptom: Termux processes killed in the background
- **Cause**: Android 16 Phantom Process Killer or battery optimization.
- **Fix**:
  ```bash
  termux-wake-lock
  ```
  Disable "Battery Optimization" for Termux in Android System Settings.

---

## 2. Desktop & Build Issues

### Symptom: `Method e in android.util.Log not mocked` in unit tests
- **Cause**: Android framework classes are stubbed out by default in JVM unit tests.
- **Fix**: Confirmed configured in `app/build.gradle.kts`:
  ```kotlin
  testOptions {
      unitTests.isReturnDefaultValues = true
  }
  ```

### Symptom: SQLCipher JNI build error (`libsqlcipher.so not found`)
- **Cause**: Missing 64-bit ABI filter or incorrect NDK.
- **Fix**: Ensure `app/build.gradle.kts` specifies:
  ```kotlin
  ndk {
      abiFilters += listOf("arm64-v8a")
  }
  ```

---

## 3. Agent Coordination Issues

### Symptom: Subagent fails with "Context budget exceeded"
- **Cause**: Aggregate `always_on` rules exceed 20,000 tokens.
- **Fix**: Keep root `AGENTS.md` concise. Move domain-specific rules into `.agents/rules/` with specific file `glob` triggers.
