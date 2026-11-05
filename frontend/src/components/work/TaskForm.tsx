"use client";
import Link from "next/link";
import { WorkSubject, WorkOpportunity } from "@/components/work/WorkReferences";
import { useState, type FormEvent } from "react";
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
import { workError } from "@/lib/interactions";
import { canCreateTask, canManageTask, getTask, getTaskOwnerOptions, saveTask, taskKey, taskPriorities, type Task } from "@/lib/tasks";
import { instantToVietnamDateTime, vietnamDateTimeToInstant } from "@/lib/work-time";

export function TaskForm({ edit = false }: { edit?: boolean }) {
  const { user } = useSession(); const id = useParams<{ id: string }>()?.id ?? "";
  const query = useQuery({ queryKey: [...taskKey(user), "detail", id], queryFn: () => getTask(id), enabled: edit && !!id && canManageTask(user) });
  if (!(edit ? canManageTask(user) : canCreateTask(user))) return <h1>Không có quyền truy cập</h1>;
  if (edit && query.isPending) return <p role="status">Đang tải nhiệm vụ…</p>;
  if (query.isError) return <Alert>{workError(query.error)}</Alert>;
  if (query.data && ["DONE", "CANCELLED"].includes(query.data.status)) return <section><h1>Nhiệm vụ đã đóng</h1><Link href={`/app/nhiem-vu/${id}`}>Về chi tiết</Link></section>;
  return <TaskEditor key={query.data?.updatedAt ?? "new"} item={query.data} reload={() => void query.refetch()} />;
}
function TaskEditor({ item, reload }: { item?: Task; reload: () => void }) {
  const { user } = useSession(); const router = useRouter(); const cache = useQueryClient(); const manager = user.roles.includes("MANAGER");
  const initialSubject = !item && typeof window !== "undefined" ? parseInitialWorkSubject(window.location.search) : { kind: item?.leadId ? "lead" as const : "customer" as const, id: item?.customerId ?? item?.leadId ?? "", notice: "" };
  const [kind, setKind] = useState<"customer" | "lead">(initialSubject.kind); const [subjectId, setSubjectId] = useState(initialSubject.id); const [subjectNotice, setSubjectNotice] = useState(initialSubject.notice); const [opportunityId, setOpportunityId] = useState(item?.opportunityId ?? ""); const [owner, setOwner] = useState("");
  const [title, setTitle] = useState(item?.title ?? ""); const [description, setDescription] = useState(item?.description ?? ""); const [priority, setPriority] = useState(item?.priority ?? "MEDIUM"); const [dueAt, setDueAt] = useState(item ? instantToVietnamDateTime(item.dueAt) : ""); const [errors, setErrors] = useState<Record<string, string>>({});
  const users = useQuery({ queryKey: [...taskKey(user), "owner-options"], queryFn: getTaskOwnerOptions, enabled: !item && manager && user.permissions.includes("identity.user.read"), retry: false });
  const mutation = useMutation({ mutationFn: (body: object) => saveTask(item?.id ?? null, body), onSuccess: saved => { void cache.invalidateQueries({ queryKey: taskKey(user) }); invalidateCustomer360(cache, user, saved.customerId ?? saved.customer?.id); router.replace(`/app/nhiem-vu/${saved.id}`); }, onError: error => { const fields = crmFieldErrors(error); setErrors(fields); const first = Object.keys(fields)[0]; if (first) window.setTimeout(() => document.getElementById(first === "customerId" || first === "leadId" ? "work-subject-picker" : first === "opportunityId" ? "work-opportunity" : `work-${first}`)?.focus(), 0); } });
  function submit(event: FormEvent) {
    event.preventDefault(); const next: Record<string, string> = {}; const instant = vietnamDateTimeToInstant(dueAt);
    if (!item && !subjectId) next.customerId = "Chọn khách hàng hoặc Lead."; if (!title.trim()) next.title = "Nhập tiêu đề."; if (!instant) next.dueAt = "Nhập ngày giờ hợp lệ."; if (!item && manager && !owner) next.ownerUserId = "Chọn người phụ trách.";
    if (Object.keys(next).length) { setErrors(next); document.getElementById(Object.keys(next)[0] === "customerId" ? "work-subject-picker" : `work-${Object.keys(next)[0]}`)?.focus(); return; }
    const fields = { title: title.trim(), description: description.trim() || null, priority, dueAt: instant };
    setErrors({}); mutation.mutate(item ? { ...fields, expectedUpdatedAt: item.updatedAt } : { ...fields, [kind === "customer" ? "customerId" : "leadId"]: subjectId, opportunityId: opportunityId || null, ...(manager ? { ownerUserId: owner } : {}) });
  }
  return <div className="offering-page"><h1>{item ? "Chỉnh sửa nhiệm vụ" : "Thêm nhiệm vụ"}</h1><form onSubmit={submit} noValidate><section className="offering-section">{subjectNotice && <Alert>{subjectNotice}</Alert>}{mutation.error instanceof ApiError && mutation.error.code === "LEAD_CLOSED" ? <LeadClosedNotice leadId={kind === "lead" ? subjectId : ""} /> : mutation.isError && !(mutation.error instanceof ApiError && mutation.error.code === "VALIDATION_ERROR" && Object.keys(errors).length > 0) && <Alert>{workError(mutation.error)}</Alert>}{mutation.error instanceof ApiError && mutation.error.code === "TASK_CHANGED" && <Button variant="secondary" onClick={reload}>Tải lại</Button>}{Object.keys(errors).length > 0 && <Alert>Vui lòng kiểm tra các trường được đánh dấu.</Alert>}
    {item ? <p>Đối tượng: <WorkSubject item={item} /> · Cơ hội: <WorkOpportunity item={item} /> · Người phụ trách: {item.ownerName}. Không thể thay đổi tại đây.</p> : <SubjectPicker kind={kind} id={subjectId} opportunityId={opportunityId} onChange={(next, id) => { setKind(next); setSubjectId(id); setOpportunityId(""); setSubjectNotice(""); }} onOpportunity={setOpportunityId} onInvalidInitial={setSubjectNotice} error={errors.customerId ?? errors.leadId} opportunityError={errors.opportunityId} />}
    <div className="opportunity-form-grid"><div className="field"><label htmlFor="work-title">Tiêu đề *</label><input id="work-title" className="input" maxLength={220} value={title} aria-invalid={Boolean(errors.title)} aria-describedby={errors.title ? "work-error-title" : undefined} onChange={e => setTitle(e.target.value)} />{errors.title && <p id="work-error-title">{errors.title}</p>}</div><div className="field"><label htmlFor="work-description">Mô tả</label><textarea id="work-description" className="input" maxLength={4000} value={description} aria-invalid={Boolean(errors.description)} aria-describedby={errors.description ? "work-error-description" : undefined} onChange={e => setDescription(e.target.value)} />{errors.description && <p id="work-error-description">{errors.description}</p>}</div><div className="field"><label htmlFor="work-priority">Ưu tiên *</label><select id="work-priority" className="input" value={priority} aria-invalid={Boolean(errors.priority)} aria-describedby={errors.priority ? "work-error-priority" : undefined} onChange={e => setPriority(e.target.value)}>{!(priority in taskPriorities) && <option value={priority}>{priority} (không hợp lệ)</option>}{Object.entries(taskPriorities).map(([value, name]) => <option key={value} value={value}>{name}</option>)}</select>{errors.priority && <p id="work-error-priority">{errors.priority}</p>}</div><div className="field"><label htmlFor="work-dueAt">Hạn *</label><DateTimeInput id="work-dueAt" value={dueAt} onChange={setDueAt} aria-invalid={Boolean(errors.dueAt)} aria-describedby={errors.dueAt ? "work-error-dueAt" : undefined} />{errors.dueAt && <p id="work-error-dueAt">{errors.dueAt}</p>}</div>
    {!item && manager && <div className="field"><label htmlFor="work-ownerUserId">Người phụ trách *</label><select id="work-ownerUserId" className="input" value={owner} aria-invalid={Boolean(errors.ownerUserId)} aria-describedby={errors.ownerUserId ? "work-error-ownerUserId" : undefined} onChange={e => setOwner(e.target.value)}><option value="">Chọn người phụ trách</option>{users.data?.map(person => <option key={person.id} value={person.id}>{person.fullName}</option>)}</select>{errors.ownerUserId && <p id="work-error-ownerUserId">{errors.ownerUserId}</p>}{(!user.permissions.includes("identity.user.read") || users.isError) && <p>Không có quyền tải người phụ trách. Liên hệ quản trị viên.</p>}</div>}</div>
  </section><footer className="dialog-actions"><Link className="button button-secondary" href={item ? `/app/nhiem-vu/${item.id}` : "/app/nhiem-vu"}>Hủy</Link><Button type="submit" busy={mutation.isPending}>Lưu nhiệm vụ</Button></footer></form></div>;
}
