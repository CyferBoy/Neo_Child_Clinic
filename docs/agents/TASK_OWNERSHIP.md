# Task Ownership & Conflict Prevention

**Core Rule**: Two concurrent agents must NEVER edit the same file.

---

## 1. File Ownership Registry

To prevent merge collisions, race conditions, and corrupted states, file paths in the Neo Child Clinic repository are assigned strict domain ownership:

| Subsystem / Path Pattern | Exclusive Owner | Escalation / Reviewer |
|---|---|---|
| `docs/architecture/adr/**` | `software-architect` | `project-manager` |
| `app/src/main/java/**/domain/**` | `software-architect` | `android-developer` |
| `app/src/main/java/**/presentation/**` | `android-developer` | `code-reviewer` |
| `app/src/main/java/**/feature/**/data/**` | `android-developer` | `database-engineer` |
| `app/src/main/java/**/core/sync/**` | `android-developer` | `software-architect` |
| `app/src/main/java/**/data/local/**` | `database-engineer` | `software-architect` |
| `app/schemas/**` | `database-engineer` | `qa-engineer` |
| `supabase/migrations/**` | `backend-engineer` | `database-engineer` |
| `supabase/functions/**` | `backend-engineer` | `security-auditor` |
| `cloudflare/backup-worker/**` | `backend-engineer` | `security-auditor` |
| `app/src/test/**` | `qa-engineer` | `android-developer` |
| `app/src/androidTest/**` | `qa-engineer` | `database-engineer` |
| `docs/**` (except ADR drafts) | `documentation-engineer` | `software-architect` |
| `build.gradle.kts`, `gradle/**` | `release-engineer` | `android-developer` |
| `.github/workflows/**` | `release-engineer` | `security-auditor` |

---

## 2. Conflict Prevention Procedure

Before assigning work to any agent:
1. **Blast Radius Analysis**: The `project-manager` checks which files the agent plans to inspect and edit.
2. **Lock Allocation**: If another active agent is touching a file in that path, the second task is placed in a pending state until the first task commits its changes.
3. **Workspace Isolation**: When parallel code edits are necessary, agents run with `workspace="branch"` (isolated Git clone) and produce diffs that are merged sequentially after peer review.
4. **Clean Tree Verification**: Before executing a git pull or checkout, agents verify that `git status` reports a clean working tree.
