import { expect, test } from "@playwright/test";
import { authPath, customers, roleApi } from "./helpers";

test.use({ storageState: authPath("data") });

test("data staff uses reconciliation and receives a redacted 360 profile", async ({ page }) => {
  const admin = await roleApi("admin");
  try {
    const list = await customers(admin);
    expect(list.length).toBeGreaterThan(0);
    const originalResponse = await admin.get(`/api/backend/customers/${list[0].id}/360`);
    expect(originalResponse.status()).toBe(200);
    const original = await originalResponse.json() as { customer: { profile: { email: string | null; phone: string | null; address: string | null; notes: string | null } }; interactions: { items: { subject: string | null; content: string | null }[] } };
    await page.goto("/app/khach-hang-360");
    await expect(page.getByRole("heading", { name: "Đối soát dữ liệu" })).toBeVisible();
    await expect(page.getByLabel("Tìm khách hàng")).toHaveCount(0);
    await page.goto(`/app/khach-hang-360/${list[0].id}`);
    await expect(page.getByRole("heading", { name: list[0].displayName })).toBeVisible();
    const identity = page.getByRole("region", { name: "Định danh và phân công" });
    for (const label of ["Email", "Điện thoại", "Địa chỉ", "Ghi chú"]) {
      await expect(identity.locator("div").filter({ has: page.getByText(label, { exact: true }) }).first()).toContainText("Ẩn theo quyền");
    }
    const { email, phone, address, notes } = original.customer.profile;
    for (const value of [email, phone, address, notes]) if (value) await expect(identity).not.toContainText(value);
    const interactions = page.getByRole("region", { name: "Tương tác" });
    for (const item of original.interactions.items) {
      if (item.subject) await expect(interactions).not.toContainText(item.subject);
      if (item.content) await expect(interactions).not.toContainText(item.content);
    }
    if (original.interactions.items.length) await expect(interactions.getByText("Nội dung bị ẩn theo quyền").first()).toBeVisible();
  } finally { await admin.dispose(); }
});
