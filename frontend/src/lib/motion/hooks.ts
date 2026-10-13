"use client";
import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { observeElement } from "./observer";

const subscribeReduced = (callback: () => void) => {
  const media = window.matchMedia("(prefers-reduced-motion: reduce)");
  media.addEventListener("change", callback);
  return () => media.removeEventListener("change", callback);
};
const subscribeVisibility = (callback: () => void) => {
  document.addEventListener("visibilitychange", callback);
  return () => document.removeEventListener("visibilitychange", callback);
};
export const useReducedMotion = () => useSyncExternalStore(subscribeReduced, () => window.matchMedia("(prefers-reduced-motion: reduce)").matches, () => true);
export const usePageVisible = () => useSyncExternalStore(subscribeVisibility, () => document.visibilityState === "visible", () => false);

export function useInView<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [inView, setInView] = useState(false), [seen, setSeen] = useState(false);
  useEffect(() => {
    const node = ref.current;
    if (!node) return;
    return observeElement(node, entry => { setInView(entry.isIntersecting); if (entry.isIntersecting) setSeen(true); });
  }, []);
  return { ref, inView, seen };
}
export function useAutoMotion<T extends HTMLElement>() {
  const view = useInView<T>(), reduced = useReducedMotion(), visible = usePageVisible();
  return { ...view, reduced, visible, allowed: view.inView && visible && !reduced };
}
