"use client";
import { useState } from "react";
import { AcademicCapIcon, BuildingOffice2Icon, ComputerDesktopIcon, HeartIcon, ShoppingBagIcon, TruckIcon } from "@heroicons/react/24/outline";
import { conceptIndustries } from "./copy";
import { useConceptVisibility } from "./motion-state";
const icons = [AcademicCapIcon, BuildingOffice2Icon, ComputerDesktopIcon, ShoppingBagIcon, TruckIcon, HeartIcon];

export function IndustryMarquee() {
  const { ref, allowed, reduced } = useConceptVisibility<HTMLElement>();
  const [paused, setPaused] = useState(false);
  const running = allowed && !paused;
  const chips = conceptIndustries.map((industry, index) => { const Icon = icons[index]; return <li key={industry}><Icon aria-hidden="true" />{industry}</li>; });
  return <section ref={ref} className="concept-industries" aria-labelledby="concept-industries-title" data-running={running || undefined}>
    <div className="concept-container"><div className="concept-industries-heading"><h2 id="concept-industries-title">Một nền tảng, nhiều ngành</h2><button type="button" className="concept-motion-control" disabled={reduced} onClick={() => setPaused(value => !value)}>{reduced ? "Chuyển động đã tắt" : paused ? "Tiếp tục" : "Tạm dừng"}</button></div>
      <div className="concept-marquee-window"><div className="concept-marquee-track"><ul>{chips}</ul><ul className="concept-marquee-clone" aria-hidden="true">{chips}</ul></div></div>
    </div>
  </section>;
}
