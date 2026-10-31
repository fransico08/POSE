"use client";
import { useEffect, useState } from "react";
import { useAutoMotion } from "@/lib/motion/hooks";
import { useHeroMotionState } from "./HeroMotionScope";
export function RotatingPhrase({ phrases, summary }: { phrases: readonly string[]; summary: string }) {
  const { ref, allowed, reduced } = useAutoMotion<HTMLParagraphElement>();
  const { hovered, focused } = useHeroMotionState();
  const [phrase, setPhrase] = useState(0);
  const running = allowed && !hovered && !focused;
  useEffect(() => { if (!running) return; const timer = setInterval(() => setPhrase(value => (value + 1) % phrases.length), 4000); return () => clearInterval(timer); }, [running, phrases.length]);
  return <p ref={ref} className="hero-audience"><span aria-hidden="true" data-marketing>Dành cho <span key={reduced ? 0 : phrase} className={running ? "rotating-phrase" : undefined}>{phrases[reduced ? 0 : phrase]}</span></span><span className="sr-only">{summary}</span></p>;
}
