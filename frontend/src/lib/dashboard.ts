import { api } from "@/lib/api";
import type { SessionUser } from "@/lib/session/types";
import { parseDdMmYyyy } from "@/lib/date-format";

export type Availability = "AVAILABLE" | "AVAILABLE_SNAPSHOT" | "ZERO" | "NO_DATA_IN_PERIOD" | "NOT_AVAILABLE" | "NOT_AVAILABLE_ZERO_DENOMINATOR" | "NOT_APPLICABLE" | "NOT_SUPPORTED";
export type DashboardMetric = {
  metricId: string; dimensions: Record<string, string | number>; value: number | null;
  unit: string; numerator: number | null; denominator: number | null; availability: Availability;
  reason: string | null; periodOrAsOf: string; generatedAt: string; sourceSql: string;
};
export type DashboardResponse = {
  organizationId: string; asOf: string; period: { start: string; end: string; timezone: string };
  filters: { ownerUserId: string | null; workflowType: string | null; metricIds: string[] };
  metrics: DashboardMetric[];
};
export const dashboardKey = (user: SessionUser) => ["dashboard", user.organization.id, user.id] as const;
export const getDashboardKpis = (params: URLSearchParams) =>
  api.request<DashboardResponse>(`/api/backend/dashboard/kpis?${params}`, undefined, { suppressForbiddenNavigation: true });

export function unavailable(metric?: DashboardMetric): string | null {
  if (!metric) return "Chưa đủ dữ liệu để tính";
  if (metric.availability === "NOT_APPLICABLE") return metric.reason === "OWNER_FILTER_NOT_SUPPORTED"
    ? "Không lọc được theo người phụ trách" : "Không áp dụng cho vai trò của bạn";
  if (metric.availability === "NOT_SUPPORTED") return "Chưa hỗ trợ dữ liệu hội thoại";
  if (metric.availability === "NO_DATA_IN_PERIOD") return "Không có dữ liệu trong kỳ đã chọn";
  if (metric.value === null || metric.availability === "NOT_AVAILABLE" || metric.availability === "NOT_AVAILABLE_ZERO_DENOMINATOR")
    return "Chưa đủ dữ liệu để tính";
  return null;
}
export function formatMetric(metric?: DashboardMetric): string {
  const message = unavailable(metric);
  if (message) return message;
  const value = metric!.value!;
  if (metric!.unit === "ratio") return `${(value * 100).toLocaleString("vi-VN", { maximumFractionDigits: 2 })}%`;
  if (metric!.unit === "currency" && typeof metric!.dimensions.currency === "string")
    return `${value.toLocaleString("vi-VN", { maximumFractionDigits: 2 })} ${metric!.dimensions.currency}`;
  if (metric!.unit === "seconds") {
    const minutes = Math.floor(value / 60), days = Math.floor(minutes / 1440);
    return days ? `${days.toLocaleString("vi-VN")} ngày` : `${Math.floor(minutes / 60).toLocaleString("vi-VN")} giờ ${minutes % 60} phút`;
  }
  return value.toLocaleString("vi-VN", { maximumFractionDigits: 2 });
}
export function periodFromDates(from: string, to: string): { start: string; end: string } | null {
  const startDate = parseDdMmYyyy(from), endDate = parseDdMmYyyy(to);
  if (!startDate || !endDate) return null;
  const start = Date.parse(`${startDate}T00:00:00+07:00`);
  const end = Date.parse(`${endDate}T00:00:00+07:00`) + 86400000;
  if (start >= end || end - start > 366 * 86400000) return null;
  return { start: new Date(start).toISOString(), end: new Date(end).toISOString() };
}
export function engagementRows(metrics: DashboardMetric[]): { name: string; value: number }[] {
  const labels: Record<string, string> = { ACTIVE: "Đang tương tác", AT_RISK: "Có nguy cơ", INACTIVE: "Không hoạt động", NEVER_ENGAGED: "Chưa từng tương tác" };
  return Object.entries(labels).map(([key, name]) => ({ name, value: metrics.find(metric => metric.dimensions.segment === key)?.value ?? 0 }));
}
