"use client";
import Link from "next/link";
import { HomeIcon, ChevronDoubleLeftIcon, ChevronDoubleRightIcon } from "@heroicons/react/24/outline";
import { displayRoles, initials } from "@/config/roles";
import type { SessionUser } from "@/lib/session/types";
export function AppSidebar({ user, collapsed = false, mobile = false, onToggle, onNavigate }: { user: SessionUser; collapsed?: boolean; mobile?: boolean; onToggle?: () => void; onNavigate?: () => void }) {
  return <aside className={`sidebar ${collapsed ? "sidebar-collapsed" : ""} ${mobile ? "sidebar-mobile" : ""}`}>
    <header className="sidebar-brand"><span className="brand-symbol" aria-hidden="true">P</span><strong className="sidebar-copy">POSE CRM</strong></header>
    {!mobile && <button type="button" className="sidebar-toggle icon-button" aria-label={collapsed ? "Mở rộng sidebar" : "Thu gọn sidebar"} aria-expanded={!collapsed} aria-controls="desktop-sidebar-navigation" onClick={onToggle}>{collapsed ? <ChevronDoubleRightIcon aria-hidden="true" /> : <ChevronDoubleLeftIcon aria-hidden="true" />}</button>}
    <nav id={mobile ? "drawer-sidebar-navigation" : "desktop-sidebar-navigation"} aria-label="Điều hướng chính">
      <p className="navigation-group sidebar-copy">Không gian làm việc</p><ul><li><Link href="/app" aria-current="page" aria-label="Tổng quan" className="navigation-link" onClick={onNavigate}><HomeIcon aria-hidden="true" /><span className="navigation-label">Tổng quan</span></Link></li></ul>
    </nav>
    <footer className="sidebar-user"><span className="avatar" aria-hidden="true">{initials(user.fullName)}</span><span className="sidebar-copy"><strong>{user.fullName}</strong><small>{displayRoles(user.roles)}</small><small>{user.email}</small></span>{collapsed && <span className="sr-only">{user.fullName}, {displayRoles(user.roles)}</span>}</footer>
  </aside>;
}
