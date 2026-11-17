import { describe, it, expect } from "vitest";
import { env, SELF } from "cloudflare:test";

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

async function tokenFor(sub: string, claims: Record<string, unknown> = {}): Promise<string> {
  return signHs256(
    { sub, aud: "authenticated", exp: Math.floor(Date.now() / 1000) + 3600, ...claims },
    (env as any).SUPABASE_JWT_SECRET
  );
}

describe("Empirical Challenger: Streaming & Chunked Transfer", () => {
  it("streams body in multiple async chunks directly into R2", async () => {
    const token = await tokenFor("challenger-stream-user");
    const chunk1 = new TextEncoder().encode("chunk-1-data-");
    const chunk2 = new TextEncoder().encode("chunk-2-data-");
    const chunk3 = new TextEncoder().encode("chunk-3-data-final");
    const expectedTotal = chunk1.byteLength + chunk2.byteLength + chunk3.byteLength;

    const { readable, writable } = new FixedLengthStream(expectedTotal);
    const writer = writable.getWriter();
    (async () => {
      await writer.write(chunk1);
      await new Promise((r) => setTimeout(r, 20));
      await writer.write(chunk2);
      await new Promise((r) => setTimeout(r, 20));
      await writer.write(chunk3);
      await writer.close();
    })();

    const res = await SELF.fetch("https://worker.example/backups/stream-multi-chunk-1", {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/octet-stream"
      },
      body: readable
    });

    expect(res.status).toBe(200);
    const body = (await res.json()) as { backupId: string; sizeBytes: number };
    expect(body.backupId).toBe("stream-multi-chunk-1");
    expect(body.sizeBytes).toBe(expectedTotal);

    const head = await (env as any).BACKUP_BUCKET.head("backups/challenger-stream-user/stream-multi-chunk-1/backup.nccb");
    expect(head).not.toBeNull();
    expect(head?.size).toBe(expectedTotal);

    const stored = await (env as any).BACKUP_BUCKET.get("backups/challenger-stream-user/stream-multi-chunk-1/backup.nccb");
    const storedText = await stored.text();
    expect(storedText).toBe("chunk-1-data-chunk-2-data-chunk-3-data-final");
  });
});

describe("Empirical Challenger: Empty Stream Edge Cases", () => {
  it("rejects empty chunked stream (no Content-Length header, 0 bytes) and cleans up R2", async () => {
    const token = await tokenFor("challenger-empty-user");
    const { readable, writable } = new FixedLengthStream(0);
    writable.close();

    const res = await SELF.fetch("https://worker.example/backups/empty-no-cl-1", {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/octet-stream"
      },
      body: readable
    });

    expect(res.status).toBe(400);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe("Empty body");

    // Verify object was deleted from R2
    const head = await (env as any).BACKUP_BUCKET.head("backups/challenger-empty-user/empty-no-cl-1/backup.nccb");
    expect(head).toBeNull();
  });

  it("rejects request with null/missing body with 400", async () => {
    const token = await tokenFor("challenger-null-body-user");
    const res = await SELF.fetch("https://worker.example/backups/null-body-backup-1", {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/octet-stream"
      }
    });

    expect(res.status).toBe(400);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe("Empty body");
  });
});

describe("Empirical Challenger: Size Limits (MAX_BACKUP_BYTES)", () => {
  const MAX_BACKUP_BYTES = 200 * 1024 * 1024;

  it("rejects declared Content-Length exceeding MAX_BACKUP_BYTES with 413", async () => {
    const token = await tokenFor("challenger-oversized-user");
    const res = await SELF.fetch("https://worker.example/backups/oversized-declared-1", {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/octet-stream",
        "Content-Length": String(MAX_BACKUP_BYTES + 1)
      },
      body: new Uint8Array([1, 2, 3])
    });

    expect(res.status).toBe(413);
    const body = (await res.json()) as { error: string };
    expect(body.error).toContain("Backup exceeds maximum allowed size");

    // Verify nothing written to R2
    const head = await (env as any).BACKUP_BUCKET.head("backups/challenger-oversized-user/oversized-declared-1/backup.nccb");
    expect(head).toBeNull();
  });

  it("post-stream size check deletes R2 object and returns 413 if stream exceeds MAX_BACKUP_BYTES", async () => {
    // We challenge the post-stream check:
    // If a client sends chunked transfer (no Content-Length or small Content-Length)
    // but the stream exceeds MAX_BACKUP_BYTES, the worker must delete the object from R2 and return 413.
    // In index.ts lines 196-199:
    // if (object.size > MAX_BACKUP_BYTES) {
    //   await env.BACKUP_BUCKET.delete(key);
    //   return json({ error: `Backup exceeds maximum allowed size of ${MAX_BACKUP_BYTES} bytes` }, 413);
    // }
    // We can simulate streaming exceeding MAX_BACKUP_BYTES by mocking BACKUP_BUCKET.put or streaming.
    // Let's test streaming with mock or actual stream.
    const token = await tokenFor("challenger-oversized-stream");
    
    // Test that the logic holds when R2 returns object.size > MAX_BACKUP_BYTES
    const originalPut = (env as any).BACKUP_BUCKET.put;
    try {
      (env as any).BACKUP_BUCKET.put = async (key: string, body: any, options: any) => {
        // Return an R2Object with size > MAX_BACKUP_BYTES
        return {
          key,
          size: MAX_BACKUP_BYTES + 500,
          etag: "mock-oversized-etag",
          version: "1"
        };
      };

      const res = await SELF.fetch("https://worker.example/backups/stream-oversized-1", {
        method: "PUT",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/octet-stream"
        },
        body: new Uint8Array([1, 2, 3])
      });

      expect(res.status).toBe(413);
      const resBody = (await res.json()) as { error: string };
      expect(resBody.error).toContain("Backup exceeds maximum allowed size");
    } finally {
      (env as any).BACKUP_BUCKET.put = originalPut;
    }
  });
});

describe("Empirical Challenger: Malformed Headers & Tokens", () => {
  it("rejects token missing Bearer prefix with 401", async () => {
    const res = await SELF.fetch("https://worker.example/backups", {
      headers: { Authorization: "Basic dXNlcjpwYXNz" }
    });
    expect(res.status).toBe(401);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe("Missing bearer token");
  });

  it("rejects token with invalid segment count (< 3 or > 3) with 401", async () => {
    const res1 = await SELF.fetch("https://worker.example/backups", {
      headers: { Authorization: "Bearer header.payload" }
    });
    expect(res1.status).toBe(401);
    expect(((await res1.json()) as any).error).toBe("Malformed token");

    const res2 = await SELF.fetch("https://worker.example/backups", {
      headers: { Authorization: "Bearer a.b.c.d" }
    });
    expect(res2.status).toBe(401);
    expect(((await res2.json()) as any).error).toBe("Malformed token");
  });

  it("rejects token with corrupted base64 or invalid json with 401", async () => {
    const res = await SELF.fetch("https://worker.example/backups", {
      headers: { Authorization: "Bearer not_json.not_json.fake_sig" }
    });
    expect(res.status).toBe(401);
  });

  it("rejects token signed with wrong secret with 401", async () => {
    const forgedToken = await signHs256(
      { sub: "attacker", aud: "authenticated", exp: Math.floor(Date.now() / 1000) + 3600 },
      "wrong-secret-key-1234567890"
    );
    const res = await SELF.fetch("https://worker.example/backups", {
      headers: { Authorization: `Bearer ${forgedToken}` }
    });
    expect(res.status).toBe(401);
    expect(((await res.json()) as any).error).toBe("Invalid signature");
  });

  it("rejects token with unexpected audience with 401", async () => {
    const badAudToken = await signHs256(
      { sub: "user-1", aud: "anon", exp: Math.floor(Date.now() / 1000) + 3600 },
      (env as any).SUPABASE_JWT_SECRET
    );
    const res = await SELF.fetch("https://worker.example/backups", {
      headers: { Authorization: `Bearer ${badAudToken}` }
    });
    expect(res.status).toBe(401);
    expect(((await res.json()) as any).error).toBe("Unexpected audience");
  });

  it("rejects token with missing subject (sub) with 401", async () => {
    const noSubToken = await signHs256(
      { aud: "authenticated", exp: Math.floor(Date.now() / 1000) + 3600 },
      (env as any).SUPABASE_JWT_SECRET
    );
    const res = await SELF.fetch("https://worker.example/backups", {
      headers: { Authorization: `Bearer ${noSubToken}` }
    });
    expect(res.status).toBe(401);
    expect(((await res.json()) as any).error).toBe("Token missing subject");
  });
});

describe("Empirical Challenger: Malformed IDs and Path Traversal", () => {
  it("rejects path traversal attempts in backupId with 400 or 404", async () => {
    const token = await tokenFor("user-path-test");
    const badIds = [
      "../../etc/passwd",
      "..%2F..%2F",
      "short",
      "a".repeat(101),
      "has spaces here",
      "semi;colon"
    ];

    for (const badId of badIds) {
      const res = await SELF.fetch(`https://worker.example/backups/${encodeURIComponent(badId)}`, {
        method: "PUT",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/octet-stream"
        },
        body: new Uint8Array([1, 2, 3])
      });
      // Should be 400 Invalid backup id or 404
      expect([400, 404]).toContain(res.status);
    }
  });
});

describe("Empirical Challenger: Multi-Tenant Authorization Security", () => {
  it("uses app_metadata.clinic_id and ignores client-injected user_metadata.clinic_id", async () => {
    // Attacker has user.sub = "attacker-sub"
    // Attacker puts clinic_id = "victim-clinic" in user_metadata
    // Legitimate app_metadata has clinic_id = "attacker-real-clinic"
    const token = await signHs256(
      {
        sub: "attacker-sub",
        aud: "authenticated",
        exp: Math.floor(Date.now() / 1000) + 3600,
        app_metadata: { clinic_id: "attacker-real-clinic" },
        user_metadata: { clinic_id: "victim-clinic" }
      },
      (env as any).SUPABASE_JWT_SECRET
    );

    const payload = new TextEncoder().encode("secret-payload");
    const res = await SELF.fetch("https://worker.example/backups/tenant-iso-1", {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/octet-stream"
      },
      body: payload
    });

    expect(res.status).toBe(200);

    // Verify the backup was written under attacker-real-clinic, NOT victim-clinic!
    const victimHead = await (env as any).BACKUP_BUCKET.head("backups/victim-clinic/tenant-iso-1/backup.nccb");
    expect(victimHead).toBeNull();

    const realHead = await (env as any).BACKUP_BUCKET.head("backups/attacker-real-clinic/tenant-iso-1/backup.nccb");
    expect(realHead).not.toBeNull();
    expect(realHead?.size).toBe(payload.byteLength);
  });
});
