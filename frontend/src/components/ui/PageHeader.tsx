import type { ReactNode } from "react";
export function PageHeader({ title, eyebrow, description, actions }: { title: string; eyebrow?: string; description?: ReactNode; actions?: ReactNode }) {
  return <header className="pose-page-header">{eyebrow && <p className="eyebrow">{eyebrow}</p>}<h1>{title}</h1>{description && <div>{description}</div>}{actions && <div>{actions}</div>}</header>;
}
