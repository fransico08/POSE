import { expect, request, type APIRequestContext, type Page } from "@playwright/test";

export function authPath(role: string): string {
  const run = process.env.POSE_E2E_RUN_ID ?? "missing";
  if (!/^pose_e2e_\d+_\d+$/.test(run) || !/^(admin|mgr|s01|s02|care|data)$/.test(role)) throw new Error("Missing isolated E2E run ID.");
  return `e2e/.auth/${run}/${role}.json`;
}

export async function roleApi(role: string): Promise<APIRequestContext> {
  return request.newContext({ baseURL: "http://127.0.0.1:3011", storageState: authPath(role) });
}

export async function firstOption(page: Page, selector: string): Promise<string> {
  const option = page.locator(selector).locator('option:not([value=""])').first();
  await expect(option).toHaveCount(1);
  const value = await option.getAttribute("value");
  if (!value) throw new Error(`No selectable option for ${selector}`);
  await page.locator(selector).selectOption(value);
  return value;
}

export async function selectCustomer(page: Page, id: string, name: string) {
  await page.locator("#work-search").fill(name);
  const option = page.locator("#work-subject-picker").locator("option").filter({ hasText: name });
  await expect(option).toHaveCount(1);
  await page.locator("#work-subject-picker").selectOption(id);
}

export async function customers(api: APIRequestContext): Promise<{ id: string; displayName: string }[]> {
  const response = await api.get("/api/backend/customers?page=0&size=100");
  expect(response.status()).toBe(200);
  return (await response.json() as { items: { id: string; displayName: string }[] }).items;
}
