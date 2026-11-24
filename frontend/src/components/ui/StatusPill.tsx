import type { ReactNode } from "react";
export function StatusPill({ children, tone = "default" }: { children: ReactNode; tone?: "default" | "accent" | "success" | "danger" }) { return <span className={`pose-status pose-status-${tone}`}>{children}</span>; }

export function statusTone(status: string): "default" | "accent" | "success" | "danger" {
  if (["ACTIVE", "QUALIFIED", "CONVERTED", "WON", "DONE", "SENT"].includes(status)) return "success";
  if (["DISQUALIFIED", "LOST", "CANCELLED", "INACTIVE", "CLOSED"].includes(status)) return "danger";
  if (["PROPOSAL", "IN_PROGRESS", "PENDING"].includes(status)) return "accent";
  return "default";
}
