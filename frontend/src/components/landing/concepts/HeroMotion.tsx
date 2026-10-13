"use client";
import { useEffect, useState, type ReactNode } from "react";
import Link from "next/link";
import { ArrowRightIcon, PauseIcon, PlayIcon } from "@heroicons/react/20/solid";
import { conceptHero } from "./copy";
import { useConceptVisibility } from "./motion-state";

export function HeroMotion({ children }: { children: ReactNode }) {
  const { ref, reduced, allowed } = useConceptVisibility<HTMLElement>();
  const [paused, setPaused] = useState(false), [hovered, setHovered] = useState(false);
  const [phrase, setPhrase] = useState(0), [won, setWon] = useState(false), [demo, setDemo] = useState("pipeline");
  const running = allowed && !paused && !hovered;
  useEffect(() => {
    if (!running) return;
    const phrases = window.setInterval(() => setPhrase(value => (value + 1) % conceptHero.phrases.length), 4000);
    const stages = window.setInterval(() => setWon(value => !value), 6000);
    return () => { clearInterval(phrases); clearInterval(stages); };
  }, [running]);
  // Delegated focus/hover only pauses decoration; the section is not a control.
  // eslint-disable-next-line jsx-a11y/no-noninteractive-element-interactions
  return <section ref={ref} className="concept-hero" aria-labelledby="concept-title" data-running={running || undefined} data-won={reduced || won || undefined} data-demo={demo} onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)} onFocusCapture={event => { if (!(event.target as HTMLElement).closest("[data-motion-control]")) setPaused(true); }}>
    <div className="concept-glow concept-glow-blue" aria-hidden="true" /><div className="concept-glow concept-glow-accent" aria-hidden="true" />
    <div className="concept-container concept-hero-layout">
      <div className="concept-copy">
        <p className="concept-eyebrow concept-enter concept-enter-one"><span />{conceptHero.eyebrow}</p>
        <h1 id="concept-title" className="concept-title concept-enter concept-enter-two">{conceptHero.title.map((line, index) => <span key={line} className={index === 2 ? "concept-title-accent" : undefined}>{line}{index < 2 && " "}</span>)}</h1>
        <p className="concept-description concept-enter concept-enter-three">{conceptHero.description}</p>
        <p className="concept-audience"><span aria-hidden="true">Dành cho <span key={reduced ? 0 : phrase} className={running ? "concept-phrase" : undefined}>{conceptHero.phrases[reduced ? 0 : phrase]}</span></span><span className="sr-only">{conceptHero.phraseSummary}</span></p>
        <div className="concept-actions concept-enter concept-enter-four"><Link className="concept-button concept-primary" href="/dang-nhap"><span>Đăng nhập</span><ArrowRightIcon aria-hidden="true" /></Link><Link className="concept-button concept-secondary" href="/#quy-trinh">Xem cách hoạt động<ArrowRightIcon aria-hidden="true" /></Link></div>
        <button type="button" className="concept-motion-control" data-motion-control disabled={reduced} onClick={() => setPaused(value => !value)}>{paused || reduced ? <PlayIcon aria-hidden="true" /> : <PauseIcon aria-hidden="true" />}{reduced ? "Chuyển động đã tắt" : paused ? "Tiếp tục hiệu ứng" : "Tạm dừng hiệu ứng"}</button>
      </div>
      <figure className="concept-figure concept-enter concept-enter-three">
        <div className="concept-demo-picker" role="group" aria-label="Chọn trọng tâm minh họa"><button type="button" aria-pressed={demo === "pipeline"} onClick={() => setDemo("pipeline")}>Cơ hội</button><button type="button" aria-pressed={demo === "customer"} onClick={() => setDemo("customer")}>Hồ sơ</button></div>
        {children}<figcaption><span className="concept-demo-label">Giao diện minh họa</span><span>{conceptHero.caption}</span></figcaption>
      </figure>
    </div>
  </section>;
}
