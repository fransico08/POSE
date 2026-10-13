"use client";
import { useState } from "react";
import { LockClosedIcon, ShieldCheckIcon } from "@heroicons/react/20/solid";
export function SecurityView() {
  const [care, setCare] = useState(true);
  return <figure className="security-view"><div className="security-switch" role="group" aria-label="Góc nhìn minh họa"><button type="button" aria-pressed={!care} onClick={() => setCare(false)}>Bán hàng</button><button type="button" aria-pressed={care} onClick={() => setCare(true)}>Chăm sóc</button></div><div className="security-card" aria-hidden="true" data-care={care || undefined}><p><ShieldCheckIcon />Khách hàng mẫu</p><p className="security-note"><LockClosedIcon /><span>Ghi chú bán hàng</span></p><p className="security-note-state">{care ? "Không hiển thị" : "Trong phạm vi được giao"}</p></div><figcaption className="demo-label">Giao diện minh họa</figcaption></figure>;
}
