"use client";
import { useId } from "react";
import { useModal } from "./use-modal";
import { Button } from "./Button";
export function Dialog({ open, onClose, onConfirm, busy = false, title = "Đăng xuất", description = "Bạn có chắc chắn muốn đăng xuất?", confirmLabel = "Đăng xuất", busyLabel = "Đang đăng xuất…", confirmVariant = "danger" }: { open: boolean; onClose: () => void; onConfirm: () => void; busy?: boolean; title?: string; description?: string; confirmLabel?: string; busyLabel?: string; confirmVariant?: "primary" | "danger" }) {
  const id = useId();
  const modal = useModal(open, () => { if (!busy) onClose(); });
  return <dialog {...modal} className="dialog" aria-modal="true" aria-labelledby={`${id}-title`} aria-describedby={`${id}-description`}>
    <h2 id={`${id}-title`}>{title}</h2><p id={`${id}-description`}>{description}</p>
    <footer className="dialog-actions"><Button variant="secondary" data-autofocus disabled={busy} onClick={onClose}>Hủy</Button><Button variant={confirmVariant} busy={busy} onClick={onConfirm}>{busy ? busyLabel : confirmLabel}</Button></footer>
  </dialog>;
}
