"use client";
import Link from "next/link";
import { useId, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { Alert } from "@/components/ui/Alert";
import { ErrorState } from "@/components/ui/ErrorState";
import { api } from "@/lib/api";
import { ApiError } from "@/lib/api/client";
import { dashboardKey } from "@/lib/dashboard";
import { StatusPill } from "@/components/ui/StatusPill";
import { Button } from "@/components/ui/Button";

type TodayItem = { type: string; rank: number; subjectId: string; title: string; ownerName?: string; reason: string; href: string };
type TodayResponse = { applicable: boolean; items: TodayItem[] };
const names: Record<string, string> = {
  OVERDUE_TASK: "Nhiệm vụ quá hạn", LEAD_RESPONSE_OVERDUE: "Lead chờ phản hồi",
  HANDOVER_NOT_CONTACTED: "Khách hàng chờ liên hệ", IDLE_OPPORTUNITY: "Cơ hội chưa chăm sóc",
  TASK_DUE_TODAY: "Nhiệm vụ đến hạn hôm nay",
};

export function TodayWork() {
  const { user } = useSession();
  const [expanded, setExpanded] = useState(false);
  const listId = useId();
  const allowed = user.permissions.includes("dashboard.read");
  const query = useQuery({
    queryKey: [...dashboardKey(user), "today"],
    queryFn: () => api.request<TodayResponse>("/api/backend/dashboard/today", undefined, { suppressForbiddenNavigation: true }),
    enabled: allowed, retry: false,
  });
  if (!allowed || query.data?.applicable === false) return null;
  return <section className="offering-section today-work" aria-labelledby="today-work-title">
    <h2 id="today-work-title">Việc hôm nay</h2>
    {query.isPending && <div aria-busy="true"><p role="status">Đang tải việc hôm nay…</p></div>}
    {query.isError && (query.error instanceof ApiError && query.error.status === 403
      ? <Alert>Bạn không có quyền xem việc hôm nay.</Alert>
      : <ErrorState inline message="Không thể tải việc hôm nay." reset={() => void query.refetch()} />)}
    {query.data && (query.data.items.length === 0 ? <p>Hôm nay chưa có việc cần ưu tiên</p> :
      <><ol id={listId} className="today-work-list">{[...query.data.items].sort((a,b) => a.rank-b.rank).slice(0, expanded ? undefined : 5).map((item, index) => <li key={`${item.type}-${item.subjectId}-${index}`}>
        <StatusPill tone={item.rank === 1 ? "danger" : "accent"}>{names[item.type] ?? "Việc cần xử lý"}</StatusPill>
        <Link className="today-work-title" href={item.href}>Mở {item.title}</Link><p className="today-work-reason">{item.reason}</p>{item.ownerName && <p className="today-work-owner">Phụ trách: {item.ownerName}</p>}
      </li>)}</ol>{query.data.items.length > 5 && <Button variant="secondary" className="today-work-toggle" aria-expanded={expanded} aria-controls={listId} onClick={() => setExpanded(!expanded)}>{expanded ? "Thu gọn" : `Xem thêm (${query.data.items.length - 5})`}</Button>}</>)}
  </section>;
}
