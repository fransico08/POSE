"use client";
import { useSyncExternalStore } from "react";
const subscribeClock = (callback: () => void) => {
  const timer = window.setInterval(callback, 60000);
  document.addEventListener("visibilitychange", callback);
  return () => { window.clearInterval(timer); document.removeEventListener("visibilitychange", callback); };
};
export function TimeGreeting() {
  const hour = useSyncExternalStore(subscribeClock, () => new Date().getHours(), () => null);
  return <>{hour === null ? "Chào" : hour >= 5 && hour < 12 ? "Chào buổi sáng" : hour >= 12 && hour < 18 ? "Chào buổi chiều" : "Chào buổi tối"}</>;
}
