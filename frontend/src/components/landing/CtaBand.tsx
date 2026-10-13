import Link from "next/link";
import { ArrowRightIcon } from "@heroicons/react/20/solid";
import { sectionCopy } from "@/content/landing";
export function CtaBand() {
  return <section className="landing-section landing-cta" aria-labelledby="cta-title"><div className="landing-container"><h2 id="cta-title" data-marketing>{sectionCopy.cta.title}</h2><p data-marketing>{sectionCopy.cta.introduction}</p><Link className="landing-button landing-primary hero-cta" href="/dang-nhap" prefetch={false} data-marketing>Đăng nhập<ArrowRightIcon aria-hidden="true" /></Link></div></section>;
}
