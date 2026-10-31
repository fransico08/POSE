"use client";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { Bars3Icon, XMarkIcon } from "@heroicons/react/24/outline";
import { landingNavigation } from "@/content/landing";
export function MobileNavDisclosure({ children, scrolled = false, lifted = false }: { children?: ReactNode; scrolled?: boolean; lifted?: boolean }) {
  const [open, setOpen] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null), panel = useRef<HTMLElement>(null);
  useEffect(() => {
    if (!open) return;
    function escape(event: KeyboardEvent) {
      if (event.key !== "Escape" || (document.activeElement !== trigger.current && !panel.current?.contains(document.activeElement))) return;
      event.preventDefault(); setOpen(false); trigger.current?.focus();
    }
    document.addEventListener("keydown", escape); return () => document.removeEventListener("keydown", escape);
  }, [open]);
  return <><header className="landing-header" data-scrolled={scrolled || undefined} data-lifted={lifted || undefined}><div className="landing-container landing-header-row">{children}<button ref={trigger} type="button" className="landing-menu motion-control motion-icon" aria-expanded={open} aria-controls="landing-mobile-navigation" aria-label={open ? "Đóng điều hướng" : "Mở điều hướng"} onClick={() => setOpen(value => !value)}>{open ? <XMarkIcon aria-hidden="true" /> : <Bars3Icon aria-hidden="true" />}</button></div></header><nav ref={panel} id="landing-mobile-navigation" className="landing-mobile-navigation" aria-label="Điều hướng trang giới thiệu" hidden={!open}><ul>{landingNavigation.map(item => <li key={item.href}><a href={item.href} onClick={() => setOpen(false)}>{item.label}</a></li>)}<li><a href="/dang-nhap">Đăng nhập</a></li></ul></nav></>;
}
