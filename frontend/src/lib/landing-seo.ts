import { faqEntries, landingMeta } from "@/content/landing";

export function landingStructuredData(siteUrl: URL) {
  const url = siteUrl.href;
  return {
    "@context": "https://schema.org",
    "@graph": [
      { "@type": "WebSite", "@id": `${url}#website`, name: "POSE CRM", url, inLanguage: "vi" },
      { "@type": "SoftwareApplication", "@id": `${url}#application`, name: "POSE CRM", url, applicationCategory: "BusinessApplication", operatingSystem: "Web", inLanguage: "vi", description: landingMeta.description },
      { "@type": "FAQPage", "@id": `${url}#faq`, mainEntity: faqEntries.map(item => ({ "@type": "Question", name: item.question, acceptedAnswer: { "@type": "Answer", text: item.answer } })) },
    ],
  };
}

export function serializeJsonLd(data: unknown): string {
  return JSON.stringify(data).replace(/</g, "\\u003c");
}
