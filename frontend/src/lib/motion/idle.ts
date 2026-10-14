export function scheduleWhenIdle(callback: () => void) {
  if (typeof window.requestIdleCallback === "function") {
    let cancelled = false;
    const cancel = window.cancelIdleCallback?.bind(window);
    const id = window.requestIdleCallback(() => { if (!cancelled) callback(); }, { timeout: 1200 });
    return () => { cancelled = true; cancel?.(id); };
  }
  const id = window.setTimeout(callback, 250);
  return () => window.clearTimeout(id);
}
