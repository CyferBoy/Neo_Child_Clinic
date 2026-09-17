/**
 * Verifies Supabase access tokens.
 *
 * Supports both:
 *  - Supabase asymmetric signing keys (ES256 / RS256) via JWKS
 *  - Legacy HS256 via SUPABASE_JWT_SECRET
 *
 * JWKS is preferred. Set SUPABASE_JWKS_URL to the project's
 * https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json endpoint.
 */

export interface VerifiedUser {
  sub: string;
  email?: string;
  role?: string;
  exp: number;
}

class AuthError extends Error {}

interface JwtHeader {
  alg: string;
  kid?: string;
  typ?: string;
}

interface JwtPayload extends VerifiedUser {
  aud?: string | string[];
  iss?: string;
}

interface JwkSet {
  keys: JsonWebKey[];
}

const jwksCache = new Map<string, { keys: JsonWebKey[]; expiresAt: number }>();
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

async function getJwks(jwksUrl: string): Promise<JsonWebKey[]> {
  const cached = jwksCache.get(jwksUrl);
  if (cached && cached.expiresAt > Date.now()) return cached.keys;

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

  const keys = await getJwks(jwksUrl);
  const jwk = keys.find((key) => key.kid === header.kid && key.alg === header.alg);
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
  jwtSecret: string | undefined,
  jwksUrl: string | undefined
): Promise<VerifiedUser> {
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    throw new AuthError("Missing bearer token");
  }

  const token = authHeader.slice("Bearer ".length).trim();
  const parts = token.split(".");
  if (parts.length !== 3) throw new AuthError("Malformed token");

  const [headerB64, payloadB64, signatureB64] = parts;
  const header = base64UrlDecodeJson<JwtHeader>(headerB64);
  const payload = base64UrlDecodeJson<JwtPayload>(payloadB64);

  // Prefer Supabase's asymmetric signing-key system when configured.
  // Fall back to legacy HS256 for backward compatibility.
  if (jwksUrl && (header.alg === "ES256" || header.alg === "RS256")) {
    await verifyWithJwks(header, headerB64, payloadB64, signatureB64, jwksUrl);
  } else if (header.alg === "HS256") {
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
  if (!payload.sub) throw new AuthError("Token missing subject");

  return payload;
}

export { AuthError };
