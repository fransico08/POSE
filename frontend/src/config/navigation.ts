import {
  HomeIcon, CubeIcon, UserPlusIcon, FunnelIcon, UsersIcon, BriefcaseIcon,
  ChatBubbleLeftRightIcon, ClipboardDocumentCheckIcon, ArrowsRightLeftIcon,
  IdentificationIcon, LifebuoyIcon, ArrowUpTrayIcon, ChartBarIcon,
  UserCircleIcon, UserGroupIcon, KeyIcon, DocumentMagnifyingGlassIcon,
} from "@heroicons/react/24/outline";
export type NavigationItem = { label: string; route: string; icon: typeof HomeIcon; permission: string[]; uc: string[]; group: string; implemented: boolean; roles?: string[] };
const item = (label: string, route: string, icon: typeof HomeIcon, permission: string[], uc: string[], group: string): NavigationItem => ({ label, route, icon, permission, uc, group, implemented: false });
export const navigation: NavigationItem[] = [
  { ...item("Tổng quan", "/app", HomeIcon, [], [], "Chung"), implemented: true },
  { ...item("Sản phẩm & dịch vụ", "/app/san-pham", CubeIcon, ["offering.manage", "offering.read"], ["UC-03"], "Bán hàng"), implemented: true, roles: ["ADMIN", "MANAGER", "SALES_REP"] },
  item("Phân công sản phẩm", "/app/phan-cong", UserPlusIcon, ["sales_assignment.manage"], ["UC-04"], "Bán hàng"),
  item("Lead", "/app/lead", FunnelIcon, ["lead.read"], ["UC-09"], "Bán hàng"),
  item("Khách hàng", "/app/khach-hang", UsersIcon, ["customer.read"], ["UC-10"], "Bán hàng"),
  item("Cơ hội bán hàng", "/app/co-hoi", BriefcaseIcon, ["opportunity.read"], ["UC-12"], "Bán hàng"),
  item("Tương tác", "/app/tuong-tac", ChatBubbleLeftRightIcon, ["interaction.read"], ["UC-13"], "Bán hàng"),
  item("Nhiệm vụ", "/app/nhiem-vu", ClipboardDocumentCheckIcon, ["task.manage"], ["UC-06", "UC-14", "UC-17"], "Bán hàng"),
  item("Bàn giao khách hàng", "/app/ban-giao", ArrowsRightLeftIcon, ["handover.request", "handover.approve"], ["UC-11"], "Bán hàng"),
  item("Hồ sơ khách hàng 360", "/app/khach-hang-360", IdentificationIcon, ["customer_360.read"], ["UC-15"], "Chăm sóc"),
  item("Yêu cầu hỗ trợ", "/app/ho-tro", LifebuoyIcon, ["support_request.manage", "support_request.escalation.handle"], ["UC-08", "UC-16"], "Chăm sóc"),
  item("Nhập dữ liệu", "/app/nhap-du-lieu", ArrowUpTrayIcon, ["import.prepare", "import.approve"], ["UC-18", "UC-19", "UC-20", "UC-21"], "Dữ liệu"),
  item("Báo cáo", "/app/bao-cao", ChartBarIcon, ["dashboard.read"], [], "Dữ liệu"),
  item("Người dùng", "/app/nguoi-dung", UserCircleIcon, ["identity.user.manage"], [], "Quản trị"),
  item("Nhóm", "/app/nhom", UserGroupIcon, ["organization.team.manage"], [], "Quản trị"),
  item("Vai trò & quyền", "/app/vai-tro", KeyIcon, ["identity.role.manage"], [], "Quản trị"),
  item("Nhật ký hệ thống", "/app/nhat-ky", DocumentMagnifyingGlassIcon, ["audit.read"], [], "Quản trị"),
];
export function can(permissions: readonly string[], permission: string | readonly string[]) {
  const required = typeof permission === "string" ? [permission] : permission;
  return required.length === 0 || required.some(value => permissions.includes(value));
}
export function permittedNavigation(permissions: readonly string[]) { return navigation.filter(entry => can(permissions, entry.permission)); }
export function implementedNavigation(permissions: readonly string[], roles: readonly string[] = []) {
  return permittedNavigation(permissions).filter(entry => entry.implemented && (!entry.roles || entry.roles.some(role => roles.includes(role))));
}
