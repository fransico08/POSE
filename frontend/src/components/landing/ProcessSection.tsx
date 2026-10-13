import { processSteps, sectionCopy } from "@/content/landing";

export function ProcessSection() {
  return <section id="quy-trinh" className="landing-section landing-surface" aria-labelledby="process-title">
    <div className="landing-container">
      <header className="landing-section-heading"><h2 id="process-title">{sectionCopy.process.title}</h2><p>{sectionCopy.process.introduction}</p></header>
      <ol className="landing-process">{processSteps.map(item => <li key={item.title}><h3>{item.title}</h3><p>{item.description}</p></li>)}</ol>
    </div>
  </section>;
}
