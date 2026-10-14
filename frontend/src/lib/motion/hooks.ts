"use client";
import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { observeElement } from "./observer";
import { scheduleWhenIdle } from "./idle";

const subscribeReduced = (callback: () => void) => {
  const media = window.matchMedia("(prefers-reduced-motion: reduce)");
  media.addEventListener("change", callback);
  return () => media.removeEventListener("change", callback);
};
const subscribeVisibility = (callback: () => void) => {
  document.addEventListener("visibilitychange", callback);
  return () => document.removeEventListener("visibilitychange", callback);
};
let painted = false, paintScheduled = false;
const paintListeners = new Set<() => void>();
const subscribePaint = (callback: () => void) => {
  paintListeners.add(callback);
  if (!painted && !paintScheduled) {
    paintScheduled = true;
    requestAnimationFrame(() => requestAnimationFrame(() => {
      scheduleWhenIdle(() => { painted = true; [...paintListeners].forEach(listener => listener()); });
    }));
  }
  return () => { paintListeners.delete(callback); };
};
export const useReducedMotion = () => useSyncExternalStore(subscribeReduced, () => window.matchMedia("(prefers-reduced-motion: reduce)").matches, () => true);
export const usePageVisible = () => useSyncExternalStore(subscribeVisibility, () => document.visibilityState === "visible", () => false);

export function useInView<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [inView, setInView] = useState(false), [seen, setSeen] = useState(false);
  useEffect(() => {
    const node = ref.current;
    if (!node) return;
    let stopObserve: (() => void) | undefined;
    const stopIdle = scheduleWhenIdle(() => {
      stopObserve = observeElement(node, entry => { setInView(entry.isIntersecting); if (entry.isIntersecting) setSeen(true); });
    });
    return () => { stopIdle(); stopObserve?.(); };
  }, []);
  return { ref, inView, seen };
}
export function useAutoMotion<T extends HTMLElement>() {
  const view = useInView<T>(), reduced = useReducedMotion(), visible = usePageVisible();
  const ready = useSyncExternalStore(subscribePaint, () => painted, () => false);
  return { ...view, reduced, visible, motionReady: ready && view.seen && !reduced, allowed: ready && view.inView && visible && !reduced };
}
