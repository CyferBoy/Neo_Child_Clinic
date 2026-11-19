/**
 * Empirical Adversarial Test for manage-staff Edge Function (P2-06).
 *
 * Transpiles supabase/functions/manage-staff/index.ts using TypeScript compiler
 * and tests admin self-demotion, self-disable, and self-delete protections.
 */

import fs from "node:fs";
import assert from "node:assert";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

// typescript is installed in the backup-worker workspace, not at the repo root.
const require = createRequire(new URL("../cloudflare/backup-worker/package.json", import.meta.url));
const ts = require("typescript");

const filePath = fileURLToPath(new URL("../supabase/functions/manage-staff/index.ts", import.meta.url));
const sourceCode = fs.readFileSync(filePath, "utf-8");

// Mock environment and Deno globals
globalThis.Deno = {
  env: {
    get(key) {
      if (key === "SUPABASE_URL") return "https://example.supabase.co";
      if (key === "SUPABASE_SERVICE_ROLE_KEY") return "mock-service-role-key";
      return undefined;
    }
  }
};

let registeredHandler = null;
const mockServe = (handler) => {
  registeredHandler = handler;
};

// Create a simulated Supabase client
const createMockSupabase = (callingUserId, callingUserRole) => {
  return {
    auth: {
      async getUser(token) {
        if (token === "valid-admin-token") {
          return {
            data: {
              user: {
                id: callingUserId,
                app_metadata: { role: callingUserRole }
              }
            },
            error: null
          };
        }
        return { data: { user: null }, error: new Error("Invalid token") };
      },
      admin: {
        async createUser() {
          return { data: { user: { id: "new-user-id" } }, error: null };
        },
        async deleteUser() {
          return { error: null };
        },
        async updateUserById(id, updates) {
          return { data: { id, ...updates }, error: null };
        },
        async signOut() {
          return { error: null };
        }
      },
      async resetPasswordForEmail() {
        return { error: null };
      }
    },
    from(table) {
      return {
        select(cols) {
          return {
            eq(col, val) {
              return {
                async single() {
                  if (table === "profiles") {
                    return {
                      data: {
                        id: val,
                        email: "test@example.com",
                        is_active: true,
                        is_deleted: false
                      },
                      error: null
                    };
                  }
                  return { data: null, error: null };
                }
              };
            }
          };
        },
        insert() {
          return Promise.resolve({ error: null });
        },
        update(updates) {
          return {
            eq(col, val) {
              return Promise.resolve({ error: null });
            }
          };
        }
      };
    }
  };
};

// Replace Deno imports with mocks
let codeToTranspile = sourceCode
  .replace(/import\s*\{\s*serve\s*\}\s*from\s*["'][^"']+["'];?/, "const serve = mockServe;")
  .replace(/import\s*\{\s*createClient\s*\}\s*from\s*["'][^"']+["'];?/, "const createClient = () => mockClient;");

// Transpile with TypeScript compiler
const transpiled = ts.transpileModule(codeToTranspile, {
  compilerOptions: {
    target: ts.ScriptTarget.ES2022,
    module: ts.ModuleKind.ES2022,
  }
}).outputText;

// Wrap in runner function
const runScript = new Function("mockServe", "mockClient", transpiled);

const adminId = "admin-uuid-1234";
const mockClient = createMockSupabase(adminId, "admin");
runScript(mockServe, mockClient);

assert(typeof registeredHandler === "function", "Handler must be registered with serve()");

async function callHandler(body) {
  const req = new Request("https://example.supabase.co/functions/v1/manage-staff", {
    method: "POST",
    headers: {
      "Authorization": "Bearer valid-admin-token",
      "Content-Type": "application/json"
    },
    body: JSON.stringify(body)
  });
  const res = await registeredHandler(req);
  const data = await res.json();
  return { status: res.status, data };
}

async function runTests() {
  console.log("Running manage-staff Admin Self-Demotion & Self-Disable Tests...\n");

  // Test 1: Admin attempts CHANGE_ROLE on self to "doctor"
  {
    const res = await callHandler({
      staffId: adminId,
      action: "CHANGE_ROLE",
      role: "doctor"
    });
    console.log("Test 1: Admin self CHANGE_ROLE to doctor ->", res);
    assert.strictEqual(res.status, 400);
    assert.match(res.data.error, /Admins cannot modify or demote their own role/);
    console.log("✓ Test 1 PASSED: Self-demotion via CHANGE_ROLE rejected\n");
  }

  // Test 2: Admin attempts CHANGE_ROLE on self to "receptionist"
  {
    const res = await callHandler({
      staffId: adminId,
      action: "CHANGE_ROLE",
      role: "receptionist"
    });
    console.log("Test 2: Admin self CHANGE_ROLE to receptionist ->", res);
    assert.strictEqual(res.status, 400);
    assert.match(res.data.error, /Admins cannot modify or demote their own role/);
    console.log("✓ Test 2 PASSED: Self-demotion to receptionist rejected\n");
  }

  // Test 3: Admin attempts UPDATE_PROFILE on self modifying role to "doctor"
  {
    const res = await callHandler({
      staffId: adminId,
      action: "UPDATE_PROFILE",
      name: "Admin Name",
      role: "doctor"
    });
    console.log("Test 3: Admin self UPDATE_PROFILE with role doctor ->", res);
    assert.strictEqual(res.status, 400);
    assert.match(res.data.error, /Admins cannot modify or demote their own role/);
    console.log("✓ Test 3 PASSED: Self-demotion via UPDATE_PROFILE rejected\n");
  }

  // Test 4: Admin attempts SET_STATUS (disable) on self
  {
    const res = await callHandler({
      staffId: adminId,
      action: "SET_STATUS",
      isActive: false
    });
    console.log("Test 4: Admin self SET_STATUS (disable) ->", res);
    assert.strictEqual(res.status, 400);
    assert.match(res.data.error, /An administrator cannot disable or delete their own account/);
    console.log("✓ Test 4 PASSED: Self-disable rejected\n");
  }

  // Test 5: Admin attempts SOFT_DELETE on self
  {
    const res = await callHandler({
      staffId: adminId,
      action: "SOFT_DELETE"
    });
    console.log("Test 5: Admin self SOFT_DELETE ->", res);
    assert.strictEqual(res.status, 400);
    assert.match(res.data.error, /An administrator cannot disable or delete their own account/);
    console.log("✓ Test 5 PASSED: Self-delete rejected\n");
  }

  // Test 6: Admin changes role of ANOTHER staff member to "doctor" (allowed)
  {
    const otherStaffId = "staff-uuid-5678";
    const res = await callHandler({
      staffId: otherStaffId,
      action: "CHANGE_ROLE",
      role: "doctor"
    });
    console.log("Test 6: Admin modifies other staff role ->", res);
    assert.strictEqual(res.status, 200);
    assert.strictEqual(res.data.success, true);
    console.log("✓ Test 6 PASSED: Modifying other staff role succeeded\n");
  }

  // Test 7: Admin updates own profile details WITHOUT modifying role (allowed)
  {
    const res = await callHandler({
      staffId: adminId,
      action: "UPDATE_PROFILE",
      name: "New Admin Display Name",
      phoneNumber: "+15551234567"
    });
    console.log("Test 7: Admin updates own profile name/phone ->", res);
    assert.strictEqual(res.status, 200);
    assert.strictEqual(res.data.success, true);
    console.log("✓ Test 7 PASSED: Updating own profile without role change succeeded\n");
  }

  console.log("ALL 7 MANAGE-STAFF ADVERSARIAL TESTS PASSED EMPIRICALLY!");
}

runTests().catch((err) => {
  console.error("Test failed:", err);
  process.exit(1);
});
