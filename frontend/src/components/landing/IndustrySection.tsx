import { industries, industryNote, sectionCopy } from "@/content/landing";
import { landingIcons } from "./icons";

export function IndustrySection() {
  return <section className="landing-section landing-surface" aria-labelledby="industries-title">
    <div className="landing-container">
      <header className="landing-section-heading"><h2 id="industries-title">{sectionCopy.industries.title}</h2><p>{sectionCopy.industries.introduction}</p></header>
      <ul className="landing-industries">{industries.map(item => {
        const Icon = landingIcons[item.icon];
        return <li key={item.title}><Icon aria-hidden="true" />{item.title}</li>;
      })}</ul>
      <p className="landing-industry-note">{industryNote}</p>
    </div>
  </section>;
}
