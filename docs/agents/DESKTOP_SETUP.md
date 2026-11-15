# Desktop Environment Setup Guide

**Workstation**: Linux Mint 22.3 Cinnamon (x86_64)  
**Host Application**: Google Antigravity Desktop 2.0  
**Repository**: `Neo_Child_Clinic`  

---

## 1. Prerequisites & Toolchain Installation

Ensure the required toolchain is installed on Linux Mint:

```bash
# Update APT packages
sudo apt update && sudo apt install -y git curl python3 python3-pip openjdk-17-jdk

# Verify JDK 17
java -version
javac -version

# Set JAVA_HOME in ~/.bashrc or ~/.zshrc if not present
echo 'export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"' >> ~/.bashrc
source ~/.bashrc
```

---

## 2. Opening the Project in Antigravity Desktop

1. Launch **Google Antigravity Desktop**.
2. Open the project root folder:
   - Click **Projects** in the left sidebar -> **Open Folder** -> Select `/path/to/Neo_Child_Clinic`.
3. Verify Custom Agents Discovery:
   - Antigravity Desktop automatically traverses `.agents/agents/` and registers the 12 specialist agents.
   - Click the **Skills & Customizations** icon in the sidebar to confirm active skills and rules.

---

## 3. Configuring Local Credentials

Create `.env.local` in the repository root (strictly gitignored):

```properties
SUPABASE_URL=https://your-supabase-project.supabase.co
SUPABASE_PUBLISHABLE_KEY=your-supabase-anon-key
BACKUP_WORKER_URL=https://your-backup-worker.workers.dev
```

---

## 4. Verification Commands

Run standard verification commands in the Desktop integrated terminal:

```bash
# 1. Validate documentation
python3 scripts/validate_docs.py --strict

# 2. Run JVM unit tests (344 tests)
./gradlew testDebugUnitTest

# 3. Build debug APK
./gradlew assembleDebug
```

---

## 5. Desktop-Specific Agent Features

- **Auxiliary Panes**: View active subagent threads, background tasks, and Git diffs in the right-hand panel.
- **Visual Swarms**: Use the Swarm board for live multi-agent tracking during parallel tasks.
- **Browser Automation**: Use the built-in browser tool to test web endpoints, Supabase dashboard views, or Cloudflare Worker APIs.
