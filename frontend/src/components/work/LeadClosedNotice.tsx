"use client";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { Alert } from "@/components/ui/Alert";
import { getLead, leadKey } from "@/lib/leads";

export function LeadClosedNotice({ leadId }: { leadId: string }) {
  const { user } = useSession();
  const lead = useQuery({ queryKey: [...leadKey(user), "closed", leadId], queryFn: () => getLead(leadId), enabled: Boolean(leadId && user.permissions.includes("lead.read")), retry: false });
  return <Alert>Lead đã đóng; hãy chọn khách hàng đã chuyển đổi. {lead.data?.convertedCustomerId && user.permissions.includes("customer.read") && <Link href={`/app/khach-hang/${lead.data.convertedCustomerId}`}>Xem khách hàng</Link>}</Alert>;
}
