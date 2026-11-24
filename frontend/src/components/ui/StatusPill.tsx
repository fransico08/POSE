import type { ReactNode } from "react";
export function StatusPill({ children, tone = "default" }: { children: ReactNode; tone?: "default" | "accent" | "success" | "danger" }) { return <span className={`pose-status pose-status-${tone}`}>{children}</span>; }
