import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { CrmList } from "@/components/crm/CrmList";
import { CrmForm } from "@/components/crm/CrmForm";
import { CrmDetail } from "@/components/crm/CrmDetail";
import { implementedNavigation } from "@/config/navigation";
import { ApiError, createApiClient } from "@/lib/api/client";
import { readError } from "@/lib/server/backend";

const state = vi.hoisted(() => ({ roles: ["SALES_REP"], permissions: ["lead.read", "lead.write", "customer.read", "customer.write", "offering.read"], status: "NEW", reject: null as unknown, owners: [{ id: "owner-1", fullName: "Nhân viên mẫu" }], calls: [] as Array<[string, RequestInit | undefined]>, push: vi.fn(), page: "records", multipleOfferings: false }));
vi.mock("next/navigation", () => ({ useParams: () => ({ id: "record-1" }), useRouter: () => ({ push: state.push }), usePathname: () => "/app" }));
vi.mock("@/components/session/SessionGate", () => ({ useSession: () => ({ user: { id: "user-1", organization: { id: "org-1" }, roles: state.roles, permissions: state.permissions } }) }));
vi.mock("@/lib/api", () => ({ api: { request: vi.fn((path: string, init?: RequestInit) => {
  state.calls.push([path, init]);
  if (init?.method && state.reject) return Promise.reject(state.reject);
  if (path.includes("owner-candidates")) return Promise.resolve(state.owners);
  if (path.includes("/offerings")) return Promise.resolve(state.multipleOfferings && path.includes("page=1") ? { items: [{ id: "offer-2", name: "Dịch vụ trang hai", active: true }], page: 1, totalPages: 2, totalItems: 3 } : { items: [{ id: "offer-1", name: "Dịch vụ mẫu", active: true }, { id: "inactive", name: "Dịch vụ cũ", active: false }], page: 0, totalPages: state.multipleOfferings ? 2 : 1, totalItems: 2 });
  const record = { id: "record-1", displayName: "Khách mẫu", email: "sample@example.test", phone: null, source: "WEBSITE", offeringId: "offer-1", relatedOfferingId: "offer-1", customerType: "INDIVIDUAL", notes: null, address: null, ownerName: "Nhân viên mẫu", offeringName: "Dịch vụ mẫu", status: state.status, updatedAt: "2026-11-03T01:00:00Z" };
  if (init?.method) return Promise.resolve(record);
  if (path.includes("?")) {
    if (state.page === "loading") return new Promise(() => {});
    if (state.page === "error") return Promise.reject(new ApiError(503, "NETWORK_ERROR", "network"));
    if (state.page === "forbidden") return Promise.reject(new ApiError(403, "FORBIDDEN_SCOPE", "scope"));
    return Promise.resolve({ items: state.page === "empty" ? [] : [record], page: 0, size: 20, totalItems: state.page === "empty" ? 0 : 1, totalPages: 1 });
  }
  return Promise.resolve(record);
}) } }));
function show(element: React.ReactNode) { return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}>{element}</QueryClientProvider>); }
beforeEach(() => { state.roles = ["SALES_REP"]; state.permissions = ["lead.read", "lead.write", "customer.read", "customer.write", "offering.read", "offering.manage"]; state.status = "NEW"; state.reject = null; state.owners = [{ id: "owner-1", fullName: "Nhân viên mẫu" }]; state.calls = []; state.push.mockReset(); state.page = "records"; state.multipleOfferings = false; window.history.replaceState({}, "", "/app/lead"); window.sessionStorage.clear(); vi.spyOn(window, "confirm").mockReturnValue(true); });

describe("Customer and Lead navigation", () => {
  it.each([["SALES_REP", true, true], ["CUSTOMER_CARE", false, true], ["DATA_STAFF", false, false], ["ADMIN", true, true]])("shows only supported routes for %s", (role, lead, customer) => {
    const items = implementedNavigation(["lead.read", "customer.read"], [role]);
    expect(items.some(item => item.route === "/app/lead")).toBe(lead);
    expect(items.some(item => item.route === "/app/khach-hang")).toBe(customer);
  });
});

describe("Customer and Lead lists", () => {
  it("shows a loading state while the list request is pending", () => { state.page = "loading"; show(<CrmList kind="lead" />); expect(screen.getByRole("status")).toHaveTextContent("Đang tải danh sách"); });
  it("renders records and applies debounced search and status in the URL", async () => {
    show(<CrmList kind="lead" />); expect((await screen.findAllByText("Khách mẫu")).length).toBeGreaterThan(0);
    fireEvent.change(screen.getByLabelText(/Tìm theo tên/), { target: { value: "tìm mẫu" } });
    fireEvent.change(screen.getByLabelText("Trạng thái"), { target: { value: "QUALIFIED" } });
    await waitFor(() => expect(state.calls.some(([url]) => url.includes("status=QUALIFIED") && url.includes("search="))).toBe(true));
    expect(new URLSearchParams(window.location.search).get("search")).toBe("tìm mẫu");
  });
  it("shows empty, network retry and forbidden states", async () => {
    state.page = "empty"; const first = show(<CrmList kind="lead" />); expect(await screen.findByText("Chưa có Lead")).toBeVisible(); first.unmount();
    state.page = "error"; const second = show(<CrmList kind="lead" />); expect(await screen.findByText(/Không thể kết nối/)).toBeVisible(); fireEvent.click(screen.getByRole("button", { name: "Thử lại" })); await waitFor(() => expect(state.calls.filter(([url]) => url.startsWith("/api/backend/leads?")).length).toBeGreaterThan(1)); second.unmount();
    state.page = "forbidden"; show(<CrmList kind="lead" />); expect(await screen.findByText(/không có quyền truy cập hồ sơ/)).toBeVisible();
  });
  it("shows Customer Care a read-only list and no owner filter", async () => {
    state.roles = ["CUSTOMER_CARE"]; state.permissions = ["customer.read"];
    show(<CrmList kind="customer" />); expect((await screen.findAllByText("Khách mẫu")).length).toBeGreaterThan(0);
    expect(screen.queryByRole("link", { name: /Thêm khách hàng/ })).toBeNull(); expect(screen.queryByLabelText(/Mã người phụ trách/)).toBeNull();
  });
  it.each(["MANAGER", "ADMIN"])("shows owner filter to %s", async role => { state.roles = [role]; show(<CrmList kind="customer" />); expect(await screen.findByLabelText(/Mã người phụ trách/)).toBeVisible(); });
  it("does not expose Data Staff records when entering a URL directly", () => { state.roles = ["DATA_STAFF"]; show(<CrmList kind="customer" />); expect(screen.getByText("Bạn không có quyền truy cập")).toBeVisible(); });
});

async function fillLead() {
  fireEvent.change(screen.getByLabelText("Tên *"), { target: { value: "Khách mẫu" } });
  fireEvent.change(screen.getByLabelText("Email"), { target: { value: "sample@example.test" } });
  fireEvent.change(screen.getByLabelText("Nguồn *"), { target: { value: "WEBSITE" } });
  fireEvent.change(await screen.findByLabelText(/Sản phẩm\/dịch vụ quan tâm/), { target: { value: "offer-1" } });
}
describe("Customer and Lead forms", () => {
  it("loads all pages and offers only active products", async () => { state.multipleOfferings = true; show(<CrmForm kind="lead" id={null} />); expect(await screen.findByRole("option", { name: "Dịch vụ trang hai" })).toBeVisible(); expect(screen.queryByRole("option", { name: "Dịch vụ cũ" })).toBeNull(); });
  it("focuses the first missing field and reports invalid email", async () => {
    show(<CrmForm kind="lead" id={null} />); await screen.findByRole("option", { name: "Dịch vụ mẫu" });
    fireEvent.click(screen.getByRole("button", { name: "Tạo Lead" })); expect((await screen.findAllByText("Vui lòng điền đầy đủ các trường bắt buộc")).length).toBeGreaterThan(0);
    await waitFor(() => expect(screen.getByLabelText("Tên *")).toHaveFocus());
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "sai" } }); fireEvent.click(screen.getByRole("button", { name: "Tạo Lead" })); expect(await screen.findByText("Hãy nhập thông tin liên hệ hợp lệ")).toBeVisible();
  });
  it("creates a lead and navigates to the new detail with a notice marker", async () => {
    show(<CrmForm kind="lead" id={null} />); await screen.findByRole("option", { name: "Dịch vụ mẫu" }); await fillLead(); fireEvent.click(screen.getByRole("button", { name: "Tạo Lead" }));
    await waitFor(() => expect(state.push).toHaveBeenCalledWith("/app/lead/record-1")); expect(window.sessionStorage.getItem("pose-lead-saved")).toBe("record-1:create");
  });
  it("shows server field errors on their fields", async () => {
    state.reject = new ApiError(422, "VALIDATION_ERROR", "invalid", [{ field: "email", message: "Email không hợp lệ từ server" }]);
    show(<CrmForm kind="lead" id={null} />); await screen.findByRole("option", { name: "Dịch vụ mẫu" }); await fillLead(); fireEvent.click(screen.getByRole("button", { name: "Tạo Lead" }));
    expect(await screen.findByText("Email không hợp lệ từ server")).toBeVisible(); expect(screen.getByLabelText("Email")).toHaveAttribute("aria-describedby", "crm-email-error");
  });
  it.each([true, false])("only shows a duplicate link when an id is provided: %s", async visible => {
    state.reject = new ApiError(409, "LEAD_DUPLICATE", "duplicate", [], visible ? { duplicateId: "00000000-0000-0000-0000-000000000001" } : undefined);
    show(<CrmForm kind="lead" id={null} />); await screen.findByRole("option", { name: "Dịch vụ mẫu" }); await fillLead(); fireEvent.click(screen.getByRole("button", { name: "Tạo Lead" }));
    expect(await screen.findByText(/có thể trùng/)).toBeVisible(); expect(Boolean(screen.queryByRole("link", { name: "Mở hồ sơ đã có" }))).toBe(visible);
  });
  it("loads eligible owners for the selected offering and shows an empty result", async () => {
    state.roles = ["MANAGER"]; state.owners = []; show(<CrmForm kind="lead" id={null} />); await screen.findByRole("option", { name: "Dịch vụ mẫu" }); await fillLead();
    expect(await screen.findByText("Chưa có Sales đủ điều kiện cho sản phẩm này")).toBeVisible(); expect(state.calls.some(([path]) => path.includes("owner-candidates?offeringId=offer-1"))).toBe(true);
  });
  it("displays manager owner validation returned with 422", async () => {
    state.roles = ["MANAGER"]; state.reject = new ApiError(422, "VALIDATION_ERROR", "invalid", [{ field: "ownerUserId", message: "Người phụ trách không đủ điều kiện." }]);
    show(<CrmForm kind="lead" id={null} />); await screen.findByRole("option", { name: "Dịch vụ mẫu" }); await fillLead(); fireEvent.change(await screen.findByLabelText("Người phụ trách *"), { target: { value: "owner-1" } }); fireEvent.click(screen.getByRole("button", { name: "Tạo Lead" }));
    expect(await screen.findByText("Người phụ trách không đủ điều kiện.")).toBeVisible();
  });
  it("sends version and null for cleared fields, never owner, and offers reload after conflict", async () => {
    state.reject = new ApiError(409, "CUSTOMER_CHANGED", "changed"); show(<CrmForm kind="customer" id="record-1" />);
    await waitFor(() => expect(screen.getByLabelText("Tên *")).toHaveValue("Khách mẫu")); await screen.findByRole("option", { name: "Dịch vụ mẫu" });
    fireEvent.change(screen.getByLabelText("Tên *"), { target: { value: "Tên sửa" } }); fireEvent.click(screen.getByRole("button", { name: "Lưu thay đổi" }));
    expect(await screen.findByRole("button", { name: "Tải lại" })).toBeVisible();
    const call = state.calls.find(([, init]) => init?.method === "PATCH")!; const body = JSON.parse(String(call[1]?.body));
    expect(body).toMatchObject({ expectedUpdatedAt: "2026-11-03T01:00:00Z", phone: null, notes: null, address: null }); expect(body).not.toHaveProperty("ownerUserId");
    fireEvent.click(screen.getByRole("button", { name: "Tải lại" })); await waitFor(() => expect(screen.getByLabelText("Tên *")).toHaveValue("Khách mẫu"));
  });
  it("asks before following a link or going back with unsaved input", async () => {
    const confirm = vi.mocked(window.confirm); confirm.mockReturnValue(false); show(<CrmForm kind="lead" id={null} />);
    await screen.findByRole("option", { name: "Dịch vụ mẫu" }); fireEvent.change(screen.getByLabelText("Tên *"), { target: { value: "Chưa lưu" } });
    fireEvent.click(screen.getByRole("link", { name: "Hủy" })); expect(confirm).toHaveBeenCalledOnce();
    fireEvent.popState(window, { state: window.history.state }); expect(confirm).toHaveBeenCalledTimes(2);
  });
});

describe("Lead and customer details", () => {
  it("shows qualification only for NEW and disqualification requires a reason with Escape returning focus", async () => {
    const user = userEvent.setup(); show(<CrmDetail kind="lead" />); expect(await screen.findByRole("button", { name: "Xác nhận đủ điều kiện" })).toBeVisible();
    const trigger = screen.getByRole("button", { name: "Không đủ điều kiện" }); await user.click(trigger);
    const dialog = screen.getByRole("dialog"); await user.click(within(dialog).getByRole("button", { name: "Xác nhận" })); expect(within(dialog).getByText("Vui lòng nhập lý do không đủ điều kiện.")).toBeVisible();
    await user.keyboard("{Escape}"); expect(trigger).toHaveFocus();
  });
  it.each(["CONVERTED", "DISQUALIFIED"])("has no edit or state actions on closed %s lead", async status => { state.status = status; show(<CrmDetail kind="lead" />); await screen.findByRole("heading", { name: "Khách mẫu" }); expect(screen.queryByRole("link", { name: "Chỉnh sửa" })).toBeNull(); expect(screen.queryByRole("button", { name: "Xác nhận đủ điều kiện" })).toBeNull(); expect(screen.queryByRole("button", { name: "Không đủ điều kiện" })).toBeNull(); });
  it("sends the reason and record version to disqualify a qualified lead", async () => {
    state.status = "QUALIFIED"; show(<CrmDetail kind="lead" />);
    expect(await screen.findByRole("button", { name: "Không đủ điều kiện" })).toBeVisible(); expect(screen.queryByRole("button", { name: "Xác nhận đủ điều kiện" })).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Không đủ điều kiện" })); fireEvent.change(screen.getByLabelText("Lý do *"), { target: { value: "Không phù hợp" } }); fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Xác nhận" }));
    await waitFor(() => expect(state.calls.some(([path]) => path.endsWith("/disqualify"))).toBe(true));
    const call = state.calls.find(([path]) => path.endsWith("/disqualify"))!; expect(JSON.parse(String(call[1]?.body))).toEqual({ expectedUpdatedAt: "2026-11-03T01:00:00Z", reason: "Không phù hợp" });
  });
  it("shows missing legacy fields and keeps Customer Care read-only", async () => { state.roles = ["CUSTOMER_CARE"]; state.permissions = ["customer.read"]; show(<CrmDetail kind="customer" />); await screen.findByRole("heading", { name: "Khách mẫu" }); expect(screen.getAllByText("Chưa cập nhật").length).toBeGreaterThan(0); expect(screen.queryByRole("link", { name: "Chỉnh sửa" })).toBeNull(); });
  it("announces successful lead creation on the destination page", async () => { window.sessionStorage.setItem("pose-lead-saved", "record-1:create"); show(<CrmDetail kind="lead" />); expect(await screen.findByText("Tạo Lead thành công")).toBeVisible(); });
  it("keeps server reason errors attached to the dialog field", async () => {
    state.reject = new ApiError(400, "VALIDATION_ERROR", "invalid", [{ field: "reason", message: "Lý do không hợp lệ." }]); show(<CrmDetail kind="lead" />);
    fireEvent.click(await screen.findByRole("button", { name: "Không đủ điều kiện" })); fireEvent.change(screen.getByLabelText("Lý do *"), { target: { value: "Không phù hợp" } }); fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Xác nhận" }));
    expect(await within(screen.getByRole("dialog")).findByText("Lý do không hợp lệ.")).toBeVisible(); expect(screen.getByLabelText("Lý do *")).toHaveAttribute("aria-describedby", "crm-reason-error");
  });
});

describe("BFF duplicate response", () => {
  it("forwards only a valid duplicate id for a duplicate conflict", async () => {
    const id = "00000000-0000-0000-0000-000000000001";
    const response = new Response(JSON.stringify({ code: "LEAD_DUPLICATE", details: { duplicateId: id, privateValue: "secret" } }), { status: 409 });
    expect((await readError(response)).details).toEqual({ duplicateId: id });
    const forbidden = await readError(new Response(JSON.stringify({ code: "FORBIDDEN_SCOPE", details: { duplicateId: id } }), { status: 403 }));
    expect(forbidden).not.toHaveProperty("details");
  });
  it("retains duplicate details through the same-origin API client", async () => {
    const id = "00000000-0000-0000-0000-000000000001";
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: "CUSTOMER_DUPLICATE", details: { duplicateId: id } }), { status: 409 }));
    const client = createApiClient({ fetcher, navigate: vi.fn(), onExpired: vi.fn() });
    await expect(client.request("/api/backend/customers", { method: "POST" })).rejects.toMatchObject({ code: "CUSTOMER_DUPLICATE", details: { duplicateId: id } });
  });
});
