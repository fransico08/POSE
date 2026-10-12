import { BuildingOffice2Icon } from "@heroicons/react/24/outline";
import type { Session } from "@/lib/session/types";
export function TenantBadge({ organization }: Pick<Session, "organization">) {
  return <p className="tenant-badge"><BuildingOffice2Icon aria-hidden="true" /><span>{organization.name} <strong className="tenant-code">{organization.code.toUpperCase()}</strong></span></p>;
}
