---
trigger: glob
globs: "**/supabase/**, **/core/security/**, **/cloudflare/**, **/*.sql"
---

# Security & RLS Guardrails

When working on backend policies, authentication, encryption, or edge functions:

1. **Zero Secret Exposure**:
   - Never hardcode or commit API keys, service roles, signing keystores (`*.jks`), or database passwords.
   - All credentials reside in `.env.local` or environment variables (strictly gitignored).
2. **Row-Level Security (RLS) Invariants**:
   - Every newly created Supabase PostgreSQL table MUST enable RLS (`ALTER TABLE ... ENABLE ROW LEVEL SECURITY;`).
   - Mutations MUST verify clinic tenancy (`clinic_id`) and active staff status (`active_staff` JWT claim).
3. **Android Keystore & Encryption**:
   - Room database encryption passkeys MUST be generated and retrieved via Android Keystore `MasterKey` (`EncryptedSharedPreferences`).
   - Never print or export cryptographic keys in application logs.
4. **Cloudflare Disaster Recovery Containers**:
   - Backups pushed to Cloudflare R2 MUST be encrypted locally with AES-256-GCM before transmission.
   - The backup worker validates bearer JWT authentication before serving backup metadata or payloads.
