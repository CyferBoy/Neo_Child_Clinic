import { describe, it, expect, vi, afterEach } from "vitest";
import { verifySupabaseJwt, ConfigError } from "../src/jwt";

// Pure unit tests: no Worker bindings needed. They pin the claim checks and the JWKS
// kid-matching/refresh behaviour independently of the HTTP layer.

const ISSUER = "https://issuer.test/auth/v1";
const encoder = new TextEncoder();

const b64u = (bytes: Uint8Array) =>
  btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

async function signHs256(payload: Record<string, unknown>): Promise<string> {
  const headerB64 = b64u(encoder.encode(JSON.stringify({ alg: "HS256", typ: "JWT" })));
  const payloadB64 = b64u(encoder.encode(JSON.stringify(payload)));
  const key = await crypto.subtle.importKey("raw", encoder.encode("unit-test-secret"), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("HMAC", key, encoder.encode(`${headerB64}.${payloadB64}`));
  return `${headerB64}.${payloadB64}.${b64u(new Uint8Array(signature))}`;
}

async function makeRsaKey(kid: string) {
  const pair = (await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"]
  )) as CryptoKeyPair;
  const jwk = (await crypto.subtle.exportKey("jwk", pair.publicKey)) as JsonWebKey & { kid?: string };
  // JWK `alg` is optional - the old code required it to equal the JWT alg, which broke
  // legitimate rotations. Deliberately delete it so kid-only matching is exercised.
  delete jwk.alg;
  jwk.kid = kid;
  return { pair, jwk };
}

async function signRs256(payload: Record<string, unknown>, pair: CryptoKeyPair, kid: string): Promise<string> {
  const headerB64 = b64u(encoder.encode(JSON.stringify({ alg: "RS256", typ: "JWT", kid })));
  const payloadB64 = b64u(encoder.encode(JSON.stringify(payload)));
  const signature = await crypto.subtle.sign(
    { name: "RSASSA-PKCS1-v1_5" },
    pair.privateKey,
    encoder.encode(`${headerB64}.${payloadB64}`)
  );
  return `${headerB64}.${payloadB64}.${b64u(new Uint8Array(signature))}`;
}

function claims(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    sub: "user-1",
    aud: "authenticated",
    iss: ISSUER,
    exp: Math.floor(Date.now() / 1000) + 3600,
    app_metadata: { role: "admin" },
    ...overrides
  };
}

function jwksResponse(keys: JsonWebKey[]): Response {
  return new Response(JSON.stringify({ keys }), { status: 200, headers: { "Content-Type": "application/json" } });
}

describe("verifySupabaseJwt configuration", () => {
  it("treats the placeholder issuer as unconfigured and fails with a CONFIG error", async () => {
    for (const issuer of ["https://placeholder.supabase.co", "https://placeholder.supabase.co/auth/v1"]) {
      const error = await verifySupabaseJwt("Bearer anything", { issuer }).catch((e) => e);
      expect(error).toBeInstanceOf(ConfigError);
      expect(error.message).toContain("SUPABASE_URL is not configured");
    }
  });
});

describe("verifySupabaseJwt claims", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("rejects a wrong issuer", async () => {
    const token = await signHs256(claims({ iss: "https://evil.test/auth/v1" }));
    await expect(
      verifySupabaseJwt(`Bearer ${token}`, { jwtSecret: "unit-test-secret", issuer: ISSUER })
    ).rejects.toThrow("Unexpected issuer");
  });

  it("rejects a token with no allowlisted app_metadata.role, even if user_metadata claims one", async () => {
    const token = await signHs256(claims({ app_metadata: {}, user_metadata: { role: "admin" } }));
    await expect(
      verifySupabaseJwt(`Bearer ${token}`, { jwtSecret: "unit-test-secret", issuer: ISSUER })
    ).rejects.toThrow("Unauthorized role");
  });

  it("rejects HS256 once JWKS is configured unless the legacy flag is explicitly set", async () => {
    const token = await signHs256(claims());
    const jwksUrl = "https://jwks.test/hs256-gate";
    await expect(
      verifySupabaseJwt(`Bearer ${token}`, { jwtSecret: "unit-test-secret", issuer: ISSUER, jwksUrl })
    ).rejects.toThrow("HS256 is disabled while JWKS is configured");

    await expect(
      verifySupabaseJwt(`Bearer ${token}`, { jwtSecret: "unit-test-secret", issuer: ISSUER, jwksUrl, allowLegacyHs256: true })
    ).resolves.toMatchObject({ sub: "user-1" });
  });
});

describe("verifySupabaseJwt JWKS", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("matches a signing key by kid alone when the JWK has no alg", async () => {
    const { pair, jwk } = await makeRsaKey("kid-without-alg");
    const fetchMock = vi.fn(async () => jwksResponse([jwk]));
    vi.stubGlobal("fetch", fetchMock);

    const token = await signRs256(claims(), pair, jwk.kid!);
    await expect(
      verifySupabaseJwt(`Bearer ${token}`, { issuer: ISSUER, jwksUrl: "https://jwks.test/kid-only" })
    ).resolves.toMatchObject({ sub: "user-1" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("refreshes the JWKS cache on an unknown kid and verifies with the rotated key", async () => {
    const { pair, jwk } = await makeRsaKey("rotated-kid");
    const stale = { ...jwk, kid: "old-kid" };
    let calls = 0;
    const fetchMock = vi.fn(async () => jwksResponse([calls++ === 0 ? stale : jwk]));
    vi.stubGlobal("fetch", fetchMock);

    const token = await signRs256(claims(), pair, "rotated-kid");
    await expect(
      verifySupabaseJwt(`Bearer ${token}`, { issuer: ISSUER, jwksUrl: "https://jwks.test/refresh-on-miss" })
    ).resolves.toMatchObject({ sub: "user-1" });
    expect(calls).toBe(2);
  });

  it("fails closed when the kid is still unknown after a refresh", async () => {
    const { jwk } = await makeRsaKey("some-other-kid");
    let calls = 0;
    const fetchMock = vi.fn(async () => jwksResponse([jwk]));
    vi.stubGlobal("fetch", fetchMock);
    const { pair } = await makeRsaKey("unknown-kid");
    const token = await signRs256(claims(), pair, "unknown-kid");

    await expect(
      verifySupabaseJwt(`Bearer ${token}`, { issuer: ISSUER, jwksUrl: "https://jwks.test/fail-closed" })
    ).rejects.toThrow("JWT signing key not found");
    expect(fetchMock).toHaveBeenCalledTimes(2); // cached lookup, then one forced refresh
  });
});
