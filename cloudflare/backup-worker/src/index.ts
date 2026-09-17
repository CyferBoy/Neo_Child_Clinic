import { verifySupabaseJwt, AuthError } from "./jwt";

export interface Env {
  BACKUP_BUCKET: R2Bucket;
  DB: D1Database;
  SUPABASE_JWT_SECRET?: string;
  SUPABASE_JWKS_URL?: string;
}

interface BackupRow {
  backup_id: string;
  account_id: string;
  storage_path: string;
  size_bytes: number;
  app_version: string;
  backup_version: number;
  created_at: string;
  confirmed_at: string;
  status: string;
}

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), { status, headers: { "Content-Type": "application/json" } });
}

function toPublicMetadata(row: BackupRow) {
  return {
    backupId: row.backup_id,
    createdAt: row.created_at,
    sizeBytes: row.size_bytes,
    appVersion: row.app_version,
    backupVersion: row.backup_version,
    status: row.status
  };
}

function objectKey(accountId: string, backupId: string): string {
  // Never derived from patient data - just the authenticated account id and a random
  // backup id the app generated, matching req. 14 ("do not use sensitive patient
  // information in filenames or object paths").
  return `backups/${accountId}/${backupId}/backup.nccb`;
}

async function getOwnedBackup(env: Env, backupId: string, accountId: string): Promise<BackupRow | null> {
  const row = await env.DB.prepare("SELECT * FROM backups WHERE backup_id = ?").bind(backupId).first<BackupRow>();
  if (!row) return null;
  // Ownership is decided from OUR OWN stored mapping, never from anything the client
  // supplied in the URL/body - this is what stops one clinic from reading another
  // clinic's backup by guessing/changing an id (req. 24).
  if (row.account_id !== accountId) return null;
  return row;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    // Browser clients send an unauthenticated CORS preflight (OPTIONS). It must be
    // answered before JWT verification; the actual backup request remains authenticated.
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, PUT, POST, DELETE, OPTIONS",
          "Access-Control-Allow-Headers": "Authorization, Content-Type",
          "Access-Control-Max-Age": "86400"
        }
      });
    }

    const url = new URL(request.url);
    const segments = url.pathname.split("/").filter(Boolean); // ["backups", ...]

    let user;
    try {
      user = await verifySupabaseJwt(request.headers.get("Authorization"), env.SUPABASE_JWT_SECRET, env.SUPABASE_JWKS_URL);
    } catch (e) {
      const message = e instanceof AuthError ? e.message : "Unauthorized";
      return json({ error: message }, 401);
    }
    const accountId = user.sub;

    if (segments[0] !== "backups") return json({ error: "Not found" }, 404);

    // POST /backups/retention  { keep: number }
    if (segments.length === 2 && segments[1] === "retention" && request.method === "POST") {
      return handleRetention(request, env, accountId);
    }

    // GET /backups
    if (segments.length === 1 && request.method === "GET") {
      const { results } = await env.DB.prepare(
        "SELECT * FROM backups WHERE account_id = ? ORDER BY created_at DESC"
      ).bind(accountId).all<BackupRow>();
      return json((results ?? []).map(toPublicMetadata));
    }

    // PUT /backups/:backupId
    if (segments.length === 2 && request.method === "PUT") {
      return handleUpload(request, env, accountId, segments[1]);
    }

    // POST /backups/:backupId/confirm
    if (segments.length === 3 && segments[2] === "confirm" && request.method === "POST") {
      return handleConfirm(request, env, accountId, segments[1]);
    }

    // GET /backups/:backupId/download
    if (segments.length === 3 && segments[2] === "download" && request.method === "GET") {
      return handleDownload(env, accountId, segments[1]);
    }

    // DELETE /backups/:backupId
    if (segments.length === 2 && request.method === "DELETE") {
      return handleDelete(env, accountId, segments[1]);
    }

    return json({ error: "Not found" }, 404);
  }
};

// Encrypted .nccb bodies are JSON-serialized clinic data plus AES-GCM overhead - 200 MB is
// a generous ceiling for even a large multi-year clinic's data, and rejecting anything
// bigger stops one authenticated account from parking unbounded storage cost on the
// Worker's R2 bucket (there is no cross-account impact either way, since objects are
// already namespaced per account, but there was no ceiling of any kind before this).
const MAX_BACKUP_BYTES = 200 * 1024 * 1024;

async function handleUpload(request: Request, env: Env, accountId: string, backupId: string): Promise<Response> {
  if (!/^[a-zA-Z0-9-]{8,100}$/.test(backupId)) return json({ error: "Invalid backup id" }, 400);

  const declaredLength = request.headers.get("Content-Length");
  if (declaredLength && Number(declaredLength) > MAX_BACKUP_BYTES) {
    return json({ error: `Backup exceeds maximum allowed size of ${MAX_BACKUP_BYTES} bytes` }, 413);
  }

  const body = await request.arrayBuffer();
  if (body.byteLength === 0) return json({ error: "Empty body" }, 400);
  if (body.byteLength > MAX_BACKUP_BYTES) {
    return json({ error: `Backup exceeds maximum allowed size of ${MAX_BACKUP_BYTES} bytes` }, 413);
  }

  const key = objectKey(accountId, backupId);
  try {
    await env.BACKUP_BUCKET.put(key, body);
  } catch (e) {
    return json({ error: "Upload failed" }, 502);
  }
  return json({ backupId, sizeBytes: body.byteLength });
}

async function handleConfirm(request: Request, env: Env, accountId: string, backupId: string): Promise<Response> {
  let confirmBody: { sizeBytes: number; appVersion: string; backupVersion: number; createdAt: string };
  try {
    confirmBody = await request.json();
  } catch {
    return json({ error: "Invalid JSON body" }, 400);
  }

  const key = objectKey(accountId, backupId);
  const head = await env.BACKUP_BUCKET.head(key);
  if (!head) return json({ error: "Uploaded object not found - upload may have failed" }, 409);
  if (head.size > MAX_BACKUP_BYTES) {
    // Should be unreachable (handleUpload already rejects an oversized body), but this is
    // the authoritative point where a backup becomes "real" (persisted to D1), so the size
    // invariant is checked again here rather than trusted from an earlier step.
    await env.BACKUP_BUCKET.delete(key);
    return json({ error: `Backup exceeds maximum allowed size of ${MAX_BACKUP_BYTES} bytes` }, 413);
  }
  if (head.size !== confirmBody.sizeBytes) {
    // The object exists but doesn't match what the client claims to have uploaded -
    // never confirm a partial/corrupted upload (req. 17: "do not download and restore a
    // corrupted/incomplete backup").
    return json({ error: "Size mismatch - upload appears incomplete" }, 409);
  }

  const now = new Date().toISOString();
  await env.DB.prepare(
    `INSERT INTO backups (backup_id, account_id, storage_path, size_bytes, app_version, backup_version, created_at, confirmed_at, status)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'CONFIRMED')
     ON CONFLICT(backup_id) DO UPDATE SET
       storage_path = excluded.storage_path, size_bytes = excluded.size_bytes,
       app_version = excluded.app_version, backup_version = excluded.backup_version,
       created_at = excluded.created_at, confirmed_at = excluded.confirmed_at, status = 'CONFIRMED'`
  ).bind(backupId, accountId, key, confirmBody.sizeBytes, confirmBody.appVersion, confirmBody.backupVersion, confirmBody.createdAt, now).run();

  const row = await getOwnedBackup(env, backupId, accountId);
  if (!row) return json({ error: "Failed to persist backup metadata" }, 500);
  return json(toPublicMetadata(row));
}

async function handleDownload(env: Env, accountId: string, backupId: string): Promise<Response> {
  const row = await getOwnedBackup(env, backupId, accountId);
  if (!row) return json({ error: "Not found" }, 404);

  const object = await env.BACKUP_BUCKET.get(row.storage_path);
  if (!object) return json({ error: "Backup object missing in storage" }, 404);

  return new Response(object.body, {
    status: 200,
    headers: { "Content-Type": "application/octet-stream", "Content-Length": String(object.size) }
  });
}

async function handleDelete(env: Env, accountId: string, backupId: string): Promise<Response> {
  const row = await getOwnedBackup(env, backupId, accountId);
  if (!row) return json({ error: "Not found" }, 404);

  await env.BACKUP_BUCKET.delete(row.storage_path);
  await env.DB.prepare("DELETE FROM backups WHERE backup_id = ?").bind(backupId).run();
  return json({ deleted: true });
}

async function handleRetention(request: Request, env: Env, accountId: string): Promise<Response> {
  let keep = 5;
  try {
    const body = await request.json<{ keep?: number }>();
    if (typeof body.keep === "number" && body.keep >= 1) keep = Math.floor(body.keep);
  } catch {
    // no body / bad body -> fall back to default of 5
  }

  const { results } = await env.DB.prepare(
    "SELECT * FROM backups WHERE account_id = ? ORDER BY created_at DESC"
  ).bind(accountId).all<BackupRow>();

  const rows = results ?? [];
  // Newest `keep` rows are always retained; never delete the newest backup even if keep=0
  // slipped through somehow (req. 20: "never delete the newest successful backup accidentally").
  const toDelete = rows.slice(Math.max(keep, 1));

  for (const row of toDelete) {
    await env.BACKUP_BUCKET.delete(row.storage_path);
    await env.DB.prepare("DELETE FROM backups WHERE backup_id = ?").bind(row.backup_id).run();
  }

  return json({ deletedCount: toDelete.length });
}
