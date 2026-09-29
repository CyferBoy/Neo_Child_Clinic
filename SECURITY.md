# Security Policy

## Supported Versions

Security updates are provided for actively maintained versions of Neo Child Clinic.

| Version | Supported |
|---------|-----------|
| 0.5.x   | Yes |
| < 0.5   | No |

## Reporting a Vulnerability

If you discover a security vulnerability in Neo Child Clinic, please report it privately.

**Please do not create a public GitHub issue for security vulnerabilities.**

You can report a vulnerability using GitHub's private vulnerability reporting feature, if available, or by contacting:

**Email:** neochildclinic.sbg@gmail.com

When reporting a vulnerability, please include:

- A clear description of the vulnerability
- The affected version
- Steps to reproduce the issue
- The potential security impact
- Screenshots, logs, or proof-of-concept details when appropriate
- Any suggested mitigation, if known

Please avoid including real patient information, credentials, access tokens, passwords, or other sensitive data in the report.

## Response and Resolution

We will review security reports and respond as soon as reasonably possible.

Confirmed vulnerabilities will be assessed based on severity and impact. Where appropriate, fixes will be released through a subsequent application update.

## Responsible Disclosure

Please allow reasonable time for investigation and remediation before publicly disclosing a vulnerability.

Security testing must not:

- Access or expose another person's data
- Modify or delete data belonging to other users
- Attempt to obtain unauthorized credentials or access tokens
- Disrupt the service
- Use real patient or other sensitive personal information

## Security Updates

Users should keep Neo Child Clinic updated to the latest supported release to receive available security fixes and improvements.

## Supabase database authorization baseline

The repository now includes `supabase/migrations/20260929000000_security_baseline.sql`.
It establishes the server-side baseline used by the Android client:

- protected business tables require an authenticated, active, non-deleted staff profile;
- `profiles` is read-only through the normal client path; privileged staff management is performed by the `manage-staff` Edge Function;
- `user_devices` is scoped to the authenticated owner;
- audit logs are append-only from the normal client path;
- the authorization helper is `SECURITY DEFINER` with a fixed `search_path` and is executable only by authenticated users.

### Deployment requirement

Apply the migration to the real Supabase project before treating these protections as active. The Android APK cannot enforce RLS by itself.

### Important limitation

The current schema does not expose an explicit `clinic_id`/tenant column across business tables. Therefore this migration deliberately uses active-staff membership as the compatible authorization boundary. If the application later supports multiple clinics/tenants, add an explicit tenant/clinic ownership model and change RLS to enforce it; do not rely on the current global-staff policy for multi-tenant isolation.

### Rate limiting

RLS does **not** provide rate limiting. Direct PostgREST traffic from an authenticated client is still subject to whatever API/service limits are configured by the backend. If abuse protection beyond those limits is required, put the affected operation behind a server-side Edge Function/API and enforce rate limits there (Redis is an optional backend component, not an Android cache).

## Server-side write abuse protection

The security baseline includes a database-backed write rate limit for authenticated client mutations. The default limit is **600 INSERT/UPDATE/DELETE operations per authenticated user per UTC minute** across the protected business tables. Trusted service-role operations bypass this client limit.

This is a database safety net, not a substitute for an API gateway/WAF or a distributed Redis limiter. It protects direct PostgREST mutations too, whereas an Android-only limiter could be bypassed. If traffic grows to multiple API instances or substantially higher volume, add a distributed edge/API rate limiter (Redis is appropriate there) and keep the database trigger as a final guard.

The rate-limit table is not client-readable/writable. Old counters should be periodically cleaned with `public.cleanup_write_rate_limits()` using a trusted scheduler.

## Database constraints

RLS answers **who may perform an operation**; database constraints answer **whether the data is valid**. Before adding CHECK constraints to production, compare them against the live schema and existing data. The repository does not contain the complete historical Supabase schema, so this project intentionally does not invent column-level constraints that may not match the deployed database.
