"use client";
import { useEffect, useState, useSyncExternalStore, type CSSProperties, type ReactNode } from "react";
import { useAutoMotion } from "@/lib/motion/hooks";
import { scheduleWhenIdle } from "@/lib/motion/idle";
import { useHeroMotionState } from "./HeroMotionScope";

interface Frame { id: string; label: string }
const wideQuery = "(min-width: 1024px)";
const subscribeWide = (callback: () => void) => {
  const media = window.matchMedia(wideQuery);
  media.addEventListener("change", callback);
  return () => media.removeEventListener("change", callback);
};
export function HeroBackdrop({ frames, cards, washes }: { frames: readonly Frame[]; cards: readonly ReactNode[]; washes: readonly CSSProperties[] }) {
  const { ref, reduced, allowed, motionReady } = useAutoMotion<HTMLDivElement>();
  const { focused } = useHeroMotionState();
  const wide = useSyncExternalStore(subscribeWide, () => window.matchMedia(wideQuery).matches, () => false);
  const [selected, setSelected] = useState(0), [assetsReady, setAssetsReady] = useState(false);
  // The backdrop fills the hero, so pointer hover would freeze it permanently; keyboard focus still pauses it.
  const running = allowed && !focused;
  const active = reduced ? 0 : selected;
  // Photos mount only on wide screens after first paint; narrow screens keep the tiny washes.
  useEffect(() => {
    if (!wide || assetsReady) return;
    return scheduleWhenIdle(() => setAssetsReady(true));
  }, [wide, assetsReady]);
  useEffect(() => {
    if (!running) return;
    const timer = window.setInterval(() => setSelected(value => (value + 1) % frames.length), 5000);
    return () => window.clearInterval(timer);
  }, [running, frames.length]);
  // The whole layer is decoration behind the hero; the server list carries the equivalent text.
  return <div ref={ref} className="hero-backdrop" aria-hidden="true" data-selected={active} data-running={running || undefined} data-motion-ready={motionReady || undefined} data-reduced={reduced || undefined}>
    <div className="hero-wash">{washes.map((style, i) => <div key={frames[i].id} className="hero-wash-layer" style={style} data-active={i === active || undefined} />)}</div>
    <div className="hero-cards">{frames.map((frame, i) => {
      const offset = ((i - active + frames.length + 3) % frames.length) - 3;
      // The tiny wash doubles as the card placeholder until its lazy photo arrives.
      return <div key={frame.id} className="hero-card" style={washes[i]} data-offset={offset} data-active={i === active || undefined}>
        {wide && assetsReady && cards[i]}<span className="hero-card-label">{frame.label}</span>
      </div>;
    })}</div>
    <div className="hero-shade" />
  </div>;
}
