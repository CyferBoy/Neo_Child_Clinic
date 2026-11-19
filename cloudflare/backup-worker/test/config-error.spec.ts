import { describe, expect, it } from "vitest";
import worker from "../src/index";
import { PLACEHOLDER_SUPABASE_URL } from "../src/jwt";

// The worker reads SUPABASE_URL from its env; pass the placeholder explicitly so this test
// never depends on (or mutates) the shared test env used by the auth/ownership specs.
describe("unconfigured SUPABASE_URL", () => {
  it("returns an explicit CONFIG error instead of a generic 401", async () => {
    const res = await worker.fetch(
      new Request("https://worker.example/backups", {
        headers: { Authorization: "Bearer anything" }
      }),
      // Only the issuer is read before the ConfigError is thrown - no bindings needed.
      { SUPABASE_URL: PLACEHOLDER_SUPABASE_URL } as any
    );

    expect(res.status).toBe(500);
    const body = (await res.json()) as { code?: string; error?: string };
    expect(body.code).toBe("CONFIG");
    expect(body.error).toContain("SUPABASE_URL is not configured");
  });
});
