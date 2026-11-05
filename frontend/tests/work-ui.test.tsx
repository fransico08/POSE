import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { InteractionDetail } from "@/components/work/InteractionDetail";
import { InteractionForm } from "@/components/work/InteractionForm";
import { InteractionList } from "@/components/work/InteractionList";
import { TaskDetail } from "@/components/work/TaskDetail";
import { TaskForm } from "@/components/work/TaskForm";
import { TaskList } from "@/components/work/TaskList";
import { implementedNavigation } from "@/config/navigation";
import { instantToVietnamDateTime, vietnamDateTimeToInstant } from "@/lib/work-time";
import { ApiError } from "@/lib/api/client";
import { workError } from "@/lib/interactions";
import { WorkOpportunity, WorkSubject } from "@/components/work/WorkReferences";

const state = vi.hoisted(() => ({ roles: ["SALES_REP"], permissions: ["interaction.read", "interaction.write", "interaction.sensitive.read", "task.manage", "customer.read"], calls: [] as Array<[string, RequestInit | undefined]>, reject: null as unknown, taskStatus: "OPEN", priority: "MEDIUM", hidden: false, subjectForbidden: false, populated: false, replace: vi.fn() }));
vi.mock("next/navigation", () => ({ useParams: () => ({ id: "item-1" }), useRouter: () => ({ replace: state.replace }) }));
vi.mock("@/components/session/SessionGate", () => ({ useSession: () => ({ user: { id: "user-1", organization: { id: "org-1" }, roles: state.roles, permissions: state.permissions } }) }));
vi.mock("@/lib/api", () => ({ api: { request: vi.fn((url: string, init?: RequestInit) => {
  state.calls.push([url, init]); if (init?.method && state.reject) return Promise.reject(state.reject);
  if (url.includes("/customers?")) return state.subjectForbidden ? Promise.reject(new ApiError(403, "FORBIDDEN_SCOPE", "scope")) : Promise.resolve({ items: [{ id: "customer-1", displayName: "Khách mẫu" }], totalPages: 1 });
  if (url.includes("/users?")) return Promise.resolve({ items: [{ id: "owner-1", fullName: "Nhân viên mẫu", status: "ACTIVE", roles: ["SALES_REP"] }], totalPages: 1 });
  if (url.includes("/leads?")) return Promise.resolve({ items: url.includes("status=NEW") ? [{ id: "lead-1", displayName: "Lead mẫu", status: "NEW" }] : [{ id: "lead-2", displayName: "Lead đủ điều kiện", status: "QUALIFIED" }], totalPages: 1 });
  if (url.includes("/leads/lead-1")) return Promise.resolve({ id: "lead-1", convertedCustomerId: "customer-2" });
  if (url.includes("/opportunities?")) return Promise.resolve({ items: [{ id: "opp-1", title: "Cơ hội mẫu" }], totalPages: 1 });
  if (url.includes("/interactions?") || url.includes("/tasks?")) return Promise.resolve({ items: state.populated ? [{ id: "work-1", customerId: "customer-1", leadId: null, opportunityId: null, customer: { id: "customer-1", name: "Khách mẫu" }, subject: "Tương tác mẫu", title: "Nhiệm vụ mẫu", type: "CALL", channel: "PHONE", recordedByName: "Nhân viên", ownerName: "Nhân viên", priority: state.priority, status: "OPEN", occurredAt: "2026-11-03T17:30:00Z", dueAt: "2026-11-03T17:30:00Z" }] : [], page: 0, size: 20, totalItems: state.populated ? 1 : 0, totalPages: state.populated ? 1 : 0 });
  if (url.includes("/interactions")) return Promise.resolve({ id: "item-1", customerId: "customer-1", leadId: null, opportunityId: null, recordedByUserId: "user-1", recordedByName: "Nhân viên mẫu", type: "CALL", channel: "PHONE", subject: state.hidden ? null : "TEST-W06", content: state.hidden ? null : "Nội dung mẫu", outcome: null, visibility: "CARE_VISIBLE", occurredAt: "2026-11-03T17:30:00Z", updatedAt: "2026-11-03T00:00:00Z" });
  return Promise.resolve({ id: "item-1", customerId: "customer-1", leadId: null, opportunityId: null, ownerUserId: "user-1", ownerName: "Nhân viên mẫu", title: "TEST-W06", description: null, priority: state.priority, status: state.taskStatus, dueAt: "2026-11-03T17:30:00Z", result: null, cancellationReason: null, updatedAt: "2026-11-03T00:00:00Z" });
}) } }));
function show(node: React.ReactNode) { return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}>{node}</QueryClientProvider>); }
beforeEach(() => { state.roles = ["SALES_REP"]; state.permissions = ["interaction.read", "interaction.write", "interaction.sensitive.read", "task.manage", "customer.read"]; state.calls = []; state.reject = null; state.taskStatus = "OPEN"; state.priority = "MEDIUM"; state.hidden = false; state.subjectForbidden = false; state.populated = false; state.replace.mockReset(); });
describe("W06 task priorities and validation", () => {
  it("shows medium in list and mobile cards", async () => {
    state.populated = true; window.history.replaceState({}, "", "/app/nhiem-vu");
    const view = show(<TaskList />); const table = await screen.findByRole("table");
    expect(within(table).getByText("Trung bình")).toBeVisible();
    expect(within(view.container.querySelector(".offering-mobile-card") as HTMLElement).getByText(/Trung bình/)).toBeVisible();
  });
  it("keeps medium when editing and submits it", async () => {
    show(<TaskForm edit />); const priority = await screen.findByLabelText("Ưu tiên *");
    expect(priority).toHaveValue("MEDIUM"); fireEvent.click(screen.getByRole("button", { name: "Lưu nhiệm vụ" }));
    await waitFor(() => expect(state.calls.some(([url, init]) => url.includes("/tasks/item-1") && init?.method === "PATCH")).toBe(true));
    expect(JSON.parse(String(state.calls.find(([url, init]) => url.includes("/tasks/item-1") && init?.method === "PATCH")![1]?.body)).priority).toBe("MEDIUM");
  });
  it("preserves and displays an unknown priority", async () => {
    state.priority = "UNKNOWN"; state.populated = true; window.history.replaceState({}, "", "/app/nhiem-vu");
    const list = show(<TaskList />); expect(await within(await screen.findByRole("table")).findByText("UNKNOWN")).toBeVisible();
    list.unmount(); show(<TaskForm edit />);
    const priority = await screen.findByLabelText("Ưu tiên *");
    expect(priority).toHaveValue("UNKNOWN"); expect(screen.getByRole("option", { name: "UNKNOWN (không hợp lệ)" })).toBeVisible();
  });
  it.each([["interaction", InteractionForm], ["task", TaskForm]])("shows server validation notice once in %s", async (kind, Component) => {
    state.reject = new ApiError(400, "VALIDATION_ERROR", "invalid", [{ field: kind === "interaction" ? "subject" : "title", message: "Trường không hợp lệ" }]);
    show(<Component edit />);
    await screen.findByLabelText("Tiêu đề *");
    fireEvent.click(screen.getByRole("button", { name: kind === "interaction" ? "Lưu tương tác" : "Lưu nhiệm vụ" }));
    await screen.findByText("Trường không hợp lệ");
    expect(screen.getAllByText("Vui lòng kiểm tra các trường được đánh dấu.")).toHaveLength(1);
  });
});
describe("W06 subject references", () => {
  it("shows references without links when the corresponding permission is absent", () => {
    state.permissions = []; const item = { customerId: "customer-1", leadId: null, opportunityId: "opp-1", customer: { id: "customer-1", name: "Khách mẫu" }, opportunity: { id: "opp-1", title: null } };
    const view = show(<><WorkSubject item={item} /><WorkOpportunity item={item} /></>);
    expect(view.container).toHaveTextContent("Khách mẫu"); expect(view.container).toHaveTextContent("Cơ hội liên quan"); expect(view.container.querySelector("a")).toBeNull();
    view.unmount();
    const leadView = show(<WorkSubject item={{ customerId: null, leadId: "lead-1", opportunityId: null, lead: { id: "lead-1", name: "Lead mẫu" } }} />);
    expect(leadView.container).toHaveTextContent("Lead mẫu"); expect(screen.queryByRole("link")).toBeNull();
  });
  it("does not link an opportunity whose title was redacted despite the read permission", () => {
    state.roles = ["DATA_STAFF"]; state.permissions = ["opportunity.read"];
    const view = show(<WorkOpportunity item={{ customerId: null, leadId: null, opportunityId: "opp-1", opportunity: { id: "opp-1", title: null } }} />);
    expect(view.container).toHaveTextContent("Cơ hội liên quan"); expect(screen.queryByRole("link")).toBeNull();
  });
  it("only requests open lead statuses", async () => {
    show(<InteractionForm />); fireEvent.change(screen.getByLabelText("Loại đối tượng"), { target: { value: "lead" } });
    await screen.findByRole("option", { name: "Lead mẫu" }); await screen.findByRole("option", { name: "Lead đủ điều kiện" });
    expect(state.calls.some(([url]) => url.includes("/leads?") && url.includes("status=NEW"))).toBe(true);
    expect(state.calls.some(([url]) => url.includes("/leads?") && url.includes("status=QUALIFIED"))).toBe(true);
    expect(state.calls.filter(([url]) => url.includes("/leads?")).every(([url]) => !url.includes("CONVERTED") && !url.includes("DISQUALIFIED"))).toBe(true);
  });
  it.each([["interaction", InteractionForm], ["task", TaskForm]])("shows the converted customer after a closed lead error in %s", async (kind, Component) => {
    state.permissions.push("lead.read"); state.reject = new ApiError(409, "LEAD_CLOSED", "closed"); show(<Component />);
    fireEvent.change(screen.getByLabelText("Loại đối tượng"), { target: { value: "lead" } });
    await screen.findByRole("option", { name: "Lead mẫu" }); fireEvent.change(screen.getByLabelText("Lead"), { target: { value: "lead-1" } });
    fireEvent.change(screen.getByLabelText("Tiêu đề *"), { target: { value: "Ví dụ" } });
    if (kind === "interaction") fireEvent.change(screen.getByLabelText("Nội dung *"), { target: { value: "Ví dụ" } });
    else fireEvent.change(screen.getByLabelText("Hạn *"), { target: { value: "04/11/2026 12:00" } });
    fireEvent.click(screen.getByRole("button", { name: kind === "interaction" ? "Lưu tương tác" : "Lưu nhiệm vụ" }));
    expect(await screen.findByText(/Lead đã đóng; hãy chọn khách hàng đã chuyển đổi/)).toBeVisible();
    expect(await screen.findByRole("link", { name: "Xem khách hàng" })).toHaveAttribute("href", "/app/khach-hang/customer-2");
  });
  it.each([["interaction", InteractionList], ["task", TaskList]])("shows the subject in the %s table and mobile card", async (_, Component) => {
    state.populated = true; window.history.replaceState({}, "", "/app/test");
    const view = show(<Component />);
    const table = await screen.findByRole("table");
    expect(within(table).getByRole("columnheader", { name: "Đối tượng" })).toBeInTheDocument();
    expect(within(table).getByRole("link", { name: "Khách mẫu" })).toHaveAttribute("href", "/app/khach-hang/customer-1");
    const card = view.container.querySelector(".offering-mobile-card")!;
    expect(within(card as HTMLElement).getByRole("link", { name: "Khách mẫu" })).toHaveAttribute("href", "/app/khach-hang/customer-1");
  });
  it("loads and selects opportunities using a lead filter", async () => {
    state.permissions.push("opportunity.read"); show(<InteractionForm />);
    fireEvent.change(screen.getByLabelText("Loại đối tượng"), { target: { value: "lead" } });
    await screen.findByRole("option", { name: "Lead mẫu" });
    fireEvent.change(screen.getByLabelText("Lead"), { target: { value: "lead-1" } });
    await screen.findByRole("option", { name: "Cơ hội mẫu" });
    fireEvent.change(screen.getByLabelText("Cơ hội bán hàng (tùy chọn)"), { target: { value: "opp-1" } });
    expect(screen.getByLabelText("Cơ hội bán hàng (tùy chọn)")).toHaveValue("opp-1");
    expect(state.calls.some(([url]) => url.includes("/opportunities?") && url.includes("leadId=lead-1") && !url.includes("customerId="))).toBe(true);
  });
  it("renders old responses without named fields safely", async () => {
    show(<InteractionDetail />);
    expect(await screen.findByRole("link", { name: "Chưa cập nhật" })).toHaveAttribute("href", "/app/khach-hang/customer-1");
    expect(screen.getByText("Cơ hội: Không có")).toBeInTheDocument();
  });
});
describe("W06 access and time", () => {
  it("converts Vietnam local times across midnight", () => { expect(vietnamDateTimeToInstant("04/11/2026 00:30")).toBe("2026-11-03T17:30:00.000Z"); expect(instantToVietnamDateTime("2026-11-03T17:30:00Z")).toBe("04/11/2026 00:30"); });
  it("maps 404 and network failures to friendly messages", () => { expect(workError(new ApiError(404, "NOT_FOUND", "missing"))).toBe("Không tìm thấy dữ liệu."); expect(workError(new ApiError(503, "NETWORK_ERROR", "offline"))).toContain("Không thể kết nối"); });
  it("only exposes menus with permissions", () => { expect(implementedNavigation(["interaction.read"], ["DATA_STAFF"]).some(item => item.route === "/app/tuong-tac")).toBe(true); expect(implementedNavigation([], ["SALES_REP"]).some(item => item.route === "/app/nhiem-vu")).toBe(false); });
  it("does not let Admin open create routes", () => { state.roles = ["ADMIN"]; const view = show(<InteractionForm />); expect(screen.getByText("Không có quyền truy cập")).toBeVisible(); view.unmount(); show(<TaskForm />); expect(screen.getByText("Không có quyền truy cập")).toBeVisible(); });
  it("shows redacted interaction to Data Staff", async () => { state.roles = ["DATA_STAFF"]; state.permissions = ["interaction.read"]; state.hidden = true; show(<InteractionDetail />); expect(await screen.findByRole("heading", { name: "Nội dung bị ẩn theo quyền" })).toBeVisible(); });
  it("explains when Data Staff cannot load the subject picker", async () => { state.roles = ["DATA_STAFF"]; state.permissions = ["interaction.read"]; state.subjectForbidden = true; show(<InteractionList />); expect(await screen.findByText("Bạn không có quyền tải danh sách đối tượng; bộ chọn chưa khả dụng.")).toBeVisible(); });
  it("hides SALES_ONLY without sensitive permission", () => { state.roles = ["CUSTOMER_CARE"]; state.permissions = ["interaction.read", "interaction.write", "customer.read"]; show(<InteractionForm />); expect(screen.queryByRole("option", { name: "Chỉ bán hàng" })).toBeNull(); });
});
describe("W06 forms and actions", () => {
  it("validates required interaction fields and associates the errors", async () => { show(<InteractionForm />); await screen.findByRole("option", { name: "Khách mẫu" }); fireEvent.click(screen.getByRole("button", { name: "Lưu tương tác" })); expect(screen.getByLabelText("Tiêu đề *")).toHaveAttribute("aria-invalid", "true"); expect(screen.getByText("Nhập nội dung.")).toBeVisible(); });
  it("attaches interaction validation returned by the server", async () => { state.reject = new ApiError(400, "VALIDATION_ERROR", "invalid", [{ field: "channel", message: "Mã kênh không hợp lệ" }]); show(<InteractionForm />); await screen.findByRole("option", { name: "Khách mẫu" }); fireEvent.change(screen.getByLabelText("Khách hàng"), { target: { value: "customer-1" } }); expect(screen.getByLabelText("Khách hàng")).toHaveValue("customer-1"); fireEvent.change(screen.getByLabelText("Tiêu đề *"), { target: { value: "TEST-W06" } }); expect(screen.getByLabelText("Khách hàng")).toHaveValue("customer-1"); fireEvent.change(screen.getByLabelText("Nội dung *"), { target: { value: "Nội dung mẫu" } }); expect(screen.getByLabelText("Khách hàng")).toHaveValue("customer-1"); fireEvent.click(screen.getByRole("button", { name: "Lưu tương tác" })); expect(await screen.findByText("Mã kênh không hợp lệ")).toBeVisible(); expect(screen.getByLabelText("Kênh *")).toHaveAttribute("aria-describedby", "work-error-channel"); });
  it("shows server field errors for task owner", async () => { state.roles = ["MANAGER"]; state.permissions.push("identity.user.read"); state.reject = new ApiError(422, "VALIDATION_ERROR", "invalid", [{ field: "ownerUserId", message: "Owner không hợp lệ" }]); show(<TaskForm />); await screen.findByRole("option", { name: "Khách mẫu" }); fireEvent.change(screen.getByLabelText("Khách hàng"), { target: { value: "customer-1" } }); fireEvent.change(screen.getByLabelText("Tiêu đề *"), { target: { value: "Nhiệm vụ" } }); fireEvent.change(screen.getByLabelText("Hạn *"), { target: { value: "04/11/2026 12:00" } }); await screen.findByRole("option", { name: "Nhân viên mẫu" }); fireEvent.change(screen.getByLabelText("Người phụ trách *"), { target: { value: "owner-1" } }); fireEvent.click(screen.getByRole("button", { name: "Lưu nhiệm vụ" })); expect(await screen.findByText("Owner không hợp lệ")).toBeVisible(); expect(screen.getByLabelText("Người phụ trách *")).toHaveAttribute("aria-invalid", "true"); });
  it("hides task actions after completion", async () => { state.taskStatus = "DONE"; show(<TaskDetail />); await screen.findByRole("heading", { name: "TEST-W06" }); expect(screen.queryByRole("link", { name: "Chỉnh sửa" })).toBeNull(); expect(screen.queryByRole("button", { name: "Giao lại" })).toBeNull(); });
  it("requires a result and restores focus after Escape", async () => { show(<TaskDetail />); const button = await screen.findByRole("button", { name: "Hoàn tất" }); button.focus(); fireEvent.click(button); const dialog = screen.getByRole("dialog"); fireEvent.click(within(dialog).getByRole("button", { name: "Xác nhận" })); expect(within(dialog).getByText("Vui lòng nhập thông tin bắt buộc.")).toBeVisible(); fireEvent.keyDown(dialog, { key: "Escape" }); await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull()); expect(button).toHaveFocus(); });
  it("only shows reassignment for a permitted manager", async () => { state.roles = ["MANAGER"]; state.permissions.push("identity.user.read"); show(<TaskDetail />); expect(await screen.findByRole("button", { name: "Giao lại" })).toBeVisible(); });
  it("reassigns a task using the selected owner and version", async () => { state.roles = ["MANAGER"]; state.permissions.push("identity.user.read"); show(<TaskDetail />); fireEvent.click(await screen.findByRole("button", { name: "Giao lại" })); const dialog = screen.getByRole("dialog"); await within(dialog).findByRole("option", { name: "Nhân viên mẫu" }); fireEvent.change(within(dialog).getByLabelText("Người phụ trách *"), { target: { value: "owner-1" } }); fireEvent.click(within(dialog).getByRole("button", { name: "Xác nhận" })); await waitFor(() => expect(state.calls.some(([url]) => url.endsWith("/assign"))).toBe(true)); const body = JSON.parse(String(state.calls.find(([url]) => url.endsWith("/assign"))![1]?.body)); expect(body).toMatchObject({ ownerUserId: "owner-1", expectedUpdatedAt: "2026-11-03T00:00:00Z" }); });
  it("keeps the interaction draft and offers reload on conflict", async () => { state.reject = new ApiError(409, "INTERACTION_CHANGED", "changed"); show(<InteractionForm edit />); const title = await screen.findByLabelText("Tiêu đề *"); fireEvent.change(title, { target: { value: "Bản nháp" } }); fireEvent.click(screen.getByRole("button", { name: "Lưu tương tác" })); expect(await screen.findByRole("button", { name: "Tải lại" })).toBeVisible(); expect(title).toHaveValue("Bản nháp"); });
  it("shows a task state conflict with a reload control", async () => { state.reject = new ApiError(409, "TASK_STATE_CONFLICT", "changed"); show(<TaskDetail />); fireEvent.click(await screen.findByRole("button", { name: "Bắt đầu" })); fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Xác nhận" })); expect(await within(screen.getByRole("dialog")).findByRole("button", { name: "Tải lại" })).toBeVisible(); });
  it("keeps the selected interaction filter in the URL", async () => { window.history.replaceState({}, "", "/app/tuong-tac"); show(<InteractionList />); await screen.findByText("Không có tương tác phù hợp"); fireEvent.change(screen.getByLabelText("Loại"), { target: { value: "CALL" } }); await waitFor(() => expect(new URLSearchParams(window.location.search).get("type")).toBe("CALL")); expect(state.calls.some(([url]) => url.includes("/interactions?") && url.includes("type=CALL"))).toBe(true); });
  it("filters tasks by status and renders mobile card container", async () => { window.history.replaceState({}, "", "/app/nhiem-vu"); const view = show(<TaskList />); await screen.findByText("Không có nhiệm vụ phù hợp"); fireEvent.change(screen.getByLabelText("Trạng thái"), { target: { value: "OPEN" } }); await waitFor(() => expect(new URLSearchParams(window.location.search).get("status")).toBe("OPEN")); await waitFor(() => expect(view.container.querySelector(".offering-mobile-cards")).not.toBeNull()); });
  it.each([["Bắt đầu", "start"], ["Hoàn tất", "complete"], ["Hủy nhiệm vụ", "cancel"]])("sends task action %s", async (label, command) => { show(<TaskDetail />); fireEvent.click(await screen.findByRole("button", { name: label })); const dialog = screen.getByRole("dialog"); if (command !== "start") fireEvent.change(within(dialog).getByLabelText(command === "complete" ? "Kết quả *" : "Lý do *"), { target: { value: "Nội dung mẫu" } }); fireEvent.click(within(dialog).getByRole("button", { name: "Xác nhận" })); await waitFor(() => expect(state.calls.some(([url, init]) => url.endsWith(`/${command}`) && init?.method === "POST")).toBe(true)); const body = JSON.parse(String(state.calls.find(([url]) => url.endsWith(`/${command}`))![1]?.body)); expect(body.expectedUpdatedAt).toBe("2026-11-03T00:00:00Z"); });
  it("traps keyboard focus in the task dialog", async () => { show(<TaskDetail />); fireEvent.click(await screen.findByRole("button", { name: "Hoàn tất" })); const dialog = screen.getByRole("dialog"); const controls = [...dialog.querySelectorAll<HTMLElement>('button:not(:disabled), textarea:not(:disabled)')]; controls[0].focus(); fireEvent.keyDown(controls[0], { key: "Tab", shiftKey: true }); expect(controls.at(-1)).toHaveFocus(); });
  it("keeps a task draft on version conflict", async () => { state.reject = new ApiError(409, "TASK_CHANGED", "changed"); show(<TaskForm edit />); const title = await screen.findByLabelText("Tiêu đề *"); fireEvent.change(title, { target: { value: "Bản nháp" } }); fireEvent.click(screen.getByRole("button", { name: "Lưu nhiệm vụ" })); expect(await screen.findByRole("button", { name: "Tải lại" })).toBeVisible(); expect(title).toHaveValue("Bản nháp"); });
});
