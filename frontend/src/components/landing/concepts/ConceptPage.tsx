import { SkipLink } from "@/components/layout/SkipLink";
import { ConceptHeader } from "./ConceptHeader";
import { HeroMotion } from "./HeroMotion";
import { IndustryMarquee } from "./IndustryMarquee";
import { MockUiStage } from "./MockUiStage";
import "./concepts.css";

export function ConceptPage({ variant, fontVariable }: { variant: "a" | "b"; fontVariable: string }) {
  return <div className={`${fontVariable} concept-page concept-${variant}`}><SkipLink /><ConceptHeader /><main id="noi-dung" tabIndex={-1}><HeroMotion><MockUiStage /></HeroMotion><span id="concept-hero-end" className="concept-sentinel" aria-hidden="true" /><IndustryMarquee /></main></div>;
}
