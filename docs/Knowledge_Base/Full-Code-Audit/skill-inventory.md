# Full Code Audit — Skill and Tooling Inventory (Approval Gate)

- **Date:** 2026-10-25
- **Repository:** `CyferBoy/Neo_Child_Clinic` @ `3b61789` (`main`; local untracked: `docs/Knowledge_Base/Database-Audit/`, `supabase/migrations/20261022_fix_stock_trigger_update_arithmetic.sql`)
- **OpenCode:** v2.0.27 (verified via `opencode --version`)
- **Mode:** read-only inventory. Nothing has been installed, created, enabled, or executed against application code or production data.
- **Path note:** the requested `docs/Knowledge-Base/Full-Code-Audit/` uses a hyphen; this repository's existing vault is `docs/Knowledge_Base/` (underscore). This report therefore lives at `docs/Knowledge_Base/Full-Code-Audit/skill-inventory.md` to preserve the existing structure.

## 1. Skill format and discovery (verified)

OpenCode V2 discovers skills from (highest precedence last):

| Scope | Path |
| --- | --- |
| Built-in | `/builtin` |
| Compat (global, then ancestors) | `.claude/skills`, `.agents/skills` |
| Global | `~/.config/opencode/skills` |
| Project | `.opencode/skills` (project root → current directory) |
| Config entries | `skills` array in `opencode.json(c)` |

Format: `.opencode/skills/<id>/SKILL.md`, YAML frontmatter optional, `description` required for model discovery, ID = directory name (not the frontmatter `name`). Supporting files are addressed relative to the skill directory. Extra portable frontmatter fields (`license`, `compatibility`) are accepted but not interpreted.

Consequences for this repo:

1. The installed `.claude/skills/security-audit` and `.agents/skills/*` skills **already auto-load** when OpenCode runs with the repository as working directory.
2. The six project audit skills under `.opencode/skills/` are currently **gitignored**: `.opencode/*` with only `.opencode/agents/docs-maintainer.md` force-tracked (`.gitignore:83`).
3. This session's working directory is the home directory, so repository skills are not advertised here. The audit phase must run with `~/Neo_Child_Clinic` as the project directory.
4. OpenCode natively supports worktrees and subagents, so skill packs that reimplement those workflows add no value.

## 2. Installed skills (already available — no action needed)

| Skill ID | Location | Capability | Audit use |
| --- | --- | --- | --- |
| `opencode` | built-in | OpenCode configuration, versions, skills, MCP, SDK | Harness verification |
| `report` | built-in | GitHub issue reporting workflow | Out of scope |
| `awesome-ui-kit` | `~/.config/opencode/skills/awesome-ui-kit/SKILL.md` | Web UI components | Not audit-relevant |
| `ui-ux-pro-max` | `~/.config/opencode/skills/ui-ux-pro-max/SKILL.md` | UI/UX design intelligence | Not audit-relevant |
| `android-database-data-flow-auditor` | `.opencode/skills/android-database-data-flow-auditor/SKILL.md` | Room/Supabase read/write path tracing, traceability matrix | Coverage area 4 |
| `database-security-supabase-rls-auditor` | `.opencode/skills/database-security-supabase-rls-auditor/SKILL.md` | RLS, policies, SECURITY DEFINER, storage, edge functions | Coverage area 6 |
| `offline-first-sync-auditor` | `.opencode/skills/offline-first-sync-auditor/SKILL.md` | Queue, retries, idempotency, conflicts, tombstones | Coverage area 5 |
| `database-performance-auditor` | `.opencode/skills/database-performance-auditor/SKILL.md` | Room/Postgres query plans, indexes, N+1, fan-out | Coverage area 7 (database side) |
| `data-integrity-business-logic-auditor` | `.opencode/skills/data-integrity-business-logic-auditor/SKILL.md` | Stock conservation, borrow/return, finance totals, soft deletes | Coverage area 8 |
| `database-test-evidence-planner` | `.opencode/skills/database-test-evidence-planner/SKILL.md` | Test matrix, evidence ledger, regression design | Coverage area 10 |
| `security-audit` | `.claude/skills/security-audit/SKILL.md` + 15 references + Node validators | Full security-audit workflow (Cloudflare `security-audit-skill`, MIT, pushed 2026-09-14, ~44.8k stars; pinned in `skills-lock.json`) | Coverage area 2 (platform-generic) |
| `kotlin-coroutines-expert` | `.agents/skills/kotlin-coroutines-expert/SKILL.md` | Coroutines/Flow patterns | Coverage area 3 (patterns, not audit) |
| `ui-states` | `.agents/skills/ui-states/SKILL.md` | Empty/loading/error state design | Peripheral UI review |

## 3. Local tooling inventory

### Available

| Tool | Evidence | Use |
| --- | --- | --- |
| OpenCode v2.0.27 | `opencode --version` | Subagents, skill loading, session control |
| Git | `git --version` works; remote fetch/push verified | History, diff, blame evidence |
| JDK 25.0.3 | `java -version` | JVM tooling only; project targets Java 17 (`app/build.gradle.kts:96`) |
| Python 3 | `python3 -V` | `scripts/validate_docs.py`, custom read-only scans |
| Node + npm | `node -v`, `npm -v` | `cloudflare/backup-worker` vitest suite |
| curl, bash, grep, awk, sed | all present | Source inspection, GitHub API metadata |
| Gradle wrapper script | `gradlew` (distribution 9.7.1, not cached) | Only on a machine with Android SDK + supported JDK |

### Missing / hard limitations (evidence)

- **No Android SDK**: `ANDROID_HOME`/`ANDROID_SDK_ROOT` unset, no `sdkmanager`/`adb` → `./gradlew` Android tasks (compile, lint, unit tests) **cannot run on this device**.
- **No standalone Gradle/Kotlin compiler/Detekt/Android Lint binaries** installed.
- **No PostgreSQL client/server**, so `supabase/tests/*.sql` cannot be executed locally; the live Supabase project is not reachable without credentials, which this audit will not use.
- **No `jq`, `gh`, `gitleaks`, `trufflehog`, `semgrep`**. Secret scanning must use repository scripts plus read-only regex scans.
- **Toolchain mismatch risk**: AGP `9.4.1`, Kotlin `2.4.10`, Gradle `9.7.1`, compileSdk 37 (`gradle/libs.versions.toml:4`, `:5`; `app/build.gradle.kts:35`) versus JDK 25 only. Even with an SDK, builds should use the project's supported JDK on a dev machine.
- **Baseline validator state**: `python3 scripts/validate_docs.py --strict` currently reports 2 errors and 18 warnings across 61 notes — both errors pre-exist in the untracked `docs/Knowledge_Base/Database-Audit/` notes (`05_Data_Flow_Diagrams.md:25`, `12_Prioritized_Issue_Register.md:341`). This report starts warning-free except for a possible orphan-note warning.

### Additional tools that would be useful (all pending approval, none installed)

| Tool | Why | Feasibility |
| --- | --- | --- |
| Detekt CLI (JVM jar) | Kotlin static analysis without Android SDK | Downloadable; JDK 25 compatibility must be tested |
| ktlint standalone jar | Formatting/lint consistency | Downloadable |
| Android Lint / full Gradle build | Manifest, lint, R8, tests | **Not this device** — needs Android SDK + supported JDK on dev machine/CI |
| `npm ci` in `cloudflare/backup-worker` | Run existing worker tests | Feasible locally; installs project devDependencies |
| Disposable Postgres | Execute `supabase/tests/*.sql` | Not feasible locally; needs Docker/dev machine |

## 4. External repository evaluations

### 4.1 `obra/superpowers` — **RECOMMENDED (selective)**

- **Repository:** https://github.com/obra/superpowers
- **License:** MIT. **Maintenance:** pushed 2026-10-23, 300 files, 85 skill files, 304,668 stars (GitHub API at check time).
- **Compatibility:** skills are plain `SKILL.md` with name/description frontmatter. Grep across the fetched bodies found no `TodoWrite`, `Task` tool, or Claude-specific dependencies in the candidates below; only `using-superpowers` references Claude tool docs (rejected).
- **Permissions/dependencies/scripts:** instruction-only for selected skills. `systematic-debugging` ships example helper files (`find-polluter.sh`, `.ts` examples) that will **not** be copied. No network calls, telemetry, or external services.

**Selected (pending approval):**

| Exact skill | Direct source URL | Capability | Why needed |
| --- | --- | --- | --- |
| `systematic-debugging` | https://raw.githubusercontent.com/obra/superpowers/main/skills/systematic-debugging/SKILL.md (+ `root-cause-tracing.md`, `defense-in-depth.md`, `condition-based-waiting.md`) | Root-cause analysis instead of symptom patching; reproduction and evidence discipline | The repo has a history of "fixed" claims without reproduction (see current pre-existing KB errors); audit findings must trace to root cause |
| `verification-before-completion` | https://raw.githubusercontent.com/obra/superpowers/main/skills/verification-before-completion/SKILL.md | No completion claims without executed evidence | Prevents false "verified" statements when Android build/tests cannot run locally |
| `test-driven-development` | https://raw.githubusercontent.com/obra/superpowers/main/skills/test-driven-development/SKILL.md (+ `writing-good-tests.md`) | Regression-first tests for remediation phase | Audit output must include reproducible failing tests per confirmed defect |
| `writing-plans` | https://raw.githubusercontent.com/obra/superpowers/main/skills/writing-plans/SKILL.md | Structured remediation plan format | Phase 6 roadmap quality |

**Overlap:** none of the four duplicates installed skills; TDD overlaps repo testing docs conceptually but no existing skill.
**Installation (after approval):** selective copy into `.opencode/skills/<id>/` with relative references intact, plus an MIT attribution file. No whole-repo install, no package manager, no scripts executed.

**Rejected from this repo and why:**

- `using-superpowers` — session bootstrap that forces skill invocation before any response; references Claude Code tooling.
- `brainstorming` — ships local server scripts; out of scope.
- `subagent-driven-development`, `dispatching-parallel-agents` — reimplements OpenCode-native subagent/worktree behavior.
- `using-git-worktrees`, `finishing-a-development-branch` — OpenCode has native worktrees.
- `executing-plans` — only relevant after remediation approval; can be added later.
- `requesting-code-review` / `receiving-code-review` — useful but redundant next to the installed security-audit workflow and repo review evidence rules; defer.
- `diagnosing-superpowers`, `writing-skills`, `using-superpowers` — meta tooling, not audit capability.

### 4.2 `ldatb/opencode-setup` — **REJECTED**

- **Repository:** https://github.com/ldatb/opencode-setup
- **License:** **none declared** (GitHub API `license: None`) → copying content is legally unsafe. **Maintenance:** pushed 2026-05-22, 0 stars, personal workspace (83 files, 27 skill files).
- `security-audit` (7.5 KB): generic checklist; strictly weaker than the already-installed Cloudflare `security-audit` (MIT, actively maintained, with coverage-ledger/finding validators). Duplicate.
- `verification-loop` (6.1 KB): explicitly requires `@lead` dispatching specialists "via task tool" (verified at line 81) — harness-specific to the author's agent setup; incompatible with this workflow.
- `systematic-debugging` / `test-driven-development` / `verification-before-completion`: same names as superpowers, unlicensed and stale; prefer upstream MIT.
- **Verdict:** do not install anything from this repository.

### 4.3 `defuj/opencode-agent-kit` — **REJECTED as a source**

- **Repository:** https://github.com/defuj/opencode-agent-kit
- **License:** MIT. **Maintenance:** pushed 2026-07-27, 16 stars, 1,020 files, 219 `SKILL.md` files (grab-bag for many stacks).
- Verified candidates the user named:
  - `coding-standards` — **exists**, but frontmatter limits it to TypeScript/JavaScript/React/Node; not applicable to Kotlin/Android. Reject.
  - `android-jetpack-compose` — **does not exist**. Actual skill is `android-kotlin-compose` (45.4 KB): matches this stack (Clean Architecture, Compose, Material 3, Hilt, Room, Navigation, Gradle KTS, tests), but it is a developer guide, not an auditor, and duplicates `ARCHITECTURE.md` + the Knowledge Base. Reject for audit; at most a future reference.
  - `r8-analyzer` — **exists but is an empty stub** (14 bytes). Reject.
- Other inspected candidates, all rejected: `kotlin-coroutines-flows` (duplicate of installed `kotlin-coroutines-expert`), `kotlin-testing` (Kotest/MockK patterns; repo uses JUnit 4 — `app/build.gradle.kts:185`), `production-audit` (generic; repo has `Release Checklist.md`), `code-review-workflow` (TypeScript/React/Vue focus), `security-review` (overlap with Cloudflare), `verification-loop` (Claude Code-specific), `database-migrations` (generic ORM guidance; Supabase SQL covered by existing DB skills), `error-handling` (TypeScript/Python/Go), `kotlin-patterns` (developer patterns, overlapping existing skills).
- **Verdict:** do not install the repository or any of its skills for this audit.

## 5. Proposed project-local skills (create only after approval)

None of the installed skills covers these audit domains; each proposed skill is a read-only checklist/workflow, no scripts, no network, no production access.

| Proposed ID | Gap it closes | Contents | Overlap check |
| --- | --- | --- | --- |
| `android-mobile-security-auditor` | Area 2 is currently generic (Cloudflare); no MASVS/MASTG-mapped Android controls | Exported components, permissions, network security config, backup rules, storage, intents/deep links/WebView, secrets, logging, release hardening | Complements `database-security-supabase-rls-auditor` (backend) without duplicating it |
| `android-performance-memory-auditor` | Existing `database-performance-auditor` is Room/Postgres-only | Compose recomposition/jank, startup cost, coroutine/listener/bitmap leaks, WorkManager/Glance behavior, ANR risks | No overlap with DB performance skill |
| `kotlin-runtime-correctness-auditor` | `kotlin-coroutines-expert` teaches patterns; nothing audits cancellation, lifecycle, null/state transitions, races | Coroutine cancellation/exception/lifecycle collection, WorkManager/Hilt scoping, null-safety and state-machine defects, interpretation of Detekt/Lint output | Adds audit checklist, not coding patterns |
| `android-build-release-auditor` | Area 9 uncovered; `r8-analyzer` candidate is an empty stub | AGP/Kotlin/JDK/Gradle matrix, dependency CVEs, R8/minify/shrink, signing and secrets, manifest merge, network security, CI (`worker-ci.yml`, backup worker) | No existing equivalent |

Format: `.opencode/skills/<id>/SKILL.md` with `name` + `description` frontmatter. Note `.opencode/*` is gitignored (`.gitignore:83`), so these are local-only unless force-added; the tracked compatibility location would be `.agents/skills/` if team sharing is wanted.

## 6. Coverage matrix (the 10 required areas)

| # | Area | Existing | Proposed / external | Verification tool |
| --- | --- | --- | --- | --- |
| 1 | Full codebase + static analysis | Cloudflare `security-audit` (partial) | `systematic-debugging`, `verification-before-completion`; `kotlin-runtime-correctness-auditor` | Manual tracing + (pending) Detekt/ktlint |
| 2 | Android security / MASVS-MASTG | Cloudflare `security-audit` (generic) | `android-mobile-security-auditor` | Manifest/gradle/network-config inspection |
| 3 | Kotlin runtime, coroutines, lifecycle | `kotlin-coroutines-expert` (patterns) | `kotlin-runtime-correctness-auditor` | Source tracing + Detekt |
| 4 | DB read/write and data flow | `android-database-data-flow-auditor` | — | Source tracing |
| 5 | Room/Supabase/offline/cache | `offline-first-sync-auditor`, data-flow auditor | — | Source tracing + SQL review |
| 6 | DB security / RLS | `database-security-supabase-rls-auditor` | — | Migrations + policy review |
| 7 | Performance, memory, resources | `database-performance-auditor` (DB only) | `android-performance-memory-auditor` | Trace review + (pending) Lint |
| 8 | Business logic / integrity | `data-integrity-business-logic-auditor` | — | Trace + targeted tests |
| 9 | Gradle/deps/build/release | none | `android-build-release-auditor` | Version/config review + dev-machine build |
| 10 | Test coverage / regression planning | `database-test-evidence-planner`, `test-driven-development` | — | Test inventory + dev-machine runs |

## 7. Approval request (exact list)

- [ ] **A. Install 4 superpowers skills** (MIT, selective copy, attribution): `systematic-debugging`, `verification-before-completion`, `test-driven-development`, `writing-plans` → `.opencode/skills/`.
- [ ] **B. Create 4 project-local audit skills**: `android-mobile-security-auditor`, `android-performance-memory-auditor`, `kotlin-runtime-correctness-auditor`, `android-build-release-auditor` → `.opencode/skills/`.
- [ ] **C. Optional tool enablement** (state per item): install Detekt/ktlint jars; run `npm ci` + tests in `cloudflare/backup-worker`; provide exact build/lint/test commands for a dev machine.
- [ ] **D. Rejections confirmed:** nothing installed from `ldatb/opencode-setup` or `defuj/opencode-agent-kit`.
- [ ] **E. Audit execution:** after A/B (+C choices), run Phase 5 read-only audit and produce findings + remediation roadmap. **No fixes, no policy changes, no production data.**

## 8. Security review of the proposal

- Selected superpowers skills are instruction text only; no tools, network calls, credentials, or telemetry. Helper scripts in `systematic-debugging` are excluded from the copy.
- Skill text can still bias agent behavior ("persuasion principles" are documented in superpowers meta-docs); the four selected bodies were fetched and grepped for harness-specific or risky instructions before approval.
- MIT attribution will accompany copied skills. `ldatb/opencode-setup` has no license and is rejected for that reason among others.
- No live Supabase access, no credentials, no production data, and no application-code edits are part of the upcoming audit.

## 9. Stopping condition

This report ends the inventory phase. No skill has been downloaded, installed, enabled, or executed. Await explicit approval of the exact list in section 7 before creating skills or starting the audit.
