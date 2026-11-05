import { expect, test } from "@playwright/test";
import { authPath, customers, roleApi, selectCustomer } from "./helpers";

test.use({ storageState: authPath("s01") });

test("sales sees assigned offerings, creates work and cannot open an out-of-scope profile", async ({ page }) => {
  const admin = await roleApi("admin"), manager = await roleApi("mgr"), sales = await roleApi("s01");
  try {
    const own = await customers(sales), all = await customers(admin);
    expect(own.length).toBeGreaterThan(0);
    const outside = all.find(customer => !own.some(item => item.id === customer.id));
    expect(outside).toBeDefined();
    const me = await (await sales.get("/api/session/me")).json() as { user: { id: string } };
    const assignmentsResponse = await manager.get("/api/backend/sales-assignments?page=0&size=100");
    expect(assignmentsResponse.status()).toBe(200);
    const assignments = (await assignmentsResponse.json() as { items: { salesUserId: string; offeringId: string; effectiveState: string }[] }).items;
    const allowed = new Set(assignments.filter(item => item.salesUserId === me.user.id && item.effectiveState === "CURRENT").map(item => item.offeringId));
    const offeringResponse = await sales.get("/api/backend/offerings?page=0&size=100");
    expect(offeringResponse.status()).toBe(200);
    const offerings = (await offeringResponse.json() as { items: { id: string; name: string }[] }).items;
    expect(offerings.length).toBeGreaterThan(0);
    expect(offerings.every(item => allowed.has(item.id))).toBe(true);
    await page.goto("/app/san-pham");
    for (const offering of offerings) await expect(page.getByText(offering.name, { exact: true }).first()).toBeVisible();

    const suffix = `${Date.now()}`;
    await page.goto("/app/tuong-tac/them");
    await selectCustomer(page, own[0].id, own[0].displayName);
    await page.locator("#work-subject").fill(`E2E-Sales-Interaction-${suffix}`);
    await page.locator("#work-content").fill(`E2E-Sales-Content-${suffix}`);
    await page.locator("#work-visibility").selectOption("SALES_ONLY");
    await page.getByRole("button", { name: "Lưu tương tác" }).click();
    await expect(page.getByRole("heading", { name: `E2E-Sales-Interaction-${suffix}` })).toBeVisible();
    await page.goto("/app/nhiem-vu/them");
    await selectCustomer(page, own[0].id, own[0].displayName);
    await page.locator("#work-title").fill(`E2E-Sales-Task-${suffix}`);
    await page.locator("#work-dueAt").fill("31/12/2099 12:00");
    await page.getByRole("button", { name: "Lưu nhiệm vụ" }).click();
    await expect(page.getByRole("heading", { name: `E2E-Sales-Task-${suffix}` })).toBeVisible();
    const denial = page.waitForResponse(response => response.url().includes(`/customers/${outside!.id}/360`));
    await page.goto(`/app/khach-hang-360/${outside!.id}`);
    expect((await denial).status()).toBe(403);
    await expect(page.getByText("Bạn không có quyền truy cập hồ sơ này trong phạm vi hiện tại.")).toBeVisible();
    await expect(page.getByRole("link", { name: "Quay lại tra cứu khách hàng 360" })).toBeVisible();
  } finally { await Promise.all([admin.dispose(), manager.dispose(), sales.dispose()]); }
});
