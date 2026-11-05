"use client";
import Link from "next/link";
import { useEffect, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { useModal } from "@/components/ui/use-modal";
import { ApiError } from "@/lib/api/client";
import { crmErrorMessage, crmFieldErrors, crmKey, customerTypes, type CrmRecord } from "@/lib/crm";
import { convertLead } from "@/lib/leads";
import { opportunityKey } from "@/lib/opportunities";

export function ConvertLeadDialog({ open, item, onClose, onReload }: { open: boolean; item: CrmRecord; onClose: () => void; onReload: () => void }) {
  const { user } = useSession(); const router = useRouter(); const cache = useQueryClient();
  const [type, setType] = useState(""); const [address, setAddress] = useState(""); const [notes, setNotes] = useState(item.notes ?? ""); const [local, setLocal] = useState<Record<string, string>>({});
  const mutation = useMutation({ mutationFn: () => convertLead(item.id, { expectedUpdatedAt: item.updatedAt, customerType: type, address: address.trim() || null, notes: notes.trim() || null }), onSuccess: result => { void cache.invalidateQueries({ queryKey: crmKey("lead", user) }); void cache.invalidateQueries({ queryKey: crmKey("customer", user) }); void cache.invalidateQueries({ queryKey: opportunityKey(user) }); window.sessionStorage.setItem("pose-customer-converted", `${result.customer.id}:${result.customerCreated ? "created" : "linked"}`); onClose(); router.replace(`/app/khach-hang/${result.customer.id}`); } });
  const modal = useModal(open, () => { if (!mutation.isPending) onClose(); }); const fields = { ...crmFieldErrors(mutation.error), ...local }; const duplicateId = mutation.error instanceof ApiError && mutation.error.code === "LEAD_CONVERSION_CONFLICT" ? mutation.error.details?.duplicateId : undefined;
  useEffect(() => { if (!mutation.error) return; const field = Object.keys(crmFieldErrors(mutation.error))[0]; if (!field) return; const timer = window.setTimeout(() => document.getElementById(`convert-${field}`)?.focus(), 0); return () => window.clearTimeout(timer); }, [mutation.error]);
  function submit(event: FormEvent) { event.preventDefault(); if (!type) { setLocal({ customerType: "Chọn loại khách hàng." }); document.getElementById("convert-customerType")?.focus(); return; } setLocal({}); mutation.mutate(); }
  return <dialog {...modal} className="dialog" aria-modal="true" aria-labelledby="convert-title"><form onSubmit={submit} noValidate><h2 id="convert-title">Chuyển thành khách hàng</h2><p>Thao tác không thể hoàn tác. Lead sẽ khóa; cơ hội đang mở, tương tác và nhiệm vụ đang mở sẽ chuyển sang khách hàng.</p>
    {mutation.isError && <Alert>{mutation.error instanceof ApiError && mutation.error.code === "LEAD_CONVERSION_CONFLICT" ? mutation.error.serverMessage ?? "Thông tin liên hệ trùng với khách hàng khác. Cần kiểm tra hoặc bàn giao trước khi chuyển đổi." : fields.ownerUserId || crmErrorMessage(mutation.error)}</Alert>}{duplicateId && <Link href={`/app/khach-hang/${encodeURIComponent(String(duplicateId))}`}>Xem khách hàng trùng</Link>}{mutation.error instanceof ApiError && ["LEAD_CHANGED", "LEAD_STATE_CONFLICT", "LEAD_CLOSED"].includes(mutation.error.code) && <Button variant="secondary" onClick={onReload}>Tải lại</Button>}
    <div className="field"><label htmlFor="convert-customerType">Loại khách hàng *</label><select id="convert-customerType" className="input" data-autofocus value={type} aria-invalid={Boolean(fields.customerType)} aria-describedby={fields.customerType ? "convert-customerType-error" : undefined} onChange={e => { setType(e.target.value); setLocal({}); }}><option value="">Chọn loại khách hàng</option>{Object.entries(customerTypes).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select>{fields.customerType && <p id="convert-customerType-error">{fields.customerType}</p>}</div>
    <div className="field"><label htmlFor="convert-address">Địa chỉ</label><input id="convert-address" className="input" maxLength={500} value={address} aria-invalid={Boolean(fields.address)} aria-describedby={fields.address ? "convert-address-error" : undefined} onChange={e => setAddress(e.target.value)} />{fields.address && <p id="convert-address-error">{fields.address}</p>}</div>
    <div className="field"><label htmlFor="convert-notes">Ghi chú</label><textarea id="convert-notes" className="input" maxLength={4000} value={notes} aria-invalid={Boolean(fields.notes)} aria-describedby={fields.notes ? "convert-notes-error" : undefined} onChange={e => setNotes(e.target.value)} />{fields.notes && <p id="convert-notes-error">{fields.notes}</p>}</div><footer className="dialog-actions"><Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>Quay lại</Button><Button type="submit" busy={mutation.isPending}>Xác nhận chuyển</Button></footer></form></dialog>;
}
