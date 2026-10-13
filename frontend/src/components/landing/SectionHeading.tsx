export function SectionHeading({ id, title, introduction }: { id: string; title: string; introduction?: string }) {
  return <div className="landing-heading"><h2 id={id} data-marketing>{title}</h2>{introduction && <p data-marketing>{introduction}</p>}</div>;
}
