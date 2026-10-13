"use client";
import { useState, type ReactNode } from "react";
import { PauseIcon, PlayIcon } from "@heroicons/react/20/solid";
import { useAutoMotion } from "@/lib/motion/hooks";
export function IndustryMarquee({ items, title }: { items: readonly { label: string; icon: ReactNode }[]; title: string }) {
  const { ref, allowed, reduced, motionReady } = useAutoMotion<HTMLElement>();
  const [paused, setPaused] = useState(false);
  const chips = items.map(item => <li key={item.label}>{item.icon}{item.label}</li>);
  return <section ref={ref} className="industry-strip landing-dark" aria-labelledby="industry-strip-title" data-motion-ready={motionReady || undefined} data-running={allowed && !paused || undefined}><div className="landing-container"><h2 id="industry-strip-title" data-marketing>{title}</h2><div className="marquee-row"><div className="marquee-window"><div className="marquee-track"><ul>{chips}</ul><ul className="marquee-clone" aria-hidden="true">{chips}</ul></div></div><button type="button" className="motion-control motion-icon" disabled={reduced} aria-label={reduced ? "Chuyển động dải ngành đã tắt" : paused ? "Tiếp tục dải ngành" : "Tạm dừng dải ngành"} onClick={() => setPaused(value => !value)}>{paused || reduced ? <PlayIcon aria-hidden="true" /> : <PauseIcon aria-hidden="true" />}</button></div></div></section>;
}
