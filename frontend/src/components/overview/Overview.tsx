"use client";
import { useSession } from "@/components/session/SessionGate";
import { permittedNavigation } from "@/config/navigation";
import { roleLabels } from "@/config/roles";
import { Badge } from "@/components/ui/Badge";
import { EmptyState } from "@/components/ui/EmptyState";
export function Overview() {
  const { user, organization } = useSession(), shortcuts = permittedNavigation(user.permissions).filter(item => item.route !== "/app");
  return <><header className="overview-heading"><p className="eyebrow">Không gian làm việc</p><h1>Tổng quan</h1><p>Chào <strong>{user.fullName}</strong>, chúc bạn một ngày làm việc hiệu quả.</p></header>
    <dl className="session-summary"><div><dt>Tổ chức</dt><dd>{organization.name} <span className="tenant-code">{organization.code.toUpperCase()}</span></dd></div><div><dt>Vai trò hiện tại</dt><dd><ul className="role-list">{user.roles.map(role => <li key={role}><Badge>{roleLabels[role] ?? "Vai trò khác"}</Badge></li>)}</ul></dd></div></dl>
    <section className="shortcuts" aria-labelledby="shortcuts-title"><header className="section-heading"><h2 id="shortcuts-title">Lối tắt</h2><p>Các công cụ phù hợp với quyền truy cập của bạn.</p></header>
      {shortcuts.length === 0 ? <EmptyState title="Chưa có lối tắt">Tài khoản hiện chưa được cấp quyền cho các chức năng nghiệp vụ. Liên hệ quản trị viên nếu cần thêm quyền.</EmptyState>
        : <ul className="shortcut-list">{shortcuts.map(item => { const Icon = item.icon; return <li key={item.route}><article className="shortcut-card"><span className={`shortcut-icon shortcut-${item.group === "Quản trị" ? "admin" : item.group === "Chăm sóc" ? "care" : item.group === "Dữ liệu" ? "data" : "sales"}`}><Icon aria-hidden="true" /></span><div className="shortcut-copy"><p className="shortcut-group">{item.group}</p><h3>{item.label}</h3><p className="future-note">Sẽ có ở giai đoạn sau</p></div></article></li>; })}</ul>}
    </section><footer className="overview-footer">POSE CRM · Nền tảng quản lý khách hàng đa ngành</footer>
  </>;
}
