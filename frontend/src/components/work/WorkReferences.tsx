"use client";
import Link from "next/link";
import { useSession } from "@/components/session/SessionGate";

type Subject = { id: string; name: string };
type Opportunity = { id: string; title: string | null };
export type WorkReferences = {
  customerId: string | null; leadId: string | null; opportunityId: string | null;
  customer?: Subject | null; lead?: Subject | null; opportunity?: Opportunity | null;
};

export function WorkSubject({ item }: { item: WorkReferences }) {
  const { user } = useSession();
  const customer = item.customerId || item.customer?.id;
  const lead = item.leadId || item.lead?.id;
  if (customer) return <>Khách hàng: {user.permissions.includes("customer.read") ? <Link href={`/app/khach-hang/${customer}`}>{item.customer?.name ?? "Chưa cập nhật"}</Link> : item.customer?.name ?? "Chưa cập nhật"}</>;
  if (lead) return <>Lead: {user.permissions.includes("lead.read") ? <Link href={`/app/lead/${lead}`}>{item.lead?.name ?? "Chưa cập nhật"}</Link> : item.lead?.name ?? "Chưa cập nhật"}</>;
  return <>Chưa cập nhật</>;
}

export function WorkOpportunity({ item }: { item: WorkReferences }) {
  const { user } = useSession();
  const id = item.opportunityId || item.opportunity?.id;
  if (id && (!user.permissions.includes("opportunity.read") || item.opportunity?.title === null)) return <>Cơ hội liên quan</>;
  return id ? <Link href={`/app/co-hoi/${id}`}>{item.opportunity?.title ?? "Cơ hội đã chọn"}</Link> : <>Không có</>;
}
