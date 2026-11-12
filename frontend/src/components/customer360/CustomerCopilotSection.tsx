"use client";

import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { Alert } from "@/components/ui/Alert";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import {
  acceptSuggestion,
  aiStatusKey,
  canUseAiCopilot,
  copilotErrorMessage,
  copilotInvokeFailureMessage,
  copilotKindLabels,
  copilotStatusLabels,
  customerSuggestionsKey,
  formatCopilotTextForEdit,
  generateHandoverBrief,
  getAiStatus,
  invalidateCustomerCopilot,
  listCustomerSuggestions,
  parseCopilotContent,
  parseCopilotEvidence,
  rejectSuggestion,
  sourceAnchorId,
  sourceChipLabel,
  summarizeCustomer,
  type CopilotInvokeResponse,
  type CopilotSuggestion,
} from "@/lib/copilot";
import { instantToVietnamDateTime } from "@/lib/work-time";

type Props = {
  customerId: string;
  handoverTriggerCount?: number;
};

function focusSourceOnPage(sourceCode: string): boolean {
  if (typeof document === "undefined") return false;
  const code = sourceCode.trim();
  const nodes = Array.from(document.querySelectorAll<HTMLElement>(`[data-c360-source="${code}"]`));
  const anchorId = sourceAnchorId(code);
  if (nodes.length === 0 && anchorId) {
    const byId = document.getElementById(anchorId);
    if (byId) nodes.push(byId);
  }
  if (nodes.length === 0) return false;
  const visible = nodes.find(el => el.offsetParent !== null || el.getClientRects().length > 0) ?? nodes[0];
  const reducedMotion =
    typeof window !== "undefined" &&
    typeof window.matchMedia === "function" &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (typeof visible.scrollIntoView === "function") {
    visible.scrollIntoView({ behavior: reducedMotion ? "auto" : "smooth", block: "center" });
  }
  visible.focus();
  return true;
}

export function CustomerCopilotSection({ customerId, handoverTriggerCount = 0 }: Props) {
  const { user } = useSession();
  const cache = useQueryClient();
  const permitted = canUseAiCopilot(user);
  const sectionRef = useRef<HTMLElement>(null);
  const lastTriggerRef = useRef(handoverTriggerCount);

  const [activeInvoke, setActiveInvoke] = useState<CopilotInvokeResponse | null>(null);
  const [lastKind, setLastKind] = useState<"C360_SUMMARY" | "HANDOVER_BRIEF">("C360_SUMMARY");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editDraft, setEditDraft] = useState("");
  const [editError, setEditError] = useState<string | null>(null);
  const [unavailableSourceNote, setUnavailableSourceNote] = useState<string | null>(null);

  const statusQuery = useQuery({
    queryKey: aiStatusKey(user),
    queryFn: getAiStatus,
    enabled: permitted,
  });

  const aiEnabled = permitted && statusQuery.data?.enabled === true;

  const suggestionsQuery = useQuery({
    queryKey: customerSuggestionsKey(user, customerId),
    queryFn: () => listCustomerSuggestions(customerId),
    enabled: aiEnabled,
  });

  const invokeMutation = useMutation({
    mutationFn: ({ kind, force }: { kind: "C360_SUMMARY" | "HANDOVER_BRIEF"; force: boolean }) =>
      kind === "HANDOVER_BRIEF" ? generateHandoverBrief(customerId, force) : summarizeCustomer(customerId, force),
    onMutate: () => {
      setActiveInvoke(null);
    },
    onSuccess: (result, variables) => {
      setLastKind(variables.kind);
      setActiveInvoke(result);
      setEditingId(null);
      setEditError(null);
      setUnavailableSourceNote(null);
      invalidateCustomerCopilot(cache, user, customerId);
    },
  });

  const reviewMutation = useMutation({
    mutationFn: ({ id, accept, finalContent }: { id: string; accept: boolean; finalContent?: string | null }) =>
      accept ? acceptSuggestion(id, finalContent) : rejectSuggestion(id),
    onSuccess: updated => {
      setEditingId(null);
      setEditError(null);
      if (activeInvoke?.suggestion?.id === updated.id) {
        setActiveInvoke({ ...activeInvoke, suggestion: updated });
      }
      invalidateCustomerCopilot(cache, user, customerId);
    },
  });

  useEffect(() => {
    if (handoverTriggerCount > lastTriggerRef.current && aiEnabled) {
      lastTriggerRef.current = handoverTriggerCount;
      setLastKind("HANDOVER_BRIEF");
      invokeMutation.mutate({ kind: "HANDOVER_BRIEF", force: true });
      if (typeof sectionRef.current?.scrollIntoView === "function") {
        sectionRef.current.scrollIntoView({ behavior: "auto", block: "start" });
      }
      sectionRef.current?.focus();
    }
  }, [handoverTriggerCount, aiEnabled, invokeMutation]);

  if (!permitted || !aiEnabled) {
    return null;
  }

  if (activeInvoke?.status === "DISABLED") {
    return null;
  }

  const suggestions = suggestionsQuery.data ?? [];
  const currentSuggestion: CopilotSuggestion | null =
    activeInvoke?.suggestion ?? suggestions[0] ?? null;

  const structured = currentSuggestion ? parseCopilotContent(currentSuggestion.content) : null;
  const evidence = currentSuggestion ? parseCopilotEvidence(currentSuggestion.evidence) : null;

  function handleChipClick(code: string) {
    const ok = focusSourceOnPage(code);
    if (ok) {
      setUnavailableSourceNote(null);
    } else {
      setUnavailableSourceNote(`Nguồn không khả dụng trên trang hiển thị (${sourceChipLabel(code)}).`);
    }
  }

  function startEdit(suggestion: CopilotSuggestion) {
    const parsed = parseCopilotContent(suggestion.content);
    setEditingId(suggestion.id);
    setEditDraft(suggestion.finalContent || formatCopilotTextForEdit(parsed, suggestion.content));
    setEditError(null);
  }

  function submitEdit(suggestionId: string) {
    const trimmed = editDraft.trim();
    if (!trimmed) {
      setEditError("Nội dung chỉnh sửa không được để trống.");
      return;
    }
    setEditError(null);
    reviewMutation.mutate({ id: suggestionId, accept: true, finalContent: trimmed });
  }

  const isBusy = invokeMutation.isPending;

  return (
    <section
      ref={sectionRef}
      tabIndex={-1}
      className="offering-section copilot-section"
      aria-labelledby="c360-copilot-title"
      aria-busy={isBusy}
    >
      <header className="offering-detail-heading copilot-header">
        <div>
          <div className="offering-detail-title">
            <h2 id="c360-copilot-title">AI Copilot</h2>
            <Badge tone="info">Do AI tạo — cần kiểm tra trước khi dùng</Badge>
          </div>
          <p className="offering-status-note">
            Tổng hợp thông tin trong phạm vi quyền của bạn và trích dẫn nguồn đối chiếu trực tiếp trên hồ sơ.
          </p>
        </div>
        <div className="offering-detail-actions">
          <Button
            variant="secondary"
            disabled={isBusy || reviewMutation.isPending}
            onClick={() => {
              setLastKind("C360_SUMMARY");
              invokeMutation.mutate({ kind: "C360_SUMMARY", force: false });
            }}
          >
            Tóm tắt hồ sơ
          </Button>
          <Button
            variant="secondary"
            disabled={isBusy || reviewMutation.isPending}
            onClick={() => {
              setLastKind("HANDOVER_BRIEF");
              invokeMutation.mutate({ kind: "HANDOVER_BRIEF", force: false });
            }}
          >
            Soạn hồ sơ bàn giao
          </Button>
          {currentSuggestion && currentSuggestion.status === "PROPOSED" && (
            <Button
              variant="secondary"
              disabled={isBusy || reviewMutation.isPending}
              onClick={() => {
                const kind =
                  currentSuggestion.kind === "HANDOVER_BRIEF" ? "HANDOVER_BRIEF" : "C360_SUMMARY";
                setLastKind(kind);
                invokeMutation.mutate({ kind, force: true });
              }}
            >
              Tạo mới
            </Button>
          )}
        </div>
      </header>

      <div aria-live="polite">
        {isBusy && (
          <div className="copilot-loading" role="status">
            <p>Đang phân tích hồ sơ và tạo gợi ý AI…</p>
            <div className="offering-skeleton" />
          </div>
        )}

        {!isBusy && invokeMutation.isError && (
          <div className="offering-inline-error">
            <Alert>{copilotErrorMessage(invokeMutation.error)}</Alert>
            <Button
              variant="secondary"
              onClick={() => invokeMutation.mutate({ kind: lastKind, force: false })}
            >
              Thử lại
            </Button>
          </div>
        )}

        {!isBusy && !invokeMutation.isError && activeInvoke?.status === "BLOCKED_BUDGET" && (
          <Alert>Đã hết hạn mức AI tháng này.</Alert>
        )}

        {!isBusy && !invokeMutation.isError && activeInvoke?.status === "FAILED" && (
          <div className="offering-inline-error">
            <Alert>{copilotInvokeFailureMessage(activeInvoke.status, activeInvoke.errorCode)}</Alert>
            <Button
              variant="secondary"
              onClick={() => invokeMutation.mutate({ kind: lastKind, force: true })}
            >
              Thử lại
            </Button>
          </div>
        )}

        {reviewMutation.isError && <Alert>{copilotErrorMessage(reviewMutation.error)}</Alert>}

        {unavailableSourceNote && (
          <p className="copilot-source-unavailable" role="status">
            {unavailableSourceNote}
          </p>
        )}

        {!isBusy && currentSuggestion && (
          <article className="copilot-card" aria-label="Gợi ý AI Copilot">
            <header className="copilot-card-header">
              <div className="offering-detail-title">
                <h3>{copilotKindLabels[currentSuggestion.kind] ?? currentSuggestion.kind}</h3>
                <Badge
                  tone={
                    currentSuggestion.status === "ACCEPTED" || currentSuggestion.status === "EDITED"
                      ? "success"
                      : currentSuggestion.status === "REJECTED"
                        ? "warning"
                        : "info"
                  }
                >
                  {copilotStatusLabels[currentSuggestion.status] ?? currentSuggestion.status}
                </Badge>
                {activeInvoke?.reused && (
                  <span className="offering-status-note">(Sử dụng gợi ý đề xuất gần nhất)</span>
                )}
              </div>
              <p className="offering-status-note">
                Tạo lúc {instantToVietnamDateTime(currentSuggestion.createdAt)}
                {currentSuggestion.reviewedAt
                  ? ` · Duyệt lúc ${instantToVietnamDateTime(currentSuggestion.reviewedAt)}`
                  : ""}
                {evidence && evidence.droppedPoints > 0
                  ? ` · Đã lọc ${evidence.droppedPoints} ý thiếu nguồn hợp lệ`
                  : ""}
              </p>
            </header>

            {currentSuggestion.status === "EDITED" && currentSuggestion.finalContent ? (
              <div className="copilot-edited-body">
                <p className="offering-description">{currentSuggestion.finalContent}</p>
              </div>
            ) : structured ? (
              <div className="copilot-sections">
                {structured.sections.map((section, sIdx) => (
                  <div key={`${section.heading}-${sIdx}`} className="copilot-section-block">
                    <h4>{section.heading}</h4>
                    <ul className="copilot-points">
                      {section.points.map((point, pIdx) => (
                        <li key={`${sIdx}-${pIdx}`} className="copilot-point">
                          <span>{point.text}</span>
                          <span className="copilot-chips" aria-label="Nguồn trích dẫn">
                            {point.sources.map(code => (
                              <button
                                key={code}
                                type="button"
                                className="copilot-chip"
                                onClick={() => handleChipClick(code)}
                              >
                                Nguồn: {sourceChipLabel(code)}
                              </button>
                            ))}
                          </span>
                        </li>
                      ))}
                    </ul>
                  </div>
                ))}
              </div>
            ) : (
              <p className="offering-description">{currentSuggestion.content}</p>
            )}

            {editingId === currentSuggestion.id && (
              <div className="copilot-edit-box">
                <div className="field">
                  <label htmlFor={`copilot-edit-${currentSuggestion.id}`}>
                    Nội dung chỉnh sửa trước khi lưu
                  </label>
                  <textarea
                    id={`copilot-edit-${currentSuggestion.id}`}
                    className="input offering-textarea"
                    value={editDraft}
                    maxLength={12000}
                    aria-invalid={Boolean(editError)}
                    aria-describedby={editError ? `copilot-edit-error-${currentSuggestion.id}` : undefined}
                    onChange={e => {
                      setEditDraft(e.target.value);
                      setEditError(null);
                    }}
                  />
                  {editError && (
                    <p id={`copilot-edit-error-${currentSuggestion.id}`} className="offering-field-error">
                      {editError}
                    </p>
                  )}
                </div>
                <div className="offering-detail-actions">
                  <Button
                    variant="secondary"
                    disabled={reviewMutation.isPending}
                    onClick={() => {
                      setEditingId(null);
                      setEditError(null);
                    }}
                  >
                    Hủy sửa
                  </Button>
                  <Button
                    busy={reviewMutation.isPending}
                    onClick={() => submitEdit(currentSuggestion.id)}
                  >
                    Lưu bản đã sửa
                  </Button>
                </div>
              </div>
            )}

            {currentSuggestion.status === "PROPOSED" && editingId !== currentSuggestion.id && (
              <footer className="offering-detail-actions copilot-actions">
                <Button
                  busy={reviewMutation.isPending}
                  onClick={() =>
                    reviewMutation.mutate({ id: currentSuggestion.id, accept: true })
                  }
                >
                  Chấp nhận
                </Button>
                <Button
                  variant="secondary"
                  disabled={reviewMutation.isPending}
                  onClick={() => startEdit(currentSuggestion)}
                >
                  Sửa
                </Button>
                <Button
                  variant="secondary"
                  disabled={reviewMutation.isPending}
                  onClick={() =>
                    reviewMutation.mutate({ id: currentSuggestion.id, accept: false })
                  }
                >
                  Từ chối
                </Button>
              </footer>
            )}
          </article>
        )}

        {suggestions.length > 1 && (
          <div className="copilot-history">
            <h3>Lịch sử gợi ý trên hồ sơ ({suggestions.length})</h3>
            <ul className="copilot-history-list">
              {suggestions.slice(0, 5).map(item => (
                <li key={item.id} className="copilot-history-item">
                  <span>
                    {copilotKindLabels[item.kind] ?? item.kind} ·{" "}
                    {instantToVietnamDateTime(item.createdAt)}
                  </span>
                  <Badge
                    tone={
                      item.status === "ACCEPTED" || item.status === "EDITED"
                        ? "success"
                        : item.status === "REJECTED"
                          ? "warning"
                          : "info"
                    }
                  >
                    {copilotStatusLabels[item.status] ?? item.status}
                  </Badge>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </section>
  );
}
