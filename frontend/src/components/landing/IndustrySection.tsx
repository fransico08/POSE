import { industries, sectionCopy } from "@/content/landing";
import { SectionHeading } from "./SectionHeading";
import { IndustrySwitcher } from "./IndustrySwitcher";
import { LandingIcon } from "./LandingIcon";
export function IndustrySection() {
  return <section className="landing-section landing-surface" aria-labelledby="industries-title"><div className="landing-container"><SectionHeading id="industries-title" {...sectionCopy.industries} /><IndustrySwitcher items={industries} icons={industries.map(industry => <LandingIcon key={industry.id} name={industry.icon} />)} /></div></section>;
}
