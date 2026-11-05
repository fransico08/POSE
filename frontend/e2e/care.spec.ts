import { expect, test } from "@playwright/test";
import { authPath, customers, roleApi } from "./helpers";

test.use({ storageState: authPath("care") });

test("care cannot see a sales-only interaction and sees only own tasks", async ({ page }) => {
  const sales = await roleApi("s01"), care = await roleApi("care");
  try {
    const salesCustomers = await customers(sales), careCustomers = await customers(care);
    const customer = careCustomers.find(item => salesCustomers.some(s => s.id === item.id));
    expect(customer).toBeDefined();
    const subject = `E2E-Private-${Date.now()}`;
    const created = await sales.post("/api/backend/interactions", { headers: { Origin: "http://127.0.0.1:3011" }, data: {
      customerId: customer!.id, type: "CALL", channel: "PHONE", subject, content: `${subject}-Content`, visibility: "SALES_ONLY", occurredAt: new Date().toISOString(),
    } });
    expect(created.status()).toBe(201);
    await page.goto(`/app/khach-hang-360/${customer!.id}`);
    await expect(page.getByRole("heading", { name: customer!.displayName })).toBeVisible();
    await expect(page.getByText(subject)).toHaveCount(0);
    await expect(page.getByRole("region", { name: "Cơ hội" })).toContainText("Bạn không có quyền xem phần này");
    const me = await (await care.get("/api/session/me")).json() as { user: { id: string } };
    const taskResponse = await care.get("/api/backend/tasks?page=0&size=20");
    expect(taskResponse.status()).toBe(200);
    const tasks = (await taskResponse.json() as { items: { title: string; ownerUserId: string }[] }).items;
    expect(tasks.length).toBeGreaterThan(0);
    expect(tasks.every(item => item.ownerUserId === me.user.id)).toBe(true);
    await page.goto("/app/nhiem-vu");
    for (const item of tasks) await expect(page.getByText(item.title, { exact: true }).first()).toBeVisible();
  } finally { await Promise.all([sales.dispose(), care.dispose()]); }
});
