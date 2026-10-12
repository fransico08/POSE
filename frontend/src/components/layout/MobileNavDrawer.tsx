"use client";
import { useId } from "react";
import { XMarkIcon } from "@heroicons/react/24/outline";
import { useModal } from "@/components/ui/use-modal";
export function MobileNavDrawer({ open, onClose, children }: { open: boolean; onClose: () => void; children: React.ReactNode }) {
  const id = useId(), modal = useModal(open, onClose);
  return <dialog {...modal} id="mobile-navigation" className="nav-drawer" aria-modal="true" aria-labelledby={id}>
    <header className="drawer-header"><h2 id={id}>Điều hướng POSE</h2><button type="button" data-autofocus className="icon-button" aria-label="Đóng điều hướng" onClick={onClose}><XMarkIcon aria-hidden="true" /></button></header>
    {children}
  </dialog>;
}
