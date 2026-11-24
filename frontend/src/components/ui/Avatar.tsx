import { initials } from "@/config/roles";
export function Avatar({ name, decorative = false }: { name: string; decorative?: boolean }) { return <span className="avatar" role={decorative ? undefined : "img"} aria-hidden={decorative || undefined} aria-label={decorative ? undefined : name}>{initials(name)}</span>; }
