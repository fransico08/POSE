import { initials } from "@/config/roles";
export function Avatar({ name }: { name: string }) { return <span className="avatar" aria-label={name}>{initials(name)}</span>; }
