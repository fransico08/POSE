import { Fraunces } from "next/font/google";
import { notFound } from "next/navigation";
import { ConceptPage } from "@/components/landing/concepts/ConceptPage";

const display = Fraunces({ weight: "variable", axes: ["opsz", "SOFT"], subsets: ["vietnamese", "latin"], display: "swap", variable: "--landing-display-font" });
export const metadata = { title: "Concept B — POSE CRM", robots: { index: false, follow: false } };
export default function HeroB() {
  if (process.env.NODE_ENV !== "development") notFound();
  return <ConceptPage variant="b" fontVariable={display.variable} />;
}
