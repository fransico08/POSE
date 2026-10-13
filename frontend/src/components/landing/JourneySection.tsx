import { journeySteps, sectionCopy } from "@/content/landing";
import { SectionHeading } from "./SectionHeading";
import { JourneyProgressFallback } from "./JourneyProgressFallback";
import { Customer360Card } from "./mock/Customer360Card";
export function JourneySection() {
  return <section id="cach-hoat-dong" className="landing-section landing-surface" aria-labelledby="journey-title"><div className="landing-container journey-layout"><div className="journey-sticky"><SectionHeading id="journey-title" {...sectionCopy.journey} /><figure><div aria-hidden="true"><Customer360Card /></div><figcaption className="demo-label">Giao diện minh họa</figcaption></figure></div><JourneyProgressFallback>{journeySteps.map((step, i) => <li key={step.id}><span className="journey-number" aria-hidden="true">{String(i + 1).padStart(2, "0")}</span><div><h3 data-marketing>{step.title}</h3><p data-marketing>{step.description}</p></div></li>)}</JourneyProgressFallback></div></section>;
}
