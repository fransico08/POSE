export const roleLabels: Record<string, string> = {
  ADMIN: "Quản trị hệ thống", MANAGER: "Quản lý", SALES_REP: "Nhân viên kinh doanh",
  CUSTOMER_CARE: "Nhân viên chăm sóc khách hàng", DATA_STAFF: "Nhân viên dữ liệu",
};
export function displayRoles(roles: string[]) { return roles.map(role => roleLabels[role] ?? "Vai trò khác").join(" · "); }
export function initials(name: string) { return name.trim().split(/\s+/u).slice(-2).map(word => word[0]).join("").toUpperCase(); }
