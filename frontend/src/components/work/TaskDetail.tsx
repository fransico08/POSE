"use client";

import Link from "next/link";
import { useEffect, useState, type FormEvent } from "react";
import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { WorkSubject, WorkOpportunity } from "@/components/work/WorkReferences";
import { TaskPriorityBadge, TaskStatusBadge } from "@/components/work/TaskIndicators";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { ErrorState } from "@/components/ui/ErrorState";
import { useModal } from "@/components/ui/use-modal";
import { ApiError } from "@/lib/api/client";
import { crmFieldErrors } from "@/lib/crm";
import { workError } from "@/lib/interactions";
import { canAssignTask, canManageTask, getTask, getTaskOwnerOptions, taskAction, taskKey } from "@/lib/tasks";
import { instantToVietnamDateTime } from "@/lib/work-time";

type Action = "start" | "complete" | "cancel" | "assign";
const labels: Record<Action, string> = { start: "Bắt đầu", complete: "Hoàn tất", cancel: "Hủy nhiệm vụ", assign: "Giao lại" };
const descriptions: Record<Action, string> = {
  start: "Chuyển nhiệm vụ sang trạng thái đang thực hiện.",
  complete: "Ghi lại kết quả trước khi đóng nhiệm vụ.",
  cancel: "Nhiệm vụ đã hủy sẽ không thể mở lại.",
  assign: "Chọn nhân viên mới phụ trách nhiệm vụ này.",
};

function TaskDialog({ action, onClose, onReload, onSubmit, busy, error }: { action: Action | null; onClose: () => void; onReload: () => void; onSubmit: (fields: Record<string, string>) => void; busy: boolean; error: unknown }) {
  const { user } = useSession();
  const [value, setValue] = useState("");
  const [localError, setLocalError] = useState("");
  const modal = useModal(Boolean(action), () => { if (!busy) onClose(); });
  const users = useQuery({ queryKey: [...taskKey(user), "owner-options"], queryFn: getTaskOwnerOptions, enabled: action === "assign" && user.permissions.includes("identity.user.read"), retry: false });
  const fields = crmFieldErrors(error);
  const field = action === "complete" ? "result" : action === "cancel" ? "reason" : "ownerUserId";

  function submit(event: FormEvent) {
    event.preventDefault();
    if (action !== "start" && !value.trim()) {
      setLocalError(action === "assign" ? "Chọn người phụ trách." : "Vui lòng nhập thông tin bắt buộc.");
      document.getElementById("task-action-value")?.focus();
      return;
    }
    onSubmit(action === "start" ? {} : { [field]: value.trim() });
  }

  return (
    <dialog {...modal} className={`dialog task-action-dialog${action ? ` task-action-${action}` : ""}`} aria-modal="true" aria-labelledby="task-action-title">
      <form onSubmit={submit} noValidate>
        <header className="task-action-heading">
          <p className="task-action-kicker">Nhiệm vụ</p>
          <h2 id="task-action-title">{action ? labels[action] : "Xác nhận"}</h2>
          {action && <p className="task-action-description">{descriptions[action]}</p>}
        </header>
        {error != null && <Alert>{workError(error)}</Alert>}
        {error instanceof ApiError && ["TASK_CHANGED", "TASK_STATE_CONFLICT"].includes(error.code) && <Button variant="secondary" onClick={onReload}>Tải lại</Button>}
        {action && action !== "start" && <div className="field">
          <label htmlFor="task-action-value">{action === "assign" ? "Người phụ trách *" : action === "complete" ? "Kết quả *" : "Lý do *"}</label>
          {action === "assign" ? (
            <select id="task-action-value" className="input" data-autofocus value={value} aria-invalid={Boolean(localError || fields[field])} aria-describedby={localError || fields[field] ? "task-action-error" : undefined} onChange={event => { setValue(event.target.value); setLocalError(""); }}>
              <option value="">Chọn người phụ trách</option>
              {users.data?.map(person => <option key={person.id} value={person.id}>{person.fullName}</option>)}
            </select>
          ) : (
            <textarea id="task-action-value" className="input" maxLength={4000} data-autofocus value={value} aria-invalid={Boolean(localError || fields[field])} aria-describedby={localError || fields[field] ? "task-action-error" : undefined} onChange={event => { setValue(event.target.value); setLocalError(""); }} />
          )}
          {(localError || fields[field]) && <p id="task-action-error" className="task-action-field-error" role="alert">{fields[field] || localError}</p>}
          {action === "assign" && (users.isError || !user.permissions.includes("identity.user.read")) && <p className="helper">Không thể tải người phụ trách.</p>}
        </div>}
        <footer className="dialog-actions">
          <Button variant="secondary" disabled={busy} onClick={onClose}>Quay lại</Button>
          <Button variant={action === "cancel" ? "danger" : "primary"} type="submit" busy={busy}>Xác nhận</Button>
        </footer>
      </form>
    </dialog>
  );
}

export function TaskDetail() {
  const { user } = useSession();
  const id = useParams<{ id: string }>()?.id ?? "";
  const cache = useQueryClient();
  const [action, setAction] = useState<Action | null>(null);
  const [notice, setNotice] = useState("");
  const [now, setNow] = useState(0);

  useEffect(() => {
    const timer = window.setTimeout(() => setNow(Date.now()), 0);
    const interval = window.setInterval(() => setNow(Date.now()), 60000);
    return () => { window.clearTimeout(timer); window.clearInterval(interval); };
  }, []);

  const query = useQuery({ queryKey: [...taskKey(user), "detail", id], queryFn: () => getTask(id), enabled: Boolean(id) && canManageTask(user) });
  const mutation = useMutation({
    mutationFn: ({ command, fields }: { command: Action; fields: Record<string, string> }) => taskAction(id, command, { expectedUpdatedAt: query.data!.updatedAt, ...fields }),
    onSuccess: saved => { cache.setQueryData([...taskKey(user), "detail", id], saved); void cache.invalidateQueries({ queryKey: taskKey(user) }); setAction(null); setNotice("Đã cập nhật nhiệm vụ."); },
  });

  if (!canManageTask(user)) return <h1>Không có quyền truy cập</h1>;
  if (query.isPending) return <p role="status">Đang tải nhiệm vụ…</p>;
  if (query.isError) return <ErrorState message={workError(query.error)} reset={() => void query.refetch()} />;

  const item = query.data;
  const open = !["DONE", "CANCELLED"].includes(item.status);
  const overdue = open && Date.parse(item.dueAt) < now;

  return (
    <div className="offering-page">
      <p><Link href="/app/nhiem-vu">Nhiệm vụ</Link> / Chi tiết</p>
      {notice && <p role="status" className="task-detail-notice">{notice}</p>}
      <header className="offering-detail-heading task-detail-heading">
        <div className="task-detail-heading-copy">
          <p className="task-detail-kicker">Nhiệm vụ CRM</p>
          <h1>{item.title ?? "Nội dung bị ẩn theo quyền"}</h1>
          <div className="task-detail-badges">
            <TaskStatusBadge status={item.status} />
            <TaskPriorityBadge priority={item.priority} />
            {overdue && <span className="task-overdue-badge">Quá hạn</span>}
          </div>
        </div>
        {open && <div className="offering-detail-actions">
          <Link className="button button-secondary" href={`/app/nhiem-vu/${id}/chinh-sua`}>Chỉnh sửa</Link>
          {(item.status === "OPEN" ? ["start", "complete", "cancel"] : ["complete", "cancel"]).map(command => <Button key={command} variant={command === "cancel" ? "danger" : "secondary"} onClick={() => { mutation.reset(); setAction(command as Action); }}>{labels[command as Action]}</Button>)}
          {canAssignTask(user) && <Button variant="secondary" onClick={() => { mutation.reset(); setAction("assign"); }}>Giao lại</Button>}
        </div>}
      </header>
      <section className="offering-section task-detail-section">
        <h2>Thông tin nhiệm vụ</h2>
        <dl className="offering-detail-grid task-detail-grid">
          <div><dt>Người phụ trách</dt><dd>{item.ownerName}</dd></div>
          <div><dt>Hạn</dt><dd><time dateTime={item.dueAt}>{instantToVietnamDateTime(item.dueAt)}</time></dd></div>
          <div className="task-detail-wide"><dt>Mô tả</dt><dd>{item.description || "Chưa cập nhật"}</dd></div>
          <div className="task-detail-wide"><dt>Kết quả</dt><dd>{item.result || "Chưa cập nhật"}</dd></div>
          <div className="task-detail-wide"><dt>Lý do hủy</dt><dd>{item.cancellationReason || "Chưa cập nhật"}</dd></div>
        </dl>
      </section>
      <section className="offering-section task-related-section">
        <h2>Đối tượng liên quan</h2>
        <p><WorkSubject item={item} /> <span aria-hidden="true">·</span> Cơ hội: <WorkOpportunity item={item} /></p>
      </section>
      <TaskDialog key={action ?? "closed"} action={action} busy={mutation.isPending} error={mutation.error} onClose={() => { setAction(null); mutation.reset(); }} onReload={() => { setAction(null); mutation.reset(); void query.refetch(); }} onSubmit={fields => action && mutation.mutate({ command: action, fields })} />
    </div>
  );
}
