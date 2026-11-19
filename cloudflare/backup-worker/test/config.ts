// wrangler.toml's SUPABASE_URL is a placeholder that now fails closed with a CONFIG error.
// Tests that sign tokens must pass a real-looking project URL explicitly instead; setup.ts
// points the Worker's env at the same value so both sides agree on the expected issuer.
export const TEST_SUPABASE_URL = "https://neochild-test-project.supabase.co";
export const TEST_ISSUER = `${TEST_SUPABASE_URL}/auth/v1`;
