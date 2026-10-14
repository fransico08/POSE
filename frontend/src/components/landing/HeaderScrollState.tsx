"use client";
import { useEffect, useState, type ReactNode } from "react";
import { observeElement } from "@/lib/motion/observer";
import { MobileNavDisclosure } from "./MobileNavDisclosure";
export function HeaderScrollState({ children }: { children: ReactNode }) {
  const [scrolled, setScrolled] = useState(false);
  useEffect(() => {
    const sentinel = document.getElementById("hero-end");
    if (!sentinel) return;
    const update = (entry: IntersectionObserverEntry) => setScrolled(!entry.isIntersecting && entry.boundingClientRect.top < 0);
    const stopSentinel = observeElement(sentinel, update);
    // An anchor can skip a thin sentinel from below to above without crossing its
    // intersection threshold. The hero shares the same observer and covers that jump.
    const hero = document.getElementById("hero-title")?.closest("section");
    const stopHero = hero ? observeElement(hero, update) : undefined;
    return () => { stopSentinel(); stopHero?.(); };
  }, []);
  return <MobileNavDisclosure scrolled={scrolled}>{children}</MobileNavDisclosure>;
}
