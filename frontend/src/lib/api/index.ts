"use client";
import { createApiClient } from "./client";
const cleanup = new Set<() => void>();
export function subscribeSessionCleanup(callback: () => void) { cleanup.add(callback); return () => { cleanup.delete(callback); }; }
export function clearClientSession() { cleanup.forEach(callback => callback()); }
export const api = createApiClient({
  fetcher: (url, init) => fetch(url, init),
  navigate: path => window.location.assign(path),
  onExpired: clearClientSession,
  locks: typeof navigator !== "undefined" && navigator.locks ? { request: (name, callback) => navigator.locks.request(name, callback) } : undefined,
});
