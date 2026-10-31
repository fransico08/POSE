"use client";
import { useEffect, useState, type ReactNode } from "react";
import { observeElement } from "@/lib/motion/observer";
import { MobileNavDisclosure } from "./MobileNavDisclosure";
export function HeaderScrollState({ children }: { children: ReactNode }) {
  const [scrolled, setScrolled] = useState(false), [lifted, setLifted] = useState(false);
  // Inside the hero the header stays dark but opaque once content scrolls beneath it.
  useEffect(() => {
    const update = () => setLifted(window.scrollY > 0);
    update(); window.addEventListener("scroll", update, { passive: true });
    return () => window.removeEventListener("scroll", update);
  }, []);
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
  return <MobileNavDisclosure scrolled={scrolled} lifted={lifted}>{children}</MobileNavDisclosure>;
}
