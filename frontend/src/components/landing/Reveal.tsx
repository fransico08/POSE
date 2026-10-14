"use client";
import type { ReactNode } from "react";
import { useAutoMotion } from "@/lib/motion/hooks";
export function Reveal({ children, className = "" }: { children: ReactNode; className?: string }) {
  const { ref, seen, reduced, allowed, motionReady } = useAutoMotion<HTMLDivElement>();
  return <div ref={ref} className={`reveal ${className}`} data-seen={motionReady && !reduced && seen || undefined} data-running={allowed || undefined}>{children}</div>;
}
