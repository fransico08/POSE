export function Badge({ children, tone = "info" }: { children: React.ReactNode; tone?: "info" | "success" | "warning" }) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}
