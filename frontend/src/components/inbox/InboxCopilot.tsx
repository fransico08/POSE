"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { Button } from "@/components/ui/Button";
import { Alert } from "@/components/ui/Alert";
import { api } from "@/lib/api";
import { aiStatusKey, canUseAiCopilot, copilotErrorMessage,
  copilotInvokeFailureMessage, getAiStatus,
  type CopilotInvokeResponse, type CopilotSuggestion } from "@/lib/copilot";

type Props = { conversationId: string; currentReply: string; onUse: (text: string) => void };
type Draft = { draft: string; sources: string[] };

function parseDraft(content: string): Draft | null {
  try {
    const parsed: unknown = JSON.parse(content);
    if (!parsed || typeof parsed !== "object") return null;
    const value = parsed as Record<string, unknown>;
    return typeof value.draft === "string" && Array.isArray(value.sources)
      && value.sources.every(source => typeof source === "string") ? value as Draft : null;
  } catch { return null; }
}

export function InboxCopilot({ conversationId, currentReply, onUse }: Props) {
  const { user } = useSession();
  const allowed = canUseAiCopilot(user) && user.permissions.includes("conversation.reply");
  const cache = useQueryClient();
  const controller = useRef<AbortController | null>(null);
  const reviewController = useRef<AbortController | null>(null);
  const mounted = useRef(false);
  const [result, setResult] = useState<CopilotInvokeResponse | null>(null);
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState("");
  const [notice, setNotice] = useState("");
  const status = useQuery({ queryKey: aiStatusKey(user), queryFn: getAiStatus, enabled: allowed });
  const key = ["copilot", user.organization.id, user.id, "conversation", conversationId] as const;
  const suggestions = useQuery({
    queryKey: key,
    queryFn: ({ signal }) => api.request<CopilotSuggestion[]>(`/api/backend/ai/suggestions?subjectType=CONVERSATION&subjectId=${encodeURIComponent(conversationId)}`, { signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]) }, { suppressForbiddenNavigation: true }),
    enabled: allowed && status.data?.enabled === true,
  });
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; controller.current?.abort(); reviewController.current?.abort(); };
  }, [conversationId]);
  const invoke = useMutation({
    mutationFn: async () => {
      controller.current?.abort();
      const active = new AbortController();
      controller.current = active;
      return api.request<CopilotInvokeResponse>(`/api/backend/ai/copilot/conversations/${encodeURIComponent(conversationId)}/reply-draft`, {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ force: false }),
        signal: AbortSignal.any([active.signal, AbortSignal.timeout(75000)]),
      }, { suppressForbiddenNavigation: true });
    },
    onSuccess: value => { setResult(value); setEditing(false); setNotice(""); void cache.invalidateQueries({ queryKey: key }); },
  });
  const review = useMutation({
    mutationFn: ({ id, accept, finalContent }: { id: string; accept: boolean; finalContent?: string }) => {
      const active = new AbortController();
      reviewController.current = active;
      return api.request<CopilotSuggestion>(`/api/backend/ai/suggestions/${encodeURIComponent(id)}/${accept ? "accept" : "reject"}`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify(accept && finalContent ? { finalContent } : {}),
        signal: AbortSignal.any([active.signal, AbortSignal.timeout(15000)]),
      }, { suppressForbiddenNavigation: true });
    },
    onSuccess: (updated, variables) => {
      if (!mounted.current) return;
      if (variables.accept) onUse(variables.finalContent ?? parseDraft(updated.content)?.draft ?? "");
      setResult(null); setEditing(false);
      setNotice(variables.accept ? "Đã đưa bản nháp vào ô trả lời. Hãy kiểm tra trước khi gửi." : "Đã từ chối bản nháp.");
      void cache.invalidateQueries({ queryKey: key });
    },
  });
  if (!allowed) return null;
  if (status.isPending) return <p role="status">Đang kiểm tra Copilot…</p>;
  if (status.isError) return <Alert>{copilotErrorMessage(status.error)}</Alert>;
  if (!status.data?.enabled) return <p role="status">Chưa có gợi ý. AI chưa được bật.</p>;
  const suggestion = result ? result.suggestion : suggestions.data?.find(item => item.kind === "REPLY_DRAFT" && item.status === "PROPOSED");
  const draft = suggestion ? parseDraft(suggestion.content) : null;
  const failure = result && result.status !== "SUCCEEDED" ? copilotInvokeFailureMessage(result.status, result.errorCode) : null;
  const useDraft = () => {
    if (!suggestion || !draft) return;
    const next = editing ? text.trim() : draft.draft;
    if (!next || next.length > 4000) return;
    if (currentReply.trim() && !window.confirm("Ô trả lời đã có nội dung. Bạn muốn thay bằng bản nháp?")) return;
    review.mutate({ id: suggestion.id, accept: true, ...(editing ? { finalContent: next } : {}) });
  };
  return <section className="inbox-copilot" aria-labelledby="inbox-copilot-heading">
    <h3 id="inbox-copilot-heading">Copilot</h3>
    <Button variant="secondary" busy={invoke.isPending} disabled={review.isPending} onClick={() => invoke.mutate()}>Soạn bản nháp</Button>
    {invoke.isPending && <p role="status">Đang tạo bản nháp…</p>}
    {invoke.isError && <Alert>{copilotErrorMessage(invoke.error)}</Alert>}
    {suggestions.isError && <Alert>{copilotErrorMessage(suggestions.error)}</Alert>}
    {failure && <p role="status">Chưa có gợi ý. {failure}</p>}
    {notice && <p role="status">{notice}</p>}
    {draft && suggestion && <div>
      <p>Do AI tạo — cần kiểm tra</p>
      {editing ? <div className="field"><label htmlFor="inbox-copilot-edit">Chỉnh sửa bản nháp</label><textarea className="input" id="inbox-copilot-edit" maxLength={4000} value={text} onChange={event => setText(event.target.value)} /></div> : <p className="inbox-copilot-draft">{draft.draft}</p>}
      <p>Nguồn: {draft.sources.map(source => {
        const [type, id] = source.split(":");
        if (type === "MESSAGE") return <button key={source} type="button" onClick={() => { const target = document.getElementById(`inbox-message-${id}`); target?.scrollIntoView({ block: "center" }); target?.focus(); if (!target) setNotice("Tin nhắn nguồn chưa được tải trong danh sách."); }}>Tin nhắn</button>;
        if (type === "INTERACTION" || type === "OPPORTUNITY") return <Link key={source} href={`/app/${type === "INTERACTION" ? "tuong-tac" : "co-hoi"}/${encodeURIComponent(id)}`}>{type === "INTERACTION" ? "Tương tác" : "Cơ hội"}</Link>;
        return <span key={source}>Hồ sơ</span>;
      })}</p>
      <Button onClick={useDraft} busy={review.isPending} disabled={editing && !text.trim()}>{editing ? "Dùng bản đã sửa" : "Dùng bản nháp"}</Button>{" "}
      {!editing && <Button variant="secondary" disabled={review.isPending} onClick={() => { setText(draft.draft); setEditing(true); }}>Sửa</Button>}{" "}
      <Button variant="secondary" disabled={review.isPending} onClick={() => review.mutate({ id: suggestion.id, accept: false })}>Từ chối</Button>
      {review.isError && <Alert>{copilotErrorMessage(review.error)}</Alert>}
    </div>}
  </section>;
}
