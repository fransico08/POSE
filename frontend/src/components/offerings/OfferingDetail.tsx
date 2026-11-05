"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useParams } from "next/navigation";
import { useSession } from "@/components/session/SessionGate";
import { Alert } from "@/components/ui/Alert";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { EmptyState } from "@/components/ui/EmptyState";
import { api } from "@/lib/api";
import { canCreateCrm } from "@/lib/crm";
import { canManageOfferings, canReadOfferings, formatOfferingDate, formatOfferingPrice, offeringErrorMessage, type Offering } from "@/lib/offerings";

type StatusAction = "activate" | "deactivate";

export function OfferingDetail() {
  const params = useParams<{ id: string }>();
  const id = params?.id ?? "";
  const { user } = useSession();
  const queryClient = useQueryClient();
  const manager = canManageOfferings(user.roles, user.permissions);
  const allowed = canReadOfferings(user.roles, user.permissions);
  const [confirmation, setConfirmation] = useState<StatusAction | null>(null);
  const [notice, setNotice] = useState("");
  const detail = useQuery({
    queryKey: ["offering", user.organization.id, user.id, id],
    queryFn: () => api.request<Offering>(`/api/backend/offerings/${encodeURIComponent(id)}`),
    enabled: allowed && Boolean(id),
  });
  useEffect(() => {
    if (window.sessionStorage.getItem("pose-offering-saved") !== id) return;
    window.sessionStorage.removeItem("pose-offering-saved");
    let clearNotice = 0;
    const showNotice = window.setTimeout(() => {
      setNotice("Đã lưu sản phẩm hoặc dịch vụ.");
      clearNotice = window.setTimeout(() => setNotice(""), 5000);
    }, 0);
    return () => { window.clearTimeout(showNotice); window.clearTimeout(clearNotice); };
  }, [id]);
  const statusMutation = useMutation({
    mutationFn: (action: StatusAction) => api.request<Offering>(`/api/backend/offerings/${encodeURIComponent(id)}/${action}`, { method: "POST" }),
    onSuccess: async updated => {
      queryClient.setQueryData(["offering", user.organization.id, user.id, id], updated);
      await queryClient.invalidateQueries({ queryKey: ["offerings", user.organization.id, user.id] });
      setConfirmation(null);
      setNotice(updated.active ? "Đã kích hoạt sản phẩm hoặc dịch vụ." : "Đã ngừng kích hoạt sản phẩm hoặc dịch vụ.");
    },
  });

  if (!allowed) return <section className="offering-section"><EmptyState title="Bạn chưa có quyền xem danh mục">Liên hệ quản trị viên để được cấp quyền phù hợp.</EmptyState></section>;
  if (detail.isPending) return <section className="offering-section" aria-busy="true"><p role="status">Đang tải thông tin sản phẩm hoặc dịch vụ…</p><div className="offering-skeleton" /><div className="offering-skeleton" /></section>;
  if (detail.isError) return <section className="offering-section"><Alert>{offeringErrorMessage(detail.error)}</Alert><div className="offering-detail-actions"><Button variant="secondary" onClick={() => void detail.refetch()}>Thử tải lại</Button><Link className="button button-ghost" href="/app/san-pham">Về danh sách</Link></div></section>;

  const offering = detail.data;
  return <div className="offering-page offering-narrow">
    <p className="eyebrow"><Link href="/app/san-pham">Sản phẩm &amp; dịch vụ</Link> / Chi tiết</p>
    {notice && <p className="offering-notice" role="status" aria-live="polite">{notice}</p>}
    {statusMutation.isError && <Alert>{offeringErrorMessage(statusMutation.error)}</Alert>}
    <header className="offering-detail-heading">
      <div><div className="offering-detail-title"><h1>{offering.name}</h1><Badge tone={offering.active ? "success" : "warning"}>{offering.active ? "Đang hoạt động" : "Ngừng hoạt động"}</Badge></div><p>{offering.kind === "PRODUCT" ? "Sản phẩm" : "Dịch vụ"} · {offering.teamName ?? "Chưa gán team"}</p></div>
      <div className="offering-detail-actions"><Link className="button button-secondary" href="/app/san-pham">Danh sách</Link>{offering.active && canCreateCrm(user, "lead") && <Link className="button button-primary" href={`/app/lead/them?offeringId=${encodeURIComponent(offering.id)}`}>Tạo Lead cho sản phẩm này</Link>}{manager && <><Link className="button button-secondary" href={`/app/san-pham/${offering.id}/chinh-sua`}>Chỉnh sửa</Link><Button variant={offering.active ? "danger" : "primary"} onClick={() => setConfirmation(offering.active ? "deactivate" : "activate")}>{offering.active ? "Ngừng kích hoạt" : "Kích hoạt"}</Button></>}</div>
    </header>
    <section className="offering-section" aria-labelledby="offering-detail-general"><h2 id="offering-detail-general">Thông tin chung</h2><dl className="offering-detail-grid">
      <div><dt>Mã</dt><dd>{offering.code}</dd></div><div><dt>Loại</dt><dd>{offering.kind === "PRODUCT" ? "Sản phẩm" : "Dịch vụ"}</dd></div>
      <div><dt>Danh mục</dt><dd>{offering.categoryName ?? "Chưa gán danh mục"}</dd></div><div><dt>Team sở hữu</dt><dd>{offering.teamName ?? "Chưa gán team (dữ liệu cũ)"}</dd></div>
      <div><dt>Đơn vị</dt><dd>{offering.unit || "—"}</dd></div><div><dt>Ngày tạo</dt><dd>{formatOfferingDate(offering.createdAt)}</dd></div>
      <div><dt>Cập nhật lần cuối</dt><dd>{formatOfferingDate(offering.updatedAt)}</dd></div>
      <div className="offering-detail-full"><dt>Mô tả</dt><dd className="offering-description">{offering.description || "Chưa có mô tả."}</dd></div>
    </dl></section>
    <section className="offering-section offering-price-card" aria-labelledby="offering-detail-price"><div><p className="eyebrow">Giá hiện hành</p><h2 id="offering-detail-price">{formatOfferingPrice(offering.referencePrice, offering.currencyCode)}{offering.pricingUnit ? <span> / {offering.pricingUnit}</span> : null}</h2><p>Hiệu lực từ {formatOfferingDate(offering.referencePriceEffectiveFrom)}</p></div></section>
    <Dialog open={confirmation !== null} onClose={() => { if (!statusMutation.isPending) setConfirmation(null); }} onConfirm={() => { if (confirmation) statusMutation.mutate(confirmation); }} busy={statusMutation.isPending} busyLabel="Đang xử lý…" title={confirmation === "activate" ? "Kích hoạt sản phẩm/dịch vụ" : "Ngừng kích hoạt sản phẩm/dịch vụ"} description={confirmation === "activate" ? `Kích hoạt “${offering.name}” để có thể sử dụng trong phạm vi được phân công?` : `Ngừng kích hoạt “${offering.name}”? Mục này sẽ không còn được chọn cho cơ hội mới. Lịch sử và phân công hiện có được giữ nguyên.`} confirmLabel={confirmation === "activate" ? "Kích hoạt" : "Ngừng kích hoạt"} confirmVariant={confirmation === "activate" ? "primary" : "danger"} />
  </div>;
}
