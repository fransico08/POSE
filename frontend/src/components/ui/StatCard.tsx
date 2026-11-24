import Link from "next/link";
import type { ReactNode } from "react";
export function StatCard({ label, value, hint, tone = "default", href, icon }: { label: string; value: ReactNode; hint?: ReactNode; tone?: "default" | "accent" | "success" | "danger"; href?: string; icon?: ReactNode }) {
  const content = <>{icon && <span className="pose-stat-icon" aria-hidden="true">{icon}</span>}<span className="pose-stat-label">{label}</span>{" "}<strong className="pose-stat-value">{value}</strong>{hint && <span className="pose-stat-hint">{hint}</span>}</>;
  return href ? <Link href={href} className={`pose-stat pose-stat-${tone}`}>{content}</Link> : <article className={`pose-stat pose-stat-${tone}`}>{content}</article>;
}
