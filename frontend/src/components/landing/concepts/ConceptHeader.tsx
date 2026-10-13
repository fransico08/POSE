"use client";
import { useEffect, useRef, useState } from "react";
import { Bars3Icon, XMarkIcon } from "@heroicons/react/24/outline";
import { conceptNavigation } from "./copy";
import { observeConceptNode } from "./motion-state";

export function ConceptHeader() {
  const [open, setOpen] = useState(false), [pastHero, setPastHero] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null), panel = useRef<HTMLElement>(null);
  useEffect(() => {
    const sentinel = document.getElementById("concept-hero-end");
    if (!sentinel) return;
    return observeConceptNode(sentinel, entry => setPastHero(!entry.isIntersecting && entry.boundingClientRect.top < 0));
  }, []);
  useEffect(() => {
    if (!open) return;
    function escape(event: KeyboardEvent) {
      if (event.key !== "Escape" || (document.activeElement !== trigger.current && !panel.current?.contains(document.activeElement))) return;
      event.preventDefault(); setOpen(false); trigger.current?.focus();
    }
    document.addEventListener("keydown", escape); return () => document.removeEventListener("keydown", escape);
  }, [open]);
  return <>
    <header className="concept-header" data-past-hero={pastHero || undefined}>
      <div className="concept-container concept-header-row">
        {/* eslint-disable-next-line @next/next/no-html-link-for-pages -- Native links keep the concept isolated from app routing. */}
        <a href="/" className="concept-brand">POSE CRM</a>
        <nav className="concept-desktop-nav" aria-label="Điều hướng trang giới thiệu"><ul>{conceptNavigation.map(item => <li key={item.href}><a href={item.href}>{item.label}</a></li>)}</ul></nav>
        <a className="concept-button concept-login" href="/dang-nhap">Đăng nhập</a>
        <button ref={trigger} className="concept-menu" type="button" aria-expanded={open} aria-controls="concept-mobile-nav" aria-label={open ? "Đóng điều hướng" : "Mở điều hướng"} onClick={() => setOpen(value => !value)}>{open ? <XMarkIcon aria-hidden="true" /> : <Bars3Icon aria-hidden="true" />}</button>
      </div>
    </header>
    <nav ref={panel} id="concept-mobile-nav" className="concept-mobile-nav" hidden={!open} aria-label="Điều hướng trang giới thiệu"><ul>{conceptNavigation.map(item => <li key={item.href}><a href={item.href} onClick={() => setOpen(false)}>{item.label}</a></li>)}<li><a href="/dang-nhap">Đăng nhập</a></li></ul></nav>
  </>;
}
