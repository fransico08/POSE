import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { OfferingList } from "@/components/offerings/OfferingList";
import { OfferingDetail } from "@/components/offerings/OfferingDetail";

const state = vi.hoisted(() => ({ roles: ["SALES_REP"], permissions: ["offering.read"], active: true }));
vi.mock("next/navigation", () => ({ useParams: () => ({ id: "offering-1" }) }));
vi.mock("@/components/session/SessionGate", () => ({ useSession: () => ({ user: { id: "sale-1", roles: state.roles, permissions: state.permissions, organization: { id: "org-1" } } }) }));
const fixture = { id: "offering-1", name: "Dịch vụ mẫu", code: "SAMPLE", kind: "SERVICE", categoryId: "category", categoryName: "Dịch vụ", active: true, teamName: "Team A", createdAt: "2026-10-01T00:00:00Z", updatedAt: "2026-10-01T00:00:00Z", referencePrice: null, currencyCode: null, pricingUnit: null, referencePriceEffectiveFrom: null, unit: null, description: null };
vi.mock("@/lib/api", () => ({ api: { request: vi.fn((path: string) => Promise.resolve(path.includes("?") ? { items: [{ ...fixture, active: state.active }], page: 0, totalItems: 1, totalPages: 1 } : { ...fixture, active: state.active })) } }));

function renderWithQuery(component: React.ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{component}</QueryClientProvider>);
}

describe("Sales offering access", () => {
  it("shows an assigned item without create or edit links", async () => {
    renderWithQuery(<OfferingList />);
    expect((await screen.findAllByText("Dịch vụ mẫu")).length).toBeGreaterThan(0);
    expect(screen.queryByRole("link", { name: /thêm sản phẩm|chỉnh sửa|^sửa$/i })).not.toBeInTheDocument();
  });
  it("shows offering detail without edit, activation or deactivation controls", async () => {
    state.roles = ["SALES_REP"]; state.permissions = ["offering.read"]; state.active = true;
    renderWithQuery(<OfferingDetail />);
    expect(await screen.findByRole("heading", { name: "Dịch vụ mẫu" })).toBeVisible();
    expect(screen.queryByRole("link", { name: /chỉnh sửa/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /kích hoạt/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Tạo Lead cho sản phẩm này" })).not.toBeInTheDocument();
  });
  it("shows lead creation shortcut on active offering only when canCreateCrm(lead) is satisfied", async () => {
    state.roles = ["SALES_REP"]; state.permissions = ["offering.read", "lead.write"]; state.active = true;
    const activeView = renderWithQuery(<OfferingDetail />);
    expect(await screen.findByRole("link", { name: "Tạo Lead cho sản phẩm này" })).toHaveAttribute("href", "/app/lead/them?offeringId=offering-1");
    activeView.unmount();
    state.active = false;
    const inactiveView = renderWithQuery(<OfferingDetail />);
    await screen.findByRole("heading", { name: "Dịch vụ mẫu" });
    expect(screen.queryByRole("link", { name: "Tạo Lead cho sản phẩm này" })).not.toBeInTheDocument();
    inactiveView.unmount();
  });
});
