import { expect, test, type APIRequestContext, type Locator, type Page } from "@playwright/test";
import { authPath, roleApi } from "./helpers";

// QA màn hình Phân công theo D1/D2 (docs/testing/sales-assignment-scope-integration-2026-11-02.md,
// docs/api/sales-assignment-api-v1.md mục 1): Sales nhận phân công phải thuộc team sở hữu Offering;
// khoảng hiệu lực đóng hai đầu không được giao với assignment ACTIVE chưa thu hồi; REVOKED/EXPIRED không chặn;
// Sales chỉ đọc Offering khi assignment đang có hiệu lực.
test.use({ storageState: authPath("mgr") });

const OVERLAP = "Khoảng thời gian phân công trùng với một phân công đang hoạt động.";

type Offering = { id: string; code: string; name: string; active: boolean; teamId: string | null };
type Assignment = { id: string; offeringId: string; salesUserId: string; status: string; effectiveState: string; effectiveFrom: string; effectiveTo: string | null };

async function json<T>(api: APIRequestContext, path: string): Promise<T> {
  const response = await api.get(path);
  expect(response.status(), path).toBe(200);
  return await response.json() as T;
}
async function me(api: APIRequestContext) {
  return (await json<{ user: { id: string; fullName: string } }>(api, "/api/session/me")).user;
}
async function assignmentsFor(api: APIRequestContext, offeringId: string, salesUserId: string) {
  const page = await json<{ items: Assignment[] }>(api, `/api/backend/sales-assignments?page=0&size=100&offeringId=${offeringId}`);
  return page.items.filter(item => item.salesUserId === salesUserId);
}
async function visibleOfferingIds(api: APIRequestContext) {
  return (await json<{ items: { id: string }[] }>(api, "/api/backend/offerings?page=0&size=100")).items.map(item => item.id);
}

async function openCreate(page: Page): Promise<Locator> {
  await page.getByRole("button", { name: "Phân công sản phẩm/dịch vụ" }).click();
  const dialog = page.getByRole("dialog", { name: "Phân công sản phẩm/dịch vụ" });
  await expect(dialog).toBeVisible();
  return dialog;
}
async function create(page: Page, offeringId: string, salesUserId: string, from: string | null, to: string) {
  const dialog = await openCreate(page);
  await dialog.getByLabel("Sản phẩm hoặc dịch vụ").selectOption(offeringId);
  await dialog.getByLabel("Nhân viên kinh doanh").selectOption(salesUserId);
  if (from) await dialog.getByLabel("Hiệu lực từ").fill(from);
  await dialog.getByLabel("Hiệu lực đến").fill(to);
  await dialog.getByRole("button", { name: "Xác nhận phân công" }).click();
  return dialog;
}
async function createOk(page: Page, offeringId: string, salesUserId: string, from: string | null, to: string) {
  const dialog = await create(page, offeringId, salesUserId, from, to);
  await expect(dialog).toBeHidden();
  await expect(page.getByRole("status").filter({ hasText: "Phân công sản phẩm/dịch vụ thành công." })).toBeVisible();
}
async function showOnly(page: Page, offeringId: string, salesName: string) {
  await page.goto("/app/phan-cong");
  await page.locator("#assignment-offering").selectOption(offeringId);
  await page.locator("#assignment-sales-search").fill(salesName);
}
function row(page: Page, text: string) {
  return page.getByRole("table").getByRole("row").filter({ hasText: text });
}

test("manager assignment screen enforces team candidates and non-overlapping periods", async ({ page }) => {
  test.setTimeout(120_000);
  const manager = await roleApi("mgr"), s01 = await roleApi("s01"), s02 = await roleApi("s02");
  const care = await roleApi("care"), data = await roleApi("data");
  try {
    const [sale, dual, careUser, dataUser, managerUser] = await Promise.all([me(s01), me(s02), me(care), me(data), me(manager)]);
    const offerings = (await json<{ items: Offering[] }>(manager, "/api/backend/offerings?page=0&size=100")).items;
    const databoot = offerings.find(item => item.code === "DATABOOT");
    expect(databoot, "Demo EDU phải có Offering DATABOOT").toBeDefined();

    // D1 — danh sách chọn chỉ có Offering ACTIVE có team và Sales thuộc team sở hữu Offering.
    await page.goto("/app/phan-cong");
    await expect(page.getByRole("heading", { name: "Phân công sản phẩm", level: 1 })).toBeVisible();
    const dialog = await openCreate(page);
    const offeringOptions = await dialog.getByLabel("Sản phẩm hoặc dịch vụ").locator('option:not([value=""])').evaluateAll(nodes => nodes.map(node => (node as HTMLOptionElement).value));
    expect(offeringOptions.sort()).toEqual(offerings.filter(item => item.active && item.teamId).map(item => item.id).sort());
    expect(offerings.some(item => !item.active && offeringOptions.includes(item.id))).toBe(false);
    await dialog.getByLabel("Sản phẩm hoặc dịch vụ").selectOption(databoot!.id);
    const salesSelect = dialog.getByLabel("Nhân viên kinh doanh");
    await expect(salesSelect).toBeEnabled();
    const candidates = await salesSelect.locator('option:not([value=""])').evaluateAll(nodes => nodes.map(node => (node as HTMLOptionElement).value));
    expect(candidates.sort()).toEqual([sale.id, dual.id].sort());
    for (const outsider of [careUser.id, dataUser.id, managerUser.id]) expect(candidates).not.toContain(outsider);
    await dialog.getByRole("button", { name: "Hủy" }).click();

    // D2 — khoảng đóng hai đầu, không cho giao nhau với assignment ACTIVE chưa thu hồi.
    const before = (await assignmentsFor(manager, databoot!.id, sale.id)).length;
    await createOk(page, databoot!.id, sale.id, "01/01/2099 00:00", "31/01/2099 23:59");

    const form = await create(page, databoot!.id, sale.id, "15/01/2099 00:00", "15/03/2099 00:00");
    await expect(form.getByText(OVERLAP)).toBeVisible();
    await form.getByRole("button", { name: "Hủy" }).click();
    expect((await assignmentsFor(manager, databoot!.id, sale.id)).length).toBe(before + 1);

    await createOk(page, databoot!.id, sale.id, "01/02/2099 00:00", "28/02/2099 23:59");
    expect((await assignmentsFor(manager, databoot!.id, sale.id)).length).toBe(before + 2);

    await showOnly(page, databoot!.id, sale.fullName);
    const january = row(page, "31/01/2099");
    await expect(january).toHaveCount(1);
    await january.getByRole("button", { name: "Gia hạn" }).click();
    let extend = page.getByRole("dialog", { name: "Gia hạn phân công" });
    await extend.getByLabel("Hiệu lực đến").fill("15/02/2099 00:00");
    await extend.getByRole("button", { name: "Lưu thời hạn" }).click();
    await expect(extend.getByText(OVERLAP)).toBeVisible();
    await extend.getByRole("button", { name: "Hủy" }).click();

    const february = row(page, "28/02/2099");
    await february.getByRole("button", { name: "Thu hồi" }).click();
    const revoke = page.getByRole("dialog", { name: "Thu hồi phân công?" });
    await expect(revoke.getByRole("button", { name: "Thu hồi phân công" })).toBeEnabled();
    await revoke.getByRole("button", { name: "Thu hồi phân công" }).click();
    await expect(revoke).toBeHidden();
    await expect(page.getByRole("status").filter({ hasText: "Thu hồi phân công thành công." })).toBeVisible();

    // REVOKED không chặn: gia hạn tháng 1 sang tháng 2 giờ hợp lệ.
    await showOnly(page, databoot!.id, sale.fullName);
    await row(page, "31/01/2099").getByRole("button", { name: "Gia hạn" }).click();
    extend = page.getByRole("dialog", { name: "Gia hạn phân công" });
    await extend.getByLabel("Hiệu lực đến").fill("15/02/2099 00:00");
    await extend.getByRole("button", { name: "Lưu thời hạn" }).click();
    await expect(extend).toBeHidden();
    await expect(page.getByRole("status").filter({ hasText: "Gia hạn phân công thành công." })).toBeVisible();
    const future = (await assignmentsFor(manager, databoot!.id, sale.id)).filter(item => new Date(item.effectiveFrom).getUTCFullYear() >= 2098);
    const revoked = future.filter(item => item.status === "REVOKED"), active = future.filter(item => item.status === "ACTIVE");
    expect(revoked).toHaveLength(1);
    expect(active).toHaveLength(1);
    expect(new Date(active[0].effectiveTo!).getTime()).toBeGreaterThan(new Date(revoked[0].effectiveFrom).getTime());
  } finally { await Promise.all([manager.dispose(), s01.dispose(), s02.dispose(), care.dispose(), data.dispose()]); }
});

test("sales sees an offering only while the assignment is current", async ({ page, browser }) => {
  test.setTimeout(120_000);
  const manager = await roleApi("mgr"), s02 = await roleApi("s02");
  const salesContext = await browser.newContext({ storageState: authPath("s02") });
  try {
    const dual = await me(s02);
    const offerings = (await json<{ items: Offering[] }>(manager, "/api/backend/offerings?page=0&size=100")).items;
    const ielts = offerings.find(item => item.code === "IELTS65");
    expect(ielts, "Demo EDU phải có Offering IELTS65").toBeDefined();
    const existing = await assignmentsFor(manager, ielts!.id, dual.id);
    expect(existing.every(item => item.status !== "ACTIVE"), "s02 chỉ có phân công IELTS65 đã thu hồi trong dữ liệu demo").toBe(true);
    expect(await visibleOfferingIds(s02)).not.toContain(ielts!.id);

    // Assignment tương lai (UPCOMING) chưa cho Sales đọc Offering.
    await page.goto("/app/phan-cong");
    await createOk(page, ielts!.id, dual.id, "01/01/2099 00:00", "31/12/2099 23:59");
    expect(await visibleOfferingIds(s02)).not.toContain(ielts!.id);

    // Assignment đang hiệu lực (mặc định bắt đầu từ thời điểm hiện tại) mở quyền đọc.
    await createOk(page, ielts!.id, dual.id, null, "31/12/2098 23:59");
    expect(await visibleOfferingIds(s02)).toContain(ielts!.id);
    const salesPage = await salesContext.newPage();
    await salesPage.goto("/app/san-pham");
    await expect(salesPage.getByText(ielts!.name, { exact: true }).first()).toBeVisible();

    // Thu hồi assignment hiện tại thì Sales không còn đọc được Offering.
    await showOnly(page, ielts!.id, dual.fullName);
    await row(page, "31/12/2098").getByRole("button", { name: "Thu hồi" }).click();
    const revoke = page.getByRole("dialog", { name: "Thu hồi phân công?" });
    await expect(revoke.getByRole("button", { name: "Thu hồi phân công" })).toBeEnabled();
    await revoke.getByRole("button", { name: "Thu hồi phân công" }).click();
    await expect(revoke).toBeHidden();
    await expect(page.getByRole("status").filter({ hasText: "Thu hồi phân công thành công." })).toBeVisible();
    expect(await visibleOfferingIds(s02)).not.toContain(ielts!.id);
    const detail = await s02.get(`/api/backend/offerings/${ielts!.id}`);
    expect(detail.status()).toBe(403);
    await salesPage.goto("/app/san-pham");
    await expect(salesPage.getByRole("heading", { level: 1 })).toBeVisible();
    await expect(salesPage.getByText(ielts!.name, { exact: true })).toHaveCount(0);
  } finally { await Promise.all([manager.dispose(), s02.dispose(), salesContext.close()]); }
});
