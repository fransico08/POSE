import { sectionCopy, trustItems } from "@/content/landing";
import { CheckCircleIcon } from "@heroicons/react/24/outline";

export function TrustSection() {
  return <section id="bao-mat" className="landing-section" aria-labelledby="trust-title">
    <div className="landing-container">
      <header className="landing-section-heading"><h2 id="trust-title">{sectionCopy.trust.title}</h2><p>{sectionCopy.trust.introduction}</p></header>
      <dl className="landing-trust">{trustItems.map(item => <div key={item.title}><dt><CheckCircleIcon aria-hidden="true" />{item.title}</dt><dd>{item.description}</dd></div>)}</dl>
    </div>
  </section>;
}
