import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Customer360Hub } from "@/components/customer360/Customer360Hub";
import { Customer360Profile } from "@/components/customer360/Customer360Profile";
import { CrmDetail } from "@/components/crm/CrmDetail";
import { implementedNavigation } from "@/config/navigation";
import { ApiError } from "@/lib/api/client";
import type { Customer360Response, ReconciliationResponse } from "@/lib/customer360";
import type { AiStatusResponse, CopilotInvokeResponse, CopilotSuggestion } from "@/lib/copilot";
import type { CustomerReassignResponse } from "@/lib/customers";

const CUSTOMER_ID = "11111111-1111-4111-8111-111111111111";

const state = vi.hoisted(() => ({
  id: "customer-1",
  roles: ["SALES_REP"],
  permissions: ["customer_360.read", "customer.read", "lead.read", "opportunity.read", "interaction.read", "task.manage"],
  calls: [] as Array<[string, RequestInit | undefined]>,
  reject: null as unknown,
  profile: null as Customer360Response | null,
  reconciliation: null as ReconciliationResponse | null,
  aiStatus: { enabled: false, provider: "mock" } as AiStatusResponse,
  suggestions: [] as CopilotSuggestion[],
  invokeResponse: null as CopilotInvokeResponse | null,
  invokeReject: null as unknown,
  reassignResponse: null as CustomerReassignResponse | null,
  reassignReject: null as unknown,
  candidates: [
    { id: "owner-1", fullName: "Nguyễn Bán Hàng", email: "s01@example.test" },
    { id: "owner-2", fullName: "Lê Tư Vấn Mới", email: "s02@example.test" },
  ],
}));

vi.mock("next/navigation", () => ({
  useParams: () => ({ id: state.id }),
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
}));

vi.mock("@/components/session/SessionGate", () => ({
  useSession: () => ({
    user: {
      id: "user-1",
      organization: { id: "org-1", name: "Tổ chức EDU", code: "EDU" },
      roles: state.roles,
      permissions: state.permissions,
    },
  }),
}));

function buildProfile(overrides: Partial<Customer360Response> = {}): Customer360Response {
  return {
    customer: {
      profile: {
        id: CUSTOMER_ID,
        displayName: "Khách hàng 360 Mẫu",
        email: "contact@example.test",
        phone: "0901234567",
        customerType: "BUSINESS",
        address: "123 Nguyễn Huệ, TP.HCM",
        notes: "Ghi chú mẫu",
        ownerUserId: "owner-1",
        ownerName: "Nguyễn Bán Hàng",
        relatedOfferingId: "off-1",
        offeringCode: "EDU-01",
        offeringName: "Khóa học Quản trị",
        status: "ACTIVE",
        createdAt: "2026-11-01T01:00:00Z",
        updatedAt: "2026-11-05T02:00:00Z",
      },
      owner: { id: "owner-1", name: "Nguyễn Bán Hàng" },
      ownerTeams: [{ id: "team-1", name: "Nhóm Tư Vấn EDU" }],
      careAssignees: [{ id: "care-1", name: "Trần Chăm Sóc" }],
    },
    sources: { count: 2, systems: ["CRM_WEB", "IMPORT_CSV"] },
    leads: {
      restricted: false,
      count: 1,
      items: [{ id: "lead-1", name: "Lead Chuyển Đổi", status: "CONVERTED", convertedAt: "2026-11-03T17:30:00Z" }],
    },
    opportunities: {
      restricted: false,
      stages: { NEW: 1, QUALIFIED: 2, PROPOSAL: 0, WON: 1, LOST: 0, CANCELLED: 0 },
      openExpectedValue: "15000000.00",
      wonFinalValue: "25000000.00",
      items: [{ id: "opp-1", title: "Cơ hội Đào tạo", stage: "QUALIFIED", expectedValue: "15000000.00", expectedCloseDate: "2026-11-20" }],
    },
    interactions: {
      restricted: false,
      count: 1,
      lastInteractionAt: "2026-11-04T17:30:00Z",
      items: [{
        id: "inter-1",
        customerId: CUSTOMER_ID,
        leadId: null,
        opportunityId: "opp-1",
        recordedByUserId: "owner-1",
        recordedByName: "Nguyễn Bán Hàng",
        type: "CALL",
        channel: "PHONE",
        subject: "Trao đổi lộ trình",
        content: "Chi tiết cuộc gọi",
        outcome: "Khách quan tâm",
        visibility: "SALES_ONLY",
        occurredAt: "2026-11-04T17:30:00Z",
        createdAt: "2026-11-04T17:30:00Z",
        updatedAt: "2026-11-04T17:30:00Z",
      }],
    },
    tasks: {
      restricted: false,
      statuses: { OPEN: 1, IN_PROGRESS: 0, DONE: 2, CANCELLED: 0 },
      overdue: 1,
      items: [{
        id: "task-1",
        customerId: CUSTOMER_ID,
        leadId: null,
        opportunityId: null,
        ownerUserId: "owner-1",
        ownerName: "Nguyễn Bán Hàng",
        createdByUserId: "owner-1",
        title: "Gửi báo giá",
        description: null,
        priority: "HIGH",
        status: "OPEN",
        dueAt: "2026-11-03T17:30:00Z",
        result: null,
        cancellationReason: null,
        origin: "MANUAL",
        completedAt: null,
        createdAt: "2026-11-01T00:00:00Z",
        updatedAt: "2026-11-01T00:00:00Z",
      }],
    },
    feedback: { restricted: false, count: 2, averageRating: 4.5, items: [] },
    support: { restricted: false, count: 1, averageRating: null, items: [] },
    refreshedAt: "2026-11-05T03:00:00Z",
    ...overrides,
  };
}

function buildReconciliation(page = 0, mismatched = 0): ReconciliationResponse {
  return {
    totals: {
      opportunity_count: 12,
      interaction_count: 26,
      feedback_count: 14,
      open_task_count: 8,
      open_support_count: 5,
      source_count: 22,
    },
    matched: 20 - mismatched,
    mismatched,
    differences: {
      items: mismatched > 0
        ? [{ customerId: `cust-diff-page-${page}`, columns: ["source_count", "interaction_count"] }]
        : [],
      page,
      size: 20,
      totalItems: mismatched,
      totalPages: mismatched > 0 ? 2 : 0,
    },
  };
}

vi.mock("@/lib/api", () => ({
  api: {
    request: vi.fn((url: string, init?: RequestInit) => {
      state.calls.push([url, init]);
      if (state.reject) return Promise.reject(state.reject);
      if (url.endsWith("/ai/status")) {
        return Promise.resolve(state.aiStatus);
      }
      if (url.includes("/ai/suggestions?")) {
        return Promise.resolve(state.suggestions);
      }
      if (url.includes("/ai/copilot/customers/")) {
        if (state.invokeReject) return Promise.reject(state.invokeReject);
        return Promise.resolve(state.invokeResponse);
      }
      if (url.includes("/ai/suggestions/") && (url.endsWith("/accept") || url.endsWith("/reject"))) {
        const body = init?.body ? JSON.parse(String(init.body)) as { finalContent?: string | null } : {};
        const isAccept = url.endsWith("/accept");
        const finalContent = isAccept ? (body.finalContent ?? null) : null;
        const base = state.invokeResponse?.suggestion ?? state.suggestions[0];
        const updated: CopilotSuggestion = {
          ...(base!),
          status: !isAccept ? "REJECTED" : finalContent ? "EDITED" : "ACCEPTED",
          finalContent,
          reviewedAt: "2026-11-08T10:30:00Z",
          reviewedBy: "user-1",
        };
        state.suggestions = [updated];
        return Promise.resolve(updated);
      }
      if (url.includes("/customers/owner-candidates")) {
        return Promise.resolve(state.candidates);
      }
      if (url.includes("/customers/") && url.endsWith("/reassign")) {
        if (state.reassignReject) return Promise.reject(state.reassignReject);
        return Promise.resolve(
          state.reassignResponse ?? {
            id: CUSTOMER_ID,
            displayName: "Khách hàng 360 Mẫu",
            email: "contact@example.test",
            phone: "0901234567",
            customerType: "BUSINESS",
            address: "123 Nguyễn Huệ, TP.HCM",
            notes: "Ghi chú mẫu",
            ownerUserId: "owner-2",
            ownerName: "Lê Tư Vấn Mới",
            relatedOfferingId: "off-1",
            offeringCode: "EDU-01",
            offeringName: "Khóa học Quản trị",
            status: "ACTIVE",
            createdAt: "2026-11-01T01:00:00Z",
            updatedAt: "2026-11-08T10:00:00Z",
            handoverId: "handover-1",
            movedOpportunityIds: ["opp-1", "opp-2"],
            movedTaskIds: ["task-1"],
          },
        );
      }
      if (url.includes("/customers/") && url.endsWith("/360")) {
        return Promise.resolve(state.profile ?? buildProfile());
      }
      if (url.includes("/customer-360/reconciliation")) {
        const p = Number(new URL(url, "https://pose.test").searchParams.get("page") ?? "0");
        return Promise.resolve(state.reconciliation ?? buildReconciliation(p, 0));
      }
      if (url.includes("/customers?")) {
        return Promise.resolve({
          items: [{
            id: CUSTOMER_ID,
            displayName: "Khách hàng 360 Mẫu",
            customerType: "BUSINESS",
            status: "ACTIVE",
            ownerName: "Nguyễn Bán Hàng",
          }],
          page: 0,
          size: 20,
          totalItems: 1,
          totalPages: 1,
        });
      }
      return Promise.resolve({
        id: "customer-1",
        displayName: "Khách hàng 360 Mẫu",
        email: "contact@example.test",
        phone: "0901234567",
        customerType: "BUSINESS",
        relatedOfferingId: "off-1",
        offeringName: "Khóa học Quản trị",
        ownerName: "Nguyễn Bán Hàng",
        address: null,
        notes: null,
        status: "ACTIVE",
        updatedAt: "2026-11-05T01:00:00Z",
      });
    }),
  },
}));

function show(node: React.ReactNode) {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      {node}
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  state.id = CUSTOMER_ID;
  state.roles = ["SALES_REP"];
  state.permissions = ["customer_360.read", "customer.read", "lead.read", "opportunity.read", "interaction.read", "task.manage"];
  state.calls = [];
  state.reject = null;
  state.profile = null;
  state.reconciliation = null;
  state.aiStatus = { enabled: false, provider: "mock" };
  state.suggestions = [];
  state.invokeResponse = null;
  state.invokeReject = null;
  state.reassignResponse = null;
  state.reassignReject = null;
});

describe("Customer 360 profile and permissions", () => {
  it("uses readable interaction labels in the Customer 360 table and mobile card", async () => {
    const view = show(<Customer360Profile />);
    const table = await screen.findByRole("table", { name: "Danh sách tương tác" });
    expect(within(table).getByText("Cuộc gọi · Điện thoại")).toBeVisible();
    expect(view.container.querySelector('[data-c360-source="INTERACTION:inter-1"].offering-mobile-card')).toHaveTextContent("Cuộc gọi · Điện thoại");
  });
  it("renders restricted sections without fake zero counts", async () => {
    state.profile = buildProfile({
      leads: { restricted: true, count: null, items: [] },
      opportunities: { restricted: true, stages: null, openExpectedValue: null, wonFinalValue: null, items: [] },
      interactions: { restricted: true, count: null, lastInteractionAt: null, items: [] },
      tasks: { restricted: true, statuses: null, overdue: null, items: [] },
      feedback: { restricted: true, count: null, averageRating: null, items: [] },
      support: { restricted: true, count: null, averageRating: null, items: [] },
    });
    show(<Customer360Profile />);
    await screen.findByRole("heading", { name: "Khách hàng 360 Mẫu" });
    expect(screen.getAllByText("Bạn không có quyền xem phần này").length).toBeGreaterThanOrEqual(6);
    for (const title of ["Lead đã chuyển", "Cơ hội", "Tương tác", "Nhiệm vụ"]) {
      const section = screen.getByRole("heading", { name: title, level: 2 }).closest("section")!;
      expect(section).toHaveTextContent("Bạn không có quyền xem phần này");
      expect(section).not.toHaveTextContent("0");
    }
    for (const title of ["Cơ hội bán hàng", "Nhiệm vụ", "Phản hồi (Feedback)", "Hỗ trợ (Support)"]) {
      const card = screen.getByRole("heading", { name: title, level: 3 }).parentElement!;
      expect(card).toHaveTextContent("Bạn không có quyền xem phần này");
      expect(card).not.toHaveTextContent("0");
    }
  });

  it("shows hidden contact/content notices and no links for Data Staff", async () => {
    state.roles = ["DATA_STAFF"];
    state.permissions = ["customer_360.read", "interaction.read", "opportunity.read"];
    state.profile = buildProfile({
      customer: {
        ...buildProfile().customer,
        profile: { ...buildProfile().customer.profile, email: null, phone: null, address: null, notes: null },
      },
      leads: { restricted: true, count: null, items: [] },
      opportunities: { restricted: true, stages: null, openExpectedValue: null, wonFinalValue: null, items: [] },
      interactions: {
        restricted: false,
        count: 1,
        lastInteractionAt: "2026-11-04T17:30:00Z",
        items: [{ ...buildProfile().interactions.items[0], subject: null, content: null, outcome: null }],
      },
      tasks: { restricted: true, statuses: null, overdue: null, items: [] },
    });
    show(<Customer360Profile />);
    await screen.findByRole("heading", { name: "Khách hàng 360 Mẫu" });
    expect(screen.getAllByText("Ẩn theo quyền")).toHaveLength(4);
    expect(screen.getAllByText("Nội dung bị ẩn theo quyền").length).toBeGreaterThan(0);
    expect(screen.queryByRole("link")).toBeNull();
  });

  it("renders links only when target read permissions are present", async () => {
    state.permissions = ["customer_360.read", "opportunity.read"];
    const first = show(<Customer360Profile />);
    await screen.findByRole("heading", { name: "Khách hàng 360 Mẫu" });
    const links = screen.getAllByRole("link").map(el => el.getAttribute("href"));
    expect(links).toContain("/app/co-hoi/opp-1");
    expect(links).toContain(`/app/co-hoi?customerId=${CUSTOMER_ID}`);
    expect(links.some(href => href?.startsWith("/app/lead"))).toBe(false);
    expect(links.some(href => href?.startsWith("/app/tuong-tac"))).toBe(false);
    expect(links.some(href => href?.startsWith("/app/nhiem-vu"))).toBe(false);
    first.unmount();

    state.permissions = ["customer_360.read", "lead.read", "opportunity.read", "interaction.read", "task.manage"];
    show(<Customer360Profile />);
    await screen.findByRole("heading", { name: "Khách hàng 360 Mẫu" });
    const allLinks = screen.getAllByRole("link").map(el => el.getAttribute("href"));
    expect(allLinks).toEqual(expect.arrayContaining([
      "/app/lead/lead-1",
      `/app/co-hoi?customerId=${CUSTOMER_ID}`,
      "/app/co-hoi/opp-1",
      `/app/tuong-tac?customerId=${CUSTOMER_ID}`,
      "/app/tuong-tac/inter-1",
      `/app/nhiem-vu?customerId=${CUSTOMER_ID}`,
      "/app/nhiem-vu/task-1",
    ]));
    const leadSection = screen.getByRole("region", { name: "Lead đã chuyển" });
    expect(within(leadSection).queryByRole("link", { name: "Xem tất cả" })).not.toBeInTheDocument();
    expect(within(leadSection).getAllByRole("link", { name: "Lead Chuyển Đổi" })[0]).toHaveAttribute("href", "/app/lead/lead-1");
  });

  it("formats VND currency, Vietnam timestamps, visibility badge and overdue warning", async () => {
    const view = show(<Customer360Profile />);
    const heading = await screen.findByRole("heading", { name: "Khách hàng 360 Mẫu" });
    expect(heading).toHaveFocus();
    expect(document.title).toContain("Khách hàng 360 Mẫu — Hồ sơ khách hàng 360");
    expect(screen.getByText("Cập nhật lúc 05/11/2026 10:00")).toBeVisible();
    expect(screen.getAllByText("15.000.000 ₫").length).toBeGreaterThan(0);
    expect(screen.getByText("25.000.000 ₫")).toBeVisible();
    expect(screen.getAllByText(/04\/11\/2026 00:30/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/05\/11\/2026 00:30/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/SALES_ONLY/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/⚠ Quá hạn/).length).toBeGreaterThan(0);
    expect(view.container.querySelectorAll(".offering-mobile-card").length).toBeGreaterThanOrEqual(4);
  });

  it("rejects invalid IDs locally with an accessible return route", async () => {
    state.id = "invalid-id";
    show(<Customer360Profile />);
    expect(screen.getByRole("heading", { level: 1, name: "Hồ sơ khách hàng 360" })).toBeVisible();
    expect(screen.getByText("Không tìm thấy hồ sơ khách hàng.")).toBeVisible();
    expect(screen.getByRole("link", { name: "Quay lại tra cứu khách hàng 360" })).toHaveAttribute("href", "/app/khach-hang-360");
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
    expect(state.calls).toHaveLength(0);
    await waitFor(() => expect(document.title).toBe("Hồ sơ khách hàng 360 | POSE CRM"));
  });

  it("handles 403, 404 and network retry states on profile", async () => {
    state.reject = new ApiError(403, "FORBIDDEN_SCOPE", "forbidden");
    const v1 = show(<Customer360Profile />);
    expect(await screen.findByText("Bạn không có quyền truy cập hồ sơ này trong phạm vi hiện tại.")).toBeVisible();
    expect(screen.getByRole("heading", { level: 1, name: "Hồ sơ khách hàng 360" })).toBeVisible();
    expect(screen.getByRole("link", { name: "Quay lại tra cứu khách hàng 360" })).toHaveAttribute("href", "/app/khach-hang-360");
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
    v1.unmount();

    state.reject = new ApiError(404, "RESOURCE_NOT_FOUND", "not found");
    const v2 = show(<Customer360Profile />);
    expect(await screen.findByText("Không tìm thấy hồ sơ khách hàng.")).toBeVisible();
    expect(screen.getByRole("link", { name: "Quay lại tra cứu khách hàng 360" })).toBeVisible();
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
    v2.unmount();

    state.reject = new ApiError(503, "NETWORK_ERROR", "offline");
    show(<Customer360Profile />);
    expect(await screen.findByText("Không thể kết nối. Kiểm tra mạng rồi thử lại.")).toBeVisible();
    state.reject = null;
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByRole("heading", { name: "Khách hàng 360 Mẫu" })).toBeVisible();
  });

  it("offers retry for server errors but not client errors", async () => {
    state.reject = new ApiError(500, "SERVER_ERROR", "server error");
    const server = show(<Customer360Profile />);
    expect(await screen.findByRole("button", { name: "Thử lại" })).toBeVisible();
    server.unmount();

    state.reject = new ApiError(400, "VALIDATION_ERROR", "invalid request");
    show(<Customer360Profile />);
    expect(await screen.findByRole("link", { name: "Quay lại tra cứu khách hàng 360" })).toBeVisible();
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
  });
});

describe("Customer 360 hub, search and reconciliation", () => {
  it("exposes navigation for all 5 roles with customer_360.read and adds 360 button on Customer detail", async () => {
    for (const role of ["ADMIN", "MANAGER", "SALES_REP", "CUSTOMER_CARE", "DATA_STAFF"]) {
      expect(implementedNavigation(["customer_360.read"], [role]).some(item => item.route === "/app/khach-hang-360")).toBe(true);
    }
    show(<CrmDetail kind="customer" />);
    const link = await screen.findByRole("link", { name: "Xem hồ sơ 360" });
    expect(link).toHaveAttribute("href", `/app/khach-hang-360/${CUSTOMER_ID}`);
  });

  it("lets Sales open a customer using links without a navigation select", async () => {
    show(<Customer360Hub />);
    expect(document.title).toBe("Hồ sơ khách hàng 360 | POSE CRM");
    const table = await screen.findByRole("table", { name: "Danh sách khách hàng" });
    expect(within(table).getByRole("link", { name: "Mở hồ sơ 360" })).toHaveAttribute("href", `/app/khach-hang-360/${CUSTOMER_ID}`);
    expect(screen.getByRole("link", { name: "Khách hàng 360 Mẫu" })).toHaveAttribute("href", `/app/khach-hang-360/${CUSTOMER_ID}`);
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Đối soát dữ liệu" })).toBeNull();
  });

  it("explains Data Staff scope and keeps focus in reconciliation at pagination boundaries", async () => {
    state.roles = ["DATA_STAFF"];
    state.permissions = ["customer_360.read"];
    state.reconciliation = buildReconciliation(0, 25);
    show(<Customer360Hub />);
    expect(screen.getByText("Vai trò Nhân viên dữ liệu dùng trang này để đối soát. Hồ sơ 360 mở được từ danh sách khách hàng lệch.")).toBeVisible();
    expect(screen.queryByText(/403/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Tìm khách hàng")).toBeNull();
    const table = await screen.findByRole("table", { name: "Danh sách khách hàng lệch đối soát" });
    expect(within(table).getByRole("link", { name: "cust-diff-page-0" })).toHaveAttribute("href", "/app/khach-hang-360/cust-diff-page-0");
    expect(within(table).getByText(/source_count/)).toBeVisible();

    const nextButton = screen.getByRole("button", { name: "Sau" });
    nextButton.focus();
    state.reconciliation = buildReconciliation(1, 25);
    fireEvent.click(nextButton);
    await waitFor(() => expect(within(table).getByRole("link", { name: "cust-diff-page-1" })).toBeVisible());
    expect(nextButton).toBeDisabled();
    expect(screen.getByRole("heading", { name: "Đối soát dữ liệu" })).toHaveFocus();
    expect(document.activeElement).not.toBe(document.body);
    state.reconciliation = buildReconciliation(0, 25);
    fireEvent.click(screen.getByRole("button", { name: "Trước" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Trước" })).toBeDisabled());
    expect(screen.getByRole("heading", { name: "Đối soát dữ liệu" })).toHaveFocus();
    expect(document.title).toBe("Hồ sơ khách hàng 360 | POSE CRM");
  });

  it("explains to Data Staff when no customer needs reconciliation", async () => {
    state.roles = ["DATA_STAFF"];
    state.permissions = ["customer_360.read"];
    show(<Customer360Hub />);
    expect(await screen.findByText("Hiện không có hồ sơ cần kiểm tra.")).toBeVisible();
    expect(screen.queryByText(/403/)).not.toBeInTheDocument();
  });

  it("shows matched message for Admin when mismatched is 0", async () => {
    state.roles = ["ADMIN"];
    state.permissions = ["customer_360.read", "customer.read"];
    state.reconciliation = buildReconciliation(0, 0);
    show(<Customer360Hub />);
    expect(await screen.findByText("Dữ liệu khớp với view tổng hợp")).toBeVisible();
    expect(screen.getByText("20 khách hàng")).toBeVisible();
  });
});

function buildSampleSuggestion(overrides: Partial<CopilotSuggestion> = {}): CopilotSuggestion {
  return {
    id: "sug-1",
    invocationId: "inv-1",
    subjectType: "CUSTOMER",
    subjectId: CUSTOMER_ID,
    kind: "C360_SUMMARY",
    content: JSON.stringify({
      sections: [
        {
          heading: "Bối cảnh khách hàng",
          points: [
            { text: "Khách hàng doanh nghiệp đang quan tâm Khóa học Quản trị.", sources: ["PROFILE"] },
            { text: "Đã trao đổi lộ trình học tập qua điện thoại.", sources: ["INTERACTION:inter-1", "TASK:task-1"] },
          ],
        },
      ],
    }),
    evidence: JSON.stringify({ sources: ["PROFILE", "INTERACTION:inter-1", "TASK:task-1"], droppedPoints: 1 }),
    status: "PROPOSED",
    finalContent: null,
    reviewedBy: null,
    createdAt: "2026-11-08T10:00:00Z",
    reviewedAt: null,
    ...overrides,
  };
}

describe("AI Copilot and Customer owner reassignment", () => {
  it("hides Copilot when AI is disabled, missing ai.assist.use, or user has DATA_STAFF role", async () => {
    state.permissions = ["customer_360.read", "ai.assist.use"];
    state.aiStatus = { enabled: false, provider: "mock" };
    const v1 = show(<Customer360Profile />);
    await screen.findByRole("heading", { name: "Khách hàng 360 Mẫu" });
    expect(screen.queryByRole("heading", { name: "AI Copilot" })).toBeNull();
    v1.unmount();

    state.calls = [];
    state.aiStatus = { enabled: true, provider: "mock" };
    state.permissions = ["customer_360.read"];
    const v2 = show(<Customer360Profile />);
    await screen.findByRole("heading", { name: "Khách hàng 360 Mẫu" });
    expect(screen.queryByRole("heading", { name: "AI Copilot" })).toBeNull();
    expect(state.calls.some(([url]) => url.endsWith("/ai/status"))).toBe(false);
    v2.unmount();

    state.roles = ["DATA_STAFF", "MANAGER"];
    state.permissions = ["customer_360.read", "ai.assist.use", "customer.write"];
    const v3 = show(<Customer360Profile />);
    await screen.findByRole("heading", { name: "Khách hàng 360 Mẫu" });
    expect(screen.queryByRole("heading", { name: "AI Copilot" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Đổi người phụ trách" })).toBeNull();
    v3.unmount();
  });

  it("renders structured Copilot summary, focuses source items on chip click, and handles unavailable sources", async () => {
    state.permissions = ["customer_360.read", "ai.assist.use", "interaction.read", "task.manage"];
    state.aiStatus = { enabled: true, provider: "mock" };
    const suggestion = buildSampleSuggestion({
      content: JSON.stringify({
        sections: [
          {
            heading: "Bối cảnh khách hàng",
            points: [
              { text: "Khách hàng doanh nghiệp đang quan tâm Khóa học Quản trị.", sources: ["PROFILE"] },
              {
                text: "Đã trao đổi lộ trình học tập qua điện thoại.",
                sources: ["INTERACTION:inter-1", "TASK:task-1", "OPPORTUNITY:opp-missing"],
              },
            ],
          },
        ],
      }),
    });
    state.invokeResponse = {
      status: "SUCCEEDED",
      reused: true,
      invocationId: "inv-1",
      suggestion,
    };

    show(<Customer360Profile />);
    await screen.findByRole("heading", { name: "AI Copilot" });
    expect(screen.getByText("Do AI tạo — cần kiểm tra trước khi dùng")).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "Tóm tắt hồ sơ" }));
    expect(await screen.findByText("Khách hàng doanh nghiệp đang quan tâm Khóa học Quản trị.")).toBeVisible();
    expect(screen.getByText("(Sử dụng gợi ý đề xuất gần nhất)")).toBeVisible();
    expect(screen.getByText(/Đã lọc 1 ý thiếu nguồn hợp lệ/)).toBeVisible();

    const interactionChips = screen.getAllByRole("button", { name: "Nguồn: Tương tác" });
    fireEvent.click(interactionChips[0]);
    const focusedSource = document.activeElement as HTMLElement | null;
    expect(focusedSource?.getAttribute("data-c360-source")).toBe("INTERACTION:inter-1");

    const missingOppChip = screen.getByRole("button", { name: "Nguồn: Cơ hội" });
    fireEvent.click(missingOppChip);
    expect(await screen.findByText("Nguồn không khả dụng trên trang hiển thị (Cơ hội).")).toBeVisible();
  });

  it("shows a clear message and retry when the provider returns a failed invocation", async () => {
    state.permissions = ["customer_360.read", "ai.assist.use"];
    state.aiStatus = { enabled: true, provider: "gemini" };
    state.invokeResponse = { status: "FAILED", errorCode: "PROVIDER_ERROR", reused: false, invocationId: "inv-f", suggestion: null };

    show(<Customer360Profile />);
    await screen.findByRole("heading", { name: "AI Copilot" });
    fireEvent.click(screen.getByRole("button", { name: "Tóm tắt hồ sơ" }));
    expect(await screen.findByText("Dịch vụ AI tạm thời gặp sự cố. Vui lòng thử lại sau.")).toBeVisible();
    expect(screen.queryByText(/Không thể kết nối mạng/)).toBeNull();

    state.invokeResponse = { status: "SUCCEEDED", reused: false, invocationId: "inv-ok", suggestion: buildSampleSuggestion() };
    fireEvent.click(screen.getAllByRole("button", { name: "Thử lại" })[0]);
    await waitFor(() => expect(screen.queryByText("Dịch vụ AI tạm thời gặp sự cố. Vui lòng thử lại sau.")).toBeNull());
  });

  it("supports Accept, Edit with finalContent, and Reject transitions on Copilot suggestion", async () => {
    state.permissions = ["customer_360.read", "ai.assist.use"];
    state.aiStatus = { enabled: true, provider: "mock" };
    state.suggestions = [buildSampleSuggestion()];

    const v1 = show(<Customer360Profile />);
    await screen.findByText("Khách hàng doanh nghiệp đang quan tâm Khóa học Quản trị.");
    expect(screen.getByText("Đề xuất")).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "Sửa" }));
    const textarea = screen.getByLabelText("Nội dung chỉnh sửa trước khi lưu");
    fireEvent.change(textarea, { target: { value: "   " } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu bản đã sửa" }));
    expect(screen.getByText("Nội dung chỉnh sửa không được để trống.")).toBeVisible();

    fireEvent.change(textarea, { target: { value: "Nội dung bàn giao đã kiểm duyệt bởi Manager" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu bản đã sửa" }));
    expect(await screen.findByText("Đã chỉnh sửa")).toBeVisible();
    expect(screen.getByText("Nội dung bàn giao đã kiểm duyệt bởi Manager")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Chấp nhận" })).toBeNull();
    v1.unmount();

    state.suggestions = [buildSampleSuggestion({ id: "sug-2" })];
    const v2 = show(<Customer360Profile />);
    await screen.findByRole("button", { name: "Từ chối" });
    fireEvent.click(screen.getByRole("button", { name: "Từ chối" }));
    expect(await screen.findByText("Đã từ chối")).toBeVisible();
    v2.unmount();
  });

  it("distinguishes BLOCKED_BUDGET and FAILED/INVALID_OUTPUT states in Copilot", async () => {
    state.permissions = ["customer_360.read", "ai.assist.use"];
    state.aiStatus = { enabled: true, provider: "mock" };
    state.invokeResponse = {
      status: "BLOCKED_BUDGET",
      reused: false,
      invocationId: "inv-budget",
      suggestion: null,
    };

    const v1 = show(<Customer360Profile />);
    await screen.findByRole("heading", { name: "AI Copilot" });
    fireEvent.click(screen.getByRole("button", { name: "Tóm tắt hồ sơ" }));
    expect(await screen.findByText("Đã hết hạn mức AI tháng này.")).toBeVisible();
    v1.unmount();

    state.invokeResponse = {
      status: "FAILED",
      reused: false,
      invocationId: "inv-failed",
      suggestion: null,
    };
    show(<Customer360Profile />);
    await screen.findByRole("heading", { name: "AI Copilot" });
    fireEvent.click(screen.getByRole("button", { name: "Soạn hồ sơ bàn giao" }));
    expect(await screen.findByText("Chưa tạo được gợi ý, thử lại sau.")).toBeVisible();
    expect(screen.getByRole("button", { name: "Thử lại" })).toBeVisible();
  });

  it("disables reassign button with explanation when relatedOfferingId is null, and validates/submits ReassignOwnerDialog", async () => {
    state.roles = ["MANAGER"];
    state.permissions = ["customer_360.read", "customer.write", "ai.assist.use"];
    state.aiStatus = { enabled: true, provider: "mock" };
    state.profile = buildProfile({
      customer: {
        ...buildProfile().customer,
        profile: { ...buildProfile().customer.profile, relatedOfferingId: null, offeringCode: null, offeringName: null },
      },
    });

    const v1 = show(<Customer360Profile />);
    const disabledBtn = await screen.findByRole("button", { name: "Đổi người phụ trách" });
    expect(disabledBtn).toBeDisabled();
    expect(screen.getByText("Hãy cập nhật “Sản phẩm/dịch vụ liên quan” trong phần sửa hồ sơ trước khi đổi người phụ trách.")).toBeVisible();
    v1.unmount();

    state.profile = buildProfile();
    state.invokeResponse = {
      status: "SUCCEEDED",
      reused: false,
      invocationId: "inv-handover",
      suggestion: buildSampleSuggestion({ kind: "HANDOVER_BRIEF" }),
    };

    show(<Customer360Profile />);
    const openBtn = await screen.findByRole("button", { name: "Đổi người phụ trách" });
    expect(openBtn).not.toBeDisabled();
    fireEvent.click(openBtn);

    const dialog = await screen.findByRole("dialog", { name: "Đổi người phụ trách" });
    fireEvent.click(within(dialog).getByRole("button", { name: "Xác nhận đổi người phụ trách" }));
    expect(within(dialog).getByText("Chọn người phụ trách mới.")).toBeVisible();
    expect(within(dialog).getByText("Nhập lý do đổi người phụ trách.")).toBeVisible();

    const ownerSelect = within(dialog).getByLabelText("Người phụ trách mới *");
    await waitFor(() => expect(within(ownerSelect).getByRole("option", { name: "Lê Tư Vấn Mới" })).toBeInTheDocument());
    fireEvent.change(ownerSelect, { target: { value: "owner-2" } });
    fireEvent.change(within(dialog).getByLabelText("Lý do bàn giao *"), {
      target: { value: "Điều phối lại phụ trách theo chuyên môn sản phẩm" },
    });
    expect(within(dialog).getByText("48/1000 ký tự")).toBeVisible();

    state.reassignReject = new ApiError(422, "VALIDATION_ERROR", "Người phụ trách không đủ điều kiện.", [
      { field: "newOwnerUserId", message: "Người phụ trách mới chưa được phân công sản phẩm liên quan." },
    ]);
    fireEvent.click(within(dialog).getByRole("button", { name: "Xác nhận đổi người phụ trách" }));
    expect(await within(dialog).findAllByText("Người phụ trách mới chưa được phân công sản phẩm liên quan.")).not.toHaveLength(0);

    state.reassignReject = null;
    fireEvent.click(within(dialog).getByRole("button", { name: "Xác nhận đổi người phụ trách" }));

    expect(
      await screen.findByText(/Đã đổi người phụ trách sang Lê Tư Vấn Mới\. Đã chuyển 2 cơ hội đang mở và 1 nhiệm vụ chưa hoàn thành\./),
    ).toBeVisible();

    const bannerTrigger = screen.getAllByRole("button", { name: "Soạn hồ sơ bàn giao" })[0];
    fireEvent.click(bannerTrigger);
    expect(await screen.findByText("Hồ sơ bàn giao")).toBeVisible();
    expect(
      state.calls.some(([url, init]) => url.endsWith(`/ai/copilot/customers/${CUSTOMER_ID}/handover-brief`) && String(init?.body).includes('"force":true')),
    ).toBe(true);
  });

  it("excludes the current owner from reassignment candidates", async () => {
    state.roles = ["MANAGER"];
    state.permissions = ["customer_360.read", "customer.write"];
    state.profile = buildProfile();
    show(<Customer360Profile />);
    fireEvent.click(await screen.findByRole("button", { name: "Đổi người phụ trách" }));
    const dialog = await screen.findByRole("dialog", { name: "Đổi người phụ trách" });
    const select = within(dialog).getByLabelText("Người phụ trách mới *");
    await waitFor(() => expect(within(select).getByRole("option", { name: "Lê Tư Vấn Mới" })).toBeInTheDocument());
    expect(within(select).queryByRole("option", { name: /Nguyễn Bán Hàng/ })).not.toBeInTheDocument();
  });

  it("shows the open opportunity eligibility error on the owner field", async () => {
    state.roles = ["MANAGER"];
    state.permissions = ["customer_360.read", "customer.write"];
    state.profile = buildProfile();
    const message = "Người phụ trách mới chưa được phân công sản phẩm/dịch vụ của 2 cơ hội đang mở trên hồ sơ này.";
    state.reassignReject = new ApiError(422, "VALIDATION_ERROR", "Dữ liệu gửi lên không hợp lệ.", [{ field: "newOwnerUserId", message }]);
    show(<Customer360Profile />);
    fireEvent.click(await screen.findByRole("button", { name: "Đổi người phụ trách" }));
    const dialog = await screen.findByRole("dialog", { name: "Đổi người phụ trách" });
    const select = within(dialog).getByLabelText("Người phụ trách mới *");
    await within(select).findByRole("option", { name: "Lê Tư Vấn Mới" });
    fireEvent.change(select, { target: { value: "owner-2" } });
    fireEvent.change(within(dialog).getByLabelText("Lý do bàn giao *"), { target: { value: "Điều phối" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Xác nhận đổi người phụ trách" }));
    await waitFor(() => expect(select).toHaveAttribute("aria-invalid", "true"));
    expect(document.getElementById("reassign-newOwnerUserId-error")).toHaveTextContent(message);
    expect(select).toHaveAttribute("aria-describedby", "reassign-newOwnerUserId-error");
  });
});
