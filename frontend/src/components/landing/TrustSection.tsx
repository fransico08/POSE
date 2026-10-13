import { sectionCopy, trustStatements } from "@/content/landing";
import { CheckCircleIcon } from "@heroicons/react/20/solid";
import { SectionHeading } from "./SectionHeading";
import { SecurityView } from "./SecurityView";
export function TrustSection() {
  return <section id="bao-mat" className="landing-section landing-dark security-section" aria-labelledby="trust-title"><div className="landing-container security-layout"><div><SectionHeading id="trust-title" {...sectionCopy.trust} /><ul className="security-statements">{trustStatements.map(text => <li key={text}><CheckCircleIcon aria-hidden="true" /><span data-marketing>{text}</span></li>)}</ul></div><SecurityView /></div></section>;
}
