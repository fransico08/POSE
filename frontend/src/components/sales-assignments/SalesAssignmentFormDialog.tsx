"use client";

import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { useModal } from "@/components/ui/use-modal";
import { api } from "@/lib/api";
import { toIsoInstant, toLocalDateTimeValue, salesAssignmentErrorMessage, salesAssignmentFieldErrors, type SalesAssignment, type SalesAssignmentCandidate } from "@/lib/sales-assignments";
import type { Offering } from "@/lib/offerings";

type FormValues = { offeringId: string; salesUserId: string; effectiveFrom: string; effectiveTo: string };
const freshForm = (): FormValues => ({ offeringId: "", salesUserId: "", effectiveFrom: toLocalDateTimeValue(new Date().toISOString()), effectiveTo: "" });
const formFromAssignment = (assignment: SalesAssignment | null): FormValues => assignment ? {
  offeringId: assignment.offeringId,
  salesUserId: assignment.salesUserId,
  effectiveFrom: toLocalDateTimeValue(assignment.effectiveFrom),
  effectiveTo: toLocalDateTimeValue(assignment.effectiveTo),
} : freshForm();

export function SalesAssignmentFormDialog({ open, assignment, offerings, onClose, onSaved }: {
  open: boolean;
  assignment: SalesAssignment | null;
  offerings: Offering[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const { user } = useSession();
  const id = useId();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<FormValues>(() => formFromAssignment(assignment));
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState("");
  const errorSummaryRef = useRef<HTMLDivElement>(null);
  const salesCandidates = useQuery({
    queryKey: ["sales-assignment-candidates", user.organization.id, user.id, form.offeringId],
    queryFn: () => api.request<SalesAssignmentCandidate[]>(`/api/backend/sales-assignments/candidates?offeringId=${encodeURIComponent(form.offeringId)}`),
    enabled: open && !assignment && Boolean(form.offeringId),
  });

  const mutation = useMutation({
    mutationFn: async (values: FormValues) => {
      const effectiveTo = values.effectiveTo ? toIsoInstant(values.effectiveTo) : null;
      if (values.effectiveTo && !effectiveTo) throw new Error("Ngày kết thúc không hợp lệ.");
      if (assignment) {
        return api.request<SalesAssignment>(`/api/backend/sales-assignments/${encodeURIComponent(assignment.id)}`, {
          method: "PATCH", headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ effectiveTo, expectedUpdatedAt: assignment.updatedAt }),
        });
      }
      const effectiveFrom = toIsoInstant(values.effectiveFrom);
      if (!effectiveFrom) throw new Error("Ngày bắt đầu không hợp lệ.");
      return api.request<SalesAssignment>("/api/backend/sales-assignments", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ offeringId: values.offeringId, salesUserId: values.salesUserId, effectiveFrom, effectiveTo }),
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["sales-assignments", user.organization.id, user.id] });
      onSaved();
    },
    onError: error => {
      setFieldErrors(salesAssignmentFieldErrors(error));
      setFormError(salesAssignmentErrorMessage(error));
    },
  });
  const modal = useModal(open, () => { if (!mutation.isPending) onClose(); });

  useEffect(() => {
    if (Object.keys(fieldErrors).length > 0) errorSummaryRef.current?.focus();
  }, [fieldErrors]);

  function update<K extends keyof FormValues>(field: K, value: FormValues[K]) {
    setForm(current => ({ ...current, [field]: value }));
    setFieldErrors(current => { const next = { ...current }; delete next[field]; return next; });
    setFormError("");
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const errors: Record<string, string> = {};
    if (!assignment && !form.offeringId) errors.offeringId = "Chọn sản phẩm hoặc dịch vụ đang hoạt động.";
    if (!assignment && !form.salesUserId) errors.salesUserId = "Chọn nhân viên kinh doanh thuộc team sở hữu Offering.";
    if (!assignment && !form.effectiveFrom) errors.effectiveFrom = "Nhập thời điểm bắt đầu hiệu lực.";
    const start = new Date(form.effectiveFrom).getTime();
    const end = form.effectiveTo ? new Date(form.effectiveTo).getTime() : null;
    if (!assignment && form.effectiveFrom && !Number.isFinite(start)) errors.effectiveFrom = "Ngày bắt đầu không hợp lệ.";
    if (form.effectiveTo && !Number.isFinite(end)) errors.effectiveTo = "Ngày kết thúc không hợp lệ.";
    if (end !== null && Number.isFinite(start) && end < start) errors.effectiveTo = "Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.";
    if (assignment && form.effectiveTo && end !== null && end < Date.now()) errors.effectiveTo = "Ngày kết thúc mới phải từ thời điểm hiện tại trở đi.";
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      setFormError("Vui lòng kiểm tra các trường được đánh dấu.");
      return;
    }
    setFormError("");
    mutation.mutate(form);
  }

  const isExtension = assignment !== null;
  const candidates = salesCandidates.data ?? [];
  const candidateHasError = salesCandidates.isError;
  const fieldId = (field: keyof FormValues) => `sales-assignment-${id}-${field}`;
  const fieldError = (field: keyof FormValues) => fieldErrors[field] ? <span className="sales-assignment-field-error" id={`${fieldId(field)}-error`}>{fieldErrors[field]}</span> : null;
  const describedBy = (field: keyof FormValues) => fieldErrors[field] ? `${fieldId(field)}-error` : undefined;

  return <dialog {...modal} className="dialog sales-assignment-dialog" aria-modal="true" aria-labelledby={`${id}-title`} aria-describedby={`${id}-description`}>
    <h2 id={`${id}-title`}>{isExtension ? "Gia hạn phân công" : "Phân công sản phẩm/dịch vụ"}</h2>
    <p id={`${id}-description`}>{isExtension ? "Cập nhật thời điểm kết thúc cho phân công hiện tại." : "Chọn Offering đang hoạt động và Sales thuộc cùng team sở hữu Offering."}</p>
    {formError && <Alert>{formError}</Alert>}
    {Object.keys(fieldErrors).length > 0 && <div className="sales-assignment-error-summary" ref={errorSummaryRef} tabIndex={-1} aria-labelledby={`${id}-error-title`}>
      <h3 id={`${id}-error-title`}>Cần kiểm tra các trường sau</h3>
      <ul>{Object.entries(fieldErrors).map(([field, message]) => <li key={field}>{message}</li>)}</ul>
    </div>}
    <form onSubmit={submit} noValidate>
      {!isExtension ? <>
        <div className="field">
          <label htmlFor={fieldId("offeringId")}>Sản phẩm hoặc dịch vụ <span aria-hidden="true">*</span></label>
          <select id={fieldId("offeringId")} className="input" value={form.offeringId} data-autofocus
            onChange={event => { update("offeringId", event.target.value); update("salesUserId", ""); }}
            aria-invalid={Boolean(fieldErrors.offeringId)} aria-describedby={describedBy("offeringId")} required>
            <option value="">Chọn Offering đang hoạt động</option>
            {offerings.filter(offering => offering.active && offering.teamId).map(offering => <option key={offering.id} value={offering.id}>{offering.name} · {offering.teamName}</option>)}
          </select>{fieldError("offeringId")}
        </div>
        <div className="field">
          <label htmlFor={fieldId("salesUserId")}>Nhân viên kinh doanh <span aria-hidden="true">*</span></label>
          <select id={fieldId("salesUserId")} className="input" value={form.salesUserId} disabled={!form.offeringId || salesCandidates.isPending || candidateHasError}
            onChange={event => update("salesUserId", event.target.value)} aria-invalid={Boolean(fieldErrors.salesUserId)}
            aria-describedby={describedBy("salesUserId")} required>
            <option value="">{!form.offeringId ? "Chọn Offering trước" : salesCandidates.isPending ? "Đang tải nhân viên…" : "Chọn nhân viên thuộc team"}</option>
            {candidates.map(candidate => <option key={candidate.id} value={candidate.id}>{candidate.fullName}</option>)}
          </select>{fieldError("salesUserId")}
          {candidateHasError && <div className="sales-assignment-inline-error"><Alert>{salesAssignmentErrorMessage(salesCandidates.error)}</Alert><Button type="button" variant="secondary" onClick={() => void salesCandidates.refetch()}>Thử lại</Button></div>}
          {form.offeringId && salesCandidates.data?.length === 0 && <p className="sales-assignment-helper" role="status">Team của Offering hiện chưa có Sales đang hoạt động đủ điều kiện nhận phân công.</p>}
        </div>
        <div className="field">
          <label htmlFor={fieldId("effectiveFrom")}>Hiệu lực từ <span aria-hidden="true">*</span></label>
          <input id={fieldId("effectiveFrom")} className="input" type="datetime-local" value={form.effectiveFrom}
            onChange={event => update("effectiveFrom", event.target.value)} aria-invalid={Boolean(fieldErrors.effectiveFrom)}
            aria-describedby={describedBy("effectiveFrom")} required />{fieldError("effectiveFrom")}
        </div>
      </> : <>
        <dl className="sales-assignment-extension-summary"><div><dt>Offering</dt><dd>{assignment.offeringName}</dd></div><div><dt>Sale</dt><dd>{assignment.salesUserName}</dd></div></dl>
        <input type="hidden" value={form.effectiveFrom} readOnly />
      </>}
      <div className="field">
        <label htmlFor={fieldId("effectiveTo")}>Hiệu lực đến</label>
        <input id={fieldId("effectiveTo")} className="input" type="datetime-local" value={form.effectiveTo}
          onChange={event => update("effectiveTo", event.target.value)} aria-invalid={Boolean(fieldErrors.effectiveTo)}
          aria-describedby={`${fieldId("effectiveTo")}-help${describedBy("effectiveTo") ? ` ${describedBy("effectiveTo")}` : ""}`} />
        <span className="sales-assignment-helper" id={`${fieldId("effectiveTo")}-help`}>Để trống nếu không đặt ngày kết thúc.</span>{fieldError("effectiveTo")}
      </div>
      <footer className="sales-assignment-form-actions">
        <Button type="button" variant="secondary" data-autofocus={isExtension ? true : undefined} disabled={mutation.isPending} onClick={onClose}>Hủy</Button>
        <Button type="submit" busy={mutation.isPending} disabled={!isExtension && Boolean(form.offeringId) && (salesCandidates.isPending || candidates.length === 0)}>
          {mutation.isPending ? "Đang lưu…" : isExtension ? "Lưu thời hạn" : "Xác nhận phân công"}
        </Button>
      </footer>
    </form>
  </dialog>;
}
