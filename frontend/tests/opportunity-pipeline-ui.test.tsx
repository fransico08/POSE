import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { OpportunityPipeline } from "@/components/opportunities/OpportunityPipeline";
import { OpportunityForm } from "@/components/opportunities/OpportunityForm";
import { OpportunityDetail } from "@/components/opportunities/OpportunityDetail";
import { ApiError, createApiClient } from "@/lib/api/client";
import { calculateLine, formatVnd, fromCents, toCents, vietnamToday } from "@/lib/opportunities";
import { implementedNavigation } from "@/config/navigation";

const state = vi.hoisted(() => ({ roles: ["SALES_REP"], permissions: ["opportunity.read", "opportunity.write", "offering.read"], calls: [] as Array<[string, RequestInit | undefined]>, reject: null as unknown, mode: "data", customerMode: "normal", stage: "NEW", searchParamsUnavailable: false, replace: vi.fn(), owners: [{ id: "owner-1", fullName: "Nhân viên mẫu" }] }));
vi.mock("next/navigation", () => ({ useParams: () => ({ id: "opp-1" }), useRouter: () => ({ replace: state.replace }), useSearchParams: () => state.searchParamsUnavailable ? null : new URLSearchParams(window.location.search) }));
vi.mock("@/components/session/SessionGate", () => ({ useSession: () => ({ user: { id: "user-1", organization: { id: "org-1" }, roles: state.roles, permissions: state.permissions } }) }));
vi.mock("@/lib/api", () => ({ api: { request: vi.fn((path: string, init?: RequestInit) => {
  state.calls.push([path, init]); if (init?.method && state.reject) return Promise.reject(state.reject);
  const opportunity = { id: "opp-1", title: "Cơ hội mẫu", stage: state.stage, expectedValue: "3000000.00", currencyCode: "VND", expectedCloseDate: "2099-12-01", notes: null, customer: { id: "00000000-0000-4000-8000-000000000001", name: "Khách mẫu" }, lead: null, owner: { id: "owner-1", name: "Nhân viên mẫu" }, lines: [{ offeringId: "offer-1", offeringCode: "O1", offeringName: "Dịch vụ mẫu", quantity: "2.00", unitPrice: "1500000.00", discountAmount: "0.00", lineTotal: "3000000.00" }], finalValue: null, actualCloseDate: null, closingReason: null, closedAt: null, createdAt: "2026-11-01T00:00:00Z", updatedAt: "2026-11-03T00:00:00Z" };
  if (path.includes("owner-candidates") || path.endsWith("/owners")) return Promise.resolve(state.owners);
  if (path.includes("/offerings")) return Promise.resolve({ items: [{ id: "offer-1", name: "Dịch vụ mẫu", active: true, referencePrice: "1500000.00" }, { id: "offer-2", name: "Dịch vụ hai", active: true, referencePrice: "999999.99" }], totalPages: 1 });
  if (path.includes("/customers/")) return ["forbidden", "missing"].includes(state.customerMode) ? Promise.reject(new ApiError(state.customerMode === "missing" ? 404 : 403, "FORBIDDEN_SCOPE", "scope")) : Promise.resolve({ id: path.split("/").at(-1), displayName: "Khách từ hồ sơ" });
  if (path.includes("/customers")) return Promise.resolve({ items: state.customerMode === "normal" ? [{ id: "00000000-0000-4000-8000-000000000001", displayName: "Khách mẫu" }] : [], totalPages: 1 });
  if (init?.method) return Promise.resolve(opportunity);
  if (path.includes("/opportunities?")) { if (state.mode === "error") return Promise.reject(new ApiError(503, "NETWORK_ERROR", "network")); if (state.mode === "forbidden") return Promise.reject(new ApiError(403, "FORBIDDEN_SCOPE", "scope")); return Promise.resolve({ items: state.mode === "empty" ? [] : [opportunity], page: Number(new URLSearchParams(path.split("?")[1]).get("page") ?? 0), size: 20, totalItems: 21, totalPages: 2 }); }
  return Promise.resolve(opportunity);
}) } }));
function show(node: React.ReactNode) { return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}>{node}</QueryClientProvider>); }
beforeEach(() => { state.roles = ["SALES_REP"]; state.permissions = ["opportunity.read", "opportunity.write", "offering.read"]; state.calls = []; state.reject = null; state.mode = "data"; state.customerMode = "normal"; state.stage = "NEW"; state.searchParamsUnavailable = false; state.replace.mockReset(); state.owners = [{ id: "owner-1", fullName: "Nhân viên mẫu" }]; window.history.replaceState({}, "", "/app/co-hoi"); window.sessionStorage.clear(); });
describe("Opportunity money", () => {
  it("uses exact cents and half-up percentage discounts", () => { expect(calculateLine("2", "1500000", "10", true)).toEqual({ discountAmount: "300000.00", total: 270000000n }); expect(fromCents(calculateLine("1", "999999.99", "0", false)!.total)).toBe("999999.99"); });
  it("rejects invalid values and discounts over the total", () => { expect(calculateLine("0", "1", "0", false)).toBeNull(); expect(calculateLine("1.234", "1", "0", false)).toBeNull(); expect(calculateLine("1", "1", "101", true)).toBeNull(); });
  it("formats large and negative exact values", () => { expect(formatVnd("999999.99")).toBe("999.999,99 ₫"); expect(formatVnd(fromCents(-toCents("42.01")!))).toBe("−42,01 ₫"); });
  it("uses the Vietnam calendar at the UTC boundary", () => { expect(vietnamToday(new Date("2026-11-03T16:59:59Z"))).toBe("2026-11-03"); expect(vietnamToday(new Date("2026-11-03T17:00:00Z"))).toBe("2026-11-04"); });
});
describe("Opportunity pipeline", () => {
  it("uses default filters when Next search params are unavailable", async () => { state.searchParamsUnavailable = true; show(<OpportunityPipeline />); expect(await screen.findByRole("heading", { name: "Cơ hội mẫu" })).toBeVisible(); expect(state.calls.some(([url]) => url.includes("/opportunities?") && url.includes("page=0") && url.includes("size=20"))).toBe(true); });
  it("loads six stages from the API and paginates", async () => { show(<OpportunityPipeline />); expect(await screen.findByRole("heading", { name: "Cơ hội mẫu" })).toBeVisible(); for (const name of ["Mới", "Đủ điều kiện", "Đề xuất", "Thành công", "Thất bại", "Đã hủy"]) expect(screen.getByRole("heading", { name })).toBeVisible(); fireEvent.click(screen.getByRole("button", { name: "Sau" })); await waitFor(() => expect(state.calls.some(([url]) => url.includes("page=1"))).toBe(true)); });
  it("updates stage and search query in the URL", async () => { show(<OpportunityPipeline />); await screen.findByRole("heading", { name: "Cơ hội mẫu" }); fireEvent.change(screen.getByLabelText("Giai đoạn"), { target: { value: "WON" } }); fireEvent.change(screen.getByLabelText(/Tìm cơ hội/), { target: { value: "Khách" } }); await waitFor(() => expect(new URLSearchParams(window.location.search).get("search")).toBe("Khách")); expect(new URLSearchParams(window.location.search).get("stage")).toBe("WON"); });
  it.each([["empty", "Không có cơ hội phù hợp"], ["error", "Không thể kết nối"], ["forbidden", "không có quyền truy cập"]])("handles %s", async (mode, message) => { state.mode = mode; show(<OpportunityPipeline />); expect(await screen.findByText(new RegExp(message, "i"))).toBeVisible(); });
  it("hides create from Admin and owner filter from Sales", async () => { state.roles = ["ADMIN"]; state.permissions.push("offering.manage"); const view = show(<OpportunityPipeline />); await screen.findByRole("heading", { name: "Cơ hội mẫu" }); expect(screen.queryByRole("link", { name: "Thêm cơ hội" })).toBeNull(); view.unmount(); state.roles = ["SALES_REP"]; show(<OpportunityPipeline />); expect(screen.queryByLabelText("Người phụ trách")).toBeNull(); });
  it("does not expose the menu to Customer Care", () => { expect(implementedNavigation(["opportunity.read"], ["CUSTOMER_CARE"]).some(item => item.route === "/app/co-hoi")).toBe(false); });
});
describe("Opportunity form", () => {
  it("creates using an exact percentage discount without server-calculated fields", async () => { show(<OpportunityForm />); await screen.findByRole("option", { name: "Dịch vụ mẫu" }); fireEvent.change(screen.getByLabelText("Khách hàng *"), { target: { value: "00000000-0000-4000-8000-000000000001" } }); fireEvent.change(screen.getByLabelText("Tên cơ hội *"), { target: { value: "Tên mới" } }); fireEvent.change(screen.getByLabelText("Ngày dự kiến chốt *"), { target: { value: "01/12/2099" } }); fireEvent.change(screen.getByLabelText("Sản phẩm/dịch vụ *"), { target: { value: "offer-1" } }); fireEvent.change(screen.getByLabelText("Số lượng *"), { target: { value: "2" } }); fireEvent.change(screen.getByLabelText("Đơn vị chiết khấu"), { target: { value: "percent" } }); fireEvent.change(screen.getByLabelText("Chiết khấu"), { target: { value: "10" } }); fireEvent.click(screen.getByRole("button", { name: "Lưu cơ hội" })); await waitFor(() => expect(state.replace).toHaveBeenCalledWith("/app/co-hoi/opp-1")); const call = state.calls.find(([, init]) => init?.method === "POST")!; const body = JSON.parse(String(call[1]?.body)); expect(body.lines[0].discountAmount).toBe("300000.00"); expect(body).not.toHaveProperty("expectedValue"); expect(body.lines[0]).not.toHaveProperty("lineTotal"); });
  it("requires an owner for Manager", async () => { state.roles = ["MANAGER"]; state.permissions.push("offering.manage"); show(<OpportunityForm />); await screen.findByRole("option", { name: "Dịch vụ mẫu" }); fireEvent.click(screen.getByRole("button", { name: "Lưu cơ hội" })); expect(screen.getByText("Chọn người phụ trách.")).toBeVisible(); });
  it("shows owner eligibility errors returned by the server", async () => { state.roles = ["MANAGER"]; state.permissions.push("offering.manage"); state.reject = new ApiError(422, "VALIDATION_ERROR", "invalid", [{ field: "ownerUserId", message: "Người phụ trách không đủ điều kiện" }]); show(<OpportunityForm />); await screen.findByRole("option", { name: "Dịch vụ mẫu" }); fireEvent.change(screen.getByLabelText("Khách hàng *"), { target: { value: "00000000-0000-4000-8000-000000000001" } }); fireEvent.change(screen.getByLabelText("Tên cơ hội *"), { target: { value: "Tên mới" } }); fireEvent.change(screen.getByLabelText("Ngày dự kiến chốt *"), { target: { value: "01/12/2099" } }); fireEvent.change(screen.getByLabelText("Sản phẩm/dịch vụ *"), { target: { value: "offer-1" } }); await screen.findByRole("option", { name: "Nhân viên mẫu" }); fireEvent.change(screen.getByLabelText("Người phụ trách *"), { target: { value: "owner-1" } }); fireEvent.click(screen.getByRole("button", { name: "Lưu cơ hội" })); expect(await screen.findByText("Người phụ trách không đủ điều kiện")).toBeVisible(); expect(screen.getByLabelText("Người phụ trách *")).toHaveAttribute("aria-invalid", "true"); });
  it("rejects a past expected close date", async () => { show(<OpportunityForm />); await screen.findByRole("option", { name: "Dịch vụ mẫu" }); fireEvent.change(screen.getByLabelText("Ngày dự kiến chốt *"), { target: { value: "01/01/2000" } }); fireEvent.click(screen.getByRole("button", { name: "Lưu cơ hội" })); expect(screen.getByText("Ngày dự kiến không hợp lệ hoặc đã qua.")).toBeVisible(); expect(state.calls.some(([, init]) => init?.method === "POST")).toBe(false); });
  it("only offers the current or later open stages when editing", async () => { state.stage = "QUALIFIED"; show(<OpportunityForm edit />); const select = await screen.findByLabelText("Giai đoạn *"); expect(within(select).queryByRole("option", { name: "Mới" })).toBeNull(); expect(within(select).getByRole("option", { name: "Đủ điều kiện" })).toBeVisible(); expect(within(select).getByRole("option", { name: "Đề xuất" })).toBeVisible(); });
  it("loads Manager candidates when the customer and product change", async () => { state.roles = ["MANAGER"]; state.permissions.push("offering.manage"); show(<OpportunityForm />); await screen.findByRole("option", { name: "Dịch vụ mẫu" }); fireEvent.change(screen.getByLabelText("Khách hàng *"), { target: { value: "00000000-0000-4000-8000-000000000001" } }); fireEvent.change(screen.getByLabelText("Sản phẩm/dịch vụ *"), { target: { value: "offer-1" } }); await screen.findByRole("option", { name: "Nhân viên mẫu" }); expect(state.calls.some(([url]) => url.includes("owner-candidates?") && url.includes("customerId=00000000-0000-4000-8000-000000000001") && url.includes("offeringIds=offer-1"))).toBe(true); });
  it("clears an owner who is no longer eligible after changing product", async () => { state.roles = ["MANAGER"]; state.permissions.push("offering.manage"); show(<OpportunityForm />); await screen.findByRole("option", { name: "Dịch vụ mẫu" }); fireEvent.change(screen.getByLabelText("Khách hàng *"), { target: { value: "00000000-0000-4000-8000-000000000001" } }); fireEvent.change(screen.getByLabelText("Sản phẩm/dịch vụ *"), { target: { value: "offer-1" } }); await screen.findByRole("option", { name: "Nhân viên mẫu" }); fireEvent.change(screen.getByLabelText("Người phụ trách *"), { target: { value: "owner-1" } }); state.owners = []; fireEvent.change(screen.getByLabelText("Sản phẩm/dịch vụ *"), { target: { value: "offer-2" } }); expect(await screen.findByText(/Người phụ trách không còn phù hợp/)).toBeVisible(); expect(screen.getByLabelText("Người phụ trách *")).toHaveValue(""); });
  it("attaches an error to the second line discount and focuses it", async () => { state.reject = new ApiError(400, "VALIDATION_ERROR", "invalid", [{ field: "lines[1].discountAmount", message: "Chiết khấu dòng hai không hợp lệ" }]); show(<OpportunityForm />); await screen.findByRole("option", { name: "Dịch vụ mẫu" }); fireEvent.change(screen.getByLabelText("Khách hàng *"), { target: { value: "00000000-0000-4000-8000-000000000001" } }); fireEvent.change(screen.getByLabelText("Tên cơ hội *"), { target: { value: "Tên mới" } }); fireEvent.change(screen.getByLabelText("Ngày dự kiến chốt *"), { target: { value: "01/12/2099" } }); fireEvent.change(screen.getByLabelText("Sản phẩm/dịch vụ *"), { target: { value: "offer-1" } }); fireEvent.click(screen.getByRole("button", { name: "Thêm dòng" })); const second = screen.getByRole("group", { name: "Dòng 2" }); fireEvent.change(within(second).getByLabelText("Sản phẩm/dịch vụ *"), { target: { value: "offer-2" } }); fireEvent.click(screen.getByRole("button", { name: "Lưu cơ hội" })); expect(await screen.findByText("Chiết khấu dòng hai không hợp lệ")).toBeVisible(); const discount = within(second).getByLabelText("Chiết khấu"); expect(discount).toHaveAttribute("aria-invalid", "true"); await waitFor(() => expect(discount).toHaveFocus()); });
  it("rejects duplicate products before sending a request", async () => { show(<OpportunityForm />); await screen.findByRole("option", { name: "Dịch vụ mẫu" }); fireEvent.change(screen.getByLabelText("Khách hàng *"), { target: { value: "00000000-0000-4000-8000-000000000001" } }); fireEvent.change(screen.getByLabelText("Tên cơ hội *"), { target: { value: "Tên mới" } }); fireEvent.change(screen.getByLabelText("Ngày dự kiến chốt *"), { target: { value: "01/12/2099" } }); fireEvent.change(screen.getByLabelText("Sản phẩm/dịch vụ *"), { target: { value: "offer-1" } }); fireEvent.click(screen.getByRole("button", { name: "Thêm dòng" })); fireEvent.change(within(screen.getByRole("group", { name: "Dòng 2" })).getByLabelText("Sản phẩm/dịch vụ *"), { target: { value: "offer-1" } }); fireEvent.click(screen.getByRole("button", { name: "Lưu cơ hội" })); expect(screen.getByText("Chọn sản phẩm khác nhau.")).toBeVisible(); expect(state.calls.some(([, init]) => init?.method === "POST")).toBe(false); });
  it("prevents more than fifty lines and keeps the final line", async () => { show(<OpportunityForm />); await screen.findByRole("option", { name: "Dịch vụ mẫu" }); expect(screen.getByRole("button", { name: "Xóa dòng 1" })).toBeDisabled(); const add = screen.getByRole("button", { name: "Thêm dòng" }); for (let index = 1; index < 50; index++) fireEvent.click(add); expect(screen.getAllByRole("group")).toHaveLength(50); expect(add).toBeDisabled(); }, 30000);
  it("sends changed lines with the version and without immutable fields", async () => { show(<OpportunityForm edit />); await screen.findByRole("option", { name: "Dịch vụ mẫu" }); fireEvent.change(screen.getByLabelText("Số lượng *"), { target: { value: "3" } }); fireEvent.click(screen.getByRole("button", { name: "Lưu cơ hội" })); await waitFor(() => expect(state.replace).toHaveBeenCalled()); const call = state.calls.find(([, init]) => init?.method === "PATCH")!; const body = JSON.parse(String(call[1]?.body)); expect(body).toMatchObject({ expectedUpdatedAt: "2026-11-03T00:00:00Z", lines: [{ offeringId: "offer-1", quantity: "3" }] }); expect(body).not.toHaveProperty("customerId"); expect(body).not.toHaveProperty("leadId"); expect(body).not.toHaveProperty("ownerUserId"); expect(body.lines[0]).not.toHaveProperty("lineTotal"); });
  it("shows server line errors at the input", async () => { state.reject = new ApiError(422, "VALIDATION_ERROR", "invalid", [{ field: "lines[0].offeringId", message: "Chưa được phân công" }]); show(<OpportunityForm />); await screen.findByRole("option", { name: "Dịch vụ mẫu" }); fireEvent.change(screen.getByLabelText("Khách hàng *"), { target: { value: "00000000-0000-4000-8000-000000000001" } }); fireEvent.change(screen.getByLabelText("Tên cơ hội *"), { target: { value: "Tên mới" } }); fireEvent.change(screen.getByLabelText("Ngày dự kiến chốt *"), { target: { value: "01/12/2099" } }); fireEvent.change(screen.getByLabelText("Sản phẩm/dịch vụ *"), { target: { value: "offer-1" } }); fireEvent.click(screen.getByRole("button", { name: "Lưu cơ hội" })); expect(await screen.findByText("Chưa được phân công")).toBeVisible(); expect(screen.getByLabelText("Sản phẩm/dịch vụ *")).toHaveAttribute("aria-invalid", "true"); });
  it("keeps a draft and offers reload on conflict", async () => { state.reject = new ApiError(409, "OPPORTUNITY_CHANGED", "changed"); show(<OpportunityForm edit />); const title = await screen.findByLabelText("Tên cơ hội *"); fireEvent.change(title, { target: { value: "Bản nháp" } }); fireEvent.click(screen.getByRole("button", { name: "Lưu cơ hội" })); expect(await screen.findByRole("button", { name: "Tải bản mới nhất" })).toBeVisible(); expect(title).toHaveValue("Bản nháp"); const call = state.calls.find(([, init]) => init?.method === "PATCH")!; const body = JSON.parse(String(call[1]?.body)); expect(body.expectedUpdatedAt).toBe("2026-11-03T00:00:00Z"); expect(body).not.toHaveProperty("customerId"); expect(body).not.toHaveProperty("ownerUserId"); });
  it("returns to detail when the opportunity is locked", async () => { state.reject = new ApiError(409, "OPPORTUNITY_LOCKED", "closed"); show(<OpportunityForm edit />); await screen.findByLabelText("Tên cơ hội *"); fireEvent.click(screen.getByRole("button", { name: "Lưu cơ hội" })); await waitFor(() => expect(state.replace).toHaveBeenCalledWith("/app/co-hoi/opp-1")); expect(window.sessionStorage.getItem("pose-opportunity-saved")).toBe("opp-1:locked"); });
});
describe("Opportunity actions", () => {
  it.each([["Chốt thành công", "win"], ["Chốt thất bại", "lose"], ["Hủy cơ hội", "cancel"]])("submits %s to its endpoint", async (label, action) => { show(<OpportunityDetail />); fireEvent.click(await screen.findByRole("button", { name: label })); const dialog = screen.getByRole("dialog"); if (action !== "win") fireEvent.change(within(dialog).getByLabelText("Lý do *"), { target: { value: "Lý do mẫu" } }); fireEvent.click(within(dialog).getByRole("button", { name: "Xác nhận" })); await waitFor(() => expect(state.calls.some(([url, init]) => url.endsWith(`/${action}`) && init?.method === "POST")).toBe(true)); const call = state.calls.find(([url]) => url.endsWith(`/${action}`))!; const body = JSON.parse(String(call[1]?.body)); expect(body.expectedUpdatedAt).toBe("2026-11-03T00:00:00Z"); if (action === "win") expect(body).toMatchObject({ finalValue: "3000000.00", actualCloseDate: vietnamToday() }); else expect(body.reason).toBe("Lý do mẫu"); });
  it("renders opportunity lines as mobile cards alongside the table", async () => { const { container } = show(<OpportunityDetail />); await screen.findByRole("heading", { name: "Cơ hội mẫu" }); const card = container.querySelector(".offering-mobile-cards .offering-mobile-card"); expect(card).not.toBeNull(); expect(card).toHaveTextContent("Dịch vụ mẫu"); expect(card).toHaveTextContent("O1"); expect(card).toHaveTextContent(formatVnd("3000000.00")); });
  it("hides edit and reopen from Sales on closed opportunities", async () => { state.stage = "LOST"; show(<OpportunityDetail />); await screen.findByRole("heading", { name: "Cơ hội mẫu" }); expect(screen.queryByRole("link", { name: "Chỉnh sửa" })).toBeNull(); expect(screen.queryByRole("button", { name: "Mở lại" })).toBeNull(); });
  it("lets Manager reopen a closed opportunity", async () => { state.stage = "LOST"; state.roles = ["MANAGER"]; state.permissions.push("opportunity.reopen"); show(<OpportunityDetail />); fireEvent.click(await screen.findByRole("button", { name: "Mở lại" })); fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Xác nhận" })); await waitFor(() => expect(state.calls.some(([url]) => url.endsWith("/reopen"))).toBe(true)); });
  it("requires a reason and closes the dialog with Escape", async () => { show(<OpportunityDetail />); const trigger = await screen.findByRole("button", { name: "Chốt thất bại" }); trigger.focus(); fireEvent.click(trigger); const dialog = screen.getByRole("dialog"); fireEvent.click(within(dialog).getByRole("button", { name: "Xác nhận" })); expect(within(dialog).getByText("Vui lòng nhập lý do.")).toBeVisible(); expect(within(dialog).getByLabelText("Lý do *")).toHaveAttribute("aria-invalid", "true"); fireEvent.keyDown(dialog, { key: "Escape" }); await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull()); expect(trigger).toHaveFocus(); });
  it("keeps keyboard focus inside the action dialog", async () => { show(<OpportunityDetail />); fireEvent.click(await screen.findByRole("button", { name: "Chốt thất bại" })); const dialog = screen.getByRole("dialog"); const focusable = [...dialog.querySelectorAll<HTMLElement>('button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex="0"]')]; focusable[0].focus(); fireEvent.keyDown(focusable[0], { key: "Tab", shiftKey: true }); expect(focusable.at(-1)).toHaveFocus(); });
  it("blocks a future closing date", async () => { show(<OpportunityDetail />); fireEvent.click(await screen.findByRole("button", { name: "Chốt thành công" })); const dialog = screen.getByRole("dialog"); fireEvent.change(within(dialog).getByLabelText("Ngày chốt thực tế *"), { target: { value: "31/12/2999" } }); fireEvent.click(within(dialog).getByRole("button", { name: "Xác nhận" })); expect(within(dialog).getByText("Kiểm tra giá trị chốt và ngày chốt thực tế.")).toBeVisible(); expect(state.calls.some(([url]) => url.endsWith("/win"))).toBe(false); });
});

describe("Opportunity review fixes", () => {
  const customerId = "00000000-0000-4000-8000-000000000001";
  it("keeps an inaccessible optional customer lookup on the current page", async () => {
    const navigate = vi.fn();
    const fetcher = vi.fn(() => Promise.resolve(Response.json({ code: "FORBIDDEN_SCOPE" }, { status: 403 })));
    const client = createApiClient({ fetcher, navigate, onExpired: vi.fn() });
    await expect(client.request(`/api/backend/customers/${customerId}`, undefined, { suppressForbiddenNavigation: true })).rejects.toMatchObject({ status: 403 });
    expect(navigate).not.toHaveBeenCalled();
  });
  it("filters by a selected customer and restores its name from the URL", async () => {
    window.history.replaceState({}, "", `/app/co-hoi?customerId=${customerId}`);
    show(<OpportunityPipeline />);
    expect(await screen.findByRole("option", { name: "Khách mẫu" })).toBeVisible();
    expect(screen.getByLabelText("Khách hàng")).toHaveValue(customerId);
    expect(state.calls.some(([url]) => url.includes(`/opportunities?`) && url.includes(`customerId=${customerId}`))).toBe(true);
    fireEvent.click(screen.getByRole("button", { name: "Bỏ lọc khách hàng" }));
    fireEvent.change(screen.getByLabelText("Khách hàng"), { target: { value: customerId } });
    await waitFor(() => expect(new URLSearchParams(window.location.search).get("customerId")).toBe(customerId));
  });
  it("resolves a customer outside the current list and falls back when inaccessible", async () => {
    state.customerMode = "other";
    window.history.replaceState({}, "", `/app/co-hoi?customerId=${customerId}`);
    const view = show(<OpportunityPipeline />);
    expect(await screen.findByRole("option", { name: "Khách từ hồ sơ" })).toBeVisible();
    view.unmount(); state.customerMode = "forbidden";
    show(<OpportunityPipeline />);
    expect(await screen.findByRole("option", { name: "Khách hàng đã chọn" })).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Bỏ lọc khách hàng" }));
    await waitFor(() => expect(new URLSearchParams(window.location.search).has("customerId")).toBe(false));
  });
  it("never sends an invalid URL customer ID", async () => {
    window.history.replaceState({}, "", "/app/co-hoi?customerId=not-a-uuid");
    show(<OpportunityPipeline />);
    expect(await screen.findByText(/Mã khách hàng không hợp lệ/)).toBeVisible();
    await screen.findByRole("heading", { name: "Cơ hội mẫu" });
    expect(state.calls.some(([url]) => url.includes("not-a-uuid"))).toBe(false);
  });
  it("also ignores an invalid customer ID in the create URL", async () => {
    state.roles = ["MANAGER"]; state.permissions.push("offering.manage");
    window.history.replaceState({}, "", "/app/co-hoi/them?customerId=not-a-uuid");
    show(<OpportunityForm />); await screen.findByRole("option", { name: "Dịch vụ mẫu" });
    fireEvent.change(screen.getByLabelText("Sản phẩm/dịch vụ *"), { target: { value: "offer-1" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu cơ hội" }));
    expect(screen.getByText("Chọn khách hàng.")).toBeVisible();
    expect(state.calls.some(([url]) => url.includes("not-a-uuid"))).toBe(false);
  });
  it("lets the user clear a customer that returns 404", async () => {
    state.customerMode = "missing";
    window.history.replaceState({}, "", `/app/co-hoi?customerId=${customerId}`);
    show(<OpportunityPipeline />);
    expect(await screen.findByRole("option", { name: "Khách hàng đã chọn" })).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Bỏ lọc khách hàng" }));
    expect(screen.getByLabelText("Khách hàng")).toHaveValue("");
  });
  it("debounces five pipeline keystrokes into one request", async () => {
    show(<OpportunityPipeline />); await screen.findByRole("heading", { name: "Cơ hội mẫu" });
    vi.useFakeTimers();
    try {
      state.calls = [];
      for (const value of ["a", "ab", "abc", "abcd", "abcde"]) fireEvent.change(screen.getByLabelText(/Tìm cơ hội/), { target: { value } });
      await act(async () => { await vi.advanceTimersByTimeAsync(300); });
      expect(state.calls.filter(([url]) => url.includes("/opportunities?")).map(([url]) => new URLSearchParams(url.split("?")[1]).get("search"))).toEqual(["abcde"]);
    } finally { vi.useRealTimers(); }
  });
  it("debounces five customer-search keystrokes in the form", async () => {
    show(<OpportunityForm />); await screen.findByRole("option", { name: "Dịch vụ mẫu" });
    vi.useFakeTimers();
    try {
      state.calls = [];
      for (const value of ["a", "ab", "abc", "abcd", "abcde"]) fireEvent.change(screen.getByLabelText("Tìm khách hàng"), { target: { value } });
      await act(async () => { await vi.advanceTimersByTimeAsync(300); });
      expect(state.calls.filter(([url]) => url.includes("/customers?")).map(([url]) => new URLSearchParams(url.split("?")[1]).get("search"))).toEqual(["abcde"]);
    } finally { vi.useRealTimers(); }
  });
  it("converts the discount in both directions without floating point", async () => {
    show(<OpportunityForm />); await screen.findByRole("option", { name: "Dịch vụ mẫu" });
    fireEvent.change(screen.getByLabelText("Sản phẩm/dịch vụ *"), { target: { value: "offer-1" } });
    fireEvent.change(screen.getByLabelText("Số lượng *"), { target: { value: "2" } });
    fireEvent.change(screen.getByLabelText("Chiết khấu"), { target: { value: "300000" } });
    fireEvent.change(screen.getByLabelText("Đơn vị chiết khấu"), { target: { value: "percent" } });
    expect(screen.getByLabelText("Chiết khấu")).toHaveValue("10.00");
    fireEvent.change(screen.getByLabelText("Đơn vị chiết khấu"), { target: { value: "money" } });
    expect(screen.getByLabelText("Chiết khấu")).toHaveValue("300000.00");
  });
  it("resets discount and announces when the price cannot be converted", async () => {
    show(<OpportunityForm />); await screen.findByRole("option", { name: "Dịch vụ mẫu" });
    fireEvent.change(screen.getByLabelText("Đơn giá *"), { target: { value: "" } });
    fireEvent.change(screen.getByLabelText("Chiết khấu"), { target: { value: "10" } });
    fireEvent.change(screen.getByLabelText("Đơn vị chiết khấu"), { target: { value: "percent" } });
    expect(screen.getByLabelText("Chiết khấu")).toHaveValue("0");
    expect(screen.getByRole("status")).toHaveTextContent("Chưa thể quy đổi chiết khấu");
  });
  it.each([[true], [false]])("handles owner selection for dual role: %s", async (dual) => {
    state.roles = dual ? ["MANAGER", "SALES_REP"] : ["MANAGER"]; state.permissions.push("offering.manage");
    show(<OpportunityForm />); await screen.findByRole("option", { name: "Dịch vụ mẫu" });
    fireEvent.change(screen.getByLabelText("Khách hàng *"), { target: { value: customerId } });
    fireEvent.change(screen.getByLabelText("Tên cơ hội *"), { target: { value: "Tên mới" } });
    fireEvent.change(screen.getByLabelText("Ngày dự kiến chốt *"), { target: { value: "01/12/2099" } });
    fireEvent.change(screen.getByLabelText("Sản phẩm/dịch vụ *"), { target: { value: "offer-1" } });
    if (dual) expect(screen.getByRole("option", { name: "Tôi phụ trách" })).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Lưu cơ hội" }));
    if (dual) { await waitFor(() => expect(state.replace).toHaveBeenCalled()); expect(JSON.parse(String(state.calls.find(([, init]) => init?.method === "POST")![1]?.body))).not.toHaveProperty("ownerUserId"); }
    else { expect(screen.getByText("Chọn người phụ trách.")).toBeVisible(); expect(state.calls.some(([, init]) => init?.method === "POST")).toBe(false); }
  });
  it("preserves the third draft and clears line errors when deleting the middle line", async () => {
    show(<OpportunityForm />); await screen.findByRole("option", { name: "Dịch vụ mẫu" });
    fireEvent.click(screen.getByRole("button", { name: "Thêm dòng" })); fireEvent.click(screen.getByRole("button", { name: "Thêm dòng" }));
    const third = within(screen.getByRole("group", { name: "Dòng 3" })).getByLabelText("Số lượng *");
    fireEvent.change(third, { target: { value: "7" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu cơ hội" }));
    expect(screen.getAllByText("Chọn sản phẩm khác nhau.")).toHaveLength(3);
    fireEvent.click(screen.getByRole("button", { name: "Xóa dòng 2" }));
    expect(within(screen.getByRole("group", { name: "Dòng 2" })).getByLabelText("Số lượng *")).toBe(third);
    expect(third).toHaveValue("7"); expect(screen.queryByText("Chọn sản phẩm khác nhau.")).toBeNull();
  });
});
