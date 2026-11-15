# Security Boundaries & Permissions Matrix

This document defines the security boundaries, privilege levels, and access controls governing all agents in the Neo Child Clinic ecosystem.

---

## 1. Privilege Levels (Principle of Least Privilege)

| Level | Classification | Permitted Tools | Permitted Targets | Assigned Agents |
|---|---|---|---|---|
| **Level 1** | **Read-Only Inspection** | `read_file`, `search_web`, read-only git | Entire repository (read-only) | `research-agent`, `code-reviewer`, `security-auditor` (recon) |
| **Level 2** | **Documentation Maintenance** | `read_file`, `write_file`, `edit_file` | `docs/**` strictly. Source code edits denied | `documentation-engineer` |
| **Level 3** | **Application Development** | `read_file`, `write_file`, `edit_file`, `run_command` | `app/src/**`, unit test suites | `android-developer`, `qa-engineer`, `performance-engineer` |
| **Level 4** | **Sensitive & Infrastructure** | Full tools (Subject to explicit confirmation) | `supabase/**`, `cloudflare/**`, database schema | `database-engineer`, `backend-engineer`, `release-engineer` |

---

## 2. Hard Security Boundaries

1. **Production Secret Boundary**:
   - No agent may read or output files containing production credentials, service role keys, or `.jks` signing keystores.
   - Credentials in `.env.local` must never be displayed in chat or committed to Git.
2. **Cloud Mutation Boundary**:
   - Agents must never execute live `psql`, `supabase db push`, or `wrangler deploy` against remote production infrastructure without explicit user confirmation.
3. **Destructive Git Boundary**:
   - Commands such as `git reset --hard`, `git push --force`, and `git clean -fd` are strictly blocked in automated workflows.
4. **Tenant Isolation (RLS)**:
   - Supabase Row-Level Security policies must enforce `clinic_id` tenancy and `active_staff` verification on all mutation operations.
