import { api } from "@/lib/api";
import { ApiError } from "@/lib/api/client";
import { hasRole, mayReadOfferings, type OwnerCandidate } from "@/lib/crm";
import type { SessionUser } from "@/lib/session/types";

export type OpportunityStage = "NEW" | "QUALIFIED" | "PROPOSAL" | "WON" | "LOST" | "CANCELLED";
export const stages: Record<OpportunityStage, string> = { NEW: "Mới", QUALIFIED: "Đủ điều kiện", PROPOSAL: "Đề xuất", WON: "Thành công", LOST: "Thất bại", CANCELLED: "Đã hủy" };
export const openStages: OpportunityStage[] = ["NEW", "QUALIFIED", "PROPOSAL"];
export type OpportunityLine = { offeringId: string; offeringCode: string; offeringName: string; quantity: string; unitPrice: string; discountAmount: string; lineTotal: string };
export type Opportunity = { id: string; title: string; stage: OpportunityStage; expectedValue: string | null; currencyCode: string; expectedCloseDate: string | null; notes: string | null; customer: { id: string; name: string } | null; lead: { id: string; name: string } | null; owner: { id: string; name: string }; lines: OpportunityLine[]; finalValue: string | null; actualCloseDate: string | null; closingReason: string | null; closedAt: string | null; createdAt: string; updatedAt: string };
export type OpportunityPage = { items: Opportunity[]; page: number; size: number; totalItems: number; totalPages: number };
export type LineInput = { offeringId: string; quantity: string; unitPrice: string; discountAmount: string };
export const isUuid = (value: string) => /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
export const opportunityKey = (user: SessionUser) => ["opportunity", user.organization.id, user.id] as const;
export const canReadOpportunity = (u: SessionUser) => u.permissions.includes("opportunity.read") && hasRole(u, ["ADMIN", "MANAGER", "SALES_REP"]);
export const canCreateOpportunity = (u: SessionUser) => u.permissions.includes("opportunity.write") && hasRole(u, ["MANAGER", "SALES_REP"]) && mayReadOfferings(u);
export const canEditOpportunity = (u: SessionUser) => u.permissions.includes("opportunity.write") && hasRole(u, ["ADMIN", "MANAGER", "SALES_REP"]);
export const canReopenOpportunity = (u: SessionUser) => u.permissions.includes("opportunity.reopen") && hasRole(u, ["ADMIN", "MANAGER"]);
const endpoint = "/api/backend/opportunities";
const send = (url: string, method: string, payload: object) => api.request<Opportunity>(url, { method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
export const listOpportunities = (query: URLSearchParams) => api.request<OpportunityPage>(`${endpoint}?${query}`);
export const getOpportunity = (id: string) => api.request<Opportunity>(`${endpoint}/${encodeURIComponent(id)}`);
export const createOpportunity = (body: object) => send(endpoint, "POST", body);
export const updateOpportunity = (id: string, body: object) => send(`${endpoint}/${encodeURIComponent(id)}`, "PATCH", body);
export const winOpportunity = (id: string, body: object) => send(`${endpoint}/${encodeURIComponent(id)}/win`, "POST", body);
export const loseOpportunity = (id: string, body: object) => send(`${endpoint}/${encodeURIComponent(id)}/lose`, "POST", body);
export const cancelOpportunity = (id: string, body: object) => send(`${endpoint}/${encodeURIComponent(id)}/cancel`, "POST", body);
export const reopenOpportunity = (id: string, body: object) => send(`${endpoint}/${encodeURIComponent(id)}/reopen`, "POST", body);
export const getOpportunityOwners = () => api.request<OwnerCandidate[]>(`${endpoint}/owners`);
export const getOwnerCandidates = (customerId: string, offeringIds: string[]) => api.request<OwnerCandidate[]>(`${endpoint}/owner-candidates?${new URLSearchParams({ customerId, offeringIds: offeringIds.join(",") })}`);
export function toCents(value: string): bigint | null { const m = /^(\d+)(?:[.,](\d{1,2}))?$/.exec(value.trim()); return m ? BigInt(m[1]) * 100n + BigInt((m[2] ?? "").padEnd(2, "0")) : null; }
export function fromCents(cents: bigint): string { const sign = cents < 0n ? "-" : ""; const n = cents < 0n ? -cents : cents; return `${sign}${n / 100n}.${String(n % 100n).padStart(2, "0")}`; }
export function convertDiscount(quantity: string, price: string, discount: string, from: "money" | "percent"): string | null {
  const q = toCents(quantity), p = toCents(price), d = toCents(discount);
  if (q === null || p === null || d === null || q <= 0n || p === 0n) return null;
  const gross = q * p;
  return fromCents(from === "money" ? (d * 1000000n + gross / 2n) / gross : (gross * d + 500000n) / 1000000n);
}
export function calculateLine(quantity: string, price: string, discount: string, percent: boolean) {
  const q = toCents(quantity), p = toCents(price), d = toCents(discount || "0");
  if (q === null || p === null || d === null || q <= 0n || (percent && d > 10000n)) return null;
  const gross = q * p;
  const discountCents = percent ? (gross * d + 500000n) / 1000000n : d;
  if (discountCents * 100n > gross) return null;
  return { discountAmount: fromCents(discountCents), total: (gross - discountCents * 100n + 50n) / 100n };
}
export function formatVnd(value: string | null) { if (value === null) return "Chưa cập nhật"; const negative = value.startsWith("-"); const cents = toCents(negative ? value.slice(1) : value); if (cents === null) return value; const n = fromCents(cents).split("."); return `${negative ? "−" : ""}${new Intl.NumberFormat("vi-VN").format(BigInt(n[0]))}${n[1] === "00" ? "" : `,${n[1]}`} ₫`; }
export function vietnamToday(now = new Date()) { const parts = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(now); const part = (name: string) => parts.find(p => p.type === name)?.value ?? ""; return `${part("year")}-${part("month")}-${part("day")}`; }
export function opportunityError(error: unknown) { if (!(error instanceof ApiError)) return "Không thể xử lý yêu cầu. Vui lòng thử lại."; const messages: Record<string, string> = { NETWORK_ERROR: "Không thể kết nối. Kiểm tra mạng rồi thử lại.", OPPORTUNITY_CHANGED: "Cơ hội đã được người khác cập nhật.", OPPORTUNITY_LOCKED: "Cơ hội đã đóng.", OPPORTUNITY_STAGE_BACKWARD: "Không thể lùi giai đoạn.", OPPORTUNITY_STATE_CONFLICT: "Trạng thái cơ hội đã thay đổi.", FORBIDDEN_SCOPE: "Bạn không có quyền truy cập trong phạm vi hiện tại.", FORBIDDEN_PERMISSION: "Bạn không có quyền thực hiện thao tác này.", VALIDATION_ERROR: "Vui lòng kiểm tra các trường được đánh dấu." }; return messages[error.code] ?? "Không thể xử lý yêu cầu. Vui lòng thử lại."; }
