"use client";
import { useRef, useState, type ReactNode } from "react";
import { ChevronLeftIcon, ChevronRightIcon } from "@heroicons/react/20/solid";
import type { Industry } from "@/content/landing";
export function IndustrySwitcher({ items, icons }: { items: readonly Industry[]; icons: readonly ReactNode[] }) {
  const [selected, setSelected] = useState(0);
  const buttons = useRef<(HTMLButtonElement | null)[]>([]), item = items[selected];
  const select = (value: number) => setSelected((value + items.length) % items.length);
  function keySelect(key: string) {
    const next = key === "Home" ? 0 : key === "End" ? items.length - 1 : key === "ArrowRight" ? (selected + 1) % items.length : key === "ArrowLeft" ? (selected + items.length - 1) % items.length : undefined;
    if (next === undefined) return false;
    select(next); buttons.current[next]?.focus(); return true;
  }
  return <div className="industry-switcher"><div className="industry-tabs" role="tablist" aria-label="Ngành minh họa">{items.map((entry, i) => <button ref={node => { buttons.current[i] = node; }} type="button" key={entry.id} role="tab" id={`industry-tab-${i}`} aria-controls={`industry-panel-${i}`} aria-selected={i === selected} tabIndex={i === selected ? 0 : -1} onClick={() => select(i)} onKeyDown={event => { if (keySelect(event.key)) event.preventDefault(); }}>{entry.label}</button>)}</div>
    {items.map((entry, i) => <div className="industry-panel" key={entry.id} id={`industry-panel-${i}`} role="tabpanel" aria-labelledby={`industry-tab-${i}`} hidden={i !== selected}><div className="industry-illustration" aria-hidden="true">{icons[i]}<span>{entry.label}</span></div><div className="industry-attributes"><p className="demo-label">Giao diện minh họa</p><p className="eyebrow">Sản phẩm/dịch vụ mẫu</p><h3>{entry.sample}</h3><dl>{entry.attributes.map(attribute => <div key={attribute.label}><dt>{attribute.label}</dt><dd>{attribute.value}</dd></div>)}</dl></div></div>)}
    <div className="industry-pagination" aria-label="Chuyển ngành"><button className="motion-control motion-icon" type="button" aria-label="Ngành trước" onClick={() => select(selected - 1)}><ChevronLeftIcon aria-hidden="true" /></button><p>{item.label}</p><div className="industry-dots" aria-hidden="true">{items.map((entry, i) => <span key={entry.id} data-selected={selected === i || undefined} />)}</div><button className="motion-control motion-icon" type="button" aria-label="Ngành tiếp theo" onClick={() => select(selected + 1)}><ChevronRightIcon aria-hidden="true" /></button></div>
  </div>;
}
