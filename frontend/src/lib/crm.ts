import { ApiError } from "@/lib/api/client";
import type { SessionUser } from "@/lib/session/types";
import { api } from "@/lib/api";
import type { Offering, OfferingPage } from "@/lib/offerings";

export type CrmKind = "lead" | "customer";
export type CrmRecord = {
  id: string; displayName: string; email: string | null; phone: string | null; notes: string | null;
  ownerUserId: string; ownerName: string | null; offeringCode: string | null; offeringName: string | null;
  status: string; createdAt: string; updatedAt: string;
  source?: string | null; offeringId?: string | null; qualifiedAt?: string | null; disqualificationReason?: string | null;
  convertedCustomerId?: string | null; convertedAt?: string | null;
  customerType?: string | null; address?: string | null; relatedOfferingId?: string | null;
};
export type CrmPage = { items: CrmRecord[]; page: number; size: number; totalItems: number; totalPages: number };
export type OwnerCandidate = { id: string; fullName: string };
export const crmBase = (kind: CrmKind) => kind === "lead" ? "/app/lead" : "/app/khach-hang";
export const crmEndpoint = (kind: CrmKind) => `/api/backend/${kind === "lead" ? "leads" : "customers"}`;
export const crmTitle = (kind: CrmKind) => kind === "lead" ? "Lead" : "Khách hàng";
export const crmKey = (kind: CrmKind, user: SessionUser) => [kind, user.organization.id, user.id] as const;
export const hasRole = (user: SessionUser, roles: string[]) => roles.some(role => user.roles.includes(role));
export const canReadCrm = (user: SessionUser, kind: CrmKind) => user.permissions.includes(`${kind}.read`) && hasRole(user, kind === "lead" ? ["ADMIN", "MANAGER", "SALES_REP"] : ["ADMIN", "MANAGER", "SALES_REP", "CUSTOMER_CARE"]);
export const canCreateCrm = (user: SessionUser, kind: CrmKind) => user.permissions.includes(`${kind}.write`) && hasRole(user, ["MANAGER", "SALES_REP"]);
export const canWriteCrm = (user: SessionUser, kind: CrmKind) => user.permissions.includes(`${kind}.write`) && hasRole(user, ["ADMIN", "MANAGER", "SALES_REP"]);
export const sources: Record<string, string> = { WEBSITE: "Website", REFERRAL: "Giới thiệu", EVENT: "Sự kiện", SOCIAL_MEDIA: "Mạng xã hội", PHONE: "Điện thoại", WALK_IN: "Khách đến trực tiếp", OTHER: "Khác" };
export const leadStatuses: Record<string, string> = { NEW: "Mới", QUALIFIED: "Đủ điều kiện", CONVERTED: "Đã chuyển đổi", DISQUALIFIED: "Không đủ điều kiện" };
export const customerTypes: Record<string, string> = { INDIVIDUAL: "Cá nhân", BUSINESS: "Doanh nghiệp" };
export const customerStatuses: Record<string, string> = { ACTIVE: "Đang hoạt động", INACTIVE: "Ngừng hoạt động" };
export const displayValue = (value: string | null | undefined) => value || "Chưa cập nhật";
export function crmErrorMessage(error: unknown) {
  if (!(error instanceof ApiError)) return "Không thể xử lý yêu cầu. Vui lòng thử lại.";
  const messages: Record<string, string> = {
    NETWORK_ERROR: "Không thể kết nối. Kiểm tra mạng rồi thử lại.",
    LEAD_DUPLICATE: "Thông tin liên hệ có thể trùng với Lead đã có.", CUSTOMER_DUPLICATE: "Thông tin liên hệ có thể trùng với khách hàng đã có.",
    LEAD_CLOSED: "Lead đã đóng và không thể chỉnh sửa.", LEAD_CHANGED: "Lead đã thay đổi. Hãy tải lại dữ liệu trước khi lưu.",
    CUSTOMER_CHANGED: "Khách hàng đã thay đổi. Hãy tải lại dữ liệu trước khi lưu.", LEAD_STATE_CONFLICT: "Trạng thái Lead đã thay đổi. Hãy tải lại dữ liệu.",
    FORBIDDEN_SCOPE: "Bạn không có quyền truy cập hồ sơ này trong phạm vi hiện tại.", FORBIDDEN_PERMISSION: "Bạn không có quyền thực hiện thao tác này.",
    VALIDATION_ERROR: "Vui lòng kiểm tra các trường được đánh dấu.",
  };
  return messages[error.code] ?? "Không thể xử lý yêu cầu. Vui lòng thử lại.";
}
export const crmFieldErrors = (error: unknown): Record<string, string> => error instanceof ApiError ? Object.fromEntries(error.fieldErrors.map(({ field, message }) => [field, message])) : {};
export async function getScopedOfferings(activeOnly = true): Promise<Offering[]> {
  const items: Offering[] = [];
  let page = 0, totalPages = 1;
  while (page < totalPages) {
    const result = await api.request<OfferingPage>(`/api/backend/offerings?page=${page}&size=100${activeOnly ? "&active=true" : ""}`);
    items.push(...result.items.filter(item => !activeOnly || item.active));
    totalPages = result.totalPages;
    page += 1;
  }
  return items;
}
