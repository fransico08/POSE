import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { TodayWork } from "@/components/overview/TodayWork";
import { ApiError } from "@/lib/api/client";

const state = vi.hoisted(() => ({ response: null as unknown, error: null as unknown, calls: 0, pending: false }));
vi.mock("@/components/session/SessionGate", () => ({ useSession: () => ({ user: { id: "user", organization: { id: "org" }, permissions: ["dashboard.read"] } }) }));
vi.mock("@/lib/api", () => ({ api: { request: async () => { state.calls++; if (state.pending) return new Promise(() => {}); if (state.error) throw state.error; return state.response; } } }));
function view() { return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><main><TodayWork /></main></QueryClientProvider>); }
beforeEach(() => { state.response = { applicable: true, items: [] }; state.error = null; state.calls = 0; state.pending = false; });

it("hides the section when it is not applicable", async () => {
  state.response = { applicable: false, items: [] }; view();
  await vi.waitFor(() => expect(screen.queryByRole("heading", { name: "Việc hôm nay" })).not.toBeInTheDocument());
  expect(state.calls).toBe(1);
});
it("shows an empty state", async () => {
  view(); expect(await screen.findByText("Hôm nay chưa có việc cần ưu tiên")).toBeInTheDocument();
});
it("announces loading without adding another main landmark", () => {
  state.pending = true; view();
  expect(screen.getAllByRole("main")).toHaveLength(1);
  expect(screen.getByRole("status")).toHaveTextContent("Đang tải việc hôm nay");
});
it("allows retry after a network error", async () => {
  state.error = new ApiError(503, "NETWORK_ERROR", "error"); view();
  const button = await screen.findByRole("button", { name: "Thử lại" });
  state.error = null; fireEvent.click(button);
  expect(await screen.findByText("Hôm nay chưa có việc cần ưu tiên")).toBeInTheDocument();
});
it("renders semantic links, reasons and keyboard focus", async () => {
  state.response = { applicable: true, items: [{ type: "OVERDUE_TASK", rank: 1, subjectId: "task-1", title: "Gọi khách", ownerName: "Lê Văn Sale", reason: "Đã quá hạn 2 ngày", href: "/app/nhiem-vu/task-1" }] };
  view(); const link = await screen.findByRole("link", { name: "Mở Gọi khách" });
  expect(link).toHaveAttribute("href", "/app/nhiem-vu/task-1");
  expect(screen.getByRole("list")).toBeInTheDocument(); expect(screen.getByText("Đã quá hạn 2 ngày")).toBeInTheDocument(); expect(screen.getByText("Phụ trách: Lê Văn Sale")).toBeInTheDocument();
  link.focus(); expect(document.activeElement).toBe(link);
});
it("shows five ranked items and expands or collapses the rest", async () => {
  state.response = { applicable: true, items: [6, 5, 4, 3, 2, 1].map(rank => ({ type: "OVERDUE_TASK", rank, subjectId: `task-${rank}`, title: `Việc ${rank}`, reason: "Cần xử lý", href: `/app/nhiem-vu/${rank}` })) };
  view();
  expect(await screen.findByRole("link", { name: "Mở Việc 1" })).toBeInTheDocument();
  expect(screen.queryByRole("link", { name: "Mở Việc 6" })).not.toBeInTheDocument();
  const toggle = screen.getByRole("button", { name: "Xem thêm (1)" });
  expect(toggle).toHaveAttribute("aria-expanded", "false");
  expect(document.getElementById(toggle.getAttribute("aria-controls")!)).toBeInTheDocument();
  fireEvent.click(toggle);
  expect(screen.getByRole("link", { name: "Mở Việc 6" })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Thu gọn" })).toHaveAttribute("aria-expanded", "true");
  fireEvent.click(screen.getByRole("button", { name: "Thu gọn" }));
  expect(screen.queryByRole("link", { name: "Mở Việc 6" })).not.toBeInTheDocument();
});
