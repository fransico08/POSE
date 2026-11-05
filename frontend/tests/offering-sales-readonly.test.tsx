import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { OfferingList } from "@/components/offerings/OfferingList";
import { OfferingDetail } from "@/components/offerings/OfferingDetail";

const state = vi.hoisted(() => ({ roles: ["SALES_REP"], permissions: ["offering.read"], active: true, code: "SAMPLE", name: "Dịch vụ mẫu" }));
vi.mock("next/navigation", () => ({ useParams: () => ({ id: "offering-1" }) }));
vi.mock("@/components/session/SessionGate", () => ({ useSession: () => ({ user: { id: "sale-1", roles: state.roles, permissions: state.permissions, organization: { id: "org-1" } } }) }));
const fixture = { id: "offering-1", name: "Dịch vụ mẫu", code: "SAMPLE", kind: "SERVICE", categoryId: "category", categoryName: "Dịch vụ", active: true, teamName: "Team A", createdAt: "2026-10-01T00:00:00Z", updatedAt: "2026-10-01T00:00:00Z", referencePrice: null, currencyCode: null, pricingUnit: null, referencePriceEffectiveFrom: null, unit: null, description: null };
vi.mock("@/lib/api", () => ({ api: { request: vi.fn((path: string) => Promise.resolve(path.endsWith("/categories") ? [] : path.includes("?") ? { items: [{ ...fixture, active: state.active, code: state.code, name: state.name }], page: 0, totalItems: 1, totalPages: 1 } : { ...fixture, active: state.active, code: state.code, name: state.name })) } }));

function renderWithQuery(component: React.ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{component}</QueryClientProvider>);
}

describe("Sales offering access", () => {
  it("shows an assigned item without create or edit links", async () => {
    state.code = "SAMPLE"; state.name = "Dịch vụ mẫu";
    renderWithQuery(<OfferingList />);
    expect((await screen.findAllByText("Dịch vụ mẫu")).length).toBeGreaterThan(0);
    expect(screen.queryByRole("link", { name: /thêm sản phẩm|chỉnh sửa|^sửa$/i })).not.toBeInTheDocument();
  });
  it("shows offering detail without edit, activation or deactivation controls", async () => {
    state.roles = ["SALES_REP"]; state.permissions = ["offering.read"]; state.active = true; state.code = "SAMPLE"; state.name = "Dịch vụ mẫu";
    renderWithQuery(<OfferingDetail />);
    expect(await screen.findByRole("heading", { name: "Dịch vụ mẫu" })).toBeVisible();
    expect(screen.queryByRole("link", { name: /chỉnh sửa/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /kích hoạt/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Tạo Lead cho sản phẩm này" })).not.toBeInTheDocument();
  });
  it("shows lead creation shortcut on active offering only when canCreateCrm(lead) is satisfied", async () => {
    state.roles = ["SALES_REP"]; state.permissions = ["offering.read", "lead.write"]; state.active = true; state.code = "SAMPLE"; state.name = "Dịch vụ mẫu";
    const activeView = renderWithQuery(<OfferingDetail />);
    expect(await screen.findByRole("link", { name: "Tạo Lead cho sản phẩm này" })).toHaveAttribute("href", "/app/lead/them?offeringId=offering-1");
    activeView.unmount();
    state.active = false;
    const inactiveView = renderWithQuery(<OfferingDetail />);
    await screen.findByRole("heading", { name: "Dịch vụ mẫu" });
    expect(screen.queryByRole("link", { name: "Tạo Lead cho sản phẩm này" })).not.toBeInTheDocument();
    inactiveView.unmount();
  });
  it("wraps long industry-specific offering codes and names inside mobile cards without overflowing", async () => {
    state.roles = ["MANAGER"]; state.permissions = ["offering.read", "offering.manage"]; state.active = true;
    state.code = "STANDARD_NATIONWIDE"; state.name = "Vận chuyển tiêu chuẩn toàn quốc";
    const view = renderWithQuery(<OfferingList />);
    await screen.findAllByText("Vận chuyển tiêu chuẩn toàn quốc");
    const mobileCard = view.container.querySelector(".offering-mobile-card") as HTMLElement;
    expect(mobileCard).not.toBeNull();
    expect(within(mobileCard).getByText("STANDARD_NATIONWIDE")).toHaveClass("offering-code");
    const offeringsCss = readFileSync(resolve(process.cwd(), "src/styles/offerings.css"), "utf8");
    const assignmentsCss = readFileSync(resolve(process.cwd(), "src/styles/sales-assignments.css"), "utf8");
    expect(offeringsCss).toMatch(/\.offering-code[^{]*\{[^}]*overflow-wrap:\s*anywhere/);
    expect(offeringsCss).toMatch(/\.offering-mobile-card\s*\{[^}]*min-width:\s*0/);
    expect(offeringsCss).toMatch(/\.offering-mobile-card\s*>\s*header\s*>\s*:first-child\s*\{[^}]*min-width:\s*0/);
    expect(assignmentsCss).toMatch(/\.sales-assignment-mobile-card\s*\{[^}]*min-width:\s*0/);
  });
});
