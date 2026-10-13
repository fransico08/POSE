import { act } from "@testing-library/react";
import { afterEach, beforeEach, vi } from "vitest";
export function installMotionFixture() {
  let reduced = false;
  const listeners = new Set<() => void>(), targets = new Set<Element>();
  let intersection: IntersectionObserverCallback;
  beforeEach(() => {
    reduced = false;
    vi.stubGlobal("matchMedia", () => ({ get matches() { return reduced; }, addEventListener: (_: string, callback: () => void) => listeners.add(callback), removeEventListener: (_: string, callback: () => void) => listeners.delete(callback) }));
    vi.stubGlobal("IntersectionObserver", class { constructor(callback: IntersectionObserverCallback) { intersection = callback; } observe(target: Element) { targets.add(target); } unobserve(target: Element) { targets.delete(target); } disconnect() { targets.clear(); } });
  });
  afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); });
  return {
    makeVisible: () => act(() => intersection([...targets].map(target => ({ target, isIntersecting: true }) as IntersectionObserverEntry), {} as IntersectionObserver)),
    setReduced: (value: boolean) => act(() => { reduced = value; [...listeners].forEach(listener => listener()); }),
  };
}
