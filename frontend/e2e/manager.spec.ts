import { expect, test } from "@playwright/test";
import { authPath, firstOption, selectCustomer } from "./helpers";

test.use({ storageState: authPath("mgr") });

test("manager completes the lead to customer, opportunity and 360 flow", async ({ page }) => {
  test.setTimeout(120_000);
  const suffix = `${Date.now()}`, name = `E2E-Lead-${suffix}`;
  const opportunity = `E2E-Opportunity-${suffix}`, interaction = `E2E-Interaction-${suffix}`, task = `E2E-Task-${suffix}`;
  await page.goto("/app/lead/them");
  await page.locator("#crm-displayName").fill(name);
  await page.locator("#crm-email").fill(`e2e-${suffix}@example.test`);
  await page.locator("#crm-source").selectOption("WEBSITE");
  const offeringId = await firstOption(page, "#crm-offeringId");
  const ownerId = await firstOption(page, "#crm-ownerUserId");
  await page.getByRole("button", { name: "Tạo Lead" }).click();
  await expect(page.getByRole("heading", { name })).toBeVisible();
  await page.getByRole("button", { name: "Xác nhận đủ điều kiện" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Xác nhận", exact: true }).click();
  await page.getByRole("button", { name: "Chuyển thành khách hàng" }).click();
  await page.locator("#convert-customerType").selectOption("INDIVIDUAL");
  await page.getByRole("button", { name: "Xác nhận chuyển" }).click();
  await expect(page).toHaveURL(/\/app\/khach-hang\/[0-9a-f-]+$/);
  const customerId = new URL(page.url()).pathname.split("/").pop()!;

  await page.getByRole("link", { name: "Thêm cơ hội" }).click();
  await page.locator("#opp-title").fill(opportunity);
  await page.locator("#opp-expectedCloseDate").fill("31/12/2099");
  await page.locator("fieldset").first().getByLabel("Sản phẩm/dịch vụ *").selectOption(offeringId);
  await expect(page.locator("#opp-ownerUserId option[value='" + ownerId + "']")).toHaveCount(1);
  await page.locator("#opp-ownerUserId").selectOption(ownerId);
  await page.getByRole("button", { name: "Lưu cơ hội" }).click();
  await expect(page.getByRole("heading", { name: opportunity })).toBeVisible();
  await page.getByRole("link", { name: "Chỉnh sửa" }).click();
  await page.locator("#opp-stage").selectOption("QUALIFIED");
  await page.getByRole("button", { name: "Lưu cơ hội" }).click();
  await expect(page.getByRole("heading", { name: opportunity })).toBeVisible();
  await page.getByRole("button", { name: "Chốt thành công" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Xác nhận" }).click();
  await expect(page.getByText("Thành công", { exact: true }).first()).toBeVisible();

  await page.goto("/app/tuong-tac/them");
  await selectCustomer(page, customerId, name);
  await page.locator("#work-subject").fill(interaction);
  await page.locator("#work-content").fill(`E2E-Content-${suffix}`);
  await page.getByRole("button", { name: "Lưu tương tác" }).click();
  await expect(page.getByRole("heading", { name: interaction })).toBeVisible();

  await page.goto("/app/nhiem-vu/them");
  await selectCustomer(page, customerId, name);
  await page.locator("#work-title").fill(task);
  await page.locator("#work-dueAt").fill("31/12/2099 12:00");
  await page.locator("#work-ownerUserId").selectOption(ownerId);
  await page.getByRole("button", { name: "Lưu nhiệm vụ" }).click();
  await expect(page.getByRole("heading", { name: task })).toBeVisible();

  await page.goto(`/app/khach-hang-360/${customerId}`);
  await expect(page.getByRole("heading", { name })).toBeVisible();
  await expect(page.getByRole("region", { name: "Lead đã chuyển" }).getByRole("link", { name }).first()).toBeVisible();
  await expect(page.getByRole("region", { name: "Lead đã chuyển" })).toContainText("Tổng số: 1 Lead");
  await expect(page.getByRole("region", { name: "Cơ hội" }).getByText(opportunity).first()).toBeVisible();
  await expect(page.getByRole("region", { name: "Tương tác" }).getByText(interaction).first()).toBeVisible();
  await expect(page.getByRole("region", { name: "Tương tác" })).toContainText("Tổng số: 1 tương tác");
  await expect(page.getByRole("region", { name: "Nhiệm vụ" }).getByText(task).first()).toBeVisible();
  const profile = await page.request.get(`/api/backend/customers/${customerId}/360`);
  expect(profile.status()).toBe(200);
  const counts = await profile.json() as { leads: { count: number }; opportunities: { stages: { WON: number } }; interactions: { count: number }; tasks: { statuses: { OPEN: number } } };
  expect(counts.leads.count).toBe(1); expect(counts.opportunities.stages.WON).toBe(1);
  expect(counts.interactions.count).toBe(1); expect(counts.tasks.statuses.OPEN).toBe(1);
  const metrics = page.getByRole("region", { name: "Chỉ số tổng hợp" });
  await expect(metrics).toContainText("Thành công: 1 cơ hội");
  await expect(metrics).toContainText("Chưa bắt đầu: 1 nhiệm vụ");
});
