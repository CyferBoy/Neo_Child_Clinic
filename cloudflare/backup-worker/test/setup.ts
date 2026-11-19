import { beforeAll } from "vitest";
import { env } from "cloudflare:test";
import schema from "../schema.sql?raw";
import { TEST_SUPABASE_URL } from "./config";

// Miniflare hands the test run a D1 database with the `backups` binding wired up but no
// tables in it - `d1Databases: ["DB"]` fixes the *binding*, not the *schema*, so every
// query in the spec died with "no such table: backups". schema.sql is only wired into the
// two `wrangler d1 execute` scripts, which CI never runs, so apply it here instead.
//
// Split on ";" rather than using applyD1Migrations: that helper wants a generated
// migrations/ directory, and schema.sql is a single flat file with no semicolons inside
// any literal.
beforeAll(async () => {
  // The Worker reads env.SUPABASE_URL at request time and the placeholder now fails closed.
  // Point it at a real-looking URL so the auth tests below can sign for the expected issuer.
  (env as any).SUPABASE_URL = TEST_SUPABASE_URL;
  for (const statement of schema.split(";").map((s) => s.trim()).filter(Boolean)) {
    await (env as any).DB.prepare(statement).run();
  }
});