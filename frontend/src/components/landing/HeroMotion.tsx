"use client";
import { useEffect, useState, type ReactNode } from "react";
import { PauseIcon, PlayIcon } from "@heroicons/react/20/solid";
import { useAutoMotion } from "@/lib/motion/hooks";
import { useHeroMotionState } from "./HeroMotionScope";

export function HeroMotion({ children, caption }: { children: ReactNode; caption: ReactNode }) {
  const { ref, reduced, allowed, motionReady } = useAutoMotion<HTMLElement>();
  const { paused, hovered, toggle } = useHeroMotionState();
  const [phase, setPhase] = useState(0), [demo, setDemo] = useState("pipeline");
  const running = allowed && !paused && !hovered;
  useEffect(() => { if (!running) return; const timer = setInterval(() => setPhase(value => (value + 1) % 3), 6000); return () => clearInterval(timer); }, [running]);
  return <figure ref={ref} className="hero-figure" data-motion-ready={motionReady || undefined} data-running={running || undefined} data-phase={reduced ? 2 : phase} data-demo={demo}>
    <div className="landing-glow landing-glow-blue" aria-hidden="true" /><div className="landing-glow landing-glow-amber" aria-hidden="true" />
    <div className="hero-demo-picker" role="group" aria-label="Chọn trọng tâm minh họa"><button type="button" aria-pressed={demo === "pipeline"} onClick={() => setDemo("pipeline")}>Cơ hội</button><button type="button" aria-pressed={demo === "customer"} onClick={() => setDemo("customer")}>Hồ sơ</button></div>
    {children}
    <div className="hero-motion-controls"><button type="button" className="motion-control" data-motion-control disabled={reduced} aria-label={reduced ? "Chuyển động đã tắt" : paused ? "Tiếp tục hiệu ứng" : "Tạm dừng hiệu ứng"} onClick={toggle}>{!reduced && (paused ? <PlayIcon aria-hidden="true" /> : <PauseIcon aria-hidden="true" />)}{reduced ? "Đã tắt chuyển động" : paused ? "Tiếp tục" : "Tạm dừng"}</button></div>
    <figcaption>{caption}</figcaption>
  </figure>;
}
