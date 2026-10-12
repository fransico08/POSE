import { createHash } from "node:crypto";
import { backendRequest, errorShape, readError, validTokens, type AuthTokens } from "./backend";
import type { ApiErrorShape } from "@/lib/session/types";
type Result = { tokens: AuthTokens; error?: never; status: number } | { tokens?: never; error: ApiErrorShape; status: number };
const flights = new Map<string, { promise: Promise<Result>; expiresAt: number }>();
export function refreshOnce(refreshToken: string): Promise<Result> {
  const key = createHash("sha256").update(refreshToken).digest("hex");
  for (const [hash, entry] of flights) if (entry.expiresAt <= Date.now()) flights.delete(hash);
  const existing = flights.get(key);
  if (existing) return existing.promise;
  const promise = (async (): Promise<Result> => {
    try {
      const response = await backendRequest("auth/refresh", { method: "POST", body: JSON.stringify({ refreshToken }) });
      if (!response.ok) return { error: await readError(response), status: response.status };
      const tokens: AuthTokens = await response.json();
      if (!validTokens(tokens)) return { error: errorShape("UPSTREAM_ERROR"), status: 502 };
      return { tokens, status: 200 };
    } catch { return { error: errorShape("NETWORK_ERROR"), status: 503 }; }
  })();
  const entry = { promise, expiresAt: Infinity };
  flights.set(key, entry);
  void promise.finally(() => {
    entry.expiresAt = Date.now() + 10000;
    const timer = setTimeout(() => { if (flights.get(key) === entry) flights.delete(key); }, 10000);
    timer.unref();
  });
  return promise;
}
