"use client";
import { useEffect, useId, useRef, useState } from "react";
import { ArrowRightStartOnRectangleIcon, ChevronDownIcon } from "@heroicons/react/24/outline";
import { displayRoles, initials } from "@/config/roles";
import type { SessionUser } from "@/lib/session/types";
export function UserMenu({ user, onLogout }: { user: SessionUser; onLogout: () => void }) {
  const [open, setOpen] = useState(false);
  const id = useId(), trigger = useRef<HTMLButtonElement>(null), item = useRef<HTMLButtonElement>(null), container = useRef<HTMLDivElement>(null);
  useEffect(() => { if (open) item.current?.focus(); }, [open]);
  useEffect(() => {
    if (!open) return;
    const outside = (event: PointerEvent) => { if (!container.current?.contains(event.target as Node)) setOpen(false); };
    document.addEventListener("pointerdown", outside);
    return () => document.removeEventListener("pointerdown", outside);
  }, [open]);
  function close() { setOpen(false); trigger.current?.focus(); }
  return <div ref={container} className="user-menu">
    <button ref={trigger} className="user-trigger" type="button" aria-haspopup="menu" aria-expanded={open} aria-controls={id} aria-label={`Menu người dùng: ${user.fullName}`} onClick={() => setOpen(value => !value)} onKeyDown={event => { if (["ArrowDown", "ArrowUp"].includes(event.key)) { event.preventDefault(); setOpen(true); } }}>
      <span className="avatar" aria-hidden="true">{initials(user.fullName)}</span><span className="user-details"><strong>{user.fullName}</strong><small>{displayRoles(user.roles)}</small></span><ChevronDownIcon className="user-chevron" aria-hidden="true" />
    </button>
    {open && <div id={id} role="menu" tabIndex={-1} aria-label="Tài khoản" className="menu-popover" onKeyDown={event => {
      if (["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) { event.preventDefault(); item.current?.focus(); }
      if (event.key === "Escape") { event.preventDefault(); close(); }
    }}><button ref={item} role="menuitem" tabIndex={-1} type="button" onBlur={() => setOpen(false)} onClick={() => { close(); onLogout(); }}><ArrowRightStartOnRectangleIcon aria-hidden="true" />Đăng xuất</button></div>}
  </div>;
}
