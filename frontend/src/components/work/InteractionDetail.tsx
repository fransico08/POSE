"use client";
import Link from "next/link";
import { WorkSubject, WorkOpportunity } from "@/components/work/WorkReferences";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { ErrorState } from "@/components/ui/ErrorState";
import { canReadInteraction, canWriteInteraction, getInteraction, interactionKey, workError } from "@/lib/interactions";
import { instantToVietnamDateTime } from "@/lib/work-time";

export function InteractionDetail() {
  const { user } = useSession(); const id = useParams<{ id: string }>()?.id ?? "";
  const query = useQuery({ queryKey: [...interactionKey(user), "detail", id], queryFn: () => getInteraction(id), enabled: Boolean(id) && canReadInteraction(user) });
  if (!canReadInteraction(user)) return <h1>Không có quyền truy cập</h1>;
  if (query.isPending) return <p role="status">Đang tải tương tác…</p>;
  if (query.isError) return <ErrorState message={workError(query.error)} reset={() => void query.refetch()} />;
  const item = query.data; const hidden = "Nội dung bị ẩn theo quyền"; const customerId = item.customerId || item.customer?.id;
  return <div className="offering-page"><p><Link href="/app/tuong-tac">Tương tác</Link> / Chi tiết</p><header className="offering-detail-heading"><h1>{item.subject ?? hidden}</h1><div className="offering-detail-actions">{customerId && user.permissions.includes("customer_360.read") && <Link className="button button-secondary" href={`/app/khach-hang-360/${encodeURIComponent(customerId)}`}>Xem hồ sơ 360</Link>}{canWriteInteraction(user) && <Link className="button button-secondary" href={`/app/tuong-tac/${id}/chinh-sua`}>Chỉnh sửa</Link>}</div></header><section className="offering-section"><h2>Thông tin tương tác</h2><dl className="offering-detail-grid"><div><dt>Người ghi</dt><dd>{item.recordedByName}</dd></div><div><dt>Loại</dt><dd>{item.type}</dd></div><div><dt>Kênh</dt><dd>{item.channel}</dd></div><div><dt>Thời điểm</dt><dd>{instantToVietnamDateTime(item.occurredAt)}</dd></div><div><dt>Đối tượng</dt><dd><WorkSubject item={item} /></dd></div><div><dt>Nội dung</dt><dd>{item.content ?? hidden}</dd></div><div><dt>Kết quả</dt><dd>{item.subject === null ? hidden : item.outcome || "Chưa cập nhật"}</dd></div></dl><p>Cơ hội: <WorkOpportunity item={item} /></p></section></div>;
}
