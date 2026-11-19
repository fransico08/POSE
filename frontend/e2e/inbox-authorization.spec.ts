import { expect, request, test } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { authPath, roleApi } from "./helpers";

const ORIGIN = "http://127.0.0.1:3011";
const BACKEND = "http://127.0.0.1:8081";

type SessionUser = { id: string; roles: string[]; permissions: string[] };
type Team = {
  id: string;
  departmentId: string;
  managerUserId: string;
  members: { id: string }[];
};
type InboxPage = { items: { id: string; lastMessagePreview: string | null }[] };

test.use({ storageState: authPath("admin") });

test("inbox role matrix keeps conversation content and replies within permission scope", async ({ browser, page }) => {
  const secret = process.env.POSE_E2E_ZALO_SECRET;
  const runId = process.env.POSE_E2E_RUN_ID;
  const password = process.env.POSE_E2E_PASSWORD;
  if (!secret || !runId || !password) throw new Error("Run this test with scripts/e2e-env.ps1 on the isolated environment.");

  const admin = await roleApi("admin");
  const manager = await roleApi("mgr");
  const assignedSales = await roleApi("s01");
  const unassignedSales = await roleApi("s02");
  const care = await roleApi("care");
  const dataStaff = await roleApi("data");
  const webhook = await request.newContext({ baseURL: BACKEND });
  const temporaryManagerLogin = await request.newContext({ baseURL: ORIGIN });
  const contexts: Awaited<ReturnType<typeof browser.newContext>>[] = [];
  const pageErrors: string[] = [];

  try {
    const readSession = async (api: typeof admin): Promise<SessionUser> => {
      const response = await api.get("/api/session/me");
      expect(response.status()).toBe(200);
      return (await response.json()).user as SessionUser;
    };
    const [adminUser, managerUser, assignedSalesUser, unassignedSalesUser, careUser, dataUser] = await Promise.all([
      readSession(admin), readSession(manager), readSession(assignedSales), readSession(unassignedSales), readSession(care), readSession(dataStaff),
    ]);

    expect(adminUser.permissions).toContain("conversation.read");
    expect(adminUser.permissions).not.toContain("conversation.reply");
    expect(managerUser.roles).toContain("MANAGER");
    expect(managerUser.permissions).toContain("conversation.read");
    expect(assignedSalesUser.roles).toContain("SALES_REP");
    expect(unassignedSalesUser.roles).toContain("SALES_REP");
    expect(careUser.roles).toContain("CUSTOMER_CARE");
    expect(dataUser.roles).toContain("DATA_STAFF");

    const teamResponse = await admin.get("/api/backend/teams?page=0&size=100");
    expect(teamResponse.status()).toBe(200);
    const teams = (await teamResponse.json()).items as Team[];
    const salesTeam = teams.find(team => team.managerUserId === managerUser.id && team.members.some(member => member.id === assignedSalesUser.id));
    expect(salesTeam).toBeTruthy();

    const memberIds = [...new Set([...salesTeam!.members.map(member => member.id), careUser.id])];
    const addCare = await admin.put(`/api/backend/teams/${salesTeam!.id}/members`, {
      headers: { Origin: ORIGIN },
      data: { memberUserIds: memberIds },
    });
    expect(addCare.status()).toBe(200);

    const suffix = randomUUID().replaceAll("-", "").slice(0, 16);
    const createOtherManager = await admin.post("/api/backend/users", {
      headers: { Origin: ORIGIN },
      data: {
        email: `w14-manager-${runId}-${suffix}@example.test`,
        fullName: `W14 Other Manager ${suffix}`,
        initialPassword: password,
        roleCodes: ["MANAGER"],
      },
    });
    expect(createOtherManager.status()).toBe(201);
    const otherManagerUser = (await createOtherManager.json()) as { id: string };

    const createOtherTeam = await admin.post("/api/backend/teams", {
      headers: { Origin: ORIGIN },
      data: {
        code: `W14_${suffix.toUpperCase()}`,
        name: `W14 Other Team ${suffix}`,
        departmentId: salesTeam!.departmentId,
        managerUserId: otherManagerUser.id,
      },
    });
    expect(createOtherTeam.status()).toBe(201);

    const managerLogin = await temporaryManagerLogin.post("/api/session/login", {
      headers: { Origin: ORIGIN },
      data: { organizationCode: "EDU", email: `w14-manager-${runId}-${suffix}@example.test`, password },
    });
    expect(managerLogin.status()).toBe(200);
    const dynamicManagerState = await temporaryManagerLogin.storageState();
    const otherManagerContext = await browser.newContext({ storageState: dynamicManagerState });
    contexts.push(otherManagerContext);
    const otherManagerPage = await otherManagerContext.newPage();
    otherManagerPage.on("pageerror", error => pageErrors.push(error.message));
    otherManagerPage.on("console", message => { if (message.type() === "error") pageErrors.push(message.text()); });
    const otherManagerApi = await request.newContext({ baseURL: ORIGIN, storageState: dynamicManagerState });

    const createConversation = async (ownerId: string, label: string) => {
      const accountKey = `W14-${suffix}-${label}-${randomUUID().slice(0, 8)}`;
      const messageText = `W14 ${label} ${suffix}`;
      const account = await admin.post("/api/backend/channels/accounts", {
        headers: { Origin: ORIGIN },
        data: {
          channelType: "ZALO_SIM",
          displayName: accountKey,
          externalAccountId: accountKey,
          teamId: salesTeam!.id,
          defaultAssigneeUserId: ownerId,
          secretRef: "POSE_CHANNEL_ZALO_SIM_WEBHOOK_SECRET",
        },
      });
      expect(account.status()).toBe(201);
      const incoming = await webhook.post("/api/v1/channels/zalo-sim/webhook", {
        headers: { "X-POSE-Webhook-Secret": secret },
        data: {
          event_name: "user_send_text",
          timestamp: new Date().toISOString(),
          app_id: accountKey,
          sender: { id: `sender-${accountKey}` },
          recipient: { id: accountKey },
          message: { msg_id: `message-${accountKey}`, text: messageText },
        },
      });
      expect(incoming.status()).toBe(200);
      return { id: (await incoming.json()).conversationId as string, messageText };
    };

    const salesConversation = await createConversation(assignedSalesUser.id, "sales");
    const careConversation = await createConversation(careUser.id, "care");

    const inbox = async (api: typeof admin): Promise<InboxPage> => {
      const response = await api.get("/api/backend/conversations?page=0&size=100");
      expect(response.status()).toBe(200);
      return (await response.json()) as InboxPage;
    };
    const containsConversation = (items: InboxPage["items"], id: string) => items.some(item => item.id === id);

    const managerInbox = await inbox(manager);
    expect(containsConversation(managerInbox.items, salesConversation.id)).toBe(true);
    expect(containsConversation(managerInbox.items, careConversation.id)).toBe(true);
    const otherManagerInbox = await inbox(otherManagerApi);
    expect(containsConversation(otherManagerInbox.items, salesConversation.id)).toBe(false);
    expect(containsConversation(otherManagerInbox.items, careConversation.id)).toBe(false);

    const salesInbox = await inbox(unassignedSales);
    expect(containsConversation(salesInbox.items, salesConversation.id)).toBe(false);
    expect(containsConversation(salesInbox.items, careConversation.id)).toBe(false);
    const careInbox = await inbox(care);
    expect(containsConversation(careInbox.items, careConversation.id)).toBe(true);
    expect(containsConversation(careInbox.items, salesConversation.id)).toBe(false);

    const adminInbox = await inbox(admin);
    expect(containsConversation(adminInbox.items, salesConversation.id)).toBe(true);
    expect(containsConversation(adminInbox.items, careConversation.id)).toBe(true);

    for (const [api, conversationId] of [
      [unassignedSales, salesConversation.id],
      [care, salesConversation.id],
      [otherManagerApi, salesConversation.id],
    ] as const) {
      const messages = await api.get(`/api/backend/conversations/${conversationId}/messages`);
      expect(messages.status()).toBe(404);
      const reply = await api.post(`/api/backend/conversations/${conversationId}/messages`, {
        headers: { Origin: ORIGIN },
        data: { content: "W14 unauthorized reply must not be sent" },
      });
      expect(reply.status()).toBe(404);
    }

    const dataList = await dataStaff.get("/api/backend/conversations?page=0&size=100");
    expect(dataList.status()).toBe(403);
    const dataMessages = await dataStaff.get(`/api/backend/conversations/${salesConversation.id}/messages`);
    expect(dataMessages.status()).toBe(403);
    const dataReply = await dataStaff.post(`/api/backend/conversations/${salesConversation.id}/messages`, {
      headers: { Origin: ORIGIN },
      data: { content: "W14 Data Staff unauthorized reply" },
    });
    expect(dataReply.status()).toBe(403);

    const beforeAdminReply = await admin.get(`/api/backend/conversations/${salesConversation.id}/messages`);
    expect(beforeAdminReply.status()).toBe(200);
    const beforeAdminItems = (await beforeAdminReply.json()).items as { direction: string }[];
    const adminReply = await admin.post(`/api/backend/conversations/${salesConversation.id}/messages`, {
      headers: { Origin: ORIGIN },
      data: { content: "W14 Admin reply without permission" },
    });
    expect(adminReply.status()).toBe(403);
    const afterAdminReply = await admin.get(`/api/backend/conversations/${salesConversation.id}/messages`);
    expect(afterAdminReply.status()).toBe(200);
    const afterAdminItems = (await afterAdminReply.json()).items as { direction: string }[];
    expect(afterAdminItems.filter(item => item.direction === "OUTBOUND")).toHaveLength(
      beforeAdminItems.filter(item => item.direction === "OUTBOUND").length,
    );

    await otherManagerPage.goto("/app/hoi-thoai");
    await expect(otherManagerPage.getByRole("heading", { name: "Hội thoại", exact: true })).toBeVisible();
    await expect(otherManagerPage.getByText(salesConversation.messageText)).toHaveCount(0);
    await expect(otherManagerPage.getByText(careConversation.messageText)).toHaveCount(0);

    const salesContext = await browser.newContext({ storageState: authPath("s02") });
    contexts.push(salesContext);
    const salesPage = await salesContext.newPage();
    salesPage.on("pageerror", error => pageErrors.push(error.message));
    salesPage.on("console", message => { if (message.type() === "error") pageErrors.push(message.text()); });
    await salesPage.goto("/app/hoi-thoai");
    await expect(salesPage.getByText(salesConversation.messageText)).toHaveCount(0);
    await expect(salesPage.getByText(careConversation.messageText)).toHaveCount(0);

    const careContext = await browser.newContext({ storageState: authPath("care") });
    contexts.push(careContext);
    const carePage = await careContext.newPage();
    carePage.on("pageerror", error => pageErrors.push(error.message));
    carePage.on("console", message => { if (message.type() === "error") pageErrors.push(message.text()); });
    await carePage.setViewportSize({ width: 375, height: 812 });
    await carePage.goto("/app/hoi-thoai");
    const careConversationButton = carePage.getByRole("button", { name: new RegExp(careConversation.messageText) });
    await expect(careConversationButton).toBeVisible();
    await expect(carePage.getByText(salesConversation.messageText)).toHaveCount(0);
    await careConversationButton.click();
    await expect(carePage.getByRole("list", { name: "Tin nhắn" }).getByText(careConversation.messageText)).toBeVisible();
    expect(await carePage.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await carePage.getByLabel("Trả lời").fill("W14 Care response in assigned scope");
    await carePage.getByRole("button", { name: "Gửi trả lời" }).click();
    await expect(carePage.getByRole("list", { name: "Tin nhắn" }).getByText("W14 Care response in assigned scope", { exact: true })).toBeVisible();
    const careMessages = await care.get(`/api/backend/conversations/${careConversation.id}/messages`);
    expect(careMessages.status()).toBe(200);
    expect((await careMessages.json()).items.some((item: { direction: string; content: string }) =>
      item.direction === "OUTBOUND" && item.content === "W14 Care response in assigned scope",
    )).toBe(true);

    await page.goto("/app/hoi-thoai");
    const adminConversationButton = page.getByRole("button", { name: new RegExp(salesConversation.messageText) });
    await expect(adminConversationButton).toBeVisible();
    await adminConversationButton.click();
    await expect(page.getByRole("list", { name: "Tin nhắn" }).getByText(salesConversation.messageText)).toBeVisible();
    await expect(page.getByLabel("Trả lời")).toHaveCount(0);
    await expect(page.getByRole("region", { name: "Copilot" })).toHaveCount(0);

    const dataContext = await browser.newContext({ storageState: authPath("data") });
    contexts.push(dataContext);
    const dataPage = await dataContext.newPage();
    dataPage.on("pageerror", error => pageErrors.push(error.message));
    dataPage.on("console", message => { if (message.type() === "error") pageErrors.push(message.text()); });
    await dataPage.goto("/app/hoi-thoai");
    await expect(dataPage.getByText("Bạn không có quyền xem hội thoại.")).toBeVisible();
    await expect(dataPage.getByText(salesConversation.messageText)).toHaveCount(0);
    await expect(dataPage.getByText(careConversation.messageText)).toHaveCount(0);

    expect(pageErrors).toEqual([]);
  } finally {
    await Promise.all([
      admin.dispose(), manager.dispose(), assignedSales.dispose(), unassignedSales.dispose(), care.dispose(), dataStaff.dispose(),
      webhook.dispose(), temporaryManagerLogin.dispose(),
      ...contexts.map(context => context.close()),
    ]);
  }
});
