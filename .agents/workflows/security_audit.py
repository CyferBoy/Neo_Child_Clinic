"""
Workflow: Security & RLS Policy Audit
Executes comprehensive vulnerability analysis across RLS policies, secrets, and auth flows.
"""

phase("Phase 1: Supabase RLS & PostgreSQL Audit")
log("Launching security-auditor for database and cloud inspection...")
sec_agent = agent(
    prompt="Audit all SQL migrations in supabase/migrations/. Verify that every table enforces RLS, that active staff checks exist for mutations, and that no public read/write holes exist.",
    role="RLS Auditor",
    type_name="security-auditor",
    schema={"type": "string"}
)
db_audit = await sec_agent()
log("Database security audit complete.")

phase("Phase 2: Android Keystore & Secret Scanning")
log("Auditing client-side Android security & credentials...")
client_sec = agent(
    prompt="Audit Android security configurations: SQLCipher KeyStore initialization, Biometric lock session invalidation, and scan for any hardcoded tokens or API keys.",
    role="Client Security Auditor",
    type_name="security-auditor",
    schema={"type": "string"}
)
client_audit = await client_sec()
log("Client security audit complete.")

phase("Phase 3: Formal Vulnerability Report")
log("Generating security audit summary...")
reporter = agent(
    prompt=f"Compile a structured CVSS-classified security report combining:\n\nDatabase Audit:\n{db_audit}\n\nClient Audit:\n{client_audit}",
    role="Security Reporter",
    type_name="documentation-engineer",
    schema={"type": "string"}
)
final_report = await reporter()
log("Security Audit Workflow complete.")
