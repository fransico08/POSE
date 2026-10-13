"use client";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { PauseIcon, PlayIcon } from "@heroicons/react/20/solid";
import { useAutoMotion } from "@/lib/motion/hooks";
export function RoleTabs({ labels, panels }: { labels: readonly string[]; panels: readonly ReactNode[] }) {
  const { ref, reduced, allowed, motionReady } = useAutoMotion<HTMLDivElement>();
  const [selected, setSelected] = useState(0), [paused, setPaused] = useState(false), [hovered, setHovered] = useState(false);
  const buttons = useRef<(HTMLButtonElement | null)[]>([]);
  const running = allowed && !paused && !hovered;
  useEffect(() => { if (!running) return; const timer = setInterval(() => setSelected(value => (value + 1) % labels.length), 6000); return () => clearInterval(timer); }, [running, labels.length]);
  function select(index: number) { setSelected(index); setPaused(true); }
  function move(key: string) {
    const next = key === "Home" ? 0 : key === "End" ? labels.length - 1 : key === "ArrowRight" ? (selected + 1) % labels.length : key === "ArrowLeft" ? (selected + labels.length - 1) % labels.length : undefined;
    if (next !== undefined) { select(next); buttons.current[next]?.focus(); return true; }
    return false;
  }
  // Delegated hover/focus controls automatic rotation; it does not make the wrapper interactive.
  // eslint-disable-next-line jsx-a11y/no-static-element-interactions
  return <div ref={ref} className="role-tabs" data-motion-ready={motionReady || undefined} data-running={running || undefined} onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)} onFocusCapture={event => { if (!(event.target as HTMLElement).closest("[data-motion-control]")) setPaused(true); }}>
    <div className="role-tab-bar"><div role="tablist" aria-label="Vai trò trong POSE CRM">{labels.map((label, i) => <button ref={node => { buttons.current[i] = node; }} type="button" key={label} role="tab" id={`role-tab-${i}`} aria-controls={`role-panel-${i}`} aria-selected={selected === i} tabIndex={selected === i ? 0 : -1} onClick={() => select(i)} onKeyDown={event => { if (move(event.key)) event.preventDefault(); }}>{label}{selected === i && <span key={`${selected}-${running}`} className={running ? "role-progress" : "role-progress-static"} aria-hidden="true" />}</button>)}</div><button type="button" className="motion-control" data-motion-control disabled={reduced} aria-label={reduced ? "Tự chuyển vai trò đã tắt" : paused ? "Tiếp tục chuyển vai trò" : "Tạm dừng chuyển vai trò"} onClick={() => setPaused(value => !value)}>{paused || reduced ? <PlayIcon aria-hidden="true" /> : <PauseIcon aria-hidden="true" />}{reduced ? "Tĩnh" : paused ? "Tiếp tục" : "Tạm dừng"}</button></div>
    {panels.map((panel, i) => {
      // APG includes a panel with nonfocusable content in the Tab sequence.
      // eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex
      return <div key={labels[i]} className="role-panel" role="tabpanel" id={`role-panel-${i}`} aria-labelledby={`role-tab-${i}`} hidden={i !== selected} tabIndex={0}>{panel}</div>;
    })}
  </div>;
}
