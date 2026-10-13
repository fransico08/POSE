"use client";
import { useEffect, useRef, type ReactNode } from "react";
import { useReducedMotion } from "@/lib/motion/hooks";
import { observeElement } from "@/lib/motion/observer";
export function JourneyProgressFallback({ children }: { children: ReactNode }) {
  const ref = useRef<HTMLOListElement>(null), reduced = useReducedMotion();
  useEffect(() => {
    if (reduced) return;
    const items = ref.current?.querySelectorAll<HTMLElement>("li");
    if (!items) return;
    // Progressive enhancement marks a decorative path; never hides text or moves focus.
    const cleanups = [...items].map(item => observeElement(item, entry => { if (entry.isIntersecting) item.dataset.reached = "true"; }));
    return () => cleanups.forEach(cleanup => cleanup());
  }, [reduced]);
  return <ol ref={ref} className="journey-list" data-reduced={reduced || undefined}>{children}</ol>;
}
