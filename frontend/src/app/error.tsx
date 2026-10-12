"use client";
import { ErrorState } from "@/components/ui/ErrorState";
import { useQueryClient } from "@tanstack/react-query";
export default function ErrorPage({ reset }: { reset: () => void }) {
  const client = useQueryClient();
  return <main id="noi-dung" className="status-page"><ErrorState reset={() => { client.removeQueries({ queryKey: ["session"] }); reset(); }} /></main>;
}
