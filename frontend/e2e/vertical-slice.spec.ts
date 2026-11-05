import { expect, test, type APIRequestContext } from "@playwright/test";
import { authPath, roleApi } from "./helpers";

test.use({ storageState: authPath("mgr") });

type Offering = { id: string; code: string; name: string; active: boolean; teamId: string | null };
type Assignment = { id: string; offeringId: string; salesUserId: string; status: string; effectiveState: string };

async function getJson<T>(api: APIRequestContext, path: string): Promise<T> {
  const response = await api.get(path);
  expect(response.status(), path).toBe(200);
  return await response.json() as T;
}

test("vertical slice flows from sales assignment to offering, lead conversion, work items and customer 360", async ({ page, browser }) => {
  test.setTimeout(120_000);
  const managerApi = await roleApi("mgr"), s01Api = await roleApi("s01"), s02Api = await roleApi("s02");
  const salesRoles: Array<{ key: "s01" | "s02"; api: APIRequestContext; id: string; fullName: string }> = [];
  let salesContext = null as Awaited<ReturnType<typeof browser.newContext>> | null;
  try {
    for (const [key, api] of [["s01", s01Api], ["s02", s02Api]] as const) {
      const me = (await getJson<{ user: { id: string; fullName: string } }>(api, "/api/session/me")).user;
      salesRoles.push({ key, api, id: me.id, fullName: me.fullName });
    }
    const offerings = (await getJson<{ items: Offering[] }>(managerApi, "/api/backend/offerings?page=0&size=100")).items.filter(item => item.active && item.teamId);
    const assignments = (await getJson<{ items: Assignment[] }>(managerApi, "/api/backend/sales-assignments?page=0&size=100")).items;

    let selected: { offering: Offering; sales: (typeof salesRoles)[number] } | null = null;
    for (const offering of offerings) {
      for (const sales of salesRoles) {
        const hasCurrent = assignments.some(item => item.offeringId === offering.id && item.salesUserId === sales.id && item.effectiveState === "CURRENT");
        if (!hasCurrent) {
          selected = { offering, sales };
          break;
        }
      }
      if (selected) break;
    }
    expect(selected, "Phải tìm được cặp (Offering, Sales) chưa có phân công CURRENT").not.toBeNull();
    const { offering, sales } = selected!;

    // 1. Manager tạo phân công đang hiệu lực cho Sales trên Offering vừa tìm được.
    await page.goto("/app/phan-cong");
    await expect(page.getByRole("heading", { name: "Phân công sản phẩm", level: 1 })).toBeVisible();
    await page.getByRole("button", { name: "Phân công sản phẩm/dịch vụ" }).click();
    const assignDialog = page.getByRole("dialog", { name: "Phân công sản phẩm/dịch vụ" });
    await expect(assignDialog).toBeVisible();
    await assignDialog.getByLabel("Sản phẩm hoặc dịch vụ").selectOption(offering.id);
    await assignDialog.getByLabel("Nhân viên kinh doanh").selectOption(sales.id);
    await assignDialog.getByLabel("Hiệu lực đến").fill("31/12/2097 23:59");
    await assignDialog.getByRole("button", { name: "Xác nhận phân công" }).click();
    await expect(assignDialog).toBeHidden();
    await expect(page.getByRole("status").filter({ hasText: "Phân công sản phẩm/dịch vụ thành công." })).toBeVisible();

    // 2. Sales thấy Offering, mở chi tiết, bấm "Tạo Lead cho sản phẩm này", tạo Lead, qualify và chuyển thành Customer.
    salesContext = await browser.newContext({ storageState: authPath(sales.key) });
    const salesPage = await salesContext.newPage();
    await salesPage.goto("/app/san-pham");
    await expect(salesPage.getByRole("link", { name: offering.name, exact: true }).first()).toBeVisible();
    await salesPage.getByRole("link", { name: offering.name, exact: true }).first().click();
    await expect(salesPage.getByRole("heading", { name: offering.name, level: 1 })).toBeVisible();

    const createLeadLink = salesPage.getByRole("link", { name: "Tạo Lead cho sản phẩm này" });
    await expect(createLeadLink).toBeVisible();
    await createLeadLink.click();
    await expect(salesPage).toHaveURL(new RegExp(`/app/lead/them\\?offeringId=${offering.id}`));
    await expect(salesPage.locator("#crm-offeringId")).toHaveValue(offering.id);

    const suffix = `${Date.now()}`;
    const leadName = `E2E-W11-Khach-${suffix}`;
    const interactionSubject = `E2E-W11-TuongTac-${suffix}`;
    const taskTitle = `E2E-W11-NhiemVu-${suffix}`;

    await salesPage.getByLabel("Tên *").fill(leadName);
    await salesPage.getByLabel("Email").fill(`w11.${suffix}@example.test`);
    await salesPage.getByLabel("Nguồn *").selectOption("WEBSITE");
    if (await salesPage.locator("#crm-ownerUserId").count() > 0) {
      await salesPage.locator("#crm-ownerUserId").selectOption(sales.id);
    }
    await salesPage.getByRole("button", { name: "Tạo Lead" }).click();
    await expect(salesPage.getByRole("heading", { name: leadName, level: 1 })).toBeVisible();

    await salesPage.getByRole("button", { name: "Xác nhận đủ điều kiện" }).click();
    const qualifyDialog = salesPage.getByRole("dialog", { name: "Xác nhận đủ điều kiện" });
    await qualifyDialog.getByRole("button", { name: "Xác nhận" }).click();
    await expect(qualifyDialog).toBeHidden();
    await expect(salesPage.getByRole("button", { name: "Chuyển thành khách hàng" })).toBeVisible();

    await salesPage.getByRole("button", { name: "Chuyển thành khách hàng" }).click();
    const convertDialog = salesPage.getByRole("dialog", { name: "Chuyển thành khách hàng" });
    await convertDialog.getByLabel("Loại khách hàng *").selectOption("INDIVIDUAL");
    await convertDialog.getByRole("button", { name: "Xác nhận chuyển" }).click();
    await expect(salesPage).toHaveURL(/\/app\/khach-hang\/[0-9a-f-]{36}$/i);
    await expect(salesPage.getByRole("heading", { name: leadName, level: 1 })).toBeVisible();
    const customerUrl = salesPage.url();
    const customerId = customerUrl.split("/").pop()!;

    // 3. Từ trang Customer, bấm "Thêm tương tác" và "Thêm nhiệm vụ"; form có sẵn khách hàng.
    await salesPage.getByRole("link", { name: "Thêm tương tác" }).click();
    await expect(salesPage).toHaveURL(`/app/tuong-tac/them?customerId=${customerId}`);
    await expect(salesPage.locator("#work-subject-picker")).toHaveValue(customerId);
    await salesPage.locator("#work-subject").fill(interactionSubject);
    await salesPage.locator("#work-content").fill(`Nội dung tương tác ${suffix}`);
    await salesPage.getByRole("button", { name: "Lưu tương tác" }).click();
    await expect(salesPage.getByRole("heading", { name: interactionSubject, level: 1 })).toBeVisible();
    await expect(salesPage.getByRole("link", { name: "Xem hồ sơ 360" })).toHaveAttribute("href", `/app/khach-hang-360/${customerId}`);

    // Quay về trang Customer qua link tên đối tượng ở chi tiết Tương tác, sau đó bấm "Thêm nhiệm vụ".
    await salesPage.getByRole("link", { name: leadName }).click();
    await expect(salesPage).toHaveURL(`/app/khach-hang/${customerId}`);
    await salesPage.getByRole("link", { name: "Thêm nhiệm vụ" }).click();
    await expect(salesPage).toHaveURL(`/app/nhiem-vu/them?customerId=${customerId}`);
    await expect(salesPage.locator("#work-subject-picker")).toHaveValue(customerId);
    await salesPage.locator("#work-title").fill(taskTitle);
    await salesPage.locator("#work-dueAt").fill("31/12/2099 12:00");
    if (await salesPage.locator("#work-ownerUserId").count() > 0) {
      await salesPage.locator("#work-ownerUserId").selectOption(sales.id);
    }
    await salesPage.getByRole("button", { name: "Lưu nhiệm vụ" }).click();
    await expect(salesPage.getByRole("heading", { name: taskTitle, level: 1 })).toBeVisible();

    // 4. Từ trang chi tiết Nhiệm vụ, bấm link sang hồ sơ 360; hồ sơ hiện Lead đã chuyển, Tương tác, Nhiệm vụ đúng số lượng.
    const view360Link = salesPage.getByRole("link", { name: "Xem hồ sơ 360" });
    await expect(view360Link).toHaveAttribute("href", `/app/khach-hang-360/${customerId}`);
    await view360Link.click();
    await expect(salesPage).toHaveURL(`/app/khach-hang-360/${customerId}`);
    await expect(salesPage.getByRole("heading", { name: leadName, level: 1 })).toBeVisible();
    await expect(salesPage.getByText("Tổng số: 1 Lead")).toBeVisible();
    await expect(salesPage.getByText(/Tổng số: 1 tương tác/)).toBeVisible();
    await expect(salesPage.getByText(/Chưa bắt đầu: 1 nhiệm vụ/)).toBeVisible();
    await expect(salesPage.getByRole("link", { name: interactionSubject }).first()).toBeVisible();
    await expect(salesPage.getByRole("link", { name: taskTitle }).first()).toBeVisible();

    // 5. Manager mở cùng hồ sơ 360 thấy đủ số liệu.
    await page.goto(`/app/khach-hang-360/${customerId}`);
    await expect(page.getByRole("heading", { name: leadName, level: 1 })).toBeVisible();
    await expect(page.getByText("Tổng số: 1 Lead")).toBeVisible();
    await expect(page.getByText(/Tổng số: 1 tương tác/)).toBeVisible();
    await expect(page.getByText(/Chưa bắt đầu: 1 nhiệm vụ/)).toBeVisible();
    await expect(page.getByRole("link", { name: interactionSubject }).first()).toBeVisible();
    await expect(page.getByRole("link", { name: taskTitle }).first()).toBeVisible();
  } finally {
    await Promise.all([managerApi.dispose(), s01Api.dispose(), s02Api.dispose(), ...(salesContext ? [salesContext.close()] : [])]);
  }
});

