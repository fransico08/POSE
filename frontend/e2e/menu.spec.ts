import { expect, test } from "@playwright/test";
import { navigation } from "../src/config/navigation";
import { authPath } from "./helpers";

for (const role of ["admin", "mgr", "s01", "s02", "care", "data"]) {
  test.describe(`menu permissions: ${role}`, () => {
    test.use({ storageState: authPath(role) });
    test("shows only allowed implemented entries", async ({ page }) => {
      const me = await page.request.get("/api/session/me");
      expect(me.status()).toBe(200);
      const session = await me.json() as { user: { roles: string[]; permissions: string[] } };
      await page.goto("/app");
      const nav = page.getByRole("navigation", { name: "Điều hướng chính" }).first();
      await expect(nav).toBeVisible();
      for (const item of navigation.filter(item => item.implemented)) {
        const permitted = item.permission.length === 0 || item.permission.some(permission => session.user.permissions.includes(permission));
        const eligibleRole = !item.roles || item.roles.some(role => session.user.roles.includes(role));
        await expect(nav.getByRole("link", { name: item.label, exact: true })).toHaveCount(permitted && eligibleRole ? 1 : 0);
      }
      if (["s01", "s02", "care", "data"].includes(role)) {
        await page.goto("/app/phan-cong");
        await expect(page.getByText("Bạn chưa có quyền quản lý phân công")).toBeVisible();
      }
    });
  });
}
