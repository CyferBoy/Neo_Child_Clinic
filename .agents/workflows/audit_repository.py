"""
Workflow: Repository Audit
Orchestrates parallel audits across Architecture, Database, Security, and QA.
Executed via Antigravity `run_workflow`.
"""

phase("Phase 1: Architecture & Package Inspection")
log("Launching software-architect to inspect Clean Architecture boundaries...")
arch_agent = agent(
    prompt="Inspect the repository package structure against Clean Architecture. Verify no DAO or Supabase leaks into ViewModels or Composables. Report any architectural violations.",
    role="Architecture Reviewer",
    type_name="software-architect",
    schema={"type": "string"}
)
arch_report = await arch_agent()
log("Architecture review completed.")

phase("Phase 2: Independent Parallel Audits")
log("Launching parallel audits across Database, Security, and Test suites...")

audit_tasks = [
    {
        "role": "Database Audit",
        "agent": "database-engineer",
        "prompt": "Audit Room v34 schema and Supabase SQL migrations. Verify foreign keys, index coverage, and atomic TransactionRunner usage."
    },
    {
        "role": "Security Audit",
        "agent": "security-auditor",
        "prompt": "Audit Supabase RLS policies, staff tenancy checks, and secret patterns. Report any critical security exposure."
    },
    {
        "role": "QA Test Coverage Audit",
        "agent": "qa-engineer",
        "prompt": "Inspect existing 344 unit tests and regression suites. Identify untested offline-first sync or stock deduction edge cases."
    }
]

async def run_audit(task):
    log(f"Starting {task['role']}...")
    a = agent(prompt=task["prompt"], role=task["role"], type_name=task["agent"], schema={"type": "string"})
    res = await a()
    return f"=== {task['role']} ===\n{res}\n"

audit_results = await parallel(audit_tasks, run_audit)
log("All parallel audits completed.")

phase("Phase 3: Synthesis & Documentation")
log("Synthesizing audit reports with documentation-engineer...")
doc_agent = agent(
    prompt=f"Synthesize the following audit findings into a concise markdown summary for docs/agents/AUDIT_SYNTHESIS.md:\n\nArchitecture:\n{arch_report}\n\nParallel Audits:\n{''.join(audit_results)}",
    role="Audit Synthesizer",
    type_name="documentation-engineer",
    schema={"type": "string"}
)
synthesis = await doc_agent()
log("Repository Audit Workflow finished successfully.")
