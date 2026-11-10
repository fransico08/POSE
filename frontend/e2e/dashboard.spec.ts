import { expect, test } from "@playwright/test";
import { authPath, roleApi } from "./helpers";

type Metric = { metricId: string; dimensions: Record<string, string>; value: number | null; numerator: number | null; denominator: number | null; availability: string };
const start = "2026-08-31T17:00:00.000Z", end = "2026-09-30T17:00:00.000Z";
const dateParams = new URLSearchParams({ start, end, asOf: end, timezone: "Asia/Ho_Chi_Minh" });
function item(metrics: Metric[], code: string) { return metrics.find(metric => metric.metricId.startsWith(code))!; }

test.describe("dashboard", () => {
  test.use({ storageState: authPath("admin") });
  test("admin compares September metrics, filters, URL and keyboard", async ({ page }) => {
    const admin = await roleApi("admin");
    try {
      await page.goto("/app/bao-cao");
      await page.getByLabel("Khoảng thời gian").selectOption("custom");
      await page.getByLabel("Từ ngày").fill("01/09/2026");
      await page.getByLabel("Đến ngày").fill("30/09/2026");
      const responsePromise = page.waitForResponse(response => response.url().includes("/dashboard/kpis") && response.url().includes("2026-08-31") && response.ok());
      await page.getByRole("button", { name: "Áp dụng" }).click();
      await responsePromise;
      const response = await admin.get(`/api/backend/dashboard/kpis?${dateParams}`);
      expect(response.status()).toBe(200);
      const data = await response.json() as { metrics: Metric[] };
      for (const code of ["A01", "A03", "A04", "B01", "B05"]) {
        const value = item(data.metrics, code);
        const text = value.value === null ? "Chưa đủ dữ liệu để tính" : value.metricId.includes("RATE") || code === "A04" || code === "B05" ? `${(value.value * 100).toLocaleString("vi-VN", { maximumFractionDigits: 2 })}%` : value.value.toLocaleString("vi-VN");
        await expect(page.locator(`[data-metric="${code}"]`)).toContainText(text);
      }
      expect(item(data.metrics, "A01").value).toBe(10);
      expect(item(data.metrics, "A03").value).toBeCloseTo(0.3);
      expect(item(data.metrics, "A04").value).toBeCloseTo(0.1);
      expect(item(data.metrics, "B01").value).toBe(8);
      const responded = data.metrics.find(metric => metric.metricId.startsWith("A05") && metric.dimensions.kind === "RESPONDED")!;
      expect([responded.numerator, responded.denominator]).toEqual([6, 10]);
      expect(responded.value).toBe(194400);
      await expect(page.locator('[data-metric="A05"]')).toContainText("6/10");
      await expect(page.locator('[data-metric="A05"]')).toContainText("2 ngày");
      const engagement = data.metrics.filter(metric => metric.metricId.startsWith("A06"));
      expect(engagement.find(metric => metric.dimensions.segment === "ACTIVE")?.value).toBe(12);
      expect(engagement.find(metric => metric.dimensions.segment === "NEVER_ENGAGED")?.value).toBe(8);
      await expect(page.locator('[data-metric="A06"]')).toContainText("12");
      await expect(page.locator('[data-metric="A06"]')).toContainText("8");
      for (const code of ["D01", "D02"]) {
        expect(data.metrics.filter(metric => metric.metricId.startsWith(code)).reduce((sum, metric) => sum + (metric.value ?? 0), 0)).toBe(26);
        const table = page.locator(`[data-metric="${code}"] tbody tr`);
        await expect(table).toHaveCount(data.metrics.filter(metric => metric.metricId.startsWith(code)).length);
        const values = await table.locator("td:last-child").allTextContents();
        expect(values.reduce((sum, value) => sum + Number(value.replaceAll(".", "")), 0)).toBe(26);
      }
      // Regression: multi-series charts once rendered only an X axis because the built-in legend
      // consumed the plot height; bars must exist and the X axis must sit inside the 240px plot.
      for (const [code, bars] of [["A02", 4], ["A06", 2], ["B03", 9], ["D01", 5]] as const) {
        const chart = page.locator(`[data-metric="${code}"] .dashboard-chart`);
        await expect(chart.locator(".recharts-rectangle")).toHaveCount(bars);
        const axisY = Number(await chart.locator(".recharts-xAxis .recharts-cartesian-axis-line").getAttribute("y1"));
        expect(axisY).toBeGreaterThan(0);
        expect(axisY).toBeLessThanOrEqual(240);
      }
      await page.reload();
      await expect(page.getByLabel("Từ ngày")).toHaveValue("01/09/2026");
      const owner = page.getByLabel("Người phụ trách");
      const usersResponse = await admin.get("/api/backend/users?page=0&size=100");
      expect(usersResponse.ok()).toBeTruthy();
      const users = await usersResponse.json() as { items: { id: string; roles: string[] }[] };
      const sales = users.items.find(user => user.roles.includes("SALES_REP"))!;
      const option = owner.locator(`option[value="${sales.id}"]`);
      await expect(option).toHaveCount(1);
      await owner.selectOption((await option.getAttribute("value"))!);
      await expect(page.getByRole("region", { name: "Workflow", exact: true }).getByText("Không lọc được theo người phụ trách")).toBeVisible();
      const filteredParams = new URLSearchParams(dateParams); filteredParams.set("ownerUserId", sales.id);
      const filteredResponse = await admin.get(`/api/backend/dashboard/kpis?${filteredParams}`);
      expect(filteredResponse.status()).toBe(200);
      const filtered = await filteredResponse.json() as { metrics: Metric[] };
      expect(item(filtered.metrics, "A01").value).not.toBe(item(data.metrics, "A01").value);
      await expect(page.locator('[data-metric="A01"]')).toContainText(String(item(filtered.metrics, "A01").value));
      await page.getByRole("button", { name: "Xóa bộ lọc" }).click();
      await page.getByLabel("Đến ngày").fill("01/08/2026");
      await page.getByRole("button", { name: "Áp dụng" }).click();
      await expect(page.locator("#dashboard-date-error")).toContainText("Khoảng ngày không hợp lệ");
      await page.getByLabel("Từ ngày").focus();
      await expect(page.getByLabel("Từ ngày")).toBeFocused();
      await page.keyboard.press("Tab");
      await expect(page.getByRole("button", { name: "Mở lịch chọn ngày" }).first()).toBeFocused();
    } finally { await admin.dispose(); }
  });
});

for (const role of ["care", "data", "s01"] as const) {
  test.describe(`dashboard ${role}`, () => {
    test.use({ storageState: authPath(role), viewport: { width: 375, height: 812 } });
    test("renders scoped results without page overflow", async ({ page }) => {
      await page.goto("/app/bao-cao");
      await expect(page.getByRole("heading", { name: "Nhiệm vụ", exact: true })).toBeVisible();
      if (role === "care") {
        await expect(page.locator('[data-metric="B01"]')).toContainText("3");
        await expect(page.getByText("Không áp dụng cho vai trò của bạn").first()).toBeVisible();
      }
      if (role === "data") {
        await expect(page.getByRole("heading", { name: "Workflow", exact: true })).toBeVisible();
        await expect(page.locator('[data-metric="A01"]')).toContainText("10");
        const api = await roleApi("data");
        try {
          const response = await api.get(`/api/backend/dashboard/kpis?${dateParams}`);
          expect(response.status()).toBe(200);
          const data = await response.json() as { metrics: Metric[] };
          expect(new Set(data.metrics.map(metric => metric.metricId)).size).toBe(21);
          expect(JSON.stringify(data)).not.toMatch(/"content"|"description"|"subject"/);
        } finally { await api.dispose(); }
      }
      if (role === "s01") await expect(page.getByLabel("Người phụ trách")).toHaveCount(0);
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
    });
  });
}
