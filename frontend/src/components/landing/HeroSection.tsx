import Link from "next/link";
import { ArrowRightIcon } from "@heroicons/react/20/solid";
import { heroCopy } from "@/content/landing";
import { HeroMotionScope } from "./HeroMotionScope";
import { HeroMotion } from "./HeroMotion";
import { RotatingPhrase } from "./RotatingPhrase";
import { MockScene } from "./mock/MockScene";

export function HeroSection() {
  return <section className="landing-hero landing-dark" aria-labelledby="hero-title"><HeroMotionScope>
    <div className="hero-copy"><p className="hero-eyebrow" data-marketing>{heroCopy.eyebrow}</p>
      <h1 id="hero-title" className="hero-title" data-marketing>{heroCopy.title.map((line, i) => <span key={line}>{line}{i < 2 && " "}</span>)}</h1>
      <p className="hero-description" data-marketing>{heroCopy.description}</p>
      <RotatingPhrase phrases={heroCopy.phrases} summary={heroCopy.phraseSummary} />
      <div className="hero-actions"><Link className="landing-button landing-primary hero-cta" href="/dang-nhap" prefetch={false} data-marketing>Đăng nhập<ArrowRightIcon aria-hidden="true" /></Link><a className="landing-button landing-secondary hero-cta" href="#cach-hoat-dong" data-marketing>Xem cách hoạt động<ArrowRightIcon aria-hidden="true" /></a></div>
    </div>
    <HeroMotion caption={<><span className="demo-label">Giao diện minh họa</span>{heroCopy.caption}</>}><MockScene /></HeroMotion>
  </HeroMotionScope></section>;
}
