"use client";
import { useEffect, useRef, useState } from "react";
import { Bars3Icon, XMarkIcon } from "@heroicons/react/24/outline";
import { commonCopy, landingNavigation } from "@/content/landing";

export function MobileNavDisclosure() {
  const [open, setOpen] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLElement>(null);

  useEffect(() => {
    if (!open) return;
    const closeWithEscape = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      const active = document.activeElement;
      // This disclosure is not a modal: do not steal focus from the page.
      if (active !== trigger.current && !panel.current?.contains(active)) return;
      event.preventDefault();
      setOpen(false);
      trigger.current?.focus();
    };
    document.addEventListener("keydown", closeWithEscape);
    return () => document.removeEventListener("keydown", closeWithEscape);
  }, [open]);

  return <>
    <div className="landing-mobile-bar landing-container">
      {/* eslint-disable-next-line @next/next/no-html-link-for-pages -- Keep public navigation as native document links. */}
      <a className="landing-brand" href="/">{commonCopy.brand}</a>
      <button ref={trigger} type="button" className="icon-button" aria-expanded={open} aria-controls="landing-mobile-navigation" aria-label={open ? "Đóng điều hướng" : "Mở điều hướng"} onClick={() => setOpen(value => !value)}>
        {open ? <XMarkIcon aria-hidden="true" /> : <Bars3Icon aria-hidden="true" />}
      </button>
    </div>
    <nav ref={panel} id="landing-mobile-navigation" className="landing-mobile-navigation" aria-label="Điều hướng trang giới thiệu" hidden={!open}>
      <ul>{landingNavigation.map(item => <li key={item.href}><a href={item.href} onClick={() => setOpen(false)}>{item.label}</a></li>)}<li><a className="button button-primary" href="/dang-nhap">{commonCopy.login}</a></li></ul>
    </nav>
  </>;
}
