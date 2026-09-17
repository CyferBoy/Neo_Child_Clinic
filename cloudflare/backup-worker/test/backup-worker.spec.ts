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
});
