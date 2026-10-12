"use client";
import { useEffect, useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { subscribeSessionCleanup } from "@/lib/api";
export function Providers({ children }: { children: React.ReactNode }) {
  const [client] = useState(() => new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 0 } } }));
  useEffect(() => subscribeSessionCleanup(() => { void client.cancelQueries(); client.clear(); }), [client]);
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
