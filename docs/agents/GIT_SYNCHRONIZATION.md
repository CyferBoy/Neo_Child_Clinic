# Cross-Environment Git Synchronization Strategy

**Environments**: Linux Mint Workstation (Desktop) & Android Termux (Mobile CLI)  
**Remote Repository**: `https://github.com/CyferBoy/Neo_Child_Clinic.git`  
**Single Source of Truth**: GitHub `main` branch  

---

## 1. Synchronization Model: Independent Local Clones (Recommended)

Both devices maintain their own independent Git clones:
- **Desktop Clone**: Ext4 filesystem on Linux Mint.
- **Mobile Clone**: Android shared storage (`/storage/emulated/0/Neo-Child-Clinic`).

### 1.1 Development Workflow

```text
[Desktop: feat/billing-fix] ──(Push)──> [GitHub PR / Branch] ──(Pull)──> [Termux: feat/billing-fix]
                                                │
                                                ▼ (Merge after CI verification)
                                          [GitHub: main]
                                                │
                          ┌─────────────────────┴─────────────────────┐
                          ▼                                           ▼
                 [Desktop: pull main]                        [Termux: pull main]
```

### 1.2 Step-by-Step Sync Routine

#### On Termux (Mobile Engineering):
```bash
# 1. Ensure working directory is clean
git status

# 2. Fetch latest changes from GitHub
git fetch origin

# 3. Pull or rebase on main
git pull --rebase origin main

# 4. Create a dedicated task branch
git checkout -b task/vaccine-reminder-catchup

# ... perform edits, verify with python3 validator ...

# 5. Commit and push
git add .
git commit -m "feat(reminder): implement catch-up schedule rules"
git push -u origin task/vaccine-reminder-catchup
```

#### On Linux Mint (Desktop Verification & Heavy Testing):
```bash
# 1. Fetch remote task branch created from Termux
git fetch origin
git checkout task/vaccine-reminder-catchup

# 2. Run full JVM unit test verification
./gradlew testDebugUnitTest

# 3. Verify debug APK compilation
./gradlew assembleDebug

# 4. Merge to main once verified
git checkout main
git merge --no-ff task/vaccine-reminder-catchup
git push origin main
```

---

## 2. Shared Directory / Local Transfer Workflow (Alternative)

If files are transferred directly via USB/ADB or shared storage sync:
1. Always run `git config core.filemode false` to prevent Android FUSE permissions from marking every file as modified.
2. Never copy `.git/index.lock` across devices.
3. Keep `.env.local` strictly local to each device. Never sync credentials.

---

## 3. Prohibited Operations
- **No Force Pushes to `main`**: `git push --force origin main` is strictly prohibited.
- **No Automatic Hard Resets**: Never execute `git reset --hard` or `git clean -fd` in automated agent scripts without explicit confirmation.
