import { describe, it, expect } from "vitest";
import { env, SELF } from "cloudflare:test";

// These tests need SUPABASE_JWT_SECRET available in the test environment (put it in a
// .dev.vars file next to wrangler.toml, e.g. `SUPABASE_JWT_SECRET=test-secret-value`) and
// require `npm install` to fetch @cloudflare/vitest-pool-workers etc. - not runnable in an
// offline sandbox, included here as the intended verification for CI.

async function signHs256(payload: Record<string, unknown>, secret: string): Promise<string> {
  const base64Url = (bytes: Uint8Array) =>
    btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  const header = { alg: "HS256", typ: "JWT" };
  const encoder = new TextEncoder();
  const headerB64 = base64Url(encoder.encode(JSON.stringify(header)));
  const payloadB64 = base64Url(encoder.encode(JSON.stringify(payload)));
  const key = await crypto.subtle.importKey("raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("HMAC", key, encoder.encode(`${headerB64}.${payloadB64}`));
  const sigB64 = base64Url(new Uint8Array(signature));
  return `${headerB64}.${payloadB64}.${sigB64}`;
}

async function tokenFor(sub: string): Promise<string> {
  return signHs256(
    { sub, aud: "authenticated", exp: Math.floor(Date.now() / 1000) + 3600 },
    (env as any).SUPABASE_JWT_SECRET
  );
}

describe("auth", () => {
  it("rejects requests with no Authorization header", async () => {
    const res = await SELF.fetch("https://worker.example/backups");
    expect(res.status).toBe(401);
  });

  it("rejects an expired token", async () => {
    const expired = await signHs256({ sub: "user-1", exp: Math.floor(Date.now() / 1000) - 10 }, (env as any).SUPABASE_JWT_SECRET);
    const res = await SELF.fetch("https://worker.example/backups", {
      headers: { Authorization: `Bearer ${expired}` }
    });
    expect(res.status).toBe(401);
  });

  it("accepts a validly signed, unexpired token", async () => {
    const token = await tokenFor("user-1");
    const res = await SELF.fetch("https://worker.example/backups", {
      headers: { Authorization: `Bearer ${token}` }
    });
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual([]);
  });
});

describe("ownership isolation", () => {
  it("does not let one account download or delete another account's backup", async () => {
    const tokenA = await tokenFor("clinic-a");
    const tokenB = await tokenFor("clinic-b");

    const payload = new TextEncoder().encode("fake-encrypted-backup-bytes");
    const put = await SELF.fetch("https://worker.example/backups/test-backup-1", {
      method: "PUT",
      headers: { Authorization: `Bearer ${tokenA}`, "Content-Type": "application/octet-stream" },
      body: payload
    });
    expect(put.status).toBe(200);

    const confirm = await SELF.fetch("https://worker.example/backups/test-backup-1/confirm", {
      method: "POST",
      headers: { Authorization: `Bearer ${tokenA}`, "Content-Type": "application/json" },
      body: JSON.stringify({ sizeBytes: payload.byteLength, appVersion: "1.0", backupVersion: 1, createdAt: new Date().toISOString() })
    });
    expect(confirm.status).toBe(200);

    // Clinic B must not be able to read or delete clinic A's backup by guessing its id.
    const downloadAsB = await SELF.fetch("https://worker.example/backups/test-backup-1/download", {
      headers: { Authorization: `Bearer ${tokenB}` }
    });
    expect(downloadAsB.status).toBe(404);

    const deleteAsB = await SELF.fetch("https://worker.example/backups/test-backup-1", {
      method: "DELETE",
      headers: { Authorization: `Bearer ${tokenB}` }
    });
    expect(deleteAsB.status).toBe(404);

    // Clinic A can read its own backup back out.
    const downloadAsA = await SELF.fetch("https://worker.example/backups/test-backup-1/download", {
      headers: { Authorization: `Bearer ${tokenA}` }
    });
    expect(downloadAsA.status).toBe(200);
  });

  it("does not let one account re-confirm (adopt) another account's backup id", async () => {
    const tokenA = await tokenFor("clinic-a");
    const tokenB = await tokenFor("clinic-b");

    const payloadA = new TextEncoder().encode("clinic-a-encrypted-backup-bytes");
    await SELF.fetch("https://worker.example/backups/confirm-cross-1", {
      method: "PUT",
      headers: { Authorization: `Bearer ${tokenA}`, "Content-Type": "application/octet-stream" },
      body: payloadA
    });
    const confirmA = await SELF.fetch("https://worker.example/backups/confirm-cross-1/confirm", {
      method: "POST",
      headers: { Authorization: `Bearer ${tokenA}`, "Content-Type": "application/json" },
      body: JSON.stringify({ sizeBytes: payloadA.byteLength, appVersion: "1.0", backupVersion: 1, createdAt: new Date().toISOString() })
    });
    expect(confirmA.status).toBe(200);

    // backup_id is the global primary key, so the confirm upsert must never adopt a row
    // owned by someone else - otherwise clinic B could point clinic A's row at clinic
    // B's object. B gets the same 404 as download/delete (no existence leak).
    const payloadB = new TextEncoder().encode("clinic-b-encrypted-backup-bytes-longer");
    await SELF.fetch("https://worker.example/backups/confirm-cross-1", {
      method: "PUT",
      headers: { Authorization: `Bearer ${tokenB}`, "Content-Type": "application/octet-stream" },
      body: payloadB
    });
    const confirmAsB = await SELF.fetch("https://worker.example/backups/confirm-cross-1/confirm", {
      method: "POST",
      headers: { Authorization: `Bearer ${tokenB}`, "Content-Type": "application/json" },
      body: JSON.stringify({ sizeBytes: payloadB.byteLength, appVersion: "1.0", backupVersion: 1, createdAt: new Date().toISOString() })
    });
    expect(confirmAsB.status).toBe(404);

    // Clinic A's row still resolves to clinic A's object.
    const downloadAsA = await SELF.fetch("https://worker.example/backups/confirm-cross-1/download", {
      headers: { Authorization: `Bearer ${tokenA}` }
    });
    expect(downloadAsA.status).toBe(200);
    expect(await downloadAsA.text()).toBe("clinic-a-encrypted-backup-bytes");
  });
});

describe("upload immutability", () => {
  it("rejects a PUT to an already-CONFIRMED backup id with 409 and keeps the original bytes", async () => {
    const token = await tokenFor("clinic-immutable");
    const original = new TextEncoder().encode("original-confirmed-backup-bytes");

    const put1 = await SELF.fetch("https://worker.example/backups/immutable-1", {
      method: "PUT",
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/octet-stream" },
      body: original
    });
    expect(put1.status).toBe(200);

    const confirm = await SELF.fetch("https://worker.example/backups/immutable-1/confirm", {
      method: "POST",
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify({ sizeBytes: original.byteLength, appVersion: "1.0", backupVersion: 1, createdAt: new Date().toISOString() })
    });
    expect(confirm.status).toBe(200);

    // R2 versioning is off: putting over a confirmed object would destroy the only copy
    // while D1 still reports the old size. Must be rejected before any put happens.
    const put2 = await SELF.fetch("https://worker.example/backups/immutable-1", {
      method: "PUT",
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/octet-stream" },
      body: new TextEncoder().encode("overwrite-attempt-bytes")
    });
    expect(put2.status).toBe(409);

    // The confirmed object still serves the original bytes.
    const download = await SELF.fetch("https://worker.example/backups/immutable-1/download", {
      headers: { Authorization: `Bearer ${token}` }
    });
    expect(download.status).toBe(200);
    expect(await download.text()).toBe("original-confirmed-backup-bytes");
  });
});

describe("retention ordering", () => {
  it("keeps the most recently CONFIRMED backup, not the one with the newest client timestamp", async () => {
    const token = await tokenFor("clinic-retention");

    const uploadAndConfirm = async (backupId: string, createdAt: string) => {
      const payload = new TextEncoder().encode(`bytes-for-${backupId}`);
      const put = await SELF.fetch(`https://worker.example/backups/${backupId}`, {
        method: "PUT",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/octet-stream" },
        body: payload
      });
      expect(put.status).toBe(200);
      const confirm = await SELF.fetch(`https://worker.example/backups/${backupId}/confirm`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ sizeBytes: payload.byteLength, appVersion: "1.0", backupVersion: 1, createdAt })
      });
      expect(confirm.status).toBe(200);
    };

    // Confirmed FIRST (older server confirmed_at) but with a device clock set far ahead...
    await uploadAndConfirm("retention-client-old-1", "2099-01-01T00:00:00.000Z");
    // ...then confirmed SECOND, with the device clock set far back. The sleep keeps the
    // two server confirmed_at values in different milliseconds so the assertion is stable.
    await new Promise((resolve) => setTimeout(resolve, 10));
    await uploadAndConfirm("retention-client-new-1", "2000-01-01T00:00:00.000Z");

    const retention = await SELF.fetch("https://worker.example/backups/retention", {
      method: "POST",
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify({ keep: 1 })
    });
    expect(retention.status).toBe(200);
    expect(await retention.json()).toEqual({ deletedCount: 1 });

    // keep=1 must retain the server-confirmed-newest backup; ordering by the client-supplied
    // created_at would have deleted it and kept the far-future one instead.
    const list = await SELF.fetch("https://worker.example/backups", {
      headers: { Authorization: `Bearer ${token}` }
    });
    const rows = (await list.json()) as Array<{ backupId: string }>;
    expect(rows.map((row) => row.backupId)).toEqual(["retention-client-new-1"]);
  });
});
