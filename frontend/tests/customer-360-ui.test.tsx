import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Customer360Hub } from "@/components/customer360/Customer360Hub";
import { Customer360Profile } from "@/components/customer360/Customer360Profile";
import { CrmDetail } from "@/components/crm/CrmDetail";
import { implementedNavigation } from "@/config/navigation";
import { ApiError } from "@/lib/api/client";
import type { Customer360Response, ReconciliationResponse } from "@/lib/customer360";

const state = vi.hoisted(() => ({
  id: "customer-1",
  roles: ["SALES_REP"],
  permissions: ["customer_360.read", "customer.read", "lead.read", "opportunity.read", "interaction.read", "task.manage"],
  calls: [] as Array<[string, RequestInit | undefined]>,
  reject: null as unknown,
  replace: vi.fn(),
  profile: null as Customer360Response | null,
  reconciliation: null as ReconciliationResponse | null,
}));

vi.mock("next/navigation", () => ({
  useParams: () => ({ id: state.id }),
  useRouter: () => ({ replace: state.replace }),
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
        id: "customer-1",
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
        customerId: "customer-1",
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
        customerId: "customer-1",
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
            id: "customer-1",
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
  state.id = "customer-1";
  state.roles = ["SALES_REP"];
  state.permissions = ["customer_360.read", "customer.read", "lead.read", "opportunity.read", "interaction.read", "task.manage"];
  state.calls = [];
  state.reject = null;
  state.replace.mockReset();
  state.profile = null;
  state.reconciliation = null;
});

describe("Customer 360 profile and permissions", () => {
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
    expect(links).toContain("/app/co-hoi?customerId=customer-1");
    expect(links.some(href => href?.startsWith("/app/lead"))).toBe(false);
    expect(links.some(href => href?.startsWith("/app/tuong-tac"))).toBe(false);
    expect(links.some(href => href?.startsWith("/app/nhiem-vu"))).toBe(false);
    first.unmount();

    state.permissions = ["customer_360.read", "lead.read", "opportunity.read", "interaction.read", "task.manage"];
    show(<Customer360Profile />);
    await screen.findByRole("heading", { name: "Khách hàng 360 Mẫu" });
    const allLinks = screen.getAllByRole("link").map(el => el.getAttribute("href"));
    expect(allLinks).toEqual(expect.arrayContaining([
      "/app/lead",
      "/app/lead/lead-1",
      "/app/co-hoi?customerId=customer-1",
      "/app/co-hoi/opp-1",
      "/app/tuong-tac?customerId=customer-1",
      "/app/tuong-tac/inter-1",
      "/app/nhiem-vu?customerId=customer-1",
      "/app/nhiem-vu/task-1",
    ]));
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

  it("handles 403, 404 and network retry states on profile", async () => {
    state.reject = new ApiError(403, "FORBIDDEN_SCOPE", "forbidden");
    const v1 = show(<Customer360Profile />);
    expect(await screen.findByText("Bạn không có quyền truy cập hồ sơ này trong phạm vi hiện tại.")).toBeVisible();
    v1.unmount();

    state.reject = new ApiError(404, "RESOURCE_NOT_FOUND", "not found");
    const v2 = show(<Customer360Profile />);
    expect(await screen.findByText("Không tìm thấy hồ sơ khách hàng.")).toBeVisible();
    v2.unmount();

    state.reject = new ApiError(503, "NETWORK_ERROR", "offline");
    show(<Customer360Profile />);
    expect(await screen.findByText("Không thể kết nối. Kiểm tra mạng rồi thử lại.")).toBeVisible();
    state.reject = null;
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByRole("heading", { name: "Khách hàng 360 Mẫu" })).toBeVisible();
  });
});

describe("Customer 360 hub, search and reconciliation", () => {
  it("exposes navigation for all 5 roles with customer_360.read and adds 360 button on Customer detail", async () => {
    for (const role of ["ADMIN", "MANAGER", "SALES_REP", "CUSTOMER_CARE", "DATA_STAFF"]) {
      expect(implementedNavigation(["customer_360.read"], [role]).some(item => item.route === "/app/khach-hang-360")).toBe(true);
    }
    show(<CrmDetail kind="customer" />);
    const link = await screen.findByRole("link", { name: "Xem hồ sơ 360" });
    expect(link).toHaveAttribute("href", "/app/khach-hang-360/customer-1");
  });

  it("lets Sales search and select a customer without exposing reconciliation", async () => {
    show(<Customer360Hub />);
    expect(document.title).toBe("Hồ sơ khách hàng 360 | POSE CRM");
    const select = await screen.findByLabelText("Chọn khách hàng");
    await screen.findByRole("option", { name: "Khách hàng 360 Mẫu" });
    expect(screen.queryByRole("heading", { name: "Đối soát dữ liệu" })).toBeNull();
    fireEvent.change(select, { target: { value: "customer-1" } });
    expect(state.replace).toHaveBeenCalledWith("/app/khach-hang-360/customer-1");
  });

  it("shows only reconciliation and 403 explanation to Data Staff, and paginates differences while keeping focus and title", async () => {
    state.roles = ["DATA_STAFF"];
    state.permissions = ["customer_360.read"];
    state.reconciliation = buildReconciliation(0, 25);
    show(<Customer360Hub />);
    expect(screen.getByText(/Nhân viên dữ liệu không có quyền tải danh sách khách hàng/)).toBeVisible();
    expect(screen.queryByLabelText("Tìm khách hàng")).toBeNull();
    const table = await screen.findByRole("table", { name: "Danh sách khách hàng lệch đối soát" });
    expect(within(table).getByRole("link", { name: "cust-diff-page-0" })).toHaveAttribute("href", "/app/khach-hang-360/cust-diff-page-0");
    expect(within(table).getByText(/source_count/)).toBeVisible();

    const nextButton = screen.getByRole("button", { name: "Sau" });
    nextButton.focus();
    state.reconciliation = buildReconciliation(1, 25);
    fireEvent.click(nextButton);
    await waitFor(() => expect(within(table).getByRole("link", { name: "cust-diff-page-1" })).toBeVisible());
    expect(nextButton).toHaveFocus();
    expect(document.title).toBe("Hồ sơ khách hàng 360 | POSE CRM");
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
