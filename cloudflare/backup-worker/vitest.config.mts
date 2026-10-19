import { cloudflareTest } from "@cloudflare/vitest-plugin";
import { defineConfig } from "vitest/config";

// The vitest 4 line replaced the old `defineWorkersConfig` + `test.poolOptions.workers`
// shape with a Vite plugin exported from the package root. The previous config imported
// the "/config" subpath, which only exists up to 0.8.x, so every CI run died with
// ERR_PACKAGE_PATH_NOT_EXPORTED before a single test ran. The package has since been
// renamed from @cloudflare/vitest-pool-workers to @cloudflare/vitest-plugin.
export default defineConfig({
  plugins: [
    cloudflareTest({
      wrangler: { configPath: "./wrangler.toml" },
      miniflare: {
        // wrangler.toml interpolates ${DATABASE_ID}; it is unset in CI, which left the
        // D1 binding unusable. This fixes the binding only - the test D1 still has no
        // tables until test/setup.ts applies schema.sql.
        d1Databases: ["DB"],
      },
    }),
  ],
  test: {
    setupFiles: ["./test/setup.ts"],
  },
});
