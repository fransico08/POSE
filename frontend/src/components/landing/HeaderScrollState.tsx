"use client";
import { useEffect, useState, type ReactNode } from "react";
import { observeElement } from "@/lib/motion/observer";
import { MobileNavDisclosure } from "./MobileNavDisclosure";
export function HeaderScrollState({ children }: { children: ReactNode }) {
  const [scrolled, setScrolled] = useState(false);
  useEffect(() => {
    const sentinel = document.getElementById("hero-end");
    if (!sentinel) return;
    return observeElement(sentinel, entry => setScrolled(!entry.isIntersecting && entry.boundingClientRect.top < 0));
  }, []);
  return <MobileNavDisclosure scrolled={scrolled}>{children}</MobileNavDisclosure>;
}
