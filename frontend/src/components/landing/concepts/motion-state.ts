"use client";
import { useEffect, useRef, useState, useSyncExternalStore } from "react";

const reducedQuery = "(prefers-reduced-motion: reduce)";
const subscribeReduced = (callback: () => void) => {
  const media = window.matchMedia(reducedQuery);
  media.addEventListener("change", callback);
  return () => media.removeEventListener("change", callback);
};
const subscribeVisibility = (callback: () => void) => {
  document.addEventListener("visibilitychange", callback);
  return () => document.removeEventListener("visibilitychange", callback);
};
const targets = new Map<Element, (entry: IntersectionObserverEntry) => void>();
let observer: IntersectionObserver | undefined;

export function observeConceptNode(node: Element, callback: (entry: IntersectionObserverEntry) => void) {
  if (!("IntersectionObserver" in window)) return () => {};
  observer ??= new IntersectionObserver(entries => {
    for (const entry of entries) targets.get(entry.target)?.(entry);
  }, { threshold: 0 });
  targets.set(node, callback); observer.observe(node);
  return () => { targets.delete(node); observer?.unobserve(node); if (!targets.size) { observer?.disconnect(); observer = undefined; } };
}

export function useConceptVisibility<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [inView, setInView] = useState(false);
  const reduced = useSyncExternalStore(subscribeReduced, () => window.matchMedia(reducedQuery).matches, () => true);
  const pageVisible = useSyncExternalStore(subscribeVisibility, () => document.visibilityState === "visible", () => false);
  useEffect(() => {
    const node = ref.current;
    if (!node) return;
    return observeConceptNode(node, entry => setInView(entry.isIntersecting));
  }, []);
  return { ref, reduced, pageVisible, inView, allowed: !reduced && pageVisible && inView };
}
