const targets = new Map<Element, (entry: IntersectionObserverEntry) => void>();
let observer: IntersectionObserver | undefined;

export function observeElement(node: Element, callback: (entry: IntersectionObserverEntry) => void) {
  if (!("IntersectionObserver" in window)) return () => {};
  observer ??= new IntersectionObserver(entries => {
    for (const entry of entries) targets.get(entry.target)?.(entry);
  }, { threshold: 0 });
  targets.set(node, callback); observer.observe(node);
  return () => {
    targets.delete(node); observer?.unobserve(node);
    if (!targets.size) { observer?.disconnect(); observer = undefined; }
  };
}
