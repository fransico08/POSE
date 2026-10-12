import { SessionGate } from "@/components/session/SessionGate";
import { AppShell } from "@/components/layout/AppShell";
export const metadata = { robots: { index: false, follow: false } };
export default function AppLayout({ children }: { children: React.ReactNode }) { return <SessionGate><AppShell>{children}</AppShell></SessionGate>; }
