"use client";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { useAutoMotion } from "@/lib/motion/hooks";
import { useMotionInteraction } from "@/lib/motion/interaction";
export function RoleTabs({ labels, panels }: { labels: readonly string[]; panels: readonly ReactNode[] }) {
  const { ref, allowed, motionReady } = useAutoMotion<HTMLDivElement>();
  const { hovered, focused, events } = useMotionInteraction<HTMLDivElement>();
  const [selected, setSelected] = useState(0), [userSelected, setUserSelected] = useState(false);
  const buttons = useRef<(HTMLButtonElement | null)[]>([]);
  const running = allowed && !userSelected && !hovered && !focused;
  useEffect(() => { if (!running) return; const timer = setInterval(() => setSelected(value => (value + 1) % labels.length), 6000); return () => clearInterval(timer); }, [running, labels.length]);
  function select(index: number) { setSelected(index); setUserSelected(true); }
  function move(key: string) {
    const next = key === "Home" ? 0 : key === "End" ? labels.length - 1 : key === "ArrowRight" ? (selected + 1) % labels.length : key === "ArrowLeft" ? (selected + labels.length - 1) % labels.length : undefined;
    if (next !== undefined) { select(next); buttons.current[next]?.focus(); return true; }
    return false;
  }
  // Delegated hover/focus controls automatic rotation; it does not make the wrapper interactive.
  return <div ref={ref} className="role-tabs" data-motion-ready={motionReady || undefined} data-running={running || undefined} {...events}>
    <div className="role-tab-bar"><div role="tablist" aria-label="Vai trò trong POSE CRM">{labels.map((label, i) => <button ref={node => { buttons.current[i] = node; }} type="button" key={label} role="tab" id={`role-tab-${i}`} aria-controls={`role-panel-${i}`} aria-selected={selected === i} tabIndex={selected === i ? 0 : -1} onClick={() => select(i)} onKeyDown={event => { if (move(event.key)) event.preventDefault(); }}>{label}{selected === i && <span key={`${selected}-${running}`} className={running ? "role-progress" : "role-progress-static"} aria-hidden="true" />}</button>)}</div></div>
    {panels.map((panel, i) => {
      // APG includes a panel with nonfocusable content in the Tab sequence.
      // eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex
      return <div key={labels[i]} className="role-panel" role="tabpanel" id={`role-panel-${i}`} aria-labelledby={`role-tab-${i}`} hidden={i !== selected} tabIndex={0}>{panel}</div>;
    })}
  </div>;
}
