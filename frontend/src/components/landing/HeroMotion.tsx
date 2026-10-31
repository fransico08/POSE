"use client";
import { useEffect, useState, type ReactNode } from "react";
import { useAutoMotion } from "@/lib/motion/hooks";
import { useHeroMotionState } from "./HeroMotionScope";

export function HeroMotion({ children, caption }: { children: ReactNode; caption: ReactNode }) {
  const { ref, reduced, allowed, motionReady } = useAutoMotion<HTMLElement>();
  const { hovered, focused } = useHeroMotionState();
  const [phase, setPhase] = useState(0), [demo, setDemo] = useState("pipeline");
  const running = allowed && !hovered && !focused;
  useEffect(() => { if (!running) return; const timer = setInterval(() => setPhase(value => (value + 1) % 3), 6000); return () => clearInterval(timer); }, [running]);
  return <figure ref={ref} className="hero-figure" data-motion-ready={motionReady || undefined} data-running={running || undefined} data-phase={reduced ? 2 : phase} data-demo={demo}>
    <div className="hero-demo-picker" role="group" aria-label="Chọn trọng tâm minh họa"><button type="button" aria-pressed={demo === "pipeline"} onClick={() => setDemo("pipeline")}>Cơ hội</button><button type="button" aria-pressed={demo === "customer"} onClick={() => setDemo("customer")}>Hồ sơ</button></div>
    {children}
    <figcaption>{caption}</figcaption>
  </figure>;
}
