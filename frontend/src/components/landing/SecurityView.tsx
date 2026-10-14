"use client";
import { useState, type ReactNode } from "react";
export function SecurityView({ children }: { children: ReactNode }) {
  const [care, setCare] = useState(true);
  return <figure className="security-view"><div className="security-switch" role="group" aria-label="Góc nhìn minh họa"><button type="button" aria-pressed={!care} onClick={() => setCare(false)}>Bán hàng</button><button type="button" aria-pressed={care} onClick={() => setCare(true)}>Chăm sóc</button></div><div className="security-card" aria-hidden="true" data-care={care || undefined}>{children}</div><figcaption className="demo-label">Giao diện minh họa</figcaption></figure>;
}
