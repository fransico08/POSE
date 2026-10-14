"use client";
import type { ReactNode } from "react";
import { useReducedMotion } from "@/lib/motion/hooks";
export function JourneyProgressFallback({ children }: { children: ReactNode }) {
  const reduced = useReducedMotion();
  // CSS enhances the decorative path; unsupported browsers retain the full line.
  return <ol className="journey-list" data-reduced={reduced || undefined}>{children}</ol>;
}
