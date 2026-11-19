/**
 * Verifies Supabase access tokens.
 *
 * Supports both:
 *  - Supabase asymmetric signing keys (ES256 / RS256) via JWKS - preferred
 *  - Legacy HS256 via SUPABASE_JWT_SECRET, only when explicitly allowed
 *
 * Every token must additionally carry an allowlisted `app_metadata.role` and the
 * exact expected issuer (`${SUPABASE_URL}/auth/v1`), checked AFTER signature
 * verification so nothing in an unverified payload is trusted.
 */

export interface VerifiedUser {
  sub: string;
  email?: string;
  role?: string;
  exp: number;
  iss?: string;
  app_metadata?: {
    role?: string;
    clinic_id?: string;
    [key: string]: unknown;
  };
}

export interface VerifyOptions {
  /** Shared secret for legacy HS256 tokens. */
  jwtSecret?: string;
  /** JWKS endpoint for ES256/RS256 tokens. When set, HS256 is rejected unless allowLegacyHs256 is true. */
  jwksUrl?: string;
  /** Exact expected `iss`, e.g. https://<project-ref>.supabase.co/auth/v1. Required (fail closed). */
  issuer?: string;
  /** Explicit opt-in to keep accepting HS256 alongside a configured JWKS. */
  allowLegacyHs256?: boolean;
}

class AuthError extends Error {}

/** A deployment/identity misconfiguration - surfaced as CONFIG, not as bad credentials. */
export class ConfigError extends Error {}

// wrangler.toml ships this placeholder so local dev/CI can boot the Worker. It is not a real
// Supabase project: an issuer built from it means the deployment was never configured.
export const PLACEHOLDER_SUPABASE_URL = "https://placeholder.supabase.co";

// Same staff-role allowlist as supabase/functions/manage-staff (validateRole) and
// 20261001_app_metadata_roles.sql. user_metadata.role never counts - it is client-writable.
const ALLOWED_BACKUP_ROLES = new Set(["admin", "doctor", "receptionist", "nurse", "inventory_manager"]);

interface JwtHeader {
  alg: string;
  kid?: string;
  typ?: string;
}

interface JwtPayload extends VerifiedUser {
  aud?: string | string[];
}

// The DOM lib's JsonWebKey omits `kid`, but RFC 7517 JWKs carry it.
interface JwksKey extends JsonWebKey {
  kid?: string;
}

interface JwkSet {
  keys: JwksKey[];
}

const jwksCache = new Map<string, { keys: JwksKey[]; expiresAt: number }>();
const JWKS_CACHE_MS = 10 * 60 * 1000;

function base64UrlToUint8Array(base64Url: string): Uint8Array {
  const padded = base64Url.replace(/-/g, "+").replace(/_/g, "/");
  const pad = padded.length % 4 === 0 ? "" : "=".repeat(4 - (padded.length % 4));
  const raw = atob(padded + pad);
  const bytes = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
  return bytes;
}

function base64UrlDecodeJson<T>(base64Url: string): T {
  const bytes = base64UrlToUint8Array(base64Url);
  const text = new TextDecoder().decode(bytes);
  return JSON.parse(text) as T;
}

async function getJwks(jwksUrl: string, forceRefresh = false): Promise<JwksKey[]> {
  const cached = jwksCache.get(jwksUrl);
  if (!forceRefresh && cached && cached.expiresAt > Date.now()) return cached.keys;

  let response = await fetch(jwksUrl, {
    headers: { Accept: "application/json" },
  });
  if (!response.ok) {
    throw new AuthError(`Unable to fetch Supabase JWKS (${response.status})`);
  }

  const body = (await response.json()) as JwkSet;
  if (!Array.isArray(body.keys) || body.keys.length === 0) {
    throw new AuthError("Supabase JWKS contains no signing keys");
  }

  jwksCache.set(jwksUrl, {
    keys: body.keys,
    expiresAt: Date.now() + JWKS_CACHE_MS,
  });
  return body.keys;
}

function cryptoAlgorithmForJwt(alg: string): any {
  if (alg === "RS256") return { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" };
  if (alg === "ES256") return { name: "ECDSA", namedCurve: "P-256" };
  throw new AuthError(`Unsupported JWT algorithm: ${alg}`);
}

function verifyAlgorithmForJwt(alg: string): any {
  if (alg === "RS256") return { name: "RSASSA-PKCS1-v1_5" };
  if (alg === "ES256") return { name: "ECDSA", hash: "SHA-256" };
  throw new AuthError(`Unsupported JWT algorithm: ${alg}`);
}

async function verifyWithJwks(
  header: JwtHeader,
  headerB64: string,
  payloadB64: string,
  signatureB64: string,
  jwksUrl: string
): Promise<void> {
  if (header.alg !== "ES256" && header.alg !== "RS256") {
    throw new AuthError(`Unsupported JWT algorithm: ${header.alg}`);
  }
  if (!header.kid) throw new AuthError("Token missing key id");

  let keys = await getJwks(jwksUrl);
  // Match on kid only. A JWK's `alg` is optional and may be absent or stale; requiring
  // equality made legitimate tokens fail after a key rotation (BE-09).
  let jwk = keys.find((key) => key.kid === header.kid);
  if (!jwk) {
    // An unknown kid is the normal signal that Supabase rotated its signing key before
    // our 10-minute cache expired: bypass the cache once, then fail closed (BE-10).
    keys = await getJwks(jwksUrl, true);
    jwk = keys.find((key) => key.kid === header.kid);
  }
  if (!jwk) throw new AuthError("JWT signing key not found");

  const importAlgorithm = cryptoAlgorithmForJwt(header.alg);
  const key = await crypto.subtle.importKey("jwk", jwk, importAlgorithm, false, ["verify"]);
  const signature = base64UrlToUint8Array(signatureB64);
  const signedData = new TextEncoder().encode(`${headerB64}.${payloadB64}`);
  const valid = await crypto.subtle.verify(
    verifyAlgorithmForJwt(header.alg),
    key,
    signature,
    signedData
  );

  if (!valid) throw new AuthError("Invalid signature");
}

async function verifyWithHs256(
  signatureB64: string,
  headerB64: string,
  payloadB64: string,
  jwtSecret: string
): Promise<void> {
  if (!jwtSecret) throw new AuthError("JWT verification is not configured");

  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(jwtSecret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["verify"]
  );

  const signature = base64UrlToUint8Array(signatureB64);
  const signedData = new TextEncoder().encode(`${headerB64}.${payloadB64}`);
  const valid = await crypto.subtle.verify("HMAC", key, signature, signedData);
  if (!valid) throw new AuthError("Invalid signature");
}

export async function verifySupabaseJwt(
  authHeader: string | null,
  options: VerifyOptions
): Promise<VerifiedUser> {
  const { jwtSecret, jwksUrl, issuer, allowLegacyHs256 } = options;
  // Fail closed: without a configured issuer there is no way to reject tokens minted for
  // another Supabase project that happen to share a signing key/secret. The placeholder is
  // truthy but is not a project - treat it as missing and fail with an explicit CONFIG error
  // instead of letting self-signed tokens through or reporting a misleading 401.
  if (!issuer || issuer.startsWith(PLACEHOLDER_SUPABASE_URL)) {
    throw new ConfigError(
      "SUPABASE_URL is not configured: replace the placeholder in wrangler.toml with a real Supabase project URL"
    );
  }
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    throw new AuthError("Missing bearer token");
  }

  const token = authHeader.slice("Bearer ".length).trim();
  const parts = token.split(".");
  if (parts.length !== 3) throw new AuthError("Malformed token");

  const [headerB64, payloadB64, signatureB64] = parts;
  const header = base64UrlDecodeJson<JwtHeader>(headerB64);
  const payload = base64UrlDecodeJson<JwtPayload>(payloadB64);

  // Verify the signature first, before reading any claim out of the payload.
  if (header.alg === "ES256" || header.alg === "RS256") {
    if (!jwksUrl) throw new AuthError("Asymmetric JWT requires JWKS configuration");
    await verifyWithJwks(header, headerB64, payloadB64, signatureB64, jwksUrl);
  } else if (header.alg === "HS256") {
    if (jwksUrl && !allowLegacyHs256) {
      throw new AuthError("HS256 is disabled while JWKS is configured");
    }
    await verifyWithHs256(signatureB64, headerB64, payloadB64, jwtSecret ?? "");
  } else {
    throw new AuthError(`Unsupported JWT algorithm: ${header.alg}`);
  }

  const nowSeconds = Math.floor(Date.now() / 1000);
  if (!payload.exp || payload.exp < nowSeconds) throw new AuthError("Token expired");
  if (payload.aud && !(
    payload.aud === "authenticated" ||
    (Array.isArray(payload.aud) && payload.aud.includes("authenticated"))
  )) {
    throw new AuthError("Unexpected audience");
  }
  if (payload.iss !== issuer) throw new AuthError("Unexpected issuer");
  if (!payload.sub) throw new AuthError("Token missing subject");

  const role = payload.app_metadata?.role;
  if (!role || !ALLOWED_BACKUP_ROLES.has(role)) throw new AuthError("Unauthorized role");

  return payload;
}

export { AuthError };
