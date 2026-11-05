"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { Alert } from "@/components/ui/Alert";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { useModal } from "@/components/ui/use-modal";
import { ConvertLeadDialog } from "@/components/crm/ConvertLeadDialog";
import { canCreateOpportunity } from "@/lib/opportunities";
import { canCreateInteraction } from "@/lib/interactions";
import { canCreateTask } from "@/lib/tasks";
import { getLead, transitionLead } from "@/lib/leads";
import { getCustomer } from "@/lib/customers";
import { invalidateCustomer360 } from "@/lib/customer360";
import { ApiError } from "@/lib/api/client";
import { formatInstantAsDdMmYyyyHhMm } from "@/lib/date-format";
import { canReadCrm, canWriteCrm, crmBase, crmErrorMessage, crmFieldErrors, crmKey, crmTitle, customerStatuses, customerTypes, displayValue, leadStatuses, sources, type CrmKind, type CrmRecord } from "@/lib/crm";

function DisqualifyDialog({ open, busy, failure, onClose, onEdit, onReload, onConfirm }: { open: boolean; busy: boolean; failure: unknown; onClose: () => void; onEdit: () => void; onReload: () => void; onConfirm: (reason: string) => void }) {
  const [reason, setReason] = useState(""); const [error, setError] = useState("");
  const modal = useModal(open, () => { if (!busy) onClose(); });
  const reasonError = error || crmFieldErrors(failure).reason;
  useEffect(() => { if (open && reasonError) document.getElementById("crm-reason")?.focus(); }, [open, reasonError]);
  function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!reason.trim()) { setError("Vui lòng nhập lý do không đủ điều kiện."); document.getElementById("crm-reason")?.focus(); return; }
    onConfirm(reason.trim());
  }
  return <dialog {...modal} className="dialog" aria-modal="true" aria-labelledby="crm-disqualify-title">
    <form onSubmit={submit} noValidate><h2 id="crm-disqualify-title">Không đủ điều kiện</h2>{failure != null && <Alert>{crmErrorMessage(failure)}</Alert>}{failure instanceof ApiError && ["LEAD_CHANGED", "LEAD_STATE_CONFLICT"].includes(failure.code) && <Button variant="secondary" onClick={onReload}>Tải lại</Button>}<div className="field"><label htmlFor="crm-reason">Lý do *</label><textarea id="crm-reason" className="input offering-textarea" rows={4} maxLength={4000} required data-autofocus value={reason} aria-invalid={Boolean(reasonError)} aria-describedby={reasonError ? "crm-reason-error" : undefined} onChange={event => { setReason(event.target.value); setError(""); onEdit(); }} />{reasonError && <p id="crm-reason-error" className="offering-field-error">{reasonError}</p>}</div><footer className="dialog-actions"><Button variant="secondary" disabled={busy} onClick={onClose}>Hủy</Button><Button type="submit" busy={busy}>Xác nhận</Button></footer></form>
  </dialog>;
}

function QualifyDialog({ open, busy, failure, item, onClose, onReload, onConfirm }: { open: boolean; busy: boolean; failure: unknown; item: CrmRecord; onClose: () => void; onReload: () => void; onConfirm: () => void }) {
  const modal = useModal(open, () => { if (!busy) onClose(); });
  return <dialog {...modal} className="dialog" aria-modal="true" aria-labelledby="crm-qualify-title">
    <h2 id="crm-qualify-title">Xác nhận đủ điều kiện</h2>
    <p>Xác nhận Lead sau đã đủ điều kiện?</p>
    <dl><div><dt>Tên</dt><dd>{item.displayName}</dd></div><div><dt>Liên hệ</dt><dd>{displayValue(item.email)} · {displayValue(item.phone)}</dd></div><div><dt>Sản phẩm/dịch vụ</dt><dd>{displayValue(item.offeringName)}</dd></div></dl>
    {failure != null && <Alert>{crmErrorMessage(failure)}</Alert>}
    {failure instanceof ApiError && ["LEAD_CHANGED", "LEAD_STATE_CONFLICT"].includes(failure.code) && <Button variant="secondary" onClick={onReload}>Tải lại</Button>}
    <footer className="dialog-actions"><Button variant="secondary" data-autofocus disabled={busy} onClick={onClose}>Quay lại</Button><Button busy={busy} onClick={onConfirm}>Xác nhận</Button></footer>
  </dialog>;
}

export function CrmDetail({ kind }: { kind: CrmKind }) {
  const id = useParams<{ id: string }>()?.id ?? ""; const { user } = useSession(); const cache = useQueryClient();
  const [open, setOpen] = useState(false), [qualifyOpen, setQualifyOpen] = useState(false), [convertOpen, setConvertOpen] = useState(false), [notice, setNotice] = useState("");
  const allowed = canReadCrm(user, kind), write = canWriteCrm(user, kind);
  const detail = useQuery({ queryKey: [...crmKey(kind, user), "detail", id], queryFn: async (): Promise<CrmRecord> => kind === "lead" ? getLead(id) : getCustomer(id), enabled: allowed && Boolean(id) });
  useEffect(() => {
    const key = `pose-${kind}-saved`, value = window.sessionStorage.getItem(key);
    if (!value?.startsWith(`${id}:`)) return;
    window.sessionStorage.removeItem(key);
    const timer = window.setTimeout(() => setNotice(value.endsWith(":create") ? kind === "lead" ? "Tạo Lead thành công" : "Tạo khách hàng thành công" : "Lưu thay đổi thành công"), 0);
    return () => window.clearTimeout(timer);
  }, [id, kind]);
  useEffect(() => { if (kind !== "customer") return; const value = window.sessionStorage.getItem("pose-customer-converted"); if (!value?.startsWith(`${id}:`)) return; window.sessionStorage.removeItem("pose-customer-converted"); const timer = window.setTimeout(() => setNotice(value.endsWith(":created") ? "Đã tạo khách hàng mới" : "Đã liên kết với khách hàng có sẵn"), 0); return () => window.clearTimeout(timer); }, [id, kind]);
  const transition = useMutation({ mutationFn: ({ action, reason }: { action: "qualify" | "disqualify"; reason?: string }) => transitionLead(id, action, detail.data!.updatedAt, reason), onSuccess: saved => { cache.setQueryData([...crmKey(kind, user), "detail", id], saved); void cache.invalidateQueries({ queryKey: crmKey(kind, user) }); invalidateCustomer360(cache, user, saved.convertedCustomerId); setOpen(false); setQualifyOpen(false); setNotice("Đã cập nhật trạng thái Lead."); } });
  if (!allowed) return <section className="offering-section"><EmptyState title="Bạn không có quyền truy cập">Liên hệ quản trị viên để được cấp quyền phù hợp.</EmptyState></section>;
  if (detail.isPending) return <section className="offering-section" aria-busy="true"><p role="status">Đang tải hồ sơ…</p><div className="offering-skeleton" /></section>;
  if (detail.isError) return <section className="offering-section"><Alert>{crmErrorMessage(detail.error)}</Alert><Button variant="secondary" onClick={() => void detail.refetch()}>Thử lại</Button></section>;
  const item = detail.data, closed = kind === "lead" && ["CONVERTED", "DISQUALIFIED"].includes(item.status);
  const rows: Array<[string, string | null | undefined]> = [
    ["Email", item.email], ["Điện thoại", item.phone], [kind === "lead" ? "Nguồn" : "Loại khách hàng", kind === "lead" ? sources[item.source ?? ""] : customerTypes[item.customerType ?? ""]],
    ["Sản phẩm/dịch vụ", item.offeringName], ["Người phụ trách", item.ownerName],
    ...(kind === "customer" ? [["Địa chỉ", item.address] as [string, string | null | undefined]] : [["Ngày đủ điều kiện", item.qualifiedAt ? formatInstantAsDdMmYyyyHhMm(item.qualifiedAt) : null] as [string, string | null | undefined], ["Lý do không đủ điều kiện", item.disqualificationReason] as [string, string | null | undefined]]),
    ["Ghi chú", item.notes], ["Ngày tạo", item.createdAt ? formatInstantAsDdMmYyyyHhMm(item.createdAt) : null], ["Cập nhật lần cuối", item.updatedAt ? formatInstantAsDdMmYyyyHhMm(item.updatedAt) : null],
  ];
  return <div className="offering-page offering-narrow"><p className="eyebrow"><Link href={crmBase(kind)}>{crmTitle(kind)}</Link> / Chi tiết</p>{notice && <p className="offering-notice" role="status">{notice}</p>}{transition.isError && !open && !qualifyOpen && <div className="offering-inline-error"><Alert>{crmErrorMessage(transition.error)}</Alert><Button variant="secondary" onClick={() => void detail.refetch()}>Tải lại</Button></div>}
    <header className="offering-detail-heading"><div><div className="offering-detail-title"><h1>{item.displayName}</h1><Badge>{(kind === "lead" ? leadStatuses : customerStatuses)[item.status] ?? item.status}</Badge></div></div><div className="offering-detail-actions"><Link className="button button-secondary" href={crmBase(kind)}>Danh sách</Link>{write && !closed && <Link className="button button-secondary" href={`${crmBase(kind)}/${id}/chinh-sua`}>Chỉnh sửa</Link>}{kind === "lead" && write && item.status === "NEW" && <Button busy={transition.isPending} onClick={() => setQualifyOpen(true)}>Xác nhận đủ điều kiện</Button>}{kind === "lead" && write && ["NEW", "QUALIFIED"].includes(item.status) && <Button variant="secondary" onClick={() => setOpen(true)}>Không đủ điều kiện</Button>}</div></header>
    {kind === "customer" && user.permissions.includes("customer_360.read") && <Link className="button button-secondary" href={`/app/khach-hang-360/${encodeURIComponent(id)}`}>Xem hồ sơ 360</Link>}
    {kind === "customer" && canCreateOpportunity(user) && <Link className="button button-primary" href={`/app/co-hoi/them?customerId=${encodeURIComponent(id)}`}>Thêm cơ hội</Link>}
    {!closed && canCreateInteraction(user) && <Link className="button button-secondary" href={`/app/tuong-tac/them?${kind === "customer" ? "customerId" : "leadId"}=${encodeURIComponent(id)}`}>Thêm tương tác</Link>}
    {!closed && canCreateTask(user) && <Link className="button button-secondary" href={`/app/nhiem-vu/them?${kind === "customer" ? "customerId" : "leadId"}=${encodeURIComponent(id)}`}>Thêm nhiệm vụ</Link>}
    {kind === "lead" && item.status === "QUALIFIED" && user.permissions.includes("lead.write") && user.permissions.includes("customer.write") && user.roles.some(role => ["MANAGER", "SALES_REP"].includes(role)) && <Button onClick={() => setConvertOpen(true)}>Chuyển thành khách hàng</Button>}
    <section className="offering-section" aria-labelledby="crm-detail-title"><h2 id="crm-detail-title">Thông tin hồ sơ</h2><dl className="offering-detail-grid">{rows.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{displayValue(value)}</dd></div>)}</dl></section>
    <DisqualifyDialog open={open} busy={transition.isPending} failure={transition.error} onClose={() => { setOpen(false); transition.reset(); }} onEdit={() => { if (transition.isError) transition.reset(); }} onReload={() => { setOpen(false); transition.reset(); void detail.refetch(); }} onConfirm={reason => transition.mutate({ action: "disqualify", reason })} />
    <QualifyDialog open={qualifyOpen} busy={transition.isPending} failure={transition.error} item={item} onClose={() => { setQualifyOpen(false); transition.reset(); }} onReload={() => { setQualifyOpen(false); transition.reset(); void detail.refetch(); }} onConfirm={() => transition.mutate({ action: "qualify" })} />
    <ConvertLeadDialog key={`${id}-${convertOpen}`} open={convertOpen} item={item} onClose={() => setConvertOpen(false)} onReload={() => { setConvertOpen(false); void detail.refetch(); }} />
  </div>;
}
