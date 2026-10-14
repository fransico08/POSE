"use client";
import type { ReactNode } from "react";
import { useBoundedMotion } from "@/lib/motion/bounded";
import styles from "./login-illustration.module.css";
const benefits = ["Thông tin khách hàng tập trung", "Phối hợp bán hàng và chăm sóc", "Truy cập phù hợp với từng vai trò"];
export function LoginIllustration({ children }: { children: ReactNode }) {
  const { ref, running, complete, reduced, motionReady } = useBoundedMotion<HTMLDivElement>();
  return <div ref={ref} className={styles.scene} data-motion-ready={motionReady || undefined} data-running={running || undefined} data-complete={complete || undefined}>
    <p className="sr-only">Thông tin khách hàng tập trung. Phối hợp bán hàng và chăm sóc. Truy cập phù hợp với từng vai trò.</p>
    <div aria-hidden="true"><p className={styles.benefit}>{benefits.map((benefit, i) => <span key={benefit} className={i === 0 ? styles.first : i === 1 ? styles.second : styles.third}>{benefit}</span>)}</p><div className={styles.card}>{children}</div><p className={styles.caption}>{reduced ? "Giao diện minh họa · Đã tắt chuyển động" : "Giao diện minh họa"}</p></div>
  </div>;
}
