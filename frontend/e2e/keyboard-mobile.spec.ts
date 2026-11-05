import { expect, test } from "@playwright/test";
import { authPath } from "./helpers";

test.use({ storageState: authPath("mgr"), viewport: { width: 375, height: 812 } });

for (const [path, label, kind] of [
  ["/app/khach-hang", "Thêm khách hàng", "link"],
  ["/app/co-hoi", "Thêm cơ hội", "link"],
  ["/app/nhiem-vu", "Thêm nhiệm vụ", "link"],
  ["/app/khach-hang-360", "Tìm khách hàng", "input"],
] as const) {
  test(`mobile keyboard and reflow: ${path}`, async ({ page }) => {
    await page.goto(path);
    const target = kind === "link" ? page.getByRole("link", { name: label }) : page.getByLabel(label);
    await expect(target).toBeVisible();
    let reached = false;
    for (let i = 0; i < 80; i++) {
      await page.keyboard.press("Tab");
      if (await target.evaluate(node => node === document.activeElement)) { reached = true; break; }
    }
    expect(reached).toBe(true);
    const indicator = await target.evaluate(node => {
      const style = getComputedStyle(node);
      return node.matches(":focus-visible") && style.outlineStyle !== "none" && parseFloat(style.outlineWidth) >= 2;
    });
    expect(indicator).toBe(true);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}
