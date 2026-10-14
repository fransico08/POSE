"use client";
import { useEffect, useRef, useState } from "react";
import { useAutoMotion } from "./hooks";
export function useBoundedMotion<T extends HTMLElement>(duration = 4800) {
  const motion = useAutoMotion<T>();
  const remaining = useRef(duration);
  const [complete, setComplete] = useState(false);
  const running = motion.allowed && !complete;
  useEffect(() => {
    if (!running) return;
    const started = performance.now();
    const timer = window.setTimeout(() => setComplete(true), remaining.current);
    return () => { window.clearTimeout(timer); remaining.current = Math.max(0, remaining.current - (performance.now() - started)); };
  }, [running]);
  return { ...motion, running, complete };
}
