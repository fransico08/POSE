"use client";
import type { ReactNode } from "react";
import { useAutoMotion } from "@/lib/motion/hooks";
import { useMotionInteraction } from "@/lib/motion/interaction";
export function IndustryMarquee({ items, title }: { items: readonly { label: string; icon: ReactNode }[]; title: string }) {
  const { ref, allowed, motionReady } = useAutoMotion<HTMLElement>();
  const { hovered, focused, events } = useMotionInteraction<HTMLElement>();
  const chips = items.map(item => <li key={item.label}>{item.icon}{item.label}</li>);
  // Delegated events pause decoration without making the region a keyboard control.
  return <section ref={ref} className="industry-strip landing-dark" aria-labelledby="industry-strip-title" data-motion-ready={motionReady || undefined} data-running={allowed && !hovered && !focused || undefined} {...events}><div className="landing-container"><h2 id="industry-strip-title" data-marketing>{title}</h2><div className="marquee-row"><div className="marquee-window"><div className="marquee-track"><ul>{chips}</ul><ul className="marquee-clone" aria-hidden="true">{chips}</ul></div></div></div></div></section>;
}
