import { ChevronDownIcon } from "@heroicons/react/20/solid";
import { faqEntries, sectionCopy } from "@/content/landing";
import { SectionHeading } from "./SectionHeading";
export function FaqSection() {
  return <section id="cau-hoi" className="landing-section landing-surface" aria-labelledby="faq-title"><div className="landing-container"><SectionHeading id="faq-title" {...sectionCopy.faq} /><div className="landing-faq">{faqEntries.map(entry => <details key={entry.id} id={`faq-${entry.id}`}><summary><span data-marketing>{entry.question}</span><ChevronDownIcon aria-hidden="true" /></summary><p>{entry.answer}</p></details>)}</div></div></section>;
}
