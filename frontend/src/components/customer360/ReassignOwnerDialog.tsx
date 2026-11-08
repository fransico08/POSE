"use client";

import { useEffect, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { useModal } from "@/components/ui/use-modal";
import { crmErrorMessage, crmFieldErrors, crmKey } from "@/lib/crm";
import { invalidateCustomer360, type Customer360ProfileData } from "@/lib/customer360";
import {
  customerKey,
  getCustomerOwnerCandidates,
  reassignCustomer,
  type CustomerReassignResponse,
} from "@/lib/customers";
import { invalidateCustomerCopilot } from "@/lib/copilot";
import { opportunityKey } from "@/lib/opportunities";
import { taskKey } from "@/lib/tasks";

type Props = {
  open: boolean;
  profile: Customer360ProfileData;
  onClose: () => void;
  onSuccess: (result: CustomerReassignResponse) => void;
};

export function ReassignOwnerDialog({ open, profile, onClose, onSuccess }: Props) {
  const { user } = useSession();
  const cache = useQueryClient();
  const [newOwnerUserId, setNewOwnerUserId] = useState("");
  const [reason, setReason] = useState("");
  const [localErrors, setLocalErrors] = useState<Record<string, string>>({});

  const offeringId = profile.relatedOfferingId;

  const candidatesQuery = useQuery({
    queryKey: [...customerKey(user), "owner-candidates", offeringId ?? "none"],
    queryFn: () => getCustomerOwnerCandidates(offeringId!),
    enabled: open && Boolean(offeringId),
  });

  const mutation = useMutation({
    mutationFn: () =>
      reassignCustomer(profile.id, {
        newOwnerUserId,
        reason: reason.trim(),
      }),
    onSuccess: result => {
      invalidateCustomer360(cache, user, profile.id);
      invalidateCustomerCopilot(cache, user, profile.id);
      void cache.invalidateQueries({ queryKey: crmKey("customer", user) });
      void cache.invalidateQueries({ queryKey: opportunityKey(user) });
      void cache.invalidateQueries({ queryKey: taskKey(user) });
      setNewOwnerUserId("");
      setReason("");
      setLocalErrors({});
      onSuccess(result);
      onClose();
    },
  });

  const modal = useModal(open, () => {
    if (!mutation.isPending) onClose();
  });

  const apiFieldErrors = crmFieldErrors(mutation.error);
  const fields: Record<string, string> = { ...apiFieldErrors, ...localErrors };

  useEffect(() => {
    if (!mutation.error) return;
    const firstField = Object.keys(crmFieldErrors(mutation.error))[0];
    if (!firstField) return;
    const targetId =
      firstField === "reason" ? "reassign-reason" : "reassign-newOwnerUserId";
    const timer = window.setTimeout(() => document.getElementById(targetId)?.focus(), 0);
    return () => window.clearTimeout(timer);
  }, [mutation.error]);

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const nextErrors: Record<string, string> = {};
    if (!newOwnerUserId) {
      nextErrors.newOwnerUserId = "Chọn người phụ trách mới.";
    }
    const trimmedReason = reason.trim();
    if (!trimmedReason) {
      nextErrors.reason = "Nhập lý do đổi người phụ trách.";
    } else if (trimmedReason.length > 1000) {
      nextErrors.reason = "Lý do tối đa 1000 ký tự.";
    }
    if (Object.keys(nextErrors).length > 0) {
      setLocalErrors(nextErrors);
      const firstId = nextErrors.newOwnerUserId ? "reassign-newOwnerUserId" : "reassign-reason";
      document.getElementById(firstId)?.focus();
      return;
    }
    setLocalErrors({});
    mutation.mutate();
  }

  const candidates = (candidatesQuery.data ?? []).filter(candidate => candidate.id !== profile.ownerUserId);

  return (
    <dialog
      {...modal}
      className="dialog task-action-dialog"
      aria-modal="true"
      aria-labelledby="reassign-owner-title"
    >
      <form onSubmit={handleSubmit} noValidate>
        <div className="task-action-heading">
          <h2 id="reassign-owner-title">Đổi người phụ trách</h2>
          <p className="task-action-description">
            Cơ hội đang mở và Task chưa xong của người phụ trách hiện tại trên hồ sơ này sẽ được chuyển sang người mới.
          </p>
        </div>

        {mutation.isError && (
          <Alert>
            {fields.relatedOfferingId || fields.newOwnerUserId || fields.reason || crmErrorMessage(mutation.error)}
          </Alert>
        )}

        {candidatesQuery.isError && (
          <Alert>Không tải được danh sách người phụ trách đủ điều kiện.</Alert>
        )}

        <div className="field">
          <label htmlFor="reassign-newOwnerUserId">Người phụ trách mới *</label>
          <select
            id="reassign-newOwnerUserId"
            className="input"
            data-autofocus
            value={newOwnerUserId}
            disabled={candidatesQuery.isPending || mutation.isPending}
            aria-invalid={Boolean(fields.newOwnerUserId || fields.relatedOfferingId)}
            aria-describedby={
              fields.newOwnerUserId || fields.relatedOfferingId
                ? "reassign-newOwnerUserId-error"
                : undefined
            }
            onChange={e => {
              setNewOwnerUserId(e.target.value);
              setLocalErrors(prev => {
                const copy = { ...prev };
                delete copy.newOwnerUserId;
                return copy;
              });
            }}
          >
            <option value="">
              {candidatesQuery.isPending ? "Đang tải danh sách…" : "Chọn người phụ trách mới"}
            </option>
            {candidates.map(candidate => (
              <option key={candidate.id} value={candidate.id}>
                {candidate.fullName}
              </option>
            ))}
          </select>
          {(fields.newOwnerUserId || fields.relatedOfferingId) && (
            <p id="reassign-newOwnerUserId-error" className="offering-field-error">
              {fields.relatedOfferingId ?? fields.newOwnerUserId}
            </p>
          )}
        </div>

        <div className="field">
          <label htmlFor="reassign-reason">Lý do bàn giao *</label>
          <textarea
            id="reassign-reason"
            className="input"
            maxLength={1000}
            value={reason}
            disabled={mutation.isPending}
            aria-invalid={Boolean(fields.reason)}
            aria-describedby={fields.reason ? "reassign-reason-error reassign-reason-counter" : "reassign-reason-counter"}
            onChange={e => {
              setReason(e.target.value);
              setLocalErrors(prev => {
                const copy = { ...prev };
                delete copy.reason;
                return copy;
              });
            }}
          />
          <p id="reassign-reason-counter" className="c360-char-counter">
            {reason.length}/1000 ký tự
          </p>
          {fields.reason && (
            <p id="reassign-reason-error" className="offering-field-error">
              {fields.reason}
            </p>
          )}
        </div>

        <footer className="dialog-actions">
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            Hủy
          </Button>
          <Button type="submit" busy={mutation.isPending}>
            Xác nhận đổi người phụ trách
          </Button>
        </footer>
      </form>
    </dialog>
  );
}
