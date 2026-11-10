import { expect, test } from "@playwright/test";
import { authPath } from "./helpers";

test.describe("value and leakage for manager", () => {
  test.use({ storageState: authPath("mgr"), viewport: { width: 375, height: 812 } });
  test("shows scoped metrics and opens a priority item", async ({ page }) => {
    await page.goto("/app/bao-cao");
    await expect(page.getByRole("heading", { name: "Giá trị và rò rỉ" })).toBeVisible();
    await expect(page.getByText("Chưa hỗ trợ dữ liệu hội thoại")).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
    await page.goto("/app");
    const region = page.getByRole("region", { name: "Việc hôm nay" });
    const first = region.getByRole("link").first();
    await expect(first).toBeVisible();
    const href = await first.getAttribute("href");
    await expect(first.locator("..")).toContainText(/quá hạn|chưa|chăm sóc|Đến hạn/i);
    await first.focus(); await page.keyboard.press("Shift+Tab"); await page.keyboard.press("Tab");
    await expect(first).toBeFocused();
    expect(await first.evaluate(element => getComputedStyle(element).outlineStyle)).not.toBe("none");
    await first.click(); await expect(page).toHaveURL(new RegExp(`${href!.replaceAll("/", "\\/")}$`));
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
  });
});

test.describe("value and leakage for care", () => {
  test.use({ storageState: authPath("care"), viewport: { width: 375, height: 812 } });
  test("does not display group E", async ({ page }) => {
    await page.goto("/app/bao-cao");
    await expect(page.getByRole("heading", { name: "Nhiệm vụ", exact: true })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Giá trị và rò rỉ" })).toHaveCount(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
  });
});
