import { initials } from "@/config/roles";
export function Avatar({ name }: { name: string }) { return <span className="avatar" role="img" aria-label={name}>{initials(name)}</span>; }
