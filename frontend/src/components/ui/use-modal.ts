"use client";
import { useEffect, useRef, type KeyboardEvent } from "react";
export function useModal(open: boolean, onClose: () => void) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current;
    if (!open || !dialog) return;
    const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    if (!dialog.open) dialog.showModal();
    (dialog.querySelector<HTMLElement>("[data-autofocus]") ?? dialog).focus();
    return () => { if (dialog.open) dialog.close(); if (trigger?.isConnected) trigger.focus(); };
  }, [open]);
  function onKeyDown(event: KeyboardEvent<HTMLDialogElement>) {
    if (event.key === "Escape") { event.preventDefault(); onClose(); }
    if (event.key !== "Tab") return;
    const elements = [...(ref.current?.querySelectorAll<HTMLElement>('button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex="0"]') ?? [])].filter(el => !el.closest("[hidden]"));
    const first = elements[0], last = elements.at(-1);
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
  }
  return { ref, onKeyDown, onCancel: (event: React.SyntheticEvent) => { event.preventDefault(); onClose(); } };
}
