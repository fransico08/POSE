"use client";
import { createContext, useContext, useState, type ReactNode } from "react";
const HeroMotionContext = createContext<{ paused: boolean; hovered: boolean; toggle: () => void } | null>(null);
export function useHeroMotionState() {
  const state = useContext(HeroMotionContext);
  if (!state) throw new Error("Hero motion requires its scoped provider");
  return state;
}
export function HeroMotionScope({ children }: { children: ReactNode }) {
  const [paused, setPaused] = useState(false), [hovered, setHovered] = useState(false);
  // Server-rendered copy is an opaque child, not authored by this client boundary.
  // eslint-disable-next-line jsx-a11y/no-static-element-interactions
  return <HeroMotionContext value={{ paused, hovered, toggle: () => setPaused(value => !value) }}><div className="landing-container hero-layout" onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)} onFocusCapture={event => { if (!(event.target as HTMLElement).closest("[data-motion-control]")) setPaused(true); }}>{children}</div></HeroMotionContext>;
}
