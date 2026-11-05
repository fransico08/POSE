import { request } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import { resolve } from "node:path";
import { authPath } from "./helpers";

const users = ["admin", "mgr", "s01", "s02", "care", "data"] as const;
export default async function setup() {
  const password = process.env.POSE_E2E_PASSWORD;
  if (!password) throw new Error("Use scripts/e2e-env.ps1 to run E2E on the isolated environment.");
  const folder = resolve(authPath("admin"), "..");
  await mkdir(folder, { recursive: true });
  for (const user of users) {
    const client = await request.newContext({ baseURL: "http://127.0.0.1:3011" });
    try {
      const response = await client.post("/api/session/login", {
        headers: { Origin: "http://127.0.0.1:3011" },
        data: { organizationCode: "EDU", email: `edu.${user}@example.com`, password },
      });
      if (response.status() !== 200) throw new Error(`E2E login setup failed for role ${user} (HTTP ${response.status()}).`);
      await client.storageState({ path: resolve(authPath(user)) });
    } finally { await client.dispose(); }
  }
}
