"use client";
import { useEffect, useState, type ReactNode } from "react";
import { useAutoMotion } from "@/lib/motion/hooks";
import { scheduleWhenIdle } from "@/lib/motion/idle";
import { useHeroMotionState } from "./HeroMotionScope";

interface Frame { id: string; label: string; description: string }
export function HeroMotion({ frames, images, backdrops }: { frames: readonly Frame[]; images: readonly ReactNode[]; backdrops: readonly ReactNode[] }) {
  const { ref, reduced, allowed, motionReady } = useAutoMotion<HTMLElement>();
  const { hovered, focused } = useHeroMotionState();
  const [selected, setSelected] = useState(0), [assetsReady, setAssetsReady] = useState(false);
  const running = allowed && !hovered && !focused;
  const active = reduced ? 0 : selected;
  useEffect(() => {
    if (!allowed || assetsReady) return;
    return scheduleWhenIdle(() => setAssetsReady(true));
  }, [allowed, assetsReady]);
  useEffect(() => {
    if (!running) return;
    const timer = window.setInterval(() => setSelected(value => (value + 1) % frames.length), 5000);
    return () => window.clearInterval(timer);
  }, [running, frames.length]);
  return <>
    <div className="hero-backdrops" aria-hidden="true">{backdrops.map((background, i) => <div key={frames[i].id} className="hero-backdrop" data-active={i === active || undefined}>{background}</div>)}</div>
    <figure ref={ref} className="hero-coverflow" aria-hidden="true" data-selected={active} data-running={running || undefined} data-motion-ready={motionReady || undefined} data-reduced={reduced || undefined}>
      <div className="coverflow-track">{frames.map((frame, i) => {
        const offset = ((i - active + frames.length + 3) % frames.length) - 3;
        return <div key={frame.id} className="coverflow-card" data-offset={offset} data-active={i === active || undefined}>
          <div className="coverflow-image">{(i === 0 || assetsReady) && images[i]}<strong className="coverflow-label">{frame.label}</strong></div>
          <p className="coverflow-description">{frame.description}</p>
        </div>;
      })}</div>
      <figcaption>Luồng CRM minh họa theo ngành</figcaption>
    </figure>
  </>;
}
