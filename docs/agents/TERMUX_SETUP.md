# Android Termux CLI Environment Setup Guide

**Device**: Android 16 (ARM64 / aarch64)  
**Host Application**: Termux (F-Droid 0.119.0-beta.3)  
**CLI Tool**: Google Antigravity CLI (`agy` 1.5.9)  
**Workspace Path**: `/storage/emulated/0/Neo-Child-Clinic`  

---

## 1. Initial Termux Setup & Package Installation

Execute these commands inside Termux to prepare the environment:

```bash
# 1. Grant shared storage permissions
termux-setup-storage

# 2. Switch to high-speed Indian mirror (mirror.albony.in - Hyderabad)
ln -sf $PREFIX/etc/termux/mirrors/asia/mirror.albony.in $PREFIX/etc/termux/chosen_mirrors
echo "deb https://mirror.albony.in/termux/termux-main/ stable main" > $PREFIX/etc/apt/sources.list

# 3. Update packages and install core toolchain
pkg update -y
pkg install -y git curl wget nano python proot
```

---

## 2. Git Configuration for Android Shared Storage

Because Android shared storage (`/storage/emulated/0`) uses FUSE emulation without Unix POSIX permissions, configure Git with these safety flags:

```bash
# Mark directory safe for Git
git config --global --add safe.directory /storage/emulated/0/Neo-Child-Clinic

# Disable filemode and symlink tracking (avoids phantom permission diffs)
git -C /storage/emulated/0/Neo-Child-Clinic config core.filemode false
git -C /storage/emulated/0/Neo-Child-Clinic config core.symlinks false
```

---

## 3. Antigravity CLI Configuration (`settings.json`)

Ensure `/storage/emulated/0/Neo-Child-Clinic` is marked as a trusted workspace in `~/.gemini/antigravity-cli/settings.json`:

```json
{
  "allowNonWorkspaceAccess": true,
  "trustedWorkspaces": [
    "/data/data/com.termux/files/home",
    "/storage/emulated/0/Neo-Child-Clinic"
  ]
}
```

---

## 4. Preventing Background Termination (Android 16)

Android 16 aggressively puts background apps to sleep. During long agent orchestration sessions:

```bash
# Acquire a wake lock to keep Termux CPU active
termux-wake-lock
```

To release the wake lock when finished:
```bash
termux-wake-unlock
```

---

## 5. Launching the Engineering Ecosystem

```bash
# Navigate to the project root
cd /storage/emulated/0/Neo-Child-Clinic

# Launch the Antigravity CLI
agy
```

Inside the CLI, verify that available agents and skills are discovered:
- Type `/help` to see available commands.
- Run `agy agents` from the shell to list discovered agent roles.
- Run `python3 scripts/validate_docs.py --strict` to verify documentation.
