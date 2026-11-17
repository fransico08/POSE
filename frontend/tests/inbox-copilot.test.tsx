import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { InboxCopilot } from "@/components/inbox/InboxCopilot";

const state = vi.hoisted(() => ({ roles: ["SALES_REP"], permissions: ["conversation.reply", "ai.assist.use"], enabled: true, hold: false, holdReview: false, resolveReview: null as ((value: unknown) => void) | null, failure: "", calls: [] as Array<[string, RequestInit | undefined]> }));
const item = { id: "s1", invocationId: "i1", kind: "REPLY_DRAFT", subjectType: "CONVERSATION", subjectId: "c1", content: JSON.stringify({ draft: "Cảm ơn bạn.", sources: ["MESSAGE:m1"] }), evidence: null, status: "PROPOSED", finalContent: null, reviewedBy: null, reviewedAt: null, createdAt: "2026-11-17T00:00:00Z" };
vi.mock("@/components/session/SessionGate", () => ({ useSession: () => ({ user: { id: "u1", organization: { id: "o1" }, roles: state.roles, permissions: state.permissions } }) }));
vi.mock("@/lib/api", () => ({ api: { request: vi.fn((url: string, init?: RequestInit) => {
  state.calls.push([url, init]);
  if (url.endsWith("/ai/status")) return Promise.resolve({ enabled: state.enabled });
  if (url.includes("/ai/suggestions?")) return Promise.resolve([]);
  if (url.endsWith("/reply-draft")) return state.hold ? new Promise(() => {}) : Promise.resolve(state.failure ? { status: state.failure, errorCode: state.failure === "FAILED" ? "TIMEOUT" : null, suggestion: null } : { status: "SUCCEEDED", reused: false, suggestion: { ...item, subjectId: url.includes("/c2/") ? "c2" : "c1" } });
  if (url.endsWith("/accept")) return state.holdReview ? new Promise(resolve => { state.resolveReview = resolve; }) : Promise.resolve({ ...item, status: JSON.parse(String(init?.body)).finalContent ? "EDITED" : "ACCEPTED" });
  if (url.endsWith("/reject")) return Promise.resolve({ ...item, status: "REJECTED" });
  return Promise.reject(new Error("unexpected request"));
}) } }));
function show(currentReply = "") {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  const onUse = vi.fn();
  const view = render(<QueryClientProvider client={client}><InboxCopilot conversationId="c1" currentReply={currentReply} onUse={onUse} /></QueryClientProvider>);
  return { ...view, client, onUse };
}
beforeEach(() => { state.roles = ["SALES_REP"]; state.permissions = ["conversation.reply", "ai.assist.use"]; state.enabled = true; state.hold = false; state.holdReview = false; state.resolveReview = null; state.failure = ""; state.calls = []; vi.restoreAllMocks(); });
it("hides the component and makes no AI requests without permission or for Data Staff", () => {
  state.roles = ["DATA_STAFF", "SALES_REP"]; const first = show(); expect(screen.queryByText("Copilot")).toBeNull(); expect(state.calls).toHaveLength(0); first.unmount();
  state.roles = ["SALES_REP"]; state.permissions = ["conversation.reply"]; show(); expect(screen.queryByText("Copilot")).toBeNull(); expect(state.calls).toHaveLength(0);
});
it("explains that AI is disabled", async () => { state.enabled = false; show(); expect(await screen.findByText("Chưa có gợi ý. AI chưa được bật.")).toBeVisible(); });
it.each(["FAILED", "BLOCKED_BUDGET"])("explains when no draft is available: %s", async failure => {
  state.failure = failure; show(); fireEvent.click(await screen.findByRole("button", { name: "Soạn bản nháp" }));
  expect(await screen.findByText(/Chưa có gợi ý\./)).toBeVisible(); expect(screen.queryByRole("button", { name: "Dùng bản nháp" })).toBeNull();
});
it("uses a draft without sending a message", async () => {
  const { onUse } = show(); fireEvent.click(await screen.findByRole("button", { name: "Soạn bản nháp" })); fireEvent.click(await screen.findByRole("button", { name: "Dùng bản nháp" }));
  await waitFor(() => expect(onUse).toHaveBeenCalledWith("Cảm ơn bạn."));
  expect(state.calls.some(([url, init]) => url.endsWith("/messages") && init?.method === "POST")).toBe(false);
});
it("confirms before replacing text and sends edited content for review", async () => {
  const confirm = vi.spyOn(window, "confirm").mockReturnValueOnce(false).mockReturnValueOnce(true); const { onUse } = show("Nội dung cũ");
  fireEvent.click(await screen.findByRole("button", { name: "Soạn bản nháp" })); fireEvent.click(await screen.findByRole("button", { name: "Sửa" }));
  fireEvent.change(screen.getByLabelText("Chỉnh sửa bản nháp"), { target: { value: "Đã sửa." } }); fireEvent.click(screen.getByRole("button", { name: "Dùng bản đã sửa" }));
  expect(onUse).not.toHaveBeenCalled(); fireEvent.click(screen.getByRole("button", { name: "Dùng bản đã sửa" }));
  await waitFor(() => expect(onUse).toHaveBeenCalledWith("Đã sửa.")); expect(confirm).toHaveBeenCalledTimes(2);
  expect(state.calls.some(([url, init]) => url.endsWith("/accept") && JSON.parse(String(init?.body)).finalContent === "Đã sửa.")).toBe(true);
});
it("rejects a draft and clears it", async () => {
  show(); fireEvent.click(await screen.findByRole("button", { name: "Soạn bản nháp" })); fireEvent.click(await screen.findByRole("button", { name: "Từ chối" }));
  expect(await screen.findByText("Đã từ chối bản nháp.")).toBeVisible(); expect(state.calls.some(([url]) => url.endsWith("/reject"))).toBe(true);
});
it("does not show a draft from another conversation", async () => {
  const { rerender, client, onUse } = show();
  fireEvent.click(await screen.findByRole("button", { name: "Soạn bản nháp" }));
  expect(await screen.findByText("Cảm ơn bạn.")).toBeVisible();
  rerender(<QueryClientProvider client={client}><InboxCopilot key="c2" conversationId="c2" currentReply="" onUse={onUse} /></QueryClientProvider>);
  await screen.findByRole("button", { name: "Soạn bản nháp" });
  expect(screen.queryByText("Cảm ơn bạn.")).toBeNull();
});
it("aborts the pending draft request when switching conversations", async () => {
  state.hold = true;
  const { rerender, client, onUse } = show();
  fireEvent.click(await screen.findByRole("button", { name: "Soạn bản nháp" }));
  await waitFor(() => expect(state.calls.some(([url]) => url.endsWith("/reply-draft"))).toBe(true));
  const signal = state.calls.find(([url]) => url.endsWith("/reply-draft"))?.[1]?.signal;
  rerender(<QueryClientProvider client={client}><InboxCopilot key="c2" conversationId="c2" currentReply="" onUse={onUse} /></QueryClientProvider>);
  expect(signal?.aborted).toBe(true);
});
it("cannot insert a previous draft when review finishes after switching", async () => {
  state.holdReview = true;
  const { rerender, client, onUse } = show();
  fireEvent.click(await screen.findByRole("button", { name: "Soạn bản nháp" }));
  fireEvent.click(await screen.findByRole("button", { name: "Dùng bản nháp" }));
  await waitFor(() => expect(state.resolveReview).not.toBeNull());
  const signal = state.calls.find(([url]) => url.endsWith("/accept"))?.[1]?.signal;
  rerender(<QueryClientProvider client={client}><InboxCopilot key="c2" conversationId="c2" currentReply="" onUse={onUse} /></QueryClientProvider>);
  expect(signal?.aborted).toBe(true);
  await act(async () => { state.resolveReview?.({ ...item, status: "ACCEPTED" }); });
  expect(onUse).not.toHaveBeenCalled();
});
