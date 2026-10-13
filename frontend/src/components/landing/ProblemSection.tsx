import { problems, sectionCopy } from "@/content/landing";
import { landingIcons } from "./icons";

export function ProblemSection() {
  return <section className="landing-section" aria-labelledby="problems-title">
    <div className="landing-container">
      <header className="landing-section-heading"><h2 id="problems-title">{sectionCopy.problems.title}</h2><p>{sectionCopy.problems.introduction}</p></header>
      <ul className="landing-problems landing-card-list">{problems.map(item => {
        const Icon = landingIcons[item.icon];
        return <li key={item.title}><article className="landing-card"><span className="landing-icon"><Icon aria-hidden="true" /></span><h3>{item.title}</h3><p>{item.description}</p></article></li>;
      })}</ul>
    </div>
  </section>;
}
