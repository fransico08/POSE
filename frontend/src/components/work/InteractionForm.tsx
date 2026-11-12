"use client";
import Link from "next/link";
import { WorkSubject, WorkOpportunity } from "@/components/work/WorkReferences";
import { useEffect, useState, type FormEvent } from "react";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { DateTimeInput } from "@/components/ui/DateTimeInput";
import { SubjectPicker, parseInitialWorkSubject } from "@/components/work/SubjectPicker";
import { LeadClosedNotice } from "@/components/work/LeadClosedNotice";
import { ApiError } from "@/lib/api/client";
import { crmFieldErrors } from "@/lib/crm";
import { invalidateCustomer360 } from "@/lib/customer360";
import { canCreateInteraction, canReadSensitive, canWriteInteraction, getInteraction, interactionChannelCode, interactionChannelLabel, interactionChannelLabels, interactionKey, interactionTypeCode, interactionTypeLabel, interactionTypeLabels, saveInteraction, workError, type Interaction } from "@/lib/interactions";
import { instantToVietnamDateTime, vietnamDateTimeToInstant } from "@/lib/work-time";

export function InteractionForm({ edit = false }: { edit?: boolean }) {
  const { user } = useSession(); const id = useParams<{ id: string }>()?.id ?? "";
  const query = useQuery({ queryKey: [...interactionKey(user), "detail", id], queryFn: () => getInteraction(id), enabled: edit && !!id && canWriteInteraction(user) });
  if (!(edit ? canWriteInteraction(user) : canCreateInteraction(user))) return <h1>Không có quyền truy cập</h1>;
  if (edit && query.isPending) return <p role="status">Đang tải tương tác…</p>;
  if (query.isError) return <Alert>{workError(query.error)}</Alert>;
  return <InteractionEditor key={query.data?.updatedAt ?? "new"} item={query.data} reload={() => void query.refetch()} />;
}
function InteractionEditor({ item, reload }: { item?: Interaction; reload: () => void }) {
  const { user } = useSession(); const router = useRouter(); const cache = useQueryClient();
  const [kind, setKind] = useState<"customer" | "lead">(item?.leadId ? "lead" : "customer"); const [subjectId, setSubjectId] = useState(item?.customerId ?? item?.leadId ?? ""); const [subjectNotice, setSubjectNotice] = useState(""); const [opportunityId, setOpportunityId] = useState(item?.opportunityId ?? "");
  useEffect(() => {
    if (item) return;
    const initial = parseInitialWorkSubject(window.location.search);
    if (!initial.id && !initial.notice) return;
    const timer = window.setTimeout(() => { setKind(initial.kind); setSubjectId(initial.id); setSubjectNotice(initial.notice); }, 0);
    return () => window.clearTimeout(timer);
  }, [item]);
  const [type, setType] = useState(interactionTypeLabel(item?.type ?? "CALL")); const [channel, setChannel] = useState(interactionChannelLabel(item?.channel ?? "PHONE")); const [subject, setSubject] = useState(item?.subject ?? ""); const [content, setContent] = useState(item?.content ?? ""); const [outcome, setOutcome] = useState(item?.outcome ?? ""); const [visibility, setVisibility] = useState(item?.visibility ?? "CARE_VISIBLE"); const [occurredAt, setOccurredAt] = useState(item ? instantToVietnamDateTime(item.occurredAt) : instantToVietnamDateTime(new Date().toISOString())); const [errors, setErrors] = useState<Record<string, string>>({});
  const mutation = useMutation({ mutationFn: (body: object) => saveInteraction(item?.id ?? null, body), onSuccess: saved => { void cache.invalidateQueries({ queryKey: interactionKey(user) }); invalidateCustomer360(cache, user, saved.customerId ?? saved.customer?.id); router.replace(`/app/tuong-tac/${saved.id}`); }, onError: error => { const fields = crmFieldErrors(error); setErrors(fields); const first = Object.keys(fields)[0]; if (first) window.setTimeout(() => document.getElementById(first === "customerId" || first === "leadId" ? "work-subject-picker" : first === "opportunityId" ? "work-opportunity" : `work-${first}`)?.focus(), 0); } });
  function submit(event: FormEvent) {
    event.preventDefault(); const next: Record<string, string> = {}; const instant = vietnamDateTimeToInstant(occurredAt);
    if (!item && !subjectId) next.customerId = "Chọn khách hàng hoặc Lead.";
    const typeCode = interactionTypeCode(type); const channelCode = interactionChannelCode(channel);
    if (!/^[A-Za-z][A-Za-z0-9_]{0,49}$/.test(typeCode)) next.type = "Nhập mã hợp lệ.";
    if (!/^[A-Za-z][A-Za-z0-9_]{0,49}$/.test(channelCode)) next.channel = "Nhập mã hợp lệ.";
    if (!subject.trim()) next.subject = "Nhập tiêu đề."; if (!content.trim()) next.content = "Nhập nội dung."; if (!instant) next.occurredAt = "Nhập ngày giờ hợp lệ.";
    if (Object.keys(next).length) { setErrors(next); document.getElementById(Object.keys(next)[0] === "customerId" ? "work-subject-picker" : `work-${Object.keys(next)[0]}`)?.focus(); return; }
    const fields = { type: typeCode.toUpperCase(), channel: channelCode.toUpperCase(), subject: subject.trim(), content: content.trim(), outcome: outcome.trim() || null, visibility, occurredAt: instant };
    setErrors({}); mutation.mutate(item ? { ...fields, expectedUpdatedAt: item.updatedAt } : { ...fields, [kind === "customer" ? "customerId" : "leadId"]: subjectId, opportunityId: opportunityId || null });
  }
  const field = (key: string, label: string, value: string, onChange: (value: string) => void, maxLength: number, multiline = false) => <div className="field"><label htmlFor={`work-${key}`}>{label}</label>{multiline ? <textarea id={`work-${key}`} className="input" maxLength={maxLength} value={value} aria-invalid={Boolean(errors[key])} aria-describedby={errors[key] ? `work-error-${key}` : undefined} onChange={e => onChange(e.target.value)} /> : <input id={`work-${key}`} className="input" list={key === "type" ? "work-type-suggestions" : key === "channel" ? "work-channel-suggestions" : undefined} maxLength={maxLength} value={value} aria-invalid={Boolean(errors[key])} aria-describedby={[key === "type" || key === "channel" ? "work-code-hint" : "", errors[key] ? `work-error-${key}` : ""].filter(Boolean).join(" ") || undefined} onChange={e => onChange(e.target.value)} />}{errors[key] && <p id={`work-error-${key}`}>{errors[key]}</p>}</div>;
  return <div className="offering-page"><h1>{item ? "Chỉnh sửa tương tác" : "Thêm tương tác"}</h1><form onSubmit={submit} noValidate><section className="offering-section">{subjectNotice && <Alert>{subjectNotice}</Alert>}{mutation.error instanceof ApiError && mutation.error.code === "LEAD_CLOSED" ? <LeadClosedNotice leadId={kind === "lead" ? subjectId : ""} /> : mutation.isError && !(mutation.error instanceof ApiError && mutation.error.code === "VALIDATION_ERROR" && Object.keys(errors).length > 0) && <Alert>{workError(mutation.error)}</Alert>}{mutation.error instanceof ApiError && mutation.error.code === "INTERACTION_CHANGED" && <Button variant="secondary" onClick={reload}>Tải lại</Button>}{Object.keys(errors).length > 0 && <Alert>Vui lòng kiểm tra các trường được đánh dấu.</Alert>}
    {item ? <p>Đối tượng: <WorkSubject item={item} /> · Cơ hội: <WorkOpportunity item={item} />. Không thể thay đổi tại đây.</p> : <SubjectPicker kind={kind} id={subjectId} opportunityId={opportunityId} onChange={(next, id) => { setKind(next); setSubjectId(id); setOpportunityId(""); setSubjectNotice(""); }} onOpportunity={setOpportunityId} onInvalidInitial={setSubjectNotice} error={errors.customerId ?? errors.leadId} opportunityError={errors.opportunityId} />}
    <p id="work-code-hint">Chọn một gợi ý hoặc nhập mã tùy chỉnh (chữ, số và dấu gạch dưới).</p><datalist id="work-type-suggestions">{Object.entries(interactionTypeLabels).map(([code, label]) => <option key={code} value={label} />)}</datalist><datalist id="work-channel-suggestions">{Object.entries(interactionChannelLabels).map(([code, label]) => <option key={code} value={label} />)}</datalist><div className="opportunity-form-grid">{field("type", "Loại *", type, setType, 50)}{field("channel", "Kênh *", channel, setChannel, 50)}{field("subject", "Tiêu đề *", subject, setSubject, 220)}{field("content", "Nội dung *", content, setContent, 4000, true)}{field("outcome", "Kết quả", outcome, setOutcome, 4000, true)}<div className="field"><label htmlFor="work-visibility">Phạm vi hiển thị</label><select id="work-visibility" className="input" aria-invalid={Boolean(errors.visibility)} aria-describedby={errors.visibility ? "work-error-visibility" : undefined} value={visibility} onChange={e => setVisibility(e.target.value as typeof visibility)}><option value="CARE_VISIBLE">Chia sẻ với chăm sóc</option>{canReadSensitive(user) && <option value="SALES_ONLY">Chỉ bán hàng</option>}</select>{errors.visibility && <p id="work-error-visibility">{errors.visibility}</p>}</div><div className="field"><label htmlFor="work-occurredAt">Thời điểm *</label><DateTimeInput id="work-occurredAt" value={occurredAt} onChange={setOccurredAt} aria-invalid={Boolean(errors.occurredAt)} aria-describedby={errors.occurredAt ? "work-error-occurredAt" : undefined} />{errors.occurredAt && <p id="work-error-occurredAt">{errors.occurredAt}</p>}</div></div>
  </section><footer className="dialog-actions"><Link className="button button-secondary" href={item ? `/app/tuong-tac/${item.id}` : "/app/tuong-tac"}>Hủy</Link><Button type="submit" busy={mutation.isPending}>Lưu tương tác</Button></footer></form></div>;
}
