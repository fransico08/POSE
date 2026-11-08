import { expect, test, type APIRequestContext } from "@playwright/test";
import { authPath, roleApi } from "./helpers";

test.use({ storageState: authPath("mgr") });

const ORIGIN = "http://127.0.0.1:3011";

type Offering = { id: string; code: string; name: string; active: boolean; teamId: string | null };
type Candidate = { id: string; fullName: string; email: string };

async function getJson<T>(api: APIRequestContext, path: string): Promise<T> {
  const response = await api.get(path);
  expect(response.status(), `GET ${path}`).toBe(200);
  return (await response.json()) as T;
}

async function postJson<T>(api: APIRequestContext, path: string, data: object, expectedStatus = 200): Promise<T> {
  const response = await api.post(path, {
    headers: { Origin: ORIGIN },
    data,
  });
  expect(response.status(), `POST ${path}`).toBe(expectedStatus);
  return (await response.json()) as T;
}

test("manager reassigns customer owner, generates handover brief with interactive source chips, and enforces new owner and Data Staff boundaries", async ({
  page,
  browser,
}) => {
  test.setTimeout(120_000);
  const managerApi = await roleApi("mgr");
  const s01Api = await roleApi("s01");
  const s02Api = await roleApi("s02");
  const tempAssignmentIds: string[] = [];
  let s02Context = null as Awaited<ReturnType<typeof browser.newContext>> | null;
  let s01Context = null as Awaited<ReturnType<typeof browser.newContext>> | null;
  let dataContext = null as Awaited<ReturnType<typeof browser.newContext>> | null;

  try {
    const s01 = (await getJson<{ user: { id: string; fullName: string } }>(s01Api, "/api/session/me")).user;
    const s02 = (await getJson<{ user: { id: string; fullName: string } }>(s02Api, "/api/session/me")).user;

    const offerings = (await getJson<{ items: Offering[] }>(managerApi, "/api/backend/offerings?page=0&size=100")).items.filter(
      item => item.active && item.teamId,
    );
    expect(offerings.length).toBeGreaterThan(0);
    const offering = offerings[0];

    const candidates = await getJson<Candidate[]>(
      managerApi,
      `/api/backend/customers/owner-candidates?offeringId=${encodeURIComponent(offering.id)}`,
    );

    for (const sales of [s01, s02]) {
      if (!candidates.some(item => item.id === sales.id)) {
        const created = await postJson<{ id: string }>(
          managerApi,
          "/api/backend/sales-assignments",
          {
            offeringId: offering.id,
            salesUserId: sales.id,
            effectiveTo: "2098-12-31T16:59:00Z",
          },
          201,
        );
        tempAssignmentIds.push(created.id);
      }
    }

    const suffix = `${Date.now()}`;
    const customerName = `E2E-W14-Khach-${suffix}`;
    const oppTitle = `E2E-W14-CoHoi-${suffix}`;
    const interactionSubject = `E2E-W14-TuongTac-${suffix}`;
    const taskTitle = `E2E-W14-NhiemVu-${suffix}`;

    const customer = await postJson<{ id: string }>(
      managerApi,
      "/api/backend/customers",
      {
        displayName: customerName,
        email: `w14.${suffix}@example.test`,
        customerType: "BUSINESS",
        relatedOfferingId: offering.id,
        ownerUserId: s01.id,
      },
      201,
    );

    await postJson(
      managerApi,
      "/api/backend/opportunities",
      {
        customerId: customer.id,
        title: oppTitle,
        stage: "QUALIFIED",
        expectedCloseDate: "2099-12-31",
        ownerUserId: s01.id,
        lines: [{ offeringId: offering.id, quantity: "1", unitPrice: "15000000.00", discountAmount: "0.00" }],
      },
      201,
    );

    await postJson(
      s01Api,
      "/api/backend/interactions",
      {
        customerId: customer.id,
        type: "CALL",
        channel: "PHONE",
        subject: interactionSubject,
        content: "Trao đổi nhu cầu đào tạo chuyên sâu cho đội ngũ.",
        outcome: "Khách đề nghị gửi lộ trình",
        visibility: "CARE_VISIBLE",
        occurredAt: new Date().toISOString(),
      },
      201,
    );

    await postJson(
      managerApi,
      "/api/backend/tasks",
      {
        customerId: customer.id,
        ownerUserId: s01.id,
        title: taskTitle,
        priority: "HIGH",
        dueAt: "2099-12-31T10:00:00Z",
      },
      201,
    );

    // 1. Manager mở hồ sơ Customer 360, kiểm tra không tràn ngang ở 375x812, sau đó đổi người phụ trách sang s02.
    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto(`/app/khach-hang-360/${customer.id}`);
    await expect(page.getByRole("heading", { name: customerName, level: 1 })).toBeVisible();
    await expect(page.getByRole("heading", { name: "AI Copilot", level: 2 })).toBeVisible();

    const overflowMobile = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    );
    expect(overflowMobile).toBe(false);

    await page.setViewportSize({ width: 1280, height: 800 });
    await page.getByRole("button", { name: "Đổi người phụ trách" }).click();
    const dialog = page.getByRole("dialog", { name: "Đổi người phụ trách" });
    await expect(dialog).toBeVisible();

    await dialog.getByLabel("Người phụ trách mới *").selectOption(s02.id);
    await dialog.getByLabel("Lý do bàn giao *").fill("Điều phối bàn giao khách hàng sang chuyên viên phụ trách mới");
    await dialog.getByRole("button", { name: "Xác nhận đổi người phụ trách" }).click();
    await expect(dialog).toBeHidden();

    const banner = page
      .getByRole("status")
      .filter({ hasText: `Đã đổi người phụ trách sang ${s02.fullName}. Đã chuyển 1 cơ hội đang mở và 1 nhiệm vụ chưa hoàn thành.` });
    await expect(banner).toBeVisible();

    // 2. Bấm "Soạn hồ sơ bàn giao" trên thông báo -> nhận bản bàn giao có chip nguồn -> bấm chip nguồn nhảy tới mục tương ứng -> bấm "Chấp nhận".
    await banner.getByRole("button", { name: "Soạn hồ sơ bàn giao" }).click();

    const copilotCard = page.getByRole("article", { name: "Gợi ý AI Copilot" });
    await expect(copilotCard).toBeVisible();
    await expect(copilotCard.getByRole("heading", { name: "Hồ sơ bàn giao", level: 3 })).toBeVisible();
    await expect(copilotCard.getByText("Đề xuất")).toBeVisible();

    const interactionChip = copilotCard.getByRole("button", { name: "Nguồn: Tương tác" }).first();
    await expect(interactionChip).toBeVisible();
    await interactionChip.click();

    const activeSourceAttr = await page.evaluate(
      () => document.activeElement?.getAttribute("data-c360-source") ?? "",
    );
    expect(activeSourceAttr.startsWith("INTERACTION:")).toBe(true);

    await copilotCard.getByRole("button", { name: "Chấp nhận" }).click();
    await expect(copilotCard.getByText("Đã chấp nhận")).toBeVisible();

    // 3. Sales mới (s02) mở được hồ sơ 360 và thấy Cơ hội/Task đã chuyển.
    s02Context = await browser.newContext({ storageState: authPath("s02") });
    const s02Page = await s02Context.newPage();
    await s02Page.goto(`/app/khach-hang-360/${customer.id}`);
    await expect(s02Page.getByRole("heading", { name: customerName, level: 1 })).toBeVisible();
    await expect(s02Page.getByRole("link", { name: oppTitle }).first()).toBeVisible();
    await expect(s02Page.getByRole("link", { name: taskTitle }).first()).toBeVisible();
    await expect(s02Page.getByRole("heading", { name: "AI Copilot", level: 2 })).toBeVisible();

    // 4. Sales cũ (s01) mất quyền đọc hồ sơ Customer đó.
    s01Context = await browser.newContext({ storageState: authPath("s01") });
    const s01Page = await s01Context.newPage();
    await s01Page.goto(`/app/khach-hang-360/${customer.id}`);
    await expect(
      s01Page.getByText("Bạn không có quyền truy cập hồ sơ này trong phạm vi hiện tại."),
    ).toBeVisible();

    // 5. Data Staff không thấy khối AI Copilot và không thấy nút Đổi người phụ trách.
    dataContext = await browser.newContext({ storageState: authPath("data") });
    const dataPage = await dataContext.newPage();
    await dataPage.goto(`/app/khach-hang-360/${customer.id}`);
    await expect(dataPage.getByRole("heading", { name: customerName, level: 1 })).toBeVisible();
    await expect(dataPage.getByRole("heading", { name: "AI Copilot" })).toHaveCount(0);
    await expect(dataPage.getByRole("button", { name: "Đổi người phụ trách" })).toHaveCount(0);
  } finally {
    for (const assignmentId of tempAssignmentIds) {
      await managerApi
        .post(`/api/backend/sales-assignments/${encodeURIComponent(assignmentId)}/revoke`, {
          headers: { Origin: ORIGIN },
          data: { reason: "Dọn phân công tạm sau E2E W14" },
        })
        .catch(() => undefined);
    }
    await Promise.all([
      managerApi.dispose(),
      s01Api.dispose(),
      s02Api.dispose(),
      ...(s02Context ? [s02Context.close()] : []),
      ...(s01Context ? [s01Context.close()] : []),
      ...(dataContext ? [dataContext.close()] : []),
    ]);
  }
});
