import Link from "next/link";
import { ArrowRightIcon } from "@heroicons/react/20/solid";
import { heroCopy, heroFrames } from "@/content/landing";
import { HeroMotionScope } from "./HeroMotionScope";
import { HeroFrames } from "./HeroFrames";
import { RotatingPhrase } from "./RotatingPhrase";

export function HeroSection() {
  return <HeroMotionScope><div className="landing-container hero-layout">
    <div className="hero-copy"><p className="hero-eyebrow" data-marketing>{heroCopy.eyebrow}</p>
      <h1 id="hero-title" className="hero-title" data-marketing>{heroCopy.title.map((line, i) => <span key={line}>{line}{i < 2 && " "}</span>)}</h1>
      <p className="hero-description" data-marketing>{heroCopy.description}</p>
      <RotatingPhrase phrases={heroCopy.phrases} summary={heroCopy.phraseSummary} />
      <div className="hero-actions"><Link className="landing-button landing-primary hero-cta" href="/dang-nhap" prefetch={false} data-marketing>Đăng nhập<ArrowRightIcon aria-hidden="true" /></Link><a className="landing-button landing-secondary hero-cta" href="#cach-hoat-dong" data-marketing>Xem cách hoạt động<ArrowRightIcon aria-hidden="true" /></a></div>
    </div>
    <HeroFrames />
    <ul className="sr-only hero-frame-summary" aria-label="Luồng CRM minh họa theo sáu ngành">{heroFrames.map(frame => <li key={frame.id}>{frame.label}: {frame.description}</li>)}</ul>
  </div></HeroMotionScope>;
}
