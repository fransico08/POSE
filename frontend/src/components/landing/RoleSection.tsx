import { roleCards, sectionCopy } from "@/content/landing";
import { landingIcons } from "./icons";

export function RoleSection() {
  return <section id="vai-tro" className="landing-section" aria-labelledby="roles-title">
    <div className="landing-container">
      <header className="landing-section-heading"><h2 id="roles-title">{sectionCopy.roles.title}</h2><p>{sectionCopy.roles.introduction}</p></header>
      <ul className="landing-roles landing-card-list">{roleCards.map(item => {
        const Icon = landingIcons[item.icon];
        return <li key={item.title}><article className="landing-card"><span className="landing-icon"><Icon aria-hidden="true" /></span><h3>{item.title}</h3><ul className="landing-responsibilities">{item.responsibilities.map(text => <li key={text}>{text}</li>)}</ul></article></li>;
      })}</ul>
    </div>
  </section>;
}
