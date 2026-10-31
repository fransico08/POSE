"use client";
import { useState, type FocusEvent } from "react";
export function useMotionInteraction<T extends HTMLElement>() {
  const [hovered, setHovered] = useState(false), [focused, setFocused] = useState(false);
  return { hovered, focused, events: {
    onMouseEnter: () => setHovered(true), onMouseLeave: () => setHovered(false),
    onFocusCapture: () => setFocused(true),
    onBlurCapture: (event: FocusEvent<T>) => { if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setFocused(false); },
  } };
}
