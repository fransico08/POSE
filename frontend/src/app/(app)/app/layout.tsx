import "@/styles/components.css";
import "@/styles/pages.css";
import "@/styles/offerings.css";
import "@/styles/sales-assignments.css";
import "@/styles/opportunities.css";
import { SessionGate } from "@/components/session/SessionGate";
import { AppShell } from "@/components/layout/AppShell";
import { Providers } from "@/components/session/Providers";
export const metadata = { robots: { index: false, follow: false } };
export default function AppLayout({ children }: { children: React.ReactNode }) { return <Providers><SessionGate><AppShell>{children}</AppShell></SessionGate></Providers>; }
