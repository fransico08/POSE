import { api } from "@/lib/api";
import { ApiError } from "@/lib/api/client";
import type { Interaction } from "@/lib/interactions";
import type { SessionUser } from "@/lib/session/types";
import type { Task } from "@/lib/tasks";

export type Customer360Name = { id: string; name: string | null };
export type Customer360ProfileData = {
  id: string;
  displayName: string;
  email: string | null;
  phone: string | null;
  customerType: string | null;
  address: string | null;
  notes: string | null;
  ownerUserId: string;
  ownerName: string | null;
  relatedOfferingId: string | null;
  offeringCode: string | null;
  offeringName: string | null;
  status: string;
  createdAt: string;
  updatedAt: string;
};
export type Customer360LeadItem = {
  id: string;
  name: string | null;
  status: string;
  convertedAt: string | null;
};
export type Customer360OpportunityItem = {
  id: string;
  title: string | null;
  stage: string;
  expectedValue: string | null;
  expectedCloseDate: string | null;
};
export type Customer360Metric = {
  restricted: boolean;
  count: number | null;
  averageRating: number | null;
  items: unknown[];
};
export type Customer360Response = {
  customer: {
    profile: Customer360ProfileData;
    owner: Customer360Name | null;
    ownerTeams: Customer360Name[];
    careAssignees: Customer360Name[];
  };
  sources: {
    count: number;
    systems: string[];
  };
  leads: {
    restricted: boolean;
    count: number | null;
    items: Customer360LeadItem[];
  };
  opportunities: {
    restricted: boolean;
    stages: Record<string, number> | null;
    openExpectedValue: string | null;
    wonFinalValue: string | null;
    items: Customer360OpportunityItem[];
  };
  interactions: {
    restricted: boolean;
    count: number | null;
    lastInteractionAt: string | null;
    items: Interaction[];
  };
  tasks: {
    restricted: boolean;
    statuses: Record<string, number> | null;
    overdue: number | null;
    items: Task[];
  };
  feedback: Customer360Metric;
  support: Customer360Metric;
  refreshedAt: string;
};
export type ReconciliationDifference = {
  customerId: string;
  columns: string[];
};
export type ReconciliationResponse = {
  totals: Record<string, number>;
  matched: number;
  mismatched: number;
  differences: {
    items: ReconciliationDifference[];
    page: number;
    size: number;
    totalItems: number;
    totalPages: number;
  };
};

export const reconciliationColumns: Record<string, string> = {
  opportunity_count: "Số cơ hội",
  interaction_count: "Số tương tác",
  feedback_count: "Số phản hồi",
  open_task_count: "Nhiệm vụ đang mở",
  open_support_count: "Hỗ trợ đang mở",
  source_count: "Số nguồn dữ liệu",
  last_interaction_at: "Tương tác gần nhất",
};

export const customer360Key = (user: SessionUser) => ["customer-360", user.organization.id, user.id] as const;
export const canReadCustomer360 = (user: SessionUser) => user.permissions.includes("customer_360.read");
export const canReconcileCustomer360 = (user: SessionUser) =>
  canReadCustomer360(user) && user.roles.some(role => ["ADMIN", "DATA_STAFF"].includes(role));

export const getCustomer360 = (id: string) =>
  api.request<Customer360Response>(`/api/backend/customers/${encodeURIComponent(id)}/360`, undefined, { suppressForbiddenNavigation: true });

export const getReconciliation = (page = 0, size = 20) =>
  api.request<ReconciliationResponse>(`/api/backend/customer-360/reconciliation?page=${page}&size=${size}`, undefined, { suppressForbiddenNavigation: true });

export function isCustomer360Id(id: string): boolean {
  return /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(id);
}

export function canRetryCustomer360Error(error: unknown): boolean {
  return error instanceof ApiError && (error.code === "NETWORK_ERROR" || (error.status >= 500 && error.status < 600));
}

export function customer360Error(error: unknown): string {
  if (!(error instanceof ApiError)) return "Không thể xử lý yêu cầu. Vui lòng thử lại.";
  if (error.status === 404 || error.code === "RESOURCE_NOT_FOUND" || error.code === "NOT_FOUND") {
    return "Không tìm thấy hồ sơ khách hàng.";
  }
  const messages: Record<string, string> = {
    NETWORK_ERROR: "Không thể kết nối. Kiểm tra mạng rồi thử lại.",
    FORBIDDEN_SCOPE: "Bạn không có quyền truy cập hồ sơ này trong phạm vi hiện tại.",
    FORBIDDEN_PERMISSION: "Bạn không có quyền thực hiện thao tác này.",
  };
  if (messages[error.code]) return messages[error.code];
  if (error.status === 403) return "Bạn không có quyền truy cập hồ sơ này trong phạm vi hiện tại.";
  return "Không thể xử lý yêu cầu. Vui lòng thử lại.";
}

