import { ChevronDownIcon } from "@heroicons/react/24/outline";
import { faqEntries, sectionCopy } from "@/content/landing";

export function FaqSection() {
  return <section id="cau-hoi" className="landing-section landing-surface" aria-labelledby="faq-title">
    <div className="landing-container">
      <header className="landing-section-heading"><h2 id="faq-title">{sectionCopy.faq.title}</h2><p>{sectionCopy.faq.introduction}</p></header>
      <div className="landing-faq">{faqEntries.map(item => <details key={item.id} id={item.id}><summary>{item.question}<ChevronDownIcon aria-hidden="true" /></summary><p>{item.answer}</p></details>)}</div>
    </div>
  </section>;
}
