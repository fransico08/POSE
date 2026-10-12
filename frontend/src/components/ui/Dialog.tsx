"use client";
import { useId } from "react";
import { useModal } from "./use-modal";
import { Button } from "./Button";
export function Dialog({ open, onClose, onConfirm, busy = false }: { open: boolean; onClose: () => void; onConfirm: () => void; busy?: boolean }) {
  const id = useId();
  const modal = useModal(open, () => { if (!busy) onClose(); });
  return <dialog {...modal} className="dialog" aria-modal="true" aria-labelledby={`${id}-title`} aria-describedby={`${id}-description`}>
    <h2 id={`${id}-title`}>Đăng xuất</h2><p id={`${id}-description`}>Bạn có chắc chắn muốn đăng xuất?</p>
    <footer className="dialog-actions"><Button variant="secondary" data-autofocus disabled={busy} onClick={onClose}>Hủy</Button><Button variant="danger" busy={busy} onClick={onConfirm}>{busy ? "Đang đăng xuất…" : "Đăng xuất"}</Button></footer>
  </dialog>;
}
