"use client";

import { useId } from "react";
import { ExclamationTriangleIcon } from "@heroicons/react/20/solid";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { useModal } from "@/components/ui/use-modal";
import { api } from "@/lib/api";
import { assignmentEffectiveLabel, formatAssignmentDate, salesAssignmentErrorMessage, type SalesAssignment, type SalesAssignmentOpenWork } from "@/lib/sales-assignments";

export function SalesAssignmentRevokeDialog({ assignment, onClose, onRevoked }: {
  assignment: SalesAssignment | null;
  onClose: () => void;
  onRevoked: () => void;
}) {
  const { user } = useSession();
  const id = useId();
  const queryClient = useQueryClient();
  const open = assignment !== null;
  const mutation = useMutation({
    mutationFn: () => api.request(`/api/backend/sales-assignments/${encodeURIComponent(assignment!.id)}/revoke`, { method: "POST" }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["sales-assignments", user.organization.id, user.id] });
      onRevoked();
    },
  });
  const modal = useModal(open, () => { if (!mutation.isPending) onClose(); });
  const work = useQuery({
    queryKey: ["sales-assignment-open-work", user.organization.id, user.id, assignment?.id],
    queryFn: () => api.request<SalesAssignmentOpenWork>(`/api/backend/sales-assignments/${encodeURIComponent(assignment!.id)}/open-work`),
    enabled: open,
  });

  return <dialog {...modal} className="dialog sales-assignment-dialog sales-assignment-revoke-dialog" aria-modal="true" aria-labelledby={`${id}-title`} aria-describedby={`${id}-description`}>
    <header className="sales-assignment-revoke-heading">
      <span className="sales-assignment-revoke-icon"><ExclamationTriangleIcon aria-hidden="true" /></span>
      <div><p className="sales-assignment-revoke-kicker">Cần xác nhận</p><h2 id={`${id}-title`}>Thu hồi phân công?</h2></div>
    </header>
    <p className="sales-assignment-revoke-description" id={`${id}-description`}>
      Sale sẽ mất quyền truy cập Offering qua phân công này sau khi thu hồi.
    </p>
    {mutation.isError && <Alert>{salesAssignmentErrorMessage(mutation.error)}</Alert>}
    {assignment && <dl className="sales-assignment-revoke-summary">
      <div><dt>Sản phẩm/dịch vụ</dt><dd>{assignment.offeringName}</dd></div>
      <div><dt>Nhân viên kinh doanh</dt><dd>{assignment.salesUserName}</dd></div>
      <div className="sales-assignment-revoke-summary-period"><dt>Thời gian hiệu lực</dt><dd>{formatAssignmentDate(assignment.effectiveFrom)} – {formatAssignmentDate(assignment.effectiveTo)}</dd></div>
      <div><dt>Trạng thái hiện tại</dt><dd><span className={`sales-assignment-state assignment-state-${assignment.effectiveState.toLowerCase()}`}>{assignmentEffectiveLabel(assignment.effectiveState)}</span></dd></div>
    </dl>}
    {work.isPending && <p className="sales-assignment-loading" role="status" aria-busy="true">Đang kiểm tra hồ sơ còn mở…</p>}
    {work.isError && <div className="sales-assignment-inline-error"><Alert>{salesAssignmentErrorMessage(work.error)}</Alert><Button variant="secondary" onClick={() => void work.refetch()}>Thử lại</Button></div>}
    {work.data && work.data.totalItems > 0 && <section className="sales-assignment-revoke-work" aria-labelledby={`${id}-work-title`}>
      <div><h3 id={`${id}-work-title`}>Cần xử lý hồ sơ trước</h3><p>Còn {work.data.totalItems} Lead, cơ hội hoặc nhiệm vụ đang mở liên quan đến Sale và Offering này. Chuyển giao các hồ sơ trước khi thu hồi.</p></div>
      <ul className="sales-assignment-open-work-list" aria-label="Hồ sơ đang mở cần xử lý">
        {work.data.items.map(item => <li key={`${item.type}-${item.id}`}><strong>{item.type === "LEAD" ? "Lead" : item.type === "OPPORTUNITY" ? "Cơ hội" : "Nhiệm vụ"}:</strong> {item.title} <span>({item.status})</span></li>)}
      </ul>
      {work.data.totalItems > work.data.items.length && <p className="sales-assignment-helper">Đang hiển thị {work.data.items.length} hồ sơ đầu tiên.</p>}
    </section>}
    {work.data && work.data.totalItems === 0 && <Alert tone="info">Không có hồ sơ mở cần chuyển giao. Lịch sử phân công sẽ được giữ lại.</Alert>}
    <footer className="dialog-actions">
      <Button variant="secondary" data-autofocus disabled={mutation.isPending} onClick={onClose}>Hủy</Button>
      <Button variant="danger" busy={mutation.isPending} disabled={!work.data || work.data.totalItems > 0 || work.isError}
        onClick={() => mutation.mutate()}>{mutation.isPending ? "Đang thu hồi…" : "Thu hồi phân công"}</Button>
    </footer>
  </dialog>;
}
