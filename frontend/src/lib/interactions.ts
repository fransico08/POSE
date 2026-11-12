import { api } from "@/lib/api";
import { ApiError } from "@/lib/api/client";
import type { SessionUser } from "@/lib/session/types";

export type Interaction = { id: string; customerId: string | null; leadId: string | null; opportunityId: string | null; customer?: { id: string; name: string } | null; lead?: { id: string; name: string } | null; opportunity?: { id: string; title: string | null } | null; recordedByUserId: string; recordedByName: string; type: string; channel: string; subject: string | null; content: string | null; outcome: string | null; visibility: "CARE_VISIBLE" | "SALES_ONLY"; occurredAt: string; createdAt: string; updatedAt: string };
export type WorkPage<T> = { items: T[]; page: number; size: number; totalItems: number; totalPages: number };
export const interactionTypeLabels: Record<string, string> = {
  CALL: "Cuộc gọi", EMAIL: "Email", MEETING: "Cuộc gặp", NOTE: "Ghi chú",
  CARE_FOLLOW_UP: "Theo dõi chăm sóc", CHAT: "Trò chuyện",
};
export const interactionChannelLabels: Record<string, string> = {
  PHONE: "Điện thoại", EMAIL: "Email", IN_PERSON: "Trực tiếp", CRM: "Hệ thống CRM", ZALO: "Zalo",
  TELEGRAM: "Telegram", MESSENGER: "Messenger", ZALO_SIM: "Zalo mô phỏng",
};
// Loại và kênh là chuỗi mở; giữ nguyên mã chưa biết để không làm mất dữ liệu.
export const interactionTypeLabel = (value: string) => Object.prototype.hasOwnProperty.call(interactionTypeLabels, value) ? interactionTypeLabels[value] : value;
export const interactionChannelLabel = (value: string) => Object.prototype.hasOwnProperty.call(interactionChannelLabels, value) ? interactionChannelLabels[value] : value;
export const interactionTypeCode = (value: string) => Object.entries(interactionTypeLabels).find(([, label]) => label === value)?.[0] ?? value;
export const interactionChannelCode = (value: string) => Object.entries(interactionChannelLabels).find(([, label]) => label === value)?.[0] ?? value;
export const interactionKey = (user: SessionUser) => ["interaction", user.organization.id, user.id] as const;
export const canReadInteraction = (user: SessionUser) => user.permissions.includes("interaction.read");
export const canWriteInteraction = (user: SessionUser) => user.permissions.includes("interaction.write");
export const canCreateInteraction = (user: SessionUser) => canWriteInteraction(user) && user.roles.some(role => ["MANAGER", "SALES_REP", "CUSTOMER_CARE"].includes(role));
export const canReadSensitive = (user: SessionUser) => user.permissions.includes("interaction.sensitive.read");
export const listInteractions = (query: URLSearchParams) => api.request<WorkPage<Interaction>>(`/api/backend/interactions?${query}`);
export const getInteraction = (id: string) => api.request<Interaction>(`/api/backend/interactions/${encodeURIComponent(id)}`);
export const saveInteraction = (id: string | null, body: object) => api.request<Interaction>(`/api/backend/interactions${id ? `/${encodeURIComponent(id)}` : ""}`, { method: id ? "PATCH" : "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
export function workError(error: unknown) {
  if (!(error instanceof ApiError)) return "Không thể xử lý yêu cầu. Vui lòng thử lại.";
  const messages: Record<string, string> = { NETWORK_ERROR: "Không thể kết nối. Kiểm tra mạng rồi thử lại.", FORBIDDEN_SCOPE: "Bạn không có quyền truy cập trong phạm vi hiện tại.", FORBIDDEN_PERMISSION: "Bạn không có quyền thực hiện thao tác này.", INTERACTION_CHANGED: "Tương tác đã thay đổi. Hãy tải lại dữ liệu.", TASK_CHANGED: "Nhiệm vụ đã thay đổi. Hãy tải lại dữ liệu.", TASK_STATE_CONFLICT: "Trạng thái nhiệm vụ đã thay đổi hoặc đã đóng.", VALIDATION_ERROR: "Vui lòng kiểm tra các trường được đánh dấu." };
  return error.status === 404 ? "Không tìm thấy dữ liệu." : messages[error.code] ?? "Không thể xử lý yêu cầu. Vui lòng thử lại.";
}
