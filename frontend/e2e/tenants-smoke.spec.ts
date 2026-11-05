import { expect, test, type Page } from "@playwright/test";
import { authPath } from "./helpers";

const TENANTS = [
  { code: "EDU", name: "POSE Academy", authKey: "edu-mgr" },
  { code: "REA", name: "POSE Realty", authKey: "rea-mgr" },
  { code: "TECH", name: "POSE Tech", authKey: "tech-mgr" },
  { code: "RET", name: "POSE Retail", authKey: "ret-mgr" },
  { code: "LOG", name: "POSE Logistics", authKey: "log-mgr" },
  { code: "CLI", name: "POSE Clinic", authKey: "cli-mgr" },
] as const;

const RAW_ENUM_PATTERN = /^(ACTIVE|INACTIVE|DRAFT|CURRENT|UPCOMING|EXPIRED|REVOKED|NEW|QUALIFIED|CONVERTED|DISQUALIFIED|PROPOSAL|WON|LOST|OPEN|IN_PROGRESS|DONE|CANCELLED|PRODUCT|SERVICE|INDIVIDUAL|BUSINESS|WEBSITE|REFERRAL|EVENT|SOCIAL_MEDIA|PHONE|WALK_IN|OTHER|LOW|MEDIUM|HIGH|URGENT|ADMIN|MANAGER|SALES_REP|CUSTOMER_CARE|DATA_STAFF)$/;
const ISO_DATE_PATTERN = /\b20\d{2}-\d{2}-\d{2}(?:T\d{2}:\d{2}|\b)/;
const VN_DATE_PATTERN = /\b\d{2}\/\d{2}\/20\d{2}(?:\s+\d{2}:\d{2})?\b/;

async function verifyPageInvariants(
  page: Page,
  tenant: (typeof TENANTS)[number],
  pageLabel: string,
  options: { expectListRows?: boolean; expectDates?: boolean } = {},
) {
  await expect(page.locator(".app-sidebar, .topbar").first()).toBeVisible();
  await expect(page.locator("body")).toContainText(tenant.name);

  const main = page.locator("main");
  await expect(main).toBeVisible();
  await expect(main.locator('[role="status"]').filter({ hasText: /^Đang tải/ })).toHaveCount(0);
  await expect(main).not.toContainText("Không thể kết nối");
  await expect(main).not.toContainText("Không thể xử lý yêu cầu");

  if (options.expectListRows) {
    await expect
      .poll(() => main.locator("table tbody tr, .opportunity-card").count(), {
        message: `Expected data rows/cards on ${pageLabel} for ${tenant.code}`,
      })
      .toBeGreaterThan(0);
  }

  const badgeTexts = await main.locator(".badge, .sales-assignment-state").allInnerTexts();
  for (const text of badgeTexts) {
    const trimmed = text.trim();
    if (!trimmed) continue;
    expect(trimmed, `Raw enum in badge on ${pageLabel} (${tenant.code})`).not.toMatch(RAW_ENUM_PATTERN);
  }

  const enumPairs = await main.evaluate((root) => {
    const labels = new Set(["Trạng thái", "Loại", "Loại khách hàng", "Nguồn", "Nguồn / Loại", "Giai đoạn", "Ưu tiên", "Vai trò hiện tại"]);
    const results: Array<{ term: string; value: string }> = [];
    for (const dt of root.querySelectorAll("dt")) {
      const term = (dt.textContent ?? "").trim();
      if (!labels.has(term)) continue;
      const dd = dt.nextElementSibling;
      if (dd && dd.tagName === "DD") {
        results.push({ term, value: (dd.textContent ?? "").trim() });
      }
    }
    for (const table of root.querySelectorAll("table")) {
      const headers = Array.from(table.querySelectorAll("thead th")).map(th => (th.textContent ?? "").trim());
      headers.forEach((header, idx) => {
        if (!labels.has(header)) return;
        for (const row of table.querySelectorAll("tbody tr")) {
          const cells = row.querySelectorAll("th, td");
          const cell = cells[idx];
          if (cell) {
            results.push({ term: `table:${header}`, value: (cell.textContent ?? "").trim() });
          }
        }
      });
    }
    return results;
  });

  for (const pair of enumPairs) {
    expect(pair.value, `Raw enum in ${pair.term} on ${pageLabel} (${tenant.code})`).not.toMatch(RAW_ENUM_PATTERN);
  }

  const mainText = await main.innerText();
  expect(mainText, `Raw ISO date on ${pageLabel} (${tenant.code})`).not.toMatch(ISO_DATE_PATTERN);
  if (options.expectDates) {
    expect(mainText, `Expected dd/MM/yyyy date on ${pageLabel} (${tenant.code})`).toMatch(VN_DATE_PATTERN);
  }

  await page.setViewportSize({ width: 375, height: 812 });
  const metrics = await page.evaluate(() => {
    const docScrollWidth = document.documentElement.scrollWidth;
    const docClientWidth = document.documentElement.clientWidth;
    const offenders: string[] = [];
    if (docScrollWidth > docClientWidth) {
      for (const el of document.querySelectorAll("*")) {
        const rect = el.getBoundingClientRect();
        if (rect.right > docClientWidth + 1) {
          offenders.push(`${el.tagName.toLowerCase()}.${(el.className || "").toString().replace(/\s+/g, ".")}[right=${Math.round(rect.right)}]`);
        }
      }
    }
    return { docScrollWidth, docClientWidth, offenders: offenders.slice(0, 6) };
  });
  expect(
    metrics.docScrollWidth,
    `Horizontal overflow at 375px on ${pageLabel} (${tenant.code}): scrollWidth=${metrics.docScrollWidth} > clientWidth=${metrics.docClientWidth} (${metrics.offenders.join(", ")})`,
  ).toBeLessThanOrEqual(metrics.docClientWidth);
  await page.setViewportSize({ width: 1280, height: 800 });
}

for (const tenant of TENANTS) {
  test.describe(`Multi-tenant smoke: ${tenant.code} (${tenant.name})`, () => {
    test.use({ storageState: authPath(tenant.authKey) });

    test(`Manager visits all menu pages and detail pages in ${tenant.code} without errors or overflow`, async ({ page }) => {
      test.setTimeout(120_000);
      const serverErrors: string[] = [];
      const consoleErrors: string[] = [];
      const pageErrors: string[] = [];

      page.on("response", response => {
        if (response.status() >= 500) {
          serverErrors.push(`${response.status()} ${response.request().method()} ${response.url()}`);
        }
      });
      page.on("console", msg => {
        if (msg.type() === "error") {
          consoleErrors.push(msg.text());
        }
      });
      page.on("pageerror", err => {
        pageErrors.push(err.message);
      });

      await page.setViewportSize({ width: 1280, height: 800 });

      // 1. Overview (/app)
      await page.goto("/app");
      await expect(page.getByRole("heading", { level: 1, name: "Tổng quan" })).toBeVisible();
      await verifyPageInvariants(page, tenant, "/app");

      // 2. Offerings (/app/san-pham) + detail (/app/san-pham/[id])
      await page.goto("/app/san-pham");
      await expect(page.getByRole("heading", { level: 1, name: "Sản phẩm & dịch vụ" })).toBeVisible();
      await verifyPageInvariants(page, tenant, "/app/san-pham", { expectListRows: true });
      const offeringHref = await page.locator('main table tbody tr a[href^="/app/san-pham/"]').first().getAttribute("href");
      expect(offeringHref).toBeTruthy();
      await page.goto(offeringHref!);
      await expect(page.locator("main h1")).toBeVisible();
      await verifyPageInvariants(page, tenant, `${offeringHref} (Offering detail)`, { expectDates: true });

      // 3. Sales Assignments (/app/phan-cong)
      await page.goto("/app/phan-cong");
      await expect(page.getByRole("heading", { level: 1, name: "Phân công sản phẩm" })).toBeVisible();
      await verifyPageInvariants(page, tenant, "/app/phan-cong", { expectListRows: true, expectDates: true });

      // 4. Leads (/app/lead) + detail (/app/lead/[id])
      await page.goto("/app/lead");
      await expect(page.getByRole("heading", { level: 1, name: "Lead" })).toBeVisible();
      await verifyPageInvariants(page, tenant, "/app/lead", { expectListRows: true });
      const leadHref = await page.locator('main table tbody tr a[href^="/app/lead/"]').first().getAttribute("href");
      expect(leadHref).toBeTruthy();
      await page.goto(leadHref!);
      await expect(page.locator("main h1")).toBeVisible();
      await verifyPageInvariants(page, tenant, `${leadHref} (Lead detail)`, { expectDates: true });

      // 5. Customers (/app/khach-hang) + detail (/app/khach-hang/[id])
      await page.goto("/app/khach-hang");
      await expect(page.getByRole("heading", { level: 1, name: "Khách hàng" })).toBeVisible();
      await verifyPageInvariants(page, tenant, "/app/khach-hang", { expectListRows: true });
      const firstCustomerLink = page.locator('main table tbody tr a[href^="/app/khach-hang/"]').first();
      const customerHref = await firstCustomerLink.getAttribute("href");
      const customerName = (await firstCustomerLink.innerText()).trim();
      expect(customerHref).toBeTruthy();
      const customerId = customerHref!.replace("/app/khach-hang/", "");
      await page.goto(customerHref!);
      await expect(page.locator("main h1")).toBeVisible();
      await verifyPageInvariants(page, tenant, `${customerHref} (Customer detail)`, { expectDates: true });

      // 6. Opportunities (/app/co-hoi) + detail (/app/co-hoi/[id])
      await page.goto("/app/co-hoi");
      await expect(page.getByRole("heading", { level: 1, name: "Cơ hội bán hàng" })).toBeVisible();
      await verifyPageInvariants(page, tenant, "/app/co-hoi", { expectListRows: true, expectDates: true });
      const opportunityHref = await page.locator('main .opportunity-card a[href^="/app/co-hoi/"]').first().getAttribute("href");
      expect(opportunityHref).toBeTruthy();
      await page.goto(opportunityHref!);
      await expect(page.locator("main h1")).toBeVisible();
      await verifyPageInvariants(page, tenant, `${opportunityHref} (Opportunity detail)`, { expectDates: true });

      // 7. Interactions (/app/tuong-tac) + detail (/app/tuong-tac/[id])
      await page.goto("/app/tuong-tac");
      await expect(page.getByRole("heading", { level: 1, name: "Tương tác" })).toBeVisible();
      await verifyPageInvariants(page, tenant, "/app/tuong-tac", { expectListRows: true, expectDates: true });
      const interactionHref = await page.locator('main table tbody tr a[href^="/app/tuong-tac/"]').first().getAttribute("href");
      expect(interactionHref).toBeTruthy();
      await page.goto(interactionHref!);
      await expect(page.locator("main h1")).toBeVisible();
      await verifyPageInvariants(page, tenant, `${interactionHref} (Interaction detail)`, { expectDates: true });

      // 8. Tasks (/app/nhiem-vu) + detail (/app/nhiem-vu/[id])
      await page.goto("/app/nhiem-vu");
      await expect(page.getByRole("heading", { level: 1, name: "Nhiệm vụ" })).toBeVisible();
      await verifyPageInvariants(page, tenant, "/app/nhiem-vu", { expectListRows: true, expectDates: true });
      const taskHref = await page.locator('main table tbody tr a[href^="/app/nhiem-vu/"]').first().getAttribute("href");
      expect(taskHref).toBeTruthy();
      await page.goto(taskHref!);
      await expect(page.locator("main h1")).toBeVisible();
      await verifyPageInvariants(page, tenant, `${taskHref} (Task detail)`, { expectDates: true });

      // 9. Customer 360 Hub (/app/khach-hang-360) + detail (/app/khach-hang-360/[id])
      await page.goto("/app/khach-hang-360");
      await expect(page.getByRole("heading", { level: 1, name: "Hồ sơ khách hàng 360" })).toBeVisible();
      await expect(page.locator("#c360-search")).toBeVisible();
      await verifyPageInvariants(page, tenant, "/app/khach-hang-360", { expectListRows: true });

      await page.goto(`/app/khach-hang-360/${customerId}`);
      await expect(page.getByRole("heading", { level: 1, name: customerName })).toBeVisible();
      await verifyPageInvariants(page, tenant, `/app/khach-hang-360/${customerId} (Customer 360 detail)`, { expectDates: true });

      expect(serverErrors, `5xx responses in ${tenant.code}`).toEqual([]);
      expect(consoleErrors, `console.error in ${tenant.code}`).toEqual([]);
      expect(pageErrors, `pageerror in ${tenant.code}`).toEqual([]);
    });
  });
}

