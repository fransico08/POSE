"use client";
import { useRef, useState, type ReactNode } from "react";
import { ChevronLeftIcon, ChevronRightIcon } from "@heroicons/react/20/solid";
import type { Industry } from "@/content/landing";
export function IndustrySwitcher({ items, icons, definitionCopy }: { items: readonly Industry[]; icons: readonly ReactNode[]; definitionCopy: { title: string; description: string } }) {
  const [selected, setSelected] = useState(0);
  const buttons = useRef<(HTMLButtonElement | null)[]>([]), item = items[selected];
  const select = (value: number) => setSelected((value + items.length) % items.length);
  function keySelect(key: string) {
    const next = key === "Home" ? 0 : key === "End" ? items.length - 1 : key === "ArrowRight" ? (selected + 1) % items.length : key === "ArrowLeft" ? (selected + items.length - 1) % items.length : undefined;
    if (next === undefined) return false;
    select(next); buttons.current[next]?.focus(); return true;
  }
  return <div className="industry-switcher"><div className="industry-tabs" role="tablist" aria-label="Ngành minh họa">{items.map((entry, i) => <button ref={node => { buttons.current[i] = node; }} type="button" key={entry.id} role="tab" id={`industry-tab-${i}`} aria-controls={`industry-panel-${i}`} aria-selected={i === selected} tabIndex={i === selected ? 0 : -1} onClick={() => select(i)} onKeyDown={event => { if (keySelect(event.key)) event.preventDefault(); }}>{entry.label}</button>)}</div>
    {items.map((entry, i) => {
      // APG panel content is static, so the panel itself joins the Tab sequence.
      // eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex
      return <div className="industry-panel" key={entry.id} id={`industry-panel-${i}`} role="tabpanel" aria-labelledby={`industry-tab-${i}`} hidden={i !== selected} tabIndex={0}><div className="industry-illustration" aria-hidden="true"><div className="industry-product-card"><div className="industry-product-category">{icons[i]}<span>{entry.label}</span></div><p>Sản phẩm/dịch vụ mẫu</p><strong className="industry-product-name">{entry.sample}</strong><div className="industry-product-highlights">{entry.attributes.map(attribute => <p key={attribute.label}><span>{attribute.label}</span><strong>{attribute.value}</strong></p>)}</div></div></div><div className="industry-attributes"><p className="demo-label">Giao diện minh họa</p><h3>{definitionCopy.title} {entry.label}</h3><p data-marketing>{definitionCopy.description}</p><dl>{entry.attributes.map(attribute => <div key={attribute.label}><dt>{attribute.label}</dt><dd>{attribute.type}</dd></div>)}</dl></div></div>;
    })}
    <div className="industry-pagination" aria-label="Chuyển ngành"><button className="motion-control motion-icon" type="button" aria-label="Ngành trước" onClick={() => select(selected - 1)}><ChevronLeftIcon aria-hidden="true" /></button><p>{item.label}</p><div className="industry-dots" role="group" aria-label="Chọn ngành minh họa">{items.map((entry, i) => <button type="button" key={entry.id} aria-label={`Xem ngành ${entry.label}`} aria-pressed={selected === i} onClick={() => select(i)}><span aria-hidden="true" /></button>)}</div><button className="motion-control motion-icon" type="button" aria-label="Ngành tiếp theo" onClick={() => select(selected + 1)}><ChevronRightIcon aria-hidden="true" /></button></div>
  </div>;
}
