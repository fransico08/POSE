import Link from "next/link";
export function Breadcrumbs({ current }: { current: string }) {
  return <nav aria-label="Breadcrumb" className="breadcrumbs"><ol><li><Link href="/app">POSE CRM</Link></li><li aria-current="page">{current}</li></ol></nav>;
}
