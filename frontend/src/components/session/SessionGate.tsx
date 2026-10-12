"use client";
import { createContext, useContext, useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api, subscribeSessionCleanup } from "@/lib/api";
import { ApiError } from "@/lib/api/client";
import type { Session } from "@/lib/session/types";
import { can } from "@/config/navigation";
import { Skeleton } from "@/components/ui/Skeleton";
import { Button } from "@/components/ui/Button";
import { Alert } from "@/components/ui/Alert";
const SessionContext = createContext<Session | null>(null);
export function useSession() {
  const session = useContext(SessionContext);
  if (!session) throw new Error("Session context unavailable");
  return { ...session, can: (permission: string | string[]) => can(session.user.permissions, permission) };
}
export function SessionGate({ children }: { children: React.ReactNode }) {
  const [cleared, setCleared] = useState(false);
  useEffect(() => subscribeSessionCleanup(() => setCleared(true)), []);
  const query = useQuery({ queryKey: ["session"], queryFn: () => api.request<Session>("/api/session/me"), enabled: !cleared, refetchOnWindowFocus: true });
  if (cleared || query.isPending || (query.error instanceof ApiError && query.error.status === 401)) return <Skeleton />;
  if (query.isError) return <main id="noi-dung" className="status-page"><h1>Không thể kết nối</h1><Alert>Không thể kiểm tra phiên đăng nhập. Vui lòng thử lại.</Alert><Button onClick={() => void query.refetch()}>Thử lại</Button></main>;
  return <SessionContext value={query.data}>{children}</SessionContext>;
}
