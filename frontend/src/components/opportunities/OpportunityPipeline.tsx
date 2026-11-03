"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { DateInput } from "@/components/ui/DateInput";
import { ApiError } from "@/lib/api/client";
import { formatIsoDateAsDdMmYyyy, parseDdMmYyyy } from "@/lib/date-format";
import { canCreateOpportunity, canReadOpportunity, formatVnd, fromCents, getOpportunityOwners, listOpportunities, opportunityError, opportunityKey, stages, toCents, type OpportunityStage } from "@/lib/opportunities";

export function OpportunityPipeline() {
  const { user } = useSession(); const allowed = canReadOpportunity(user); const manager = user.roles.some(r => ["ADMIN", "MANAGER"].includes(r));
  const [filters, setFilters] = useState<Record<string, string>>({ page: "0", size: "20" }); const [ready, setReady] = useState(false);
  useEffect(() => { const timer = window.setTimeout(() => { const params = new URLSearchParams(window.location.search); const values: Record<string, string> = { page: "0", size: "20" }; for (const key of ["search", "stage", "customerId", "expectedCloseFrom", "expectedCloseTo", "ownerUserId", "page"]) { const value = params.get(key); if (value) values[key] = value; } setFilters(values); setReady(true); }, 0); return () => window.clearTimeout(timer); }, []);
  useEffect(() => { if (!ready) return; const query = new URLSearchParams(Object.entries(filters).filter(([key, value]) => Boolean(value) && (key !== "ownerUserId" || manager))); window.history.replaceState(window.history.state, "", `${window.location.pathname}?${query}`); }, [filters, ready, manager]);
  const query = new URLSearchParams(Object.entries(filters).filter(([key, value]) => Boolean(value) && (key !== "ownerUserId" || manager)));
  const owners = useQuery({ queryKey: [...opportunityKey(user), "owners"], queryFn: getOpportunityOwners, enabled: allowed && manager });
  const list = useQuery({ queryKey: [...opportunityKey(user), "list", query.toString()], queryFn: () => listOpportunities(query), enabled: allowed && ready });
  const change = (key: string, value: string) => setFilters(old => ({ ...old, [key]: value, page: key === "page" ? value : "0" }));
  if (!allowed) return <section className="offering-section"><EmptyState title="Bạn không có quyền truy cập">Liên hệ quản trị viên để được cấp quyền phù hợp.</EmptyState></section>;
  return <div className="opportunity-page"><header className="opportunity-heading"><div><p className="eyebrow">Bán hàng</p><h1>Cơ hội bán hàng</h1><p>Theo dõi cơ hội trong phạm vi được phân công.</p></div>{canCreateOpportunity(user) && <Link className="button button-primary" href="/app/co-hoi/them">Thêm cơ hội</Link>}</header>
    <section className="offering-section" aria-label="Tìm kiếm và bộ lọc"><div className="opportunity-filters">
      <div className="field"><label htmlFor="opp-search">Tìm cơ hội, khách hàng, sản phẩm</label><input id="opp-search" className="input" value={filters.search ?? ""} onChange={e => change("search", e.target.value)} /></div>
      <div className="field"><label htmlFor="opp-stage">Giai đoạn</label><select id="opp-stage" className="input" value={filters.stage ?? ""} onChange={e => change("stage", e.target.value)}><option value="">Tất cả</option>{Object.entries(stages).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></div>
      <div className="field"><label htmlFor="opp-customer">Mã khách hàng</label><input id="opp-customer" className="input" value={filters.customerId ?? ""} onChange={e => change("customerId", e.target.value)} /></div>
      <div className="field"><label htmlFor="opp-from">Dự kiến từ ngày</label>{ready && <DateInput id="opp-from" value={filters.expectedCloseFrom ? formatIsoDateAsDdMmYyyy(filters.expectedCloseFrom) : ""} onChange={value => { const iso = parseDdMmYyyy(value); if (iso || !value) change("expectedCloseFrom", iso ?? ""); }} />}</div>
      <div className="field"><label htmlFor="opp-to">Dự kiến đến ngày</label>{ready && <DateInput id="opp-to" value={filters.expectedCloseTo ? formatIsoDateAsDdMmYyyy(filters.expectedCloseTo) : ""} onChange={value => { const iso = parseDdMmYyyy(value); if (iso || !value) change("expectedCloseTo", iso ?? ""); }} />}</div>
      {manager && <div className="field"><label htmlFor="opp-owner">Người phụ trách</label><select id="opp-owner" className="input" value={filters.ownerUserId ?? ""} onChange={e => change("ownerUserId", e.target.value)}><option value="">Tất cả</option>{owners.data?.map(owner => <option key={owner.id} value={owner.id}>{owner.fullName}</option>)}</select>{owners.isError && <p>Không tải được người phụ trách.</p>}</div>}
    </div></section>
    {(!ready || list.isPending) && <section className="offering-section" aria-busy="true"><p role="status">Đang tải cơ hội…</p><div className="offering-skeleton" /></section>}
    {list.isError && <section className="offering-section">{list.error instanceof ApiError && list.error.status === 403 ? <Alert>{opportunityError(list.error)}</Alert> : <ErrorState inline message={opportunityError(list.error)} reset={() => void list.refetch()} />}</section>}
    {list.data && <><p role="status">Tổng số: {list.data.totalItems}</p>{list.data.items.length === 0 && <EmptyState title="Không có cơ hội phù hợp">Thử thay đổi bộ lọc hoặc tạo cơ hội mới.</EmptyState>}<div className="opportunity-board">{(Object.keys(stages) as OpportunityStage[]).map(stage => { const items = list.data!.items.filter(item => item.stage === stage); return <section className="opportunity-column" key={stage} aria-labelledby={`stage-${stage}`}><header><h2 id={`stage-${stage}`}>{stages[stage]}</h2><span className="opportunity-count">{items.length}</span></header><ul className="opportunity-card-list">{items.map(item => { const final = item.finalValue === null ? null : toCents(item.finalValue), expected = item.expectedValue === null ? null : toCents(item.expectedValue); return <li className="opportunity-card" key={item.id}><h3><Link href={`/app/co-hoi/${item.id}`}>{item.title}</Link></h3><p className="opportunity-customer">{item.customer?.name ?? item.lead?.name ?? "Chưa cập nhật"}</p><dl className="opportunity-card-summary"><div><dt>Người phụ trách</dt><dd>{item.owner.name}</dd></div><div><dt>Dự kiến</dt><dd>{formatVnd(item.expectedValue)}</dd></div><div><dt>Ngày dự kiến</dt><dd>{item.expectedCloseDate ? formatIsoDateAsDdMmYyyy(item.expectedCloseDate) : "Chưa cập nhật"}</dd></div>{item.stage === "WON" && <><div><dt>Giá trị chốt</dt><dd>{formatVnd(item.finalValue)}</dd></div><div><dt>Chênh lệch</dt><dd>{final !== null && expected !== null ? formatVnd(fromCents(final - expected)) : "Chưa cập nhật"}</dd></div></>}</dl></li>; })}</ul>{!items.length && <p className="opportunity-column-empty">Chưa có cơ hội.</p>}</section>; })}</div><footer className="offering-pagination"><p>Trang {list.data.page + 1} / {Math.max(1, list.data.totalPages)}</p><div><Button variant="secondary" disabled={list.data.page <= 0} onClick={() => change("page", String(list.data!.page - 1))}>Trước</Button><Button variant="secondary" disabled={list.data.page + 1 >= list.data.totalPages} onClick={() => change("page", String(list.data!.page + 1))}>Sau</Button></div></footer></>}
  </div>;
}
