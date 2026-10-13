"use client";
import { useEffect, useState, type ReactNode } from "react";
import { ArrowRightIcon, PauseIcon, PlayIcon } from "@heroicons/react/20/solid";
import Link from "next/link";
import { heroCopy } from "@/content/landing";
import { useAutoMotion } from "@/lib/motion/hooks";

export function HeroMotion({ children }: { children: ReactNode }) {
  const { ref, reduced, allowed, motionReady } = useAutoMotion<HTMLElement>();
  const [paused, setPaused] = useState(false), [hovered, setHovered] = useState(false);
  const [phrase, setPhrase] = useState(0), [phase, setPhase] = useState(0), [demo, setDemo] = useState("pipeline");
  const running = allowed && !paused && !hovered;
  useEffect(() => {
    if (!running) return;
    const phrases = setInterval(() => setPhrase(value => (value + 1) % heroCopy.phrases.length), 4000);
    const stages = setInterval(() => setPhase(value => (value + 1) % 3), 6000);
    return () => { clearInterval(phrases); clearInterval(stages); };
  }, [running]);
  // Ancestor handlers pause decoration only; the section is not an action target.
  // eslint-disable-next-line jsx-a11y/no-noninteractive-element-interactions
  return <section ref={ref} className="landing-hero landing-dark" aria-labelledby="hero-title" data-motion-ready={motionReady || undefined} data-running={running || undefined} data-phase={reduced ? 2 : phase} data-demo={demo} onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)} onFocusCapture={event => { if (!(event.target as HTMLElement).closest("[data-motion-control]")) setPaused(true); }}>
    <div className="landing-glow landing-glow-blue" aria-hidden="true" /><div className="landing-glow landing-glow-amber" aria-hidden="true" />
    <div className="landing-container hero-layout"><div className="hero-copy">
      <p className="hero-eyebrow hero-enter" data-marketing>{heroCopy.eyebrow}</p>
      <h1 id="hero-title" className="hero-title hero-enter hero-enter-two" data-marketing>{heroCopy.title.map((line, i) => <span key={line}>{line}{i < 2 && " "}</span>)}</h1>
      <p className="hero-description hero-enter hero-enter-three" data-marketing>{heroCopy.description}</p>
      <p className="hero-audience"><span aria-hidden="true" data-marketing>Dành cho <span key={reduced ? 0 : phrase} className={running ? "rotating-phrase" : undefined}>{heroCopy.phrases[reduced ? 0 : phrase]}</span></span><span className="sr-only">{heroCopy.phraseSummary}</span></p>
      <div className="hero-actions hero-enter hero-enter-four"><Link className="landing-button landing-primary hero-cta" href="/dang-nhap" prefetch={false} data-marketing>Đăng nhập<ArrowRightIcon aria-hidden="true" /></Link><a className="landing-button landing-secondary hero-cta" href="#cach-hoat-dong" data-marketing>Xem cách hoạt động<ArrowRightIcon aria-hidden="true" /></a></div>
    </div><figure className="hero-figure hero-enter hero-enter-three">
      <div className="hero-demo-picker" role="group" aria-label="Chọn trọng tâm minh họa"><button type="button" aria-pressed={demo === "pipeline"} onClick={() => setDemo("pipeline")}>Cơ hội</button><button type="button" aria-pressed={demo === "customer"} onClick={() => setDemo("customer")}>Hồ sơ</button></div>
      {children}
      <div className="hero-motion-controls"><button type="button" className="motion-control" data-motion-control disabled={reduced} aria-label={reduced ? "Chuyển động đã tắt" : paused ? "Tiếp tục hiệu ứng" : "Tạm dừng hiệu ứng"} onClick={() => setPaused(value => !value)}>{paused || reduced ? <PlayIcon aria-hidden="true" /> : <PauseIcon aria-hidden="true" />}{reduced ? "Tĩnh" : paused ? "Tiếp tục" : "Tạm dừng"}</button></div>
      <figcaption><span className="demo-label">Giao diện minh họa</span>{heroCopy.caption}</figcaption>
    </figure></div>
  </section>;
}
