import { ApiError } from "@/lib/api/client";
import { formatInstantAsDdMmYyyyHhMm } from "@/lib/date-format";

export interface Offering {
  id: string;
  code: string;
  name: string;
  kind: "PRODUCT" | "SERVICE";
  categoryId: string;
  categoryName: string | null;
  description: string | null;
  unit: string | null;
  referencePrice: string | null;
  currencyCode: string | null;
  pricingUnit: string | null;
  referencePriceEffectiveFrom: string | null;
  active: boolean;
  teamId: string | null;
  teamName: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface OfferingPage {
  items: Offering[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface OfferingCategory {
  id: string;
  code: string;
  name: string;
}

export interface OfferingTeam {
  id: string;
  code: string;
  name: string;
  status: "ACTIVE" | "INACTIVE";
}

export interface OfferingTeamPage {
  items: OfferingTeam[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface OfferingFilters {
  page: number;
  search: string;
  kind: string;
  categoryId: string;
  active: string;
  teamId: string;
}

export function canManageOfferings(roles: readonly string[], permissions: readonly string[]) {
  return permissions.includes("offering.manage") && (roles.includes("ADMIN") || roles.includes("MANAGER"));
}

export function canReadOfferings(roles: readonly string[], permissions: readonly string[]) {
  return canManageOfferings(roles, permissions) || (roles.includes("SALES_REP") && permissions.includes("offering.read"));
}

export function offeringListUrl(filters: OfferingFilters, manager: boolean) {
  const query = new URLSearchParams({ page: String(filters.page), size: "20" });
  if (filters.search) query.set("search", filters.search);
  if (filters.kind) query.set("kind", filters.kind);
  if (manager && filters.categoryId) query.set("categoryId", filters.categoryId);
  if (manager && filters.active) query.set("active", filters.active);
  if (manager && filters.teamId) query.set("teamId", filters.teamId);
  return `/api/backend/offerings?${query.toString()}`;
}

export function offeringErrorMessage(error: unknown) {
  if (!(error instanceof ApiError)) return "Không thể xử lý yêu cầu. Vui lòng thử lại.";
  switch (error.code) {
    case "NETWORK_ERROR": return "Không thể kết nối. Kiểm tra mạng rồi thử lại.";
    case "OFFERING_NAME_EXISTS": return "Đã có sản phẩm hoặc dịch vụ cùng tên và loại đang hoạt động.";
    case "OFFERING_CHANGED": return "Thông tin đã được người khác cập nhật. Tải lại dữ liệu trước khi lưu.";
    case "OFFERING_PRICE_TIMELINE_CONFLICT": return "Lịch sử giá đã thay đổi. Tải lại thông tin và thử lại.";
    case "OFFERING_TEAM_REQUIRED": return "Hãy gán sản phẩm hoặc dịch vụ cho một team trước khi kích hoạt.";
    case "TEAM_INACTIVE": return "Team đã ngừng hoạt động. Hãy chọn một team đang hoạt động.";
    case "FORBIDDEN_SCOPE": return "Bạn không còn quyền truy cập dữ liệu này.";
    case "RESOURCE_NOT_FOUND": return "Không tìm thấy sản phẩm hoặc dịch vụ này.";
    default: return error.message;
  }
}

export function offeringFieldErrors(error: unknown) {
  if (!(error instanceof ApiError)) return {};
  return Object.fromEntries(error.fieldErrors.map(({ field, message }) => [field, message]));
}

export function fieldLabel(field: string) {
  const labels: Record<string, string> = {
    name: "Tên", kind: "Loại", categoryId: "Danh mục", teamId: "Team",
    referencePrice: "Giá tham khảo", unit: "Đơn vị", description: "Mô tả", active: "Trạng thái",
    expectedUpdatedAt: "Phiên bản dữ liệu",
  };
  return labels[field] ?? field;
}

export function formatOfferingPrice(amount: string | null, currencyCode: string | null) {
  if (amount === null) return "Chưa có giá";
  const currency = currencyCode?.trim() || "VND";
  try {
    const match = /^(\d+)(?:\.(\d{1,2}))?$/u.exec(amount);
    if (!match) return `${amount} ${currency}`;
    const groupedInteger = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 0 }).format(BigInt(match[1]));
    const decimalSeparator = new Intl.NumberFormat("vi-VN", { minimumFractionDigits: 1, maximumFractionDigits: 1 }).formatToParts(1.1).find(part => part.type === "decimal")?.value ?? ",";
    const exactNumber = `${groupedInteger}${match[2] ? `${decimalSeparator}${match[2]}` : ""}`;
    const template = new Intl.NumberFormat("vi-VN", { style: "currency", currency, maximumFractionDigits: 2 }).formatToParts(0);
    const numericTypes = new Set(["integer", "group", "decimal", "fraction"]);
    const first = template.findIndex(part => numericTypes.has(part.type));
    let last = -1;
    template.forEach((part, index) => { if (numericTypes.has(part.type)) last = index; });
    if (first < 0 || last < first) return `${exactNumber} ${currency}`;
    return `${template.slice(0, first).map(part => part.value).join("")}${exactNumber}${template.slice(last + 1).map(part => part.value).join("")}`;
  } catch {
    return `${amount} ${currency}`;
  }
}

export function formatOfferingDate(value: string | null | undefined) {
  if (!value) return "—";
  return formatInstantAsDdMmYyyyHhMm(value);
}
