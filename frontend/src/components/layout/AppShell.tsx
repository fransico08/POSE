"use client";
import { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useSession } from "@/components/session/SessionGate";
import { clearClientSession } from "@/lib/api";
import { AppSidebar } from "./AppSidebar";
import { Topbar } from "./Topbar";
import { Breadcrumbs } from "./Breadcrumbs";
import { TenantBadge } from "./TenantBadge";
import { SkipLink } from "./SkipLink";
import { MobileNavDrawer } from "./MobileNavDrawer";
import { Dialog } from "@/components/ui/Dialog";
import { navigation } from "@/config/navigation";
export function AppShell({ children }: { children: React.ReactNode }) {
  const { user, organization } = useSession(), path = usePathname() ?? "", router = useRouter();
  const [collapsed, setCollapsed] = useState(true), [drawer, setDrawer] = useState(false), [logoutDialog, setLogoutDialog] = useState(false), [busy, setBusy] = useState(false);
  const currentNavigation = navigation.find(entry => path === entry.route || (entry.route !== "/app" && path.startsWith(`${entry.route}/`)));
  const title = path === "/app/khong-co-quyen" ? "Không có quyền truy cập" : currentNavigation?.label ?? "Tổng quan";
  useEffect(() => {
    const desktop = window.matchMedia("(min-width: 1024px)"), mobile = window.matchMedia("(max-width: 767px)");
    const update = () => { setCollapsed(!desktop.matches); if (!mobile.matches) setDrawer(false); };
    update(); desktop.addEventListener("change", update); mobile.addEventListener("change", update);
    return () => { desktop.removeEventListener("change", update); mobile.removeEventListener("change", update); };
  }, []);
  async function logout() {
    if (busy) return;
    setBusy(true);
    try { await fetch("/api/session/logout", { method: "POST", credentials: "same-origin", cache: "no-store", signal: AbortSignal.timeout(15000) }); }
    catch { /* Logout must clear local state even while offline. */ }
    finally { clearClientSession(); router.replace("/dang-nhap"); router.refresh(); }
  }
  return <div className={`app-shell ${collapsed ? "shell-collapsed" : ""}`}>
    <SkipLink /><AppSidebar user={user} collapsed={collapsed} onToggle={() => setCollapsed(value => !value)} />
    <div className="app-workspace"><Topbar user={user} title={title} drawerOpen={drawer} onOpenDrawer={() => setDrawer(true)} onLogout={() => setLogoutDialog(true)} />
      <div className="page-context"><Breadcrumbs current={title} /><TenantBadge organization={organization} /></div>
      <main id="noi-dung" tabIndex={-1} className="app-main">{children}</main>
    </div>
    <MobileNavDrawer open={drawer} onClose={() => setDrawer(false)}><AppSidebar user={user} mobile onNavigate={() => setDrawer(false)} /></MobileNavDrawer>
    <Dialog open={logoutDialog} onClose={() => setLogoutDialog(false)} onConfirm={() => void logout()} busy={busy} />
  </div>;
}
