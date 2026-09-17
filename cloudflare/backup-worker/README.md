# Neo Child Clinic - Backup Worker

Cloudflare Worker that mediates cloud backup/restore between the Android app and a private
R2 bucket. Full architecture, setup, and security notes: `../../docs/BACKUP_RESTORE.md`.

## Local setup

```bash
npm install
cp .dev.vars.example .dev.vars
npm run db:migrate:local
npm run dev
```

For local JWT verification, `SUPABASE_JWT_SECRET` supports legacy HS256 tokens. If the
Supabase project uses asymmetric signing keys, also set `SUPABASE_JWKS_URL` to:

```text
https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json
```

## Deploy

```bash
npm install
npm run db:migrate:remote
wrangler r2 bucket create neo-child-clinic-backups
wrangler secret put SUPABASE_JWT_SECRET
wrangler secret put SUPABASE_JWKS_URL
npm run deploy
```

`SUPABASE_JWKS_URL` is the preferred verification path for ES256/RS256 access tokens.
HS256 remains supported through `SUPABASE_JWT_SECRET` for backward compatibility.

Set the deployed Worker's URL as `BACKUP_WORKER_URL` in the Android app's build environment
(see the app's `.env.local` / CI secrets, alongside `SUPABASE_URL`).
