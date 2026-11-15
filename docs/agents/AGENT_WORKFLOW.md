# Reusable Agent Workflows

This document outlines the standard workflows available to developers and agents in the Neo Child Clinic ecosystem.

---

## 1. Available Automated Workflows (`.agents/workflows/`)

| Workflow Script | Purpose | Agents Involved | Execution Mode |
|---|---|---|---|
| **`audit_repository.py`** | Comprehensive architectural, database, security, and test audit | `software-architect`, `database-engineer`, `security-auditor`, `qa-engineer`, `documentation-engineer` | Parallel inspection followed by synthesis |
| **`feature_development.py`** | Structured new feature implementation | `software-architect`, `android-developer`, `code-reviewer`, `qa-engineer` | Sequential pipeline |
| **`bug_investigation.py`** | Root cause analysis and verified fix | `android-developer`, `code-reviewer`, `qa-engineer` | Root-cause tracing with regression test |
| **`security_audit.py`** | RLS, token lifecycle, and secret review | `security-auditor`, `documentation-engineer` | Multi-phase security assessment |
| **`release_readiness.py`** | Pre-release build, shrinking, and test audit | `release-engineer`, `qa-engineer`, `documentation-engineer` | Readiness checklist & release notes |

---

## 2. Invoking Workflows

### 2.1 From Python Scripts
Workflows are executed using the native `run_workflow` tool:

```python
# Run the Repository Audit workflow
run_workflow(
    ScriptPath="/storage/emulated/0/Neo-Child-Clinic/.agents/workflows/audit_repository.py",
    Description="Full repository architecture, database, and security audit"
)
```

### 2.2 With Arguments
Workflows accept an `Args` JSON object:

```python
# Run Feature Development with arguments
run_workflow(
    ScriptPath="/storage/emulated/0/Neo-Child-Clinic/.agents/workflows/feature_development.py",
    Args={"feature_request": "Implement catch-up vaccination calculation for missed MMR doses"},
    Description="Implement MMR catch-up vaccination logic"
)
```

---

## 3. Manual Workflow Sequence

When executing workflows interactively through chat prompts:
1. State the objective clearly (e.g., "Run a security audit on the Supabase migrations").
2. The orchestrator invokes the appropriate specialist agent (`security-auditor`).
3. The specialist reports findings.
4. The orchestrator assigns remediation or peer review.
5. All deliverables are committed cleanly to Git.
