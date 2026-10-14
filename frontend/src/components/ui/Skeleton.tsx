"use client";
import { useBoundedMotion } from "@/lib/motion/bounded";
export function Skeleton() {
  const { ref, running, motionReady, complete } = useBoundedMotion<HTMLElement>();
  return <main ref={ref} id="noi-dung" className="checking" data-motion-ready={motionReady || undefined} data-running={running || undefined} data-complete={complete || undefined} aria-busy="true" aria-label="Đang kiểm tra phiên đăng nhập">
    <h1 className="sr-only">Đang kiểm tra phiên đăng nhập</h1>
    <p role="status">Đang kiểm tra phiên đăng nhập</p><span className="skeleton skeleton-title" aria-hidden="true" /><span className="skeleton" aria-hidden="true" /><span className="skeleton" aria-hidden="true" />
  </main>;
}
