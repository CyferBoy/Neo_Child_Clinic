# Audit Coverage Gaps

## 1. Environment & Build Limitations
- **Offline Sandbox**: Gradle wrapper execution (`./gradlew`) failed due to network restrictions (`UnknownHostException: services.gradle.org`), preventing offline re-verification of the 310 unit tests in this specific shell session.
- **Git History**: The working directory lacked a `.git` commit history log in the archive review snapshot, requiring static validation of code state rather than commit diff analysis.

## 2. Backend & Live Infrastructure
- **Live Supabase RLS**: Live database trigger execution, active PostgREST policies (`pg_policies`), and Supabase Edge Functions could not be executed or inspected against a live instance.
- **Batch Reassignment Replay**: NCC-AUDIT-001 (stock trigger batch reassignment) requires a staging test harness to verify PostgreSQL PL/pgSQL behavior under active transaction updates.
