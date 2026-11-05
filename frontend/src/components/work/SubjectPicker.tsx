"use client";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useSession } from "@/components/session/SessionGate";
import { getCustomer, getCustomers } from "@/lib/customers";
import { getLead } from "@/lib/leads";
import { api } from "@/lib/api";
import { ApiError } from "@/lib/api/client";
import type { CrmPage, CrmRecord } from "@/lib/crm";
import { isUuid, listOpportunities } from "@/lib/opportunities";
import { useDebouncedValue } from "@/lib/use-debounced-value";

export function parseInitialWorkSubject(search: string): { kind: "customer" | "lead"; id: string; notice: string } {
  const params = new URLSearchParams(search);
  const customerId = params.get("customerId")?.trim() ?? "";
  const leadId = params.get("leadId")?.trim() ?? "";
  if (!customerId && !leadId) return { kind: "customer", id: "", notice: "" };
  if (customerId && leadId) return { kind: "customer", id: "", notice: "Đường dẫn chỉ được chọn một đối tượng (khách hàng hoặc Lead); hãy chọn lại." };
  const kind = leadId ? "lead" : "customer";
  const raw = leadId || customerId;
  if (!isUuid(raw)) return { kind, id: "", notice: "Mã đối tượng trên đường dẫn không hợp lệ; hãy chọn khách hàng hoặc Lead." };
  return { kind, id: raw, notice: "" };
}

export function SubjectPicker({ kind, id, opportunityId, selectedName, onChange, onOpportunity, error, opportunityError, onInvalidInitial }: { kind: "customer" | "lead"; id: string; opportunityId?: string; selectedName?: string; onChange: (kind: "customer" | "lead", id: string) => void; onOpportunity?: (id: string) => void; error?: string; opportunityError?: string; onInvalidInitial?: (message: string) => void }) {
  const { user } = useSession(); const [search, setSearch] = useState(""); const term = useDebouncedValue(search);
  const records = useQuery({ queryKey: ["work-subject", user.organization.id, user.id, kind, term], queryFn: async () => {
    if (kind === "customer") return getCustomers(new URLSearchParams({ search: term, size: "20" }), { suppressForbiddenNavigation: true });
    const pages = await Promise.all(["NEW", "QUALIFIED"].map(status => api.request<CrmPage>(`/api/backend/leads?${new URLSearchParams({ search: term, size: "20", status })}`, undefined, { suppressForbiddenNavigation: true })));
    return { ...pages[0], items: Array.from(new Map(pages.flatMap(page => page.items).map(item => [item.id, item])).values()).sort((a, b) => a.displayName.localeCompare(b.displayName, "vi")) };
  }, retry: false });
  const inList = Boolean(id && records.data?.items.some(item => item.id === id));
  const selected = useQuery({ queryKey: ["work-subject-selected", user.organization.id, user.id, kind, id], queryFn: (): Promise<CrmRecord> => kind === "customer" ? getCustomer(id, { suppressForbiddenNavigation: true }) : getLead(id, { suppressForbiddenNavigation: true }), enabled: Boolean(id && isUuid(id) && records.isSuccess && !inList), retry: false });
  const closedLead = Boolean(kind === "lead" && selected.data && ["CONVERTED", "DISQUALIFIED"].includes(selected.data.status));
  useEffect(() => {
    if (!id) return;
    if (selected.isError || closedLead) {
      const message = closedLead ? "Lead trên đường dẫn đã đóng, không thể tạo mới tương tác hoặc nhiệm vụ; hãy chọn đối tượng khác." : "Đối tượng trên đường dẫn không tồn tại hoặc nằm ngoài phạm vi truy cập; hãy chọn lại.";
      const timer = window.setTimeout(() => { onChange(kind, ""); onInvalidInitial?.(message); }, 0);
      return () => window.clearTimeout(timer);
    }
  }, [closedLead, id, kind, onChange, onInvalidInitial, selected.isError]);
  const validSelected = Boolean(id && (inList || (selected.isSuccess && !closedLead)));
  const opportunities = useQuery({ queryKey: ["work-opportunity", user.organization.id, user.id, kind, id], queryFn: async () => { const items = []; let page = 0, totalPages = 1; while (page < totalPages) { const result = await listOpportunities(new URLSearchParams({ [kind === "customer" ? "customerId" : "leadId"]: id, size: "100", page: String(page) })); items.push(...result.items); page += 1; totalPages = result.totalPages; } return items; }, enabled: Boolean(onOpportunity && validSelected && user.permissions.includes("opportunity.read")), retry: false });
  const resolvedName = selectedName ?? selected.data?.displayName;
  return <div className="opportunity-form-grid">
    <div className="field"><label htmlFor="work-kind">Loại đối tượng</label><select id="work-kind" className="input" value={kind} onChange={event => onChange(event.target.value as "customer" | "lead", "")}><option value="customer">Khách hàng</option><option value="lead">Lead</option></select></div>
    <div className="field"><label htmlFor="work-search">Tìm đối tượng</label><input id="work-search" className="input" value={search} onChange={event => setSearch(event.target.value)} /></div>
    <div className="field"><label htmlFor="work-subject-picker">{kind === "customer" ? "Khách hàng" : "Lead"}</label><select id="work-subject-picker" className="input" value={id} aria-invalid={Boolean(error)} aria-describedby={error ? "work-subject-error" : undefined} onChange={event => onChange(kind, event.target.value)}><option value="">Chọn đối tượng</option>{id && !inList && !selected.isError && !closedLead && <option value={id}>{resolvedName ?? "Đối tượng đã chọn"}</option>}{records.data?.items.map(item => <option key={item.id} value={item.id}>{item.displayName}</option>)}</select>{error && <p id="work-subject-error">{error}</p>}{records.isError && <p>{records.error instanceof ApiError && records.error.status === 403 ? "Bạn không có quyền tải danh sách đối tượng; bộ chọn chưa khả dụng." : "Không tải được danh sách đối tượng."}</p>}</div>
    {onOpportunity && <div className="field"><label htmlFor="work-opportunity">Cơ hội bán hàng (tùy chọn)</label><select id="work-opportunity" className="input" value={opportunityId ?? ""} aria-invalid={Boolean(opportunityError)} aria-describedby={opportunityError ? "work-opportunity-error" : undefined} onChange={event => onOpportunity(event.target.value)} disabled={!id || !user.permissions.includes("opportunity.read")}><option value="">Không chọn</option>{opportunities.data?.map(item => <option key={item.id} value={item.id}>{item.title}</option>)}</select>{opportunityError && <p id="work-opportunity-error">{opportunityError}</p>}{opportunities.isError && <p>Không tải được cơ hội.</p>}</div>}
  </div>;
}
