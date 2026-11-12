import type { QueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { ApiError } from "@/lib/api/client";
import type { SessionUser } from "@/lib/session/types";

export type AiStatusResponse = {
  enabled: boolean;
  provider?: string;
  remainingBudgetUsd?: number;
};

export type CopilotPoint = {
  text: string;
  sources: string[];
};

export type CopilotSection = {
  heading: string;
  points: CopilotPoint[];
};

export type CopilotStructuredContent = {
  sections: CopilotSection[];
};

export type CopilotEvidence = {
  sources: string[];
  droppedPoints: number;
};

export type CopilotSuggestionStatus = "PROPOSED" | "ACCEPTED" | "EDITED" | "REJECTED" | "EXPIRED";

export type CopilotSuggestion = {
  id: string;
  invocationId: string;
  kind: "C360_SUMMARY" | "HANDOVER_BRIEF" | string;
  subjectType: string;
  subjectId: string;
  content: string;
  evidence: string | null;
  status: CopilotSuggestionStatus;
  finalContent: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  createdAt: string;
};

export type CopilotInvokeStatus = "SUCCEEDED" | "DISABLED" | "BLOCKED_BUDGET" | "FAILED";

export type CopilotInvokeResponse = {
  status: CopilotInvokeStatus;
  errorCode?: string | null;
  reused: boolean;
  invocationId: string;
  suggestion: CopilotSuggestion | null;
};

export const copilotKey = (user: SessionUser) => ["copilot", user.organization.id, user.id] as const;
export const aiStatusKey = (user: SessionUser) => [...copilotKey(user), "status"] as const;
export const customerSuggestionsKey = (user: SessionUser, customerId: string) =>
  [...copilotKey(user), "suggestions", customerId] as const;

export function invalidateCustomerCopilot(cache: QueryClient, user: SessionUser, customerId: string) {
  void cache.invalidateQueries({ queryKey: customerSuggestionsKey(user, customerId) });
}

export const canUseAiCopilot = (user: SessionUser): boolean =>
  user.permissions.includes("ai.assist.use") && !user.roles.includes("DATA_STAFF");

export const canReassignCustomer = (user: SessionUser): boolean =>
  user.permissions.includes("customer.write") &&
  !user.roles.includes("DATA_STAFF") &&
  (user.roles.length === 0 || user.roles.some(role => role === "ADMIN" || role === "MANAGER"));

// Above the BFF AI proxy timeout (70s) so the proxy reports a slow provider instead of the browser aborting first.
const COPILOT_REQUEST_TIMEOUT_MS = 75000;

export const getAiStatus = () =>
  api.request<AiStatusResponse>("/api/backend/ai/status", undefined, { suppressForbiddenNavigation: true });

export const summarizeCustomer = (customerId: string, force = false) =>
  api.request<CopilotInvokeResponse>(
    `/api/backend/ai/copilot/customers/${encodeURIComponent(customerId)}/summary`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ force }),
      signal: AbortSignal.timeout(COPILOT_REQUEST_TIMEOUT_MS),
    },
    { suppressForbiddenNavigation: true },
  );

export const generateHandoverBrief = (customerId: string, force = false) =>
  api.request<CopilotInvokeResponse>(
    `/api/backend/ai/copilot/customers/${encodeURIComponent(customerId)}/handover-brief`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ force }),
      signal: AbortSignal.timeout(COPILOT_REQUEST_TIMEOUT_MS),
    },
    { suppressForbiddenNavigation: true },
  );

export const listCustomerSuggestions = (customerId: string) =>
  api.request<CopilotSuggestion[]>(
    `/api/backend/ai/suggestions?subjectType=CUSTOMER&subjectId=${encodeURIComponent(customerId)}`,
    undefined,
    { suppressForbiddenNavigation: true },
  );

export const acceptSuggestion = (suggestionId: string, finalContent?: string | null) =>
  api.request<CopilotSuggestion>(
    `/api/backend/ai/suggestions/${encodeURIComponent(suggestionId)}/accept`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(finalContent && finalContent.trim() ? { finalContent: finalContent.trim() } : {}),
    },
    { suppressForbiddenNavigation: true },
  );

export const rejectSuggestion = (suggestionId: string) =>
  api.request<CopilotSuggestion>(
    `/api/backend/ai/suggestions/${encodeURIComponent(suggestionId)}/reject`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({}),
    },
    { suppressForbiddenNavigation: true },
  );

export function parseCopilotContent(raw: string | null | undefined): CopilotStructuredContent | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!parsed || typeof parsed !== "object" || !Array.isArray((parsed as { sections?: unknown }).sections)) {
      return null;
    }
    const sections: CopilotSection[] = [];
    for (const sec of (parsed as { sections: unknown[] }).sections) {
      if (!sec || typeof sec !== "object") continue;
      const heading = (sec as { heading?: unknown }).heading;
      const pointsRaw = (sec as { points?: unknown }).points;
      if (typeof heading !== "string" || !Array.isArray(pointsRaw)) continue;
      const points: CopilotPoint[] = [];
      for (const pt of pointsRaw) {
        if (!pt || typeof pt !== "object") continue;
        const text = (pt as { text?: unknown }).text;
        const sources = (pt as { sources?: unknown }).sources;
        if (typeof text === "string" && Array.isArray(sources)) {
          points.push({
            text,
            sources: sources.filter((s): s is string => typeof s === "string"),
          });
        }
      }
      if (points.length > 0) {
        sections.push({ heading, points });
      }
    }
    return sections.length > 0 ? { sections } : null;
  } catch {
    return null;
  }
}

export function parseCopilotEvidence(raw: string | null | undefined): CopilotEvidence | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!parsed || typeof parsed !== "object") return null;
    const sourcesRaw = (parsed as { sources?: unknown }).sources;
    const droppedRaw = (parsed as { droppedPoints?: unknown }).droppedPoints;
    return {
      sources: Array.isArray(sourcesRaw) ? sourcesRaw.filter((s): s is string => typeof s === "string") : [],
      droppedPoints: typeof droppedRaw === "number" ? droppedRaw : 0,
    };
  } catch {
    return null;
  }
}

export function formatCopilotTextForEdit(structured: CopilotStructuredContent | null, rawContent: string): string {
  if (!structured) return rawContent;
  return structured.sections
    .map(sec => `${sec.heading}:\n${sec.points.map(pt => `- ${pt.text}`).join("\n")}`)
    .join("\n\n");
}

export function sourceAnchorId(sourceCode: string): string | null {
  const code = sourceCode.trim();
  if (code === "PROFILE") return "c360-source-profile";
  if (code.startsWith("INTERACTION:")) return `c360-source-interaction-${code.slice("INTERACTION:".length)}`;
  if (code.startsWith("TASK:")) return `c360-source-task-${code.slice("TASK:".length)}`;
  if (code.startsWith("OPPORTUNITY:")) return `c360-source-opportunity-${code.slice("OPPORTUNITY:".length)}`;
  if (code.startsWith("HANDOVER:")) return `c360-source-handover-${code.slice("HANDOVER:".length)}`;
  return null;
}

export function sourceChipLabel(sourceCode: string): string {
  const code = sourceCode.trim();
  if (code === "PROFILE") return "Hồ sơ";
  if (code.startsWith("INTERACTION:")) return "Tương tác";
  if (code.startsWith("TASK:")) return "Nhiệm vụ";
  if (code.startsWith("OPPORTUNITY:")) return "Cơ hội";
  if (code.startsWith("HANDOVER:")) return "Bàn giao";
  return code;
}

export const copilotKindLabels: Record<string, string> = {
  C360_SUMMARY: "Tóm tắt hồ sơ",
  HANDOVER_BRIEF: "Hồ sơ bàn giao",
};

export const copilotStatusLabels: Record<CopilotSuggestionStatus, string> = {
  PROPOSED: "Đề xuất",
  ACCEPTED: "Đã chấp nhận",
  EDITED: "Đã chỉnh sửa",
  REJECTED: "Đã từ chối",
  EXPIRED: "Hết hạn",
};

export function copilotInvokeFailureMessage(status: CopilotInvokeStatus, errorCode?: string | null): string | null {
  if (status === "SUCCEEDED" || status === "DISABLED") return null;
  if (status === "BLOCKED_BUDGET") return "Đã hết hạn mức AI tháng này.";
  switch (errorCode) {
    case "RATE_LIMIT": return "Dịch vụ AI đang giới hạn số lượt gọi. Vui lòng thử lại sau ít phút.";
    case "TIMEOUT": return "Dịch vụ AI phản hồi quá chậm. Vui lòng thử lại.";
    case "NETWORK": return "Không kết nối được tới dịch vụ AI. Vui lòng thử lại.";
    case "REFUSAL": return "Dịch vụ AI từ chối xử lý nội dung này.";
    case "INVALID_OUTPUT": return "AI trả về kết quả chưa hợp lệ nên đã bị loại. Vui lòng thử lại.";
    case "PROVIDER_ERROR": return "Dịch vụ AI tạm thời gặp sự cố. Vui lòng thử lại sau.";
    default: return "Chưa tạo được gợi ý, thử lại sau.";
  }
}

export function copilotErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return "Chưa tạo được gợi ý, thử lại sau.";
  if (error.code === "NETWORK_ERROR") return "Không thể kết nối mạng. Vui lòng kiểm tra kết nối và thử lại.";
  if (error.code === "FORBIDDEN_PERMISSION" || error.code === "FORBIDDEN_SCOPE" || error.status === 403) {
    return "Bạn không có quyền sử dụng AI Copilot trên hồ sơ này.";
  }
  if (error.code === "AI_SUGGESTION_REVIEWED") {
    return "Gợi ý này đã được duyệt trước đó.";
  }
  return error.message || "Chưa tạo được gợi ý, thử lại sau.";
}
