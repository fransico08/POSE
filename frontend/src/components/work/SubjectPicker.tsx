"use client";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useSession } from "@/components/session/SessionGate";
import { getCustomers } from "@/lib/customers";
import { api } from "@/lib/api";
import { ApiError } from "@/lib/api/client";
import type { CrmPage } from "@/lib/crm";
import { listOpportunities } from "@/lib/opportunities";
import { useDebouncedValue } from "@/lib/use-debounced-value";

export function SubjectPicker({ kind, id, opportunityId, selectedName, onChange, onOpportunity, error, opportunityError }: { kind: "customer" | "lead"; id: string; opportunityId?: string; selectedName?: string; onChange: (kind: "customer" | "lead", id: string) => void; onOpportunity?: (id: string) => void; error?: string; opportunityError?: string }) {
  const { user } = useSession(); const [search, setSearch] = useState(""); const term = useDebouncedValue(search);
  const records = useQuery({ queryKey: ["work-subject", user.organization.id, user.id, kind, term], queryFn: () => kind === "customer" ? getCustomers(new URLSearchParams({ search: term, size: "20" }), { suppressForbiddenNavigation: true }) : api.request<CrmPage>(`/api/backend/leads?${new URLSearchParams({ search: term, size: "20" })}`, undefined, { suppressForbiddenNavigation: true }), retry: false });
  const opportunities = useQuery({ queryKey: ["work-opportunity", user.organization.id, user.id, kind, id], queryFn: async () => { const items = []; let page = 0, totalPages = 1; while (page < totalPages) { const result = await listOpportunities(new URLSearchParams({ [kind === "customer" ? "customerId" : "leadId"]: id, size: "100", page: String(page) })); items.push(...result.items); page += 1; totalPages = result.totalPages; } return items; }, enabled: Boolean(onOpportunity && id && user.permissions.includes("opportunity.read")), retry: false });
  return <div className="opportunity-form-grid">
    <div className="field"><label htmlFor="work-kind">Loại đối tượng</label><select id="work-kind" className="input" value={kind} onChange={event => onChange(event.target.value as "customer" | "lead", "")}><option value="customer">Khách hàng</option><option value="lead">Lead</option></select></div>
    <div className="field"><label htmlFor="work-search">Tìm đối tượng</label><input id="work-search" className="input" value={search} onChange={event => setSearch(event.target.value)} /></div>
    <div className="field"><label htmlFor="work-subject-picker">{kind === "customer" ? "Khách hàng" : "Lead"}</label><select id="work-subject-picker" className="input" value={id} aria-invalid={Boolean(error)} aria-describedby={error ? "work-subject-error" : undefined} onChange={event => onChange(kind, event.target.value)}><option value="">Chọn đối tượng</option>{id && !records.data?.items.some(item => item.id === id) && <option value={id}>{selectedName ?? "Đối tượng đã chọn"}</option>}{records.data?.items.map(item => <option key={item.id} value={item.id}>{item.displayName}</option>)}</select>{error && <p id="work-subject-error">{error}</p>}{records.isError && <p>{records.error instanceof ApiError && records.error.status === 403 ? "Bạn không có quyền tải danh sách đối tượng; bộ chọn chưa khả dụng." : "Không tải được danh sách đối tượng."}</p>}</div>
    {onOpportunity && <div className="field"><label htmlFor="work-opportunity">Cơ hội bán hàng (tùy chọn)</label><select id="work-opportunity" className="input" value={opportunityId ?? ""} aria-invalid={Boolean(opportunityError)} aria-describedby={opportunityError ? "work-opportunity-error" : undefined} onChange={event => onOpportunity(event.target.value)} disabled={!id || !user.permissions.includes("opportunity.read")}><option value="">Không chọn</option>{opportunities.data?.map(item => <option key={item.id} value={item.id}>{item.title}</option>)}</select>{opportunityError && <p id="work-opportunity-error">{opportunityError}</p>}{opportunities.isError && <p>Không tải được cơ hội.</p>}</div>}
  </div>;
}
