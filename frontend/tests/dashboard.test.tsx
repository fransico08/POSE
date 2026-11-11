import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Dashboard } from "@/components/dashboard/Dashboard";
import { engagementRows, formatMetric, periodFromDates, type DashboardMetric } from "@/lib/dashboard";
import { ApiError } from "@/lib/api/client";

const state = vi.hoisted(() => ({ roles: ["ADMIN"], permissions: ["dashboard.read", "identity.user.read"], error: null as unknown, response: null as unknown, pending: false, calls: [] as string[] }));
vi.mock("@/components/session/SessionGate", () => ({ useSession: () => ({ user: { id: "self", organization: { id: "org" }, roles: state.roles, permissions: state.permissions } }) }));
vi.mock("next/dynamic", () => ({ default: () => () => <div data-testid="chart" /> }));
vi.mock("@/lib/api", () => ({ api: { request: async (url: string) => {
  state.calls.push(url);
  if (url.includes("/users?")) return { items: [{ id: "self", fullName: "Tên người dùng" }, { id: "other", fullName: "Người khác" }], totalPages: 1 };
  if (state.pending) return new Promise(() => {});
  if (state.error) throw state.error;
  return state.response;
} } }));

function metric(metricId: string, value: number | null, dimensions: DashboardMetric["dimensions"] = {}, availability: DashboardMetric["availability"] = "AVAILABLE"): DashboardMetric {
  return { metricId, dimensions, value, unit: "count", numerator: null, denominator: null, availability, reason: null, periodOrAsOf: "2026-10-01T00:00:00Z", generatedAt: "2026-10-01T00:00:00Z", sourceSql: "test" };
}
function makeResponse(care = false) {
  const codes = ["A01", "A02", "A03", "A04", "A05", "A06", "B01", "B02", "B03", "B04", "B05", "C01", "C02", "C03", "D01", "D02"];
  const metrics = codes.map(code => {
    if (care && !code.startsWith("B")) return { ...metric(code, null, {}, "NOT_APPLICABLE"), reason: "OUTSIDE_ROLE_SCOPE" };
    const dimensions: Record<string, DashboardMetric["dimensions"]> = { A02: { status: "NEW" }, A05: { kind: "RESPONDED" }, A06: { segment: "ACTIVE" }, B03: { priority: "MEDIUM", status: "OPEN" }, B04: { ownerUserId: "self" }, C01: { workflowType: "LEAD_QUALIFIED" }, C02: { workflowType: "LEAD_QUALIFIED", retryCount: 1 }, C03: { workflowType: "LEAD_QUALIFIED" }, D01: { channel: "PHONE" }, D02: { userId: "other" } };
    return metric(code, 3, dimensions[code] ?? {});
  });
  return { organizationId: "org", asOf: "2026-10-01T00:00:00Z", period: { start: "2026-08-31T17:00:00Z", end: "2026-09-30T17:00:00Z", timezone: "Asia/Ho_Chi_Minh" }, filters: { ownerUserId: null, workflowType: null, metricIds: [] }, metrics };
}
function view() {
  const cache = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={cache}><Dashboard /></QueryClientProvider>);
}
beforeEach(() => { window.history.replaceState({}, "", "/app/bao-cao"); state.roles = ["ADMIN"]; state.permissions = ["dashboard.read", "identity.user.read"]; state.response = makeResponse(); state.error = null; state.pending = false; state.calls = []; });
describe("dashboard semantics", () => {
  it("never displays null as zero and formats zero, rates and duration", () => {
    expect(formatMetric(metric("A01", null))).toBe("Chưa đủ dữ liệu để tính");
    expect(formatMetric(metric("A01", 0, {}, "ZERO"))).toBe("0");
    expect(formatMetric({ ...metric("A03", 0.12345), unit: "ratio" })).toBe("12,35%");
    expect(formatMetric({ ...metric("A05", 3660), unit: "seconds" })).toBe("1 giờ 1 phút");
    expect(formatMetric(metric("D01", null, {}, "NO_DATA_IN_PERIOD"))).toBe("Không có dữ liệu trong kỳ đã chọn");
    expect(formatMetric(metric("E04", null, {}, "NOT_SUPPORTED"))).toBe("Chưa hỗ trợ dữ liệu hội thoại");
    expect(formatMetric({ ...metric("E02", 120000000, { currency: "VND" }), unit: "currency" })).toBe("120.000.000 VND");
  });
  it("converts inclusive Vietnam dates and supplies four engagement segments", () => {
    expect(periodFromDates("01/09/2026", "30/09/2026")).toEqual({ start: "2026-08-31T17:00:00.000Z", end: "2026-09-30T17:00:00.000Z" });
    expect(periodFromDates("02/09/2026", "01/09/2026")).toBeNull();
    expect(engagementRows([metric("A06", 12, { segment: "ACTIVE" })]).map(row => row.value)).toEqual([12, 0, 0, 0]);
  });
  it("renders all groups, charts and equivalent tables with names", async () => {
    view(); await screen.findByRole("heading", { name: "Tổng quan" });
    expect(screen.getAllByTestId("chart")).toHaveLength(4);
    for (const title of ["Lead theo trạng thái", "Mức độ tương tác khách hàng", "Nhiệm vụ theo ưu tiên và trạng thái", "Tương tác theo kênh"])
      expect(screen.getByRole("table", { name: `Số liệu ${title}` })).toBeInTheDocument();
    expect(within(screen.getByRole("table", { name: "Số liệu Tương tác theo người ghi" })).getByText("Người khác")).toBeInTheDocument();
  });
  it("shows restricted groups and hides owner filter for care", async () => {
    state.roles = ["CUSTOMER_CARE"]; state.permissions = ["dashboard.read"]; state.response = makeResponse(true);
    view(); await screen.findByRole("heading", { name: "Nhiệm vụ" });
    expect(screen.queryByLabelText("Người phụ trách")).not.toBeInTheDocument();
    expect(screen.getAllByText("Không áp dụng cho vai trò của bạn").length).toBeGreaterThanOrEqual(3);
    expect(screen.queryByRole("heading", { name: "Giá trị và rò rỉ" })).not.toBeInTheDocument();
  });
  it("shows group E availability and formats values without inventing zero", async () => {
    const data = makeResponse();
    data.metrics.push(metric("E01_OVERDUE_LEAD_RESPONSE", 4),
      { ...metric("E02_UNATTENDED_OPPORTUNITY_VALUE", 120000000, { currency: "VND" }), unit: "currency" },
      metric("E03_UNCONTACTED_AFTER_HANDOVER", null, {}, "NO_DATA_IN_PERIOD"),
      metric("E04_FIRST_MESSAGE_RESPONSE_TIME", null, {}, "NOT_SUPPORTED"),
      { ...metric("E05_AI_SUGGESTION_ACCEPTANCE_RATE", 0.75, { kind: "C360_SUMMARY" }), unit: "ratio" });
    state.response = data; view(); await screen.findByRole("heading", { name: "Giá trị và rò rỉ" });
    expect(screen.getByText("120.000.000 VND")).toBeInTheDocument();
    expect(screen.getByText("75%")).toBeInTheDocument();
    expect(screen.getByText("Không có dữ liệu trong kỳ đã chọn")).toBeInTheDocument();
    expect(screen.getByText("Chưa hỗ trợ dữ liệu hội thoại")).toBeInTheDocument();
  });
  it("validates custom dates and synchronizes inclusive UTC interval to URL", async () => {
    view(); await screen.findByRole("heading", { name: "Tổng quan" });
    fireEvent.change(screen.getByLabelText("Khoảng thời gian"), { target: { value: "custom" } });
    fireEvent.change(screen.getByLabelText("Từ ngày"), { target: { value: "01/09/2026" } });
    fireEvent.change(screen.getByLabelText("Đến ngày"), { target: { value: "30/09/2026" } });
    fireEvent.click(screen.getByRole("button", { name: "Áp dụng" }));
    await waitFor(() => expect(new URLSearchParams(window.location.search).get("start")).toBe("2026-08-31T17:00:00.000Z"));
    expect(new URLSearchParams(window.location.search).get("end")).toBe("2026-09-30T17:00:00.000Z");
    fireEvent.change(screen.getByLabelText("Đến ngày"), { target: { value: "01/08/2026" } });
    fireEvent.click(screen.getByRole("button", { name: "Áp dụng" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Khoảng ngày không hợp lệ");
  });
  it("handles 403 without retry and network errors with retry", async () => {
    state.error = new ApiError(403, "FORBIDDEN_SCOPE", "denied"); const first = view();
    await screen.findByText("Bạn không có quyền xem báo cáo trong phạm vi này.");
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument(); first.unmount();
    state.error = new ApiError(503, "NETWORK_ERROR", "network"); view();
    expect(await screen.findByRole("button", { name: "Thử lại" })).toBeInTheDocument();
  });
  it("announces loading while the response is pending", async () => {
    state.pending = true; view();
    const status = await screen.findByText("Đang tải báo cáo…");
    expect(status.closest("section")).toHaveAttribute("aria-busy", "true");
  });
  it("uses non-identifying labels without identity permission", async () => {
    state.roles = ["SALES_REP"]; state.permissions = ["dashboard.read"]; view();
    const table = await screen.findByRole("table", { name: "Số liệu Tương tác theo người ghi" });
    expect(within(table).getByText("Người dùng khác 1")).toBeInTheDocument();
    expect(screen.getByText("Bạn")).toBeInTheDocument();
  });
  it("shows field errors for invalid server filters", async () => {
    state.error = new ApiError(400, "VALIDATION_ERROR", "invalid", [{ field: "start", message: "Ngày không hợp lệ" }]);
    view();
    expect(await screen.findByText("start: Ngày không hợp lệ")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
  });
  it("loads a saved URL range and hides owner controls for sales", async () => {
    window.history.replaceState({}, "", "/app/bao-cao?start=2026-08-31T17%3A00%3A00.000Z&end=2026-09-30T17%3A00%3A00.000Z");
    state.roles = ["SALES_REP"]; state.permissions = ["dashboard.read"]; view();
    expect(await screen.findByLabelText("Từ ngày")).toHaveValue("01/09/2026");
    expect(screen.getByLabelText("Đến ngày")).toHaveValue("30/09/2026");
    expect(screen.queryByLabelText("Người phụ trách")).not.toBeInTheDocument();
  });
  it("shows owner-filter workflow restriction and allows clearing it", async () => {
    const data = makeResponse();
    data.metrics = data.metrics.map(m => m.metricId.startsWith("C") ? { ...m, value: null, availability: "NOT_APPLICABLE", reason: "OWNER_FILTER_NOT_SUPPORTED" } : m);
    state.response = data; window.history.replaceState({}, "", "/app/bao-cao?ownerUserId=other");
    view(); await screen.findAllByText("Không lọc được theo người phụ trách");
    fireEvent.click(screen.getByRole("button", { name: "Xóa bộ lọc" }));
    await waitFor(() => expect(new URLSearchParams(window.location.search).has("ownerUserId")).toBe(false));
    await waitFor(() => expect(state.calls.filter(call => call.includes("/dashboard/kpis")).some(call => !call.includes("ownerUserId="))).toBe(true));
  });
});
