"use client";
import { useState, type ReactNode } from "react";
export function CompareSlider({ before, after, handle }: { before: ReactNode; after: ReactNode; handle?: ReactNode }) {
  const [value, setValue] = useState(50);
  return <div className="compare-slider"><div className="compare-stage" data-position={value}>
    <div className="compare-before" aria-hidden="true">{before}</div><div className="compare-after" aria-hidden="true">{after}</div>
    <span className="compare-divider" aria-hidden="true"><span className="compare-handle">{handle}</span></span>
    <input id="compare-range" type="range" min="0" max="100" step="1" value={value} aria-valuetext={`Chưa có POSE: ${value} phần trăm bên trái; Với POSE: ${100 - value} phần trăm bên phải`} onChange={event => setValue(Number(event.target.value))} />
  </div><div className="compare-controls"><label htmlFor="compare-range">So sánh hai cách làm việc</label><button type="button" className="landing-text-control" onClick={() => setValue(100)}>Chưa có POSE</button><button type="button" className="landing-text-control" onClick={() => setValue(0)}>Với POSE</button></div></div>;
}
