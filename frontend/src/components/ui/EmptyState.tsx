import { Squares2X2Icon } from "@heroicons/react/24/outline";
export function EmptyState({ title, children }: { title: string; children: React.ReactNode }) {
  return <section className="empty-state"><Squares2X2Icon aria-hidden="true" /><h3>{title}</h3><p>{children}</p></section>;
}
