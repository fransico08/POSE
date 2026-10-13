import { capabilities, sectionCopy } from "@/content/landing";
import { SectionHeading } from "./SectionHeading";
import { LandingIcon } from "./LandingIcon";
import { Reveal } from "./Reveal";
import { BentoDemo } from "./mock/BentoDemo";
export function BentoSection() {
  return <section id="nang-luc" className="landing-section landing-canvas" aria-labelledby="bento-title"><div className="landing-container"><SectionHeading id="bento-title" {...sectionCopy.bento} /><div className="bento-grid">{capabilities.map(card => <Reveal key={card.id} className={`bento-cell bento-cell-${card.id}`}><article><span className="landing-icon-box"><LandingIcon name={card.icon} /></span><h3 data-marketing>{card.title}</h3><p data-marketing>{card.description}</p><BentoDemo kind={card.id} /></article></Reveal>)}</div><p className="demo-label">Giao diện minh họa</p></div></section>;
}
