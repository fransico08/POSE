"use client";
import { createContext, useContext, type ReactNode } from "react";
import { useMotionInteraction } from "@/lib/motion/interaction";
const HeroMotionContext = createContext<{ hovered: boolean; focused: boolean } | null>(null);
export function useHeroMotionState() {
  const state = useContext(HeroMotionContext);
  if (!state) throw new Error("Hero motion requires its scoped provider");
  return state;
}
export function HeroMotionScope({ children }: { children: ReactNode }) {
  const { hovered, focused, events } = useMotionInteraction<HTMLElement>();
  // Server-rendered copy is an opaque child, not authored by this client boundary.
  return <HeroMotionContext value={{ hovered, focused }}><section className="landing-hero landing-dark" aria-labelledby="hero-title" {...events}>{children}</section></HeroMotionContext>;
}
