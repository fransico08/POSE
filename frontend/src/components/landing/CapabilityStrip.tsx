import { capabilities, sectionCopy } from "@/content/landing";
import { landingIcons } from "./icons";

export function CapabilityStrip() {
  return <section id="nang-luc" className="landing-section landing-surface" aria-labelledby="capabilities-title">
    <div className="landing-container">
      <header className="landing-section-heading"><h2 id="capabilities-title">{sectionCopy.capabilities.title}</h2><p>{sectionCopy.capabilities.introduction}</p></header>
      <ul className="landing-capabilities landing-card-list">{capabilities.map(item => {
        const Icon = landingIcons[item.icon];
        return <li key={item.title}><article className="landing-card"><span className="landing-icon"><Icon aria-hidden="true" /></span><h3>{item.title}</h3><p>{item.description}</p></article></li>;
      })}</ul>
    </div>
  </section>;
}
