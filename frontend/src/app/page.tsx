import { SkipLink } from "@/components/layout/SkipLink";
import { LandingHeader } from "@/components/landing/LandingHeader";
import { HeroMotion } from "@/components/landing/HeroMotion";
import { MockScene } from "@/components/landing/mock/MockScene";
import { IndustryMarquee } from "@/components/landing/IndustryMarquee";
import { LandingIcon } from "@/components/landing/LandingIcon";
import { CompareSection } from "@/components/landing/CompareSection";
import { JourneySection } from "@/components/landing/JourneySection";
import { BentoSection } from "@/components/landing/BentoSection";
import { RoleSection } from "@/components/landing/RoleSection";
import { IndustrySection } from "@/components/landing/IndustrySection";
import { TrustSection } from "@/components/landing/TrustSection";
import { FaqSection } from "@/components/landing/FaqSection";
import { CtaBand } from "@/components/landing/CtaBand";
import { LandingFooter } from "@/components/landing/LandingFooter";
import type { Metadata } from "next";
import { industries, landingMeta, sectionCopy } from "@/content/landing";
import { Fraunces } from "next/font/google";
import "@/styles/landing-v2.css";
import { getSiteUrl } from "@/lib/site";
import { landingStructuredData, serializeJsonLd } from "@/lib/landing-seo";

const display = Fraunces({ weight: ["700", "800"], subsets: ["vietnamese", "latin"], display: "swap", variable: "--landing-display-font" });

export const metadata: Metadata = {
  metadataBase: getSiteUrl(),
  title: { absolute: landingMeta.title },
  description: landingMeta.description,
  alternates: { canonical: "/" },
  robots: { index: true, follow: true },
  openGraph: { type: "website", locale: "vi_VN", title: landingMeta.title, description: landingMeta.description, url: "/", images: [{ url: "/images/pose-crm-overview-og.webp", width: 1200, height: 630, alt: landingMeta.imageAlt }] },
  twitter: { card: "summary_large_image", title: landingMeta.title, description: landingMeta.description, images: [{ url: "/images/pose-crm-overview-og.webp", alt: landingMeta.imageAlt }] },
};

export default function HomePage() {
  return <div className={`${display.variable} landing-page`}><SkipLink /><LandingHeader /><main id="noi-dung" tabIndex={-1}><HeroMotion><MockScene /></HeroMotion><span id="hero-end" className="hero-sentinel" aria-hidden="true" /><IndustryMarquee title={sectionCopy.industriesStrip.title} items={industries.map(industry => ({ label: industry.label, icon: <LandingIcon name={industry.icon} /> }))} /><CompareSection /><RoleSection /><JourneySection /><BentoSection /><IndustrySection /><TrustSection /><FaqSection /><CtaBand /></main><LandingFooter /><script type="application/ld+json" dangerouslySetInnerHTML={{ __html: serializeJsonLd(landingStructuredData(getSiteUrl())) }} /></div>;
}
