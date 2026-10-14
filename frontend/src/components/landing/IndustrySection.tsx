import { industries, sectionCopy } from "@/content/landing";
import { SectionHeading } from "./SectionHeading";
import { IndustrySwitcher } from "./IndustrySwitcher";
import { LandingIcon } from "./LandingIcon";
import { IndustryPanel } from "./IndustryPanel";
export function IndustrySection() {
  return <section className="landing-section landing-surface" aria-labelledby="industries-title"><div className="landing-container"><SectionHeading id="industries-title" {...sectionCopy.industries} /><IndustrySwitcher items={industries.map(({ id, label }) => ({ id, label }))} panels={industries.map(industry => <IndustryPanel key={industry.id} item={industry} icon={<LandingIcon name={industry.icon} />} />)} /></div></section>;
}
