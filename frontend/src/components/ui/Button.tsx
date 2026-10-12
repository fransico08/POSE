import type { ButtonHTMLAttributes } from "react";
type Props = ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "primary" | "secondary" | "danger" | "ghost"; busy?: boolean };
export function Button({ variant = "primary", busy = false, className = "", disabled, children, type = "button", ...props }: Props) {
  return <button {...props} type={type} className={`button button-${variant} ${className}`} disabled={disabled || busy} aria-busy={busy || undefined}>{children}</button>;
}
