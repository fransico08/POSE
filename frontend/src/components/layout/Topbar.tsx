"use client";
import { useEffect, useRef } from "react";
import { Bars3Icon } from "@heroicons/react/24/outline";
import { UserMenu } from "./UserMenu";
import type { SessionUser } from "@/lib/session/types";
export function Topbar({ user, title, drawerOpen, onOpenDrawer, onLogout }: { user: SessionUser; title: string; drawerOpen: boolean; onOpenDrawer: () => void; onLogout: () => void }) {
  const ref = useRef<HTMLElement>(null);
  useEffect(() => {
    const header = ref.current;
    if (!header) return;
    const update = () => {
      const height = header.getBoundingClientRect().height;
      const sticky = window.innerHeight > 500 && height <= window.innerHeight / 4;
      header.dataset.sticky = String(sticky);
      document.documentElement.style.setProperty("--sticky-offset", `${sticky ? height : 0}px`);
    };
    const observer = new ResizeObserver(update);
    observer.observe(header); window.addEventListener("resize", update); update();
    return () => { observer.disconnect(); window.removeEventListener("resize", update); document.documentElement.style.removeProperty("--sticky-offset"); };
  }, []);
  return <header ref={ref} className="topbar">
    <button type="button" className="icon-button mobile-menu-button" aria-label="Mở điều hướng" aria-expanded={drawerOpen} aria-controls="mobile-navigation" onClick={onOpenDrawer}><Bars3Icon aria-hidden="true" /></button>
    <p className="topbar-title"><span className="desktop-title">{title}</span><span className="mobile-brand">POSE</span></p>
    <UserMenu user={user} onLogout={onLogout} />
  </header>;
}
