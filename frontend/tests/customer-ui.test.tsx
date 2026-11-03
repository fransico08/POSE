import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { CrmForm } from "@/components/crm/CrmForm";
import { CrmDetail } from "@/components/crm/CrmDetail";
import { ApiError } from "@/lib/api/client";

const state = vi.hoisted(() => ({ roles: ["SALES_REP"], permissions: ["customer.read", "customer.write", "offering.read"], reject: null as unknown, push: vi.fn(), calls: [] as Array<[string, RequestInit | undefined]> }));
vi.mock("next/navigation", () => ({ useParams: () => ({ id: "customer-1" }), useRouter: () => ({ replace: state.push }) }));
vi.mock("@/components/session/SessionGate", () => ({ useSession: () => ({ user: { id: "user-1", organization: { id: "org-1" }, roles: state.roles, permissions: state.permissions } }) }));
vi.mock("@/lib/api", () => ({ api: { request: vi.fn((path: string, init?: RequestInit) => {
  state.calls.push([path, init]);
  if (init?.method && state.reject) return Promise.reject(state.reject);
  if (path.includes("/offerings")) return Promise.resolve({ items: [{ id: "off-1", name: "Dịch vụ mẫu", active: true }], page: 0, totalPages: 1, totalItems: 1 });
  return Promise.resolve({ id: "customer-1", displayName: "Hồ sơ mẫu", email: "sample@example.test", phone: null, customerType: "INDIVIDUAL", relatedOfferingId: "off-1", offeringName: "Dịch vụ mẫu", ownerName: "Nhân viên", address: null, notes: null, status: "ACTIVE", updatedAt: "2026-11-03T01:00:00Z" });
}) } }));
function show(element: React.ReactNode) { return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}>{element}</QueryClientProvider>); }
beforeEach(() => { state.roles = ["SALES_REP"]; state.permissions = ["customer.read", "customer.write", "offering.read"]; state.reject = null; state.calls = []; state.push.mockReset(); window.sessionStorage.clear(); window.history.replaceState({}, "", "/app/khach-hang"); vi.spyOn(window, "confirm").mockReturnValue(true); });
async function fillCustomer() {
  fireEvent.change(screen.getByLabelText("Tên *"), { target: { value: "Hồ sơ mẫu" } });
  fireEvent.change(screen.getByLabelText("Email"), { target: { value: "sample@example.test" } });
  fireEvent.change(screen.getByLabelText("Loại khách hàng *"), { target: { value: "INDIVIDUAL" } });
  fireEvent.change(await screen.findByLabelText(/Sản phẩm\/dịch vụ liên quan/), { target: { value: "off-1" } });
}
describe("Customer form and profile", () => {
  it("creates a customer through the BFF and provides a success marker", async () => {
    show(<CrmForm kind="customer" id={null} />); await screen.findByRole("option", { name: "Dịch vụ mẫu" }); await fillCustomer(); fireEvent.click(screen.getByRole("button", { name: "Tạo khách hàng" }));
    await waitFor(() => expect(state.push).toHaveBeenCalledWith("/app/khach-hang/customer-1"));
    const call = state.calls.find(([, init]) => init?.method === "POST")!;
    expect(call[0]).toBe("/api/backend/customers"); expect(JSON.parse(String(call[1]?.body))).toMatchObject({ relatedOfferingId: "off-1", customerType: "INDIVIDUAL", address: null });
    expect(window.sessionStorage.getItem("pose-customer-saved")).toBe("customer-1:create");
  });
  it("shows a readable duplicate customer link only if the backend supplies the id", async () => {
    state.reject = new ApiError(409, "CUSTOMER_DUPLICATE", "duplicate", [], { duplicateId: "00000000-0000-0000-0000-000000000001" });
    show(<CrmForm kind="customer" id={null} />); await screen.findByRole("option", { name: "Dịch vụ mẫu" }); await fillCustomer(); fireEvent.click(screen.getByRole("button", { name: "Tạo khách hàng" }));
    expect(await screen.findByRole("link", { name: "Mở hồ sơ đã có" })).toHaveAttribute("href", "/app/khach-hang/00000000-0000-0000-0000-000000000001");
  });
  it("shows the creation message and omits out-of-scope features", async () => {
    window.sessionStorage.setItem("pose-customer-saved", "customer-1:create"); show(<CrmDetail kind="customer" />);
    expect(await screen.findByText("Tạo khách hàng thành công")).toBeVisible();
    expect(screen.queryByRole("link", { name: /360|bàn giao|cơ hội|tương tác|nhiệm vụ/i })).toBeNull();
  });
  it("does not let Admin create a customer even with write permission", () => { state.roles = ["ADMIN"]; show(<CrmForm kind="customer" id={null} />); expect(screen.getByText("Bạn không có quyền thực hiện thao tác này")).toBeVisible(); });
});
