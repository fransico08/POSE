import { SkipLink } from "@/components/layout/SkipLink";
import { LandingHeader } from "@/components/landing/LandingHeader";
import { Hero } from "@/components/landing/Hero";
import { CapabilityStrip } from "@/components/landing/CapabilityStrip";
import { ProblemSection } from "@/components/landing/ProblemSection";
import { ProcessSection } from "@/components/landing/ProcessSection";
import { RoleSection } from "@/components/landing/RoleSection";
import { IndustrySection } from "@/components/landing/IndustrySection";
import { TrustSection } from "@/components/landing/TrustSection";
import { FaqSection } from "@/components/landing/FaqSection";
import { CtaBand } from "@/components/landing/CtaBand";
import { LandingFooter } from "@/components/landing/LandingFooter";
import type { Metadata } from "next";
import { landingMeta } from "@/content/landing";
import { getSiteUrl } from "@/lib/site";
import { landingStructuredData, serializeJsonLd } from "@/lib/landing-seo";

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
  return <><SkipLink /><LandingHeader /><main id="noi-dung" tabIndex={-1}><Hero /><CapabilityStrip /><ProblemSection /><ProcessSection /><RoleSection /><IndustrySection /><TrustSection /><FaqSection /><CtaBand /></main><LandingFooter /><script type="application/ld+json" dangerouslySetInnerHTML={{ __html: serializeJsonLd(landingStructuredData(getSiteUrl())) }} /></>;
}
