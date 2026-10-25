"use client";
import Link from "next/link";
import { ChevronDoubleLeftIcon, ChevronDoubleRightIcon } from "@heroicons/react/24/outline";
import { usePathname } from "next/navigation";
import type { CSSProperties } from "react";
import { implementedNavigation } from "@/config/navigation";
import { displayRoles, initials } from "@/config/roles";
import type { SessionUser } from "@/lib/session/types";
export function AppSidebar({ user, collapsed = false, mobile = false, onToggle, onNavigate }: { user: SessionUser; collapsed?: boolean; mobile?: boolean; onToggle?: () => void; onNavigate?: () => void }) {
  const path = usePathname(), items = implementedNavigation(user.permissions);
  const activeIndex = items.findIndex(entry => entry.route === path);
  const indicatorStyle: CSSProperties & { "--active-index": number } = { "--active-index": activeIndex, opacity: activeIndex < 0 ? 0 : 1 };
  return <aside className={`sidebar ${collapsed ? "sidebar-collapsed" : ""} ${mobile ? "sidebar-mobile" : ""}`}>
    <header className="sidebar-brand"><span className="brand-symbol" aria-hidden="true">P</span><strong className="sidebar-copy">POSE CRM</strong></header>
    {!mobile && <button type="button" className="sidebar-toggle icon-button" aria-label={collapsed ? "Mở rộng sidebar" : "Thu gọn sidebar"} aria-expanded={!collapsed} aria-controls="desktop-sidebar-navigation" onClick={onToggle}>{collapsed ? <ChevronDoubleRightIcon aria-hidden="true" /> : <ChevronDoubleLeftIcon aria-hidden="true" />}</button>}
    <nav id={mobile ? "drawer-sidebar-navigation" : "desktop-sidebar-navigation"} aria-label="Điều hướng chính">
      <p className="navigation-group sidebar-copy">Chung</p><div className="navigation-track"><span className="navigation-indicator" aria-hidden="true" style={indicatorStyle} /><ul>{items.map(entry => { const Icon = entry.icon; return <li key={entry.route}><Link href={entry.route} aria-current={path === entry.route ? "page" : undefined} aria-label={entry.label} className="navigation-link" onClick={onNavigate}><Icon aria-hidden="true" /><span className="navigation-label">{entry.label}</span></Link></li>; })}</ul></div>
    </nav>
    <footer className="sidebar-user"><span className="avatar" aria-hidden="true">{initials(user.fullName)}</span><span className="sidebar-copy"><strong>{user.fullName}</strong><small>{displayRoles(user.roles)}</small></span><small className="sidebar-email sidebar-copy">{user.email}</small>{collapsed && <span className="sr-only">{user.fullName}, {displayRoles(user.roles)}</span>}</footer>
  </aside>;
}
