import { ApiError } from "@/lib/api/client";
import { formatInstantAsDdMmYyyyHhMm } from "@/lib/date-format";

export type AssignmentStatus = "ACTIVE" | "EXPIRED" | "REVOKED";
export type AssignmentEffectiveState = "CURRENT" | "UPCOMING" | "EXPIRED" | "REVOKED";

export interface SalesAssignment {
  id: string;
  offeringId: string;
  offeringCode: string;
  offeringName: string;
  teamId: string | null;
  teamName: string | null;
  salesUserId: string;
  salesUserName: string;
  status: AssignmentStatus;
  effectiveState: AssignmentEffectiveState;
  effectiveFrom: string;
  effectiveTo: string | null;
  revokedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SalesAssignmentPage {
  items: SalesAssignment[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface SalesAssignmentCandidate {
  id: string;
  fullName: string;
}

export interface SalesAssignmentOpenWork {
  totalItems: number;
  items: Array<{ type: "LEAD" | "OPPORTUNITY" | "TASK"; id: string; title: string; status: string }>;
}

export interface AssignmentTeamPage {
  items: Array<{ id: string; name: string; status: "ACTIVE" | "INACTIVE" }>;
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface SalesAssignmentFilters {
  page: number;
  search: string;
  teamId: string;
  offeringId: string;
  salesSearch: string;
  status: string;
  validity: string;
}

export function canManageSalesAssignments(roles: readonly string[], permissions: readonly string[]) {
  return permissions.includes("sales_assignment.manage")
    && (roles.includes("ADMIN") || roles.includes("MANAGER"));
}

export function salesAssignmentListUrl(filters: SalesAssignmentFilters) {
  const query = new URLSearchParams({ page: String(filters.page), size: "20" });
  if (filters.search) query.set("search", filters.search);
  if (filters.teamId) query.set("teamId", filters.teamId);
  if (filters.offeringId) query.set("offeringId", filters.offeringId);
  if (filters.salesSearch) query.set("salesSearch", filters.salesSearch);
  if (filters.status) query.set("status", filters.status);
  if (filters.validity) query.set("validity", filters.validity);
  return `/api/backend/sales-assignments?${query.toString()}`;
}

export function salesAssignmentErrorMessage(error: unknown) {
  if (!(error instanceof ApiError)) return "Không thể xử lý yêu cầu. Vui lòng thử lại.";
  switch (error.code) {
    case "NETWORK_ERROR": return "Không thể kết nối. Kiểm tra mạng rồi thử lại.";
    case "VALIDATION_ERROR": return "Vui lòng kiểm tra các trường được đánh dấu.";
    case "SALES_ASSIGNMENT_CHANGED": return "Phân công đã được cập nhật. Tải lại dữ liệu trước khi tiếp tục.";
    case "SALES_ASSIGNMENT_REVOKED": return "Phân công này đã bị thu hồi.";
    case "SALES_ASSIGNMENT_OPEN_WORK": return "Cần chuyển giao các hồ sơ đang mở trước khi thu hồi phân công.";
    case "SALES_ASSIGNMENT_OVERLAP": return "Khoảng thời gian phân công trùng với một phân công đang hoạt động.";
    case "OFFERING_TEAM_REQUIRED": return "Sản phẩm hoặc dịch vụ chưa được gán team.";
    case "TEAM_INACTIVE": return "Team sở hữu sản phẩm hoặc dịch vụ đã ngừng hoạt động.";
    case "FORBIDDEN_SCOPE": return "Bạn không còn quyền truy cập dữ liệu này.";
    case "RESOURCE_NOT_FOUND": return "Không tìm thấy phân công này.";
    default: return error.message;
  }
}

export function salesAssignmentFieldErrors(error: unknown) {
  if (!(error instanceof ApiError)) return {};
  return Object.fromEntries(error.fieldErrors.map(({ field, message }) => [field, message]));
}

export function salesAssignmentFieldLabel(field: string) {
  const labels: Record<string, string> = {
    offeringId: "Sản phẩm/dịch vụ",
    salesUserId: "Nhân viên kinh doanh",
    effectiveFrom: "Hiệu lực từ",
    effectiveTo: "Hiệu lực đến",
    expectedUpdatedAt: "Phiên bản dữ liệu",
  };
  return labels[field] ?? field;
}

export function assignmentEffectiveLabel(state: SalesAssignment["effectiveState"]) {
  switch (state) {
    case "CURRENT": return "Đang có hiệu lực";
    case "UPCOMING": return "Sắp có hiệu lực";
    case "EXPIRED": return "Đã hết hạn";
    case "REVOKED": return "Đã thu hồi";
  }
}

export function assignmentStatusLabel(status: SalesAssignment["status"]) {
  switch (status) {
    case "ACTIVE": return "Đang phân công";
    case "EXPIRED": return "Đã hết hạn";
    case "REVOKED": return "Đã thu hồi";
  }
}

export function formatAssignmentDate(value: string | null | undefined) {
  if (!value) return "Không thời hạn";
  return formatInstantAsDdMmYyyyHhMm(value);
}

export function toLocalDateTimeValue(value: string | null | undefined) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
}

export function toIsoInstant(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}
