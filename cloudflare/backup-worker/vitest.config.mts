import { cloudflareTest } from "@cloudflare/vitest-pool-workers";
import { defineConfig } from "vitest/config";

// vitest-pool-workers 0.23 (the vitest 4 line) replaced the old
// `defineWorkersConfig` + `test.poolOptions.workers` shape with a Vite plugin. The previous
// config imported "@cloudflare/vitest-pool-workers/config", a subpath that only exists up to
// 0.8.x, so every CI run died with ERR_PACKAGE_PATH_NOT_EXPORTED before a single test ran.
export default defineConfig({
  plugins: [
    cloudflareTest({
      wrangler: { configPath: "./wrangler.toml" },
      miniflare: {
        // wrangler.toml interpolates ${DATABASE_ID}; it is unset in CI, which left the
        // D1 binding unusable and every test hitting "no such table: backups".
        d1Databases: ["DB"],
      },
    }),
  ],
});
