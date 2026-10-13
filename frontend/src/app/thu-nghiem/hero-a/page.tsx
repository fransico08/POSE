import { Plus_Jakarta_Sans } from "next/font/google";
import { notFound } from "next/navigation";
import { ConceptPage } from "@/components/landing/concepts/ConceptPage";

const display = Plus_Jakarta_Sans({ weight: "800", subsets: ["vietnamese", "latin"], display: "swap", variable: "--landing-display-font" });
export const metadata = { title: "Concept A — POSE CRM", robots: { index: false, follow: false } };
export default function HeroA() {
  if (process.env.NODE_ENV !== "development") notFound();
  return <ConceptPage variant="a" fontVariable={display.variable} />;
}
