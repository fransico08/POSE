"use client";
import Link from "next/link";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { ApiError } from "@/lib/api/client";
import { getLead, getLeadOwnerCandidates, saveLead } from "@/lib/leads";
import { getCustomer, getCustomerOwnerCandidates, saveCustomer } from "@/lib/customers";
import { canCreateCrm, canWriteCrm, crmBase, crmErrorMessage, crmFieldErrors, crmKey, crmTitle, customerTypes, getScopedOfferings, sources, type CrmKind, type CrmRecord } from "@/lib/crm";

type Values = { displayName: string; email: string; phone: string; source: string; customerType: string; offeringId: string; address: string; notes: string; ownerUserId: string };
const empty: Values = { displayName: "", email: "", phone: "", source: "", customerType: "", offeringId: "", address: "", notes: "", ownerUserId: "" };
function fromRecord(record: CrmRecord): Values { return { ...empty, displayName: record.displayName, email: record.email ?? "", phone: record.phone ?? "", source: record.source ?? "", customerType: record.customerType ?? "", offeringId: record.offeringId ?? record.relatedOfferingId ?? "", address: record.address ?? "", notes: record.notes ?? "" }; }
const required = "Vui lòng điền đầy đủ các trường bắt buộc";
const invalidContact = "Hãy nhập thông tin liên hệ hợp lệ";

export function CrmForm({ kind, id }: { kind: CrmKind; id: string | null }) {
  const { user } = useSession(); const router = useRouter(); const cache = useQueryClient();
  const manager = user.roles.includes("MANAGER");
  const allowed = id ? canWriteCrm(user, kind) : canCreateCrm(user, kind);
  const [values, setValues] = useState<Values>(empty), [initial, setInitial] = useState<Values>(empty);
  const [errors, setErrors] = useState<Record<string, string>>({}); const [failure, setFailure] = useState<unknown>(null);
  const initialized = useRef(false); const dirty = JSON.stringify(values) !== JSON.stringify(initial);
  const detail = useQuery({ queryKey: [...crmKey(kind, user), "detail", id], queryFn: async (): Promise<CrmRecord> => kind === "lead" ? getLead(id!) : getCustomer(id!), enabled: Boolean(id && allowed) });
  const mayReadOfferings = (user.roles.includes("SALES_REP") && user.permissions.includes("offering.read")) || (user.roles.some(role => ["ADMIN", "MANAGER"].includes(role)) && user.permissions.includes("offering.manage"));
  const offerings = useQuery({ queryKey: ["crm-offering-options", user.organization.id, user.id], queryFn: () => getScopedOfferings(), enabled: allowed && mayReadOfferings });
  const owners = useQuery({ queryKey: [...crmKey(kind, user), "owners", values.offeringId], queryFn: () => kind === "lead" ? getLeadOwnerCandidates(values.offeringId) : getCustomerOwnerCandidates(values.offeringId), enabled: allowed && !id && manager && Boolean(values.offeringId) });
  useEffect(() => { if (!id || !detail.data || initialized.current) return; const current = fromRecord(detail.data); initialized.current = true; setValues(current); setInitial(current); }, [id, detail.data]);
  useEffect(() => {
    if (!dirty) return;
    const before = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ""; };
    const click = (event: MouseEvent) => { const target = event.target instanceof Element ? event.target.closest("a[href]") : null; if (target && !window.confirm("Bạn có thay đổi chưa lưu. Rời trang?")) { event.preventDefault(); event.stopPropagation(); } };
    const guardedUrl = window.location.href;
    window.history.pushState(window.history.state, "", guardedUrl);
    const back = (event: PopStateEvent) => {
      event.stopImmediatePropagation();
      if (window.confirm("Bạn có thay đổi chưa lưu. Rời trang?")) {
        window.removeEventListener("popstate", back, true); window.removeEventListener("beforeunload", before); window.history.back();
      } else window.history.pushState(window.history.state, "", guardedUrl);
    };
    window.addEventListener("beforeunload", before); window.addEventListener("popstate", back, true); document.addEventListener("click", click, true);
    return () => { window.removeEventListener("beforeunload", before); window.removeEventListener("popstate", back, true); document.removeEventListener("click", click, true); };
  }, [dirty]);
  function update(field: keyof Values, value: string) { setValues(current => ({ ...current, [field]: value, ...(field === "offeringId" ? { ownerUserId: "" } : {}) })); setErrors(current => { const next = { ...current }; delete next[field]; return next; }); setFailure(null); }
  function focusFirst(next: Record<string, string>) { const first = Object.keys(next)[0]; if (first) window.setTimeout(() => document.getElementById(`crm-${first}`)?.focus(), 0); }
  const mutation = useMutation({ mutationFn: async () => {
    const payload = { displayName: values.displayName.trim(), email: values.email.trim() || null, phone: values.phone.trim() || null, notes: values.notes.trim() || null,
      ...(kind === "lead" ? { source: values.source, offeringId: values.offeringId } : { customerType: values.customerType, relatedOfferingId: values.offeringId, address: values.address.trim() || null }),
      ...(id ? { expectedUpdatedAt: detail.data?.updatedAt } : manager ? { ownerUserId: values.ownerUserId } : {}) };
    return kind === "lead" ? saveLead(id, payload) : saveCustomer(id, payload);
  }, onSuccess: saved => { setInitial(values); cache.setQueryData([...crmKey(kind, user), "detail", saved.id], saved); void cache.invalidateQueries({ queryKey: crmKey(kind, user) }); window.sessionStorage.setItem(`pose-${kind}-saved`, `${saved.id}:${id ? "edit" : "create"}`); router.push(`${crmBase(kind)}/${saved.id}`); },
  onError: error => { const server = crmFieldErrors(error); if (server.relatedOfferingId) { server.offeringId = server.relatedOfferingId; delete server.relatedOfferingId; } setErrors(server); setFailure(error); focusFirst(server); } });
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const next: Record<string, string> = {};
    if (!values.displayName.trim()) next.displayName = required;
    if (!values.email.trim() && !values.phone.trim()) next.email = required;
    if (values.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email.trim())) next.email = invalidContact;
    if (values.phone.trim()) { const raw = values.phone.trim().replace(/[\s.()\-]/g, ""); const phone = /^0\d{9}$/.test(raw) ? `+84${raw.slice(1)}` : raw.startsWith("84") ? `+${raw}` : raw; if (!/^\+[1-9]\d{7,14}$/.test(phone)) next.phone = invalidContact; }
    if (!(kind === "lead" ? values.source : values.customerType)) next[kind === "lead" ? "source" : "customerType"] = required;
    if (!values.offeringId) next.offeringId = required;
    if (!id && manager && !values.ownerUserId) next.ownerUserId = required;
    setErrors(next); setFailure(null); if (Object.keys(next).length) { focusFirst(next); return; } mutation.mutate();
  }
  async function reloadLatest() {
    const result = await detail.refetch();
    if (result.data) { const next = fromRecord(result.data); initialized.current = true; setValues(next); setInitial(next); setErrors({}); setFailure(null); }
    else if (result.error) setFailure(result.error);
  }
  if (!allowed) return <section className="offering-section"><EmptyState title="Bạn không có quyền thực hiện thao tác này">Liên hệ quản trị viên để được cấp quyền phù hợp.</EmptyState></section>;
  if (id && detail.isPending) return <section className="offering-section" aria-busy="true"><p role="status">Đang tải hồ sơ…</p><div className="offering-skeleton" /></section>;
  if (id && detail.isError) return <section className="offering-section"><Alert>{crmErrorMessage(detail.error)}</Alert><Button onClick={() => void detail.refetch()}>Thử lại</Button></section>;
  if (id && kind === "lead" && ["CONVERTED", "DISQUALIFIED"].includes(detail.data?.status ?? "")) return <section className="offering-section"><Alert>Lead đã đóng và không thể chỉnh sửa.</Alert><Link href={`${crmBase(kind)}/${id}`}>Về hồ sơ</Link></section>;
  const duplicate = failure instanceof ApiError && ["LEAD_DUPLICATE", "CUSTOMER_DUPLICATE"].includes(failure.code) ? failure.details?.duplicateId : undefined;
  const changed = failure instanceof ApiError && ["LEAD_CHANGED", "CUSTOMER_CHANGED"].includes(failure.code);
  const error = (field: string) => errors[field] ? <p className="offering-field-error" id={`crm-${field}-error`}>{errors[field]}</p> : null;
  const attrs = (field: string) => ({ "aria-invalid": Boolean(errors[field]), "aria-describedby": errors[field] ? `crm-${field}-error` : undefined });
  const activeOfferings = offerings.data ?? [];
  return <div className="offering-page offering-narrow"><header className="offering-heading"><div><p className="eyebrow"><Link href={crmBase(kind)}>{crmTitle(kind)}</Link> / {id ? "Chỉnh sửa" : "Thêm mới"}</p><h1>{id ? "Chỉnh sửa" : "Thêm"} {kind === "lead" ? "Lead" : "khách hàng"}</h1></div></header>
    {failure != null && <div className="offering-inline-error"><Alert>{crmErrorMessage(failure)}</Alert>{duplicate && <Link href={`${crmBase(kind)}/${encodeURIComponent(duplicate)}`}>Mở hồ sơ đã có</Link>}{changed && <Button variant="secondary" onClick={() => void reloadLatest()}>Tải lại</Button>}</div>}
    {!mayReadOfferings && <Alert>Bạn chưa có quyền xem danh sách sản phẩm/dịch vụ; hãy liên hệ quản trị viên nếu cần thay đổi.</Alert>}
    {offerings.isError && <div className="offering-inline-error"><Alert>Không tải được sản phẩm/dịch vụ.</Alert><Button variant="secondary" onClick={() => void offerings.refetch()}>Thử lại</Button></div>}
    <form className="offering-section offering-form" onSubmit={submit} noValidate><h2>Thông tin {crmTitle(kind)}</h2><p>Các trường có dấu * là bắt buộc. Cần ít nhất một thông tin liên hệ.</p><div className="offering-form-grid">
      <div className="field offering-field-full"><label htmlFor="crm-displayName">Tên *</label><input id="crm-displayName" className="input" value={values.displayName} maxLength={180} required {...attrs("displayName")} onChange={e => update("displayName", e.target.value)} />{error("displayName")}</div>
      <div className="field"><label htmlFor="crm-email">Email</label><input id="crm-email" className="input" type="email" autoComplete="email" inputMode="email" value={values.email} {...attrs("email")} onChange={e => update("email", e.target.value)} />{error("email")}</div>
      <div className="field"><label htmlFor="crm-phone">Điện thoại</label><input id="crm-phone" className="input" type="tel" autoComplete="tel" inputMode="tel" value={values.phone} {...attrs("phone")} onChange={e => update("phone", e.target.value)} />{error("phone")}</div>
      <div className="field"><label htmlFor={`crm-${kind === "lead" ? "source" : "customerType"}`}>{kind === "lead" ? "Nguồn" : "Loại khách hàng"} *</label><select className="input" id={`crm-${kind === "lead" ? "source" : "customerType"}`} value={kind === "lead" ? values.source : values.customerType} required {...attrs(kind === "lead" ? "source" : "customerType")} onChange={e => update(kind === "lead" ? "source" : "customerType", e.target.value)}><option value="">Chọn</option>{Object.entries(kind === "lead" ? sources : customerTypes).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>{error(kind === "lead" ? "source" : "customerType")}</div>
      <div className="field"><label htmlFor="crm-offeringId">Sản phẩm/dịch vụ {kind === "lead" ? "quan tâm" : "liên quan"} *</label><select className="input" id="crm-offeringId" value={values.offeringId} {...attrs("offeringId")} onChange={e => update("offeringId", e.target.value)}><option value="">Chọn sản phẩm/dịch vụ</option>{values.offeringId && !activeOfferings.some(item => item.id === values.offeringId) && <option value={values.offeringId} disabled>{detail.data?.offeringName ?? "Sản phẩm cũ không còn hoạt động"}</option>}{activeOfferings.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select>{error("offeringId")}</div>
      {!id && manager && <div className="field"><label htmlFor="crm-ownerUserId">Người phụ trách *</label><select className="input" id="crm-ownerUserId" value={values.ownerUserId} {...attrs("ownerUserId")} onChange={e => update("ownerUserId", e.target.value)}><option value="">Chọn người phụ trách</option>{owners.data?.map(owner => <option key={owner.id} value={owner.id}>{owner.fullName}</option>)}</select>{error("ownerUserId")}{owners.isPending && values.offeringId && <p role="status">Đang tải người phụ trách…</p>}{owners.data && !owners.data.length && <p>Chưa có Sales đủ điều kiện cho sản phẩm này</p>}{owners.isError && <><Alert>Không tải được người phụ trách.</Alert><Button variant="secondary" onClick={() => void owners.refetch()}>Thử lại</Button></>}</div>}
      {kind === "customer" && <div className="field"><label htmlFor="crm-address">Địa chỉ</label><input id="crm-address" className="input" value={values.address} maxLength={500} {...attrs("address")} onChange={e => update("address", e.target.value)} />{error("address")}</div>}
      <div className="field offering-field-full"><label htmlFor="crm-notes">Ghi chú</label><textarea id="crm-notes" className="input offering-textarea" rows={4} maxLength={4000} value={values.notes} {...attrs("notes")} onChange={e => update("notes", e.target.value)} />{error("notes")}</div>
    </div><footer className="offering-form-actions"><Link className="button button-secondary" href={id ? `${crmBase(kind)}/${id}` : crmBase(kind)}>Hủy</Link><Button type="submit" busy={mutation.isPending} disabled={(mayReadOfferings && (offerings.isPending || offerings.isError)) || (!mayReadOfferings && !id)}>{mutation.isPending ? "Đang lưu…" : id ? "Lưu thay đổi" : kind === "lead" ? "Tạo Lead" : "Tạo khách hàng"}</Button></footer></form>
  </div>;
}
