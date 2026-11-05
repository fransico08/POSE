import Link from "next/link";

type Subject = { id: string; name: string };
type Opportunity = { id: string; title: string };
export type WorkReferences = {
  customerId: string | null; leadId: string | null; opportunityId: string | null;
  customer?: Subject | null; lead?: Subject | null; opportunity?: Opportunity | null;
};

export function WorkSubject({ item }: { item: WorkReferences }) {
  const customer = item.customerId || item.customer?.id;
  const lead = item.leadId || item.lead?.id;
  if (customer) return <>Khách hàng: <Link href={`/app/khach-hang/${customer}`}>{item.customer?.name ?? "Chưa cập nhật"}</Link></>;
  if (lead) return <>Lead: <Link href={`/app/lead/${lead}`}>{item.lead?.name ?? "Chưa cập nhật"}</Link></>;
  return <>Chưa cập nhật</>;
}

export function WorkOpportunity({ item }: { item: WorkReferences }) {
  const id = item.opportunityId || item.opportunity?.id;
  return id ? <Link href={`/app/co-hoi/${id}`}>{item.opportunity?.title ?? "Cơ hội đã chọn"}</Link> : <>Không có</>;
}
