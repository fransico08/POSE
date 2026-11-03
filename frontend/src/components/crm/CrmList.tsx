"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { Alert } from "@/components/ui/Alert";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { ApiError } from "@/lib/api/client";
import { getLeads } from "@/lib/leads";
import { getCustomers } from "@/lib/customers";
import { canCreateCrm, canReadCrm, crmBase, crmErrorMessage, crmKey, crmTitle, customerStatuses, customerTypes, displayValue, getScopedOfferings, hasRole, leadStatuses, sources, type CrmKind } from "@/lib/crm";

const keys = ["search", "status", "offeringId", "customerType", "ownerUserId", "page", "size"] as const;
export function CrmList({ kind }: { kind: CrmKind }) {
  const { user } = useSession();
  const [filters, setFilters] = useState<Record<string, string>>({ page: "0", size: "20" });
  const [search, setSearch] = useState("");
  const [ownerDraft, setOwnerDraft] = useState("");
  const [ownerError, setOwnerError] = useState("");
  const [ready, setReady] = useState(false);
  const manager = hasRole(user, ["ADMIN", "MANAGER"]);
  const allowed = canReadCrm(user, kind);
  useEffect(() => {
    function restore() {
      const query = new URLSearchParams(window.location.search);
      const next: Record<string, string> = { page: "0", size: "20" };
      keys.forEach(key => { const value = query.get(key); if (value && (key !== "ownerUserId" || manager)) next[key] = value; });
      setFilters(next); setSearch(next.search ?? ""); setOwnerDraft(next.ownerUserId ?? ""); setReady(true);
    }
    restore(); window.addEventListener("popstate", restore); return () => window.removeEventListener("popstate", restore);
  }, [manager]);
  useEffect(() => {
    if (!ready) return;
    const timer = window.setTimeout(() => setFilters(current => current.search === search.trim() ? current : { ...current, search: search.trim(), page: "0" }), 350);
    return () => window.clearTimeout(timer);
  }, [search, ready]);
  useEffect(() => {
    if (!ready) return;
    const query = new URLSearchParams();
    keys.forEach(key => { if (filters[key] && !(key === "ownerUserId" && !manager) && !(kind === "customer" && key === "offeringId") && !(kind === "lead" && key === "customerType")) query.set(key, filters[key]); });
    window.history.replaceState(window.history.state, "", `${window.location.pathname}?${query}`);
  }, [filters, ready, manager, kind]);
  const query = new URLSearchParams(Object.entries(filters).filter(([key, value]) => Boolean(value) && !(key === "ownerUserId" && !manager) && !(kind === "customer" && key === "offeringId") && !(kind === "lead" && key === "customerType")));
  const list = useQuery({ queryKey: [...crmKey(kind, user), "list", query.toString()], queryFn: () => kind === "lead" ? getLeads(query) : getCustomers(query), enabled: allowed && ready });
  const mayReadOfferings = (user.roles.includes("SALES_REP") && user.permissions.includes("offering.read")) || (manager && user.permissions.includes("offering.manage"));
  const offerings = useQuery({ queryKey: ["crm-offering-filter", user.organization.id, user.id], queryFn: () => getScopedOfferings(false), enabled: allowed && kind === "lead" && mayReadOfferings });
  const change = (name: string, value: string) => setFilters(current => ({ ...current, [name]: value, page: name === "page" ? value : "0" }));
  function applyOwner() {
    const value = ownerDraft.trim();
    if (value && !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)) { setOwnerError("Nhập mã UUID hợp lệ."); return; }
    setOwnerError(""); change("ownerUserId", value);
  }
  if (!allowed) return <section className="offering-section"><EmptyState title="Bạn không có quyền truy cập">Liên hệ quản trị viên để được cấp quyền phù hợp.</EmptyState></section>;
  const statuses = kind === "lead" ? leadStatuses : customerStatuses;
  const hasFilters = Boolean(filters.search || filters.status || filters.offeringId || filters.customerType || filters.ownerUserId);
  return <div className="offering-page">
    <header className="offering-heading"><div><p className="eyebrow">Bán hàng</p><h1>{crmTitle(kind)}</h1><p>Tra cứu hồ sơ trong phạm vi được phân công.</p></div>{canCreateCrm(user, kind) && <Link className="button button-primary" href={`${crmBase(kind)}/them`}>{kind === "lead" ? "Thêm Lead" : "Thêm khách hàng"}</Link>}</header>
    <section className="offering-section" aria-label="Tìm kiếm và bộ lọc"><div className="offering-filters">
      <div className="field offering-search-field"><label htmlFor="crm-search">Tìm theo tên, email hoặc điện thoại</label><input id="crm-search" className="input" value={search} maxLength={200} onChange={event => setSearch(event.target.value)} /></div>
      <div className="field"><label htmlFor="crm-status">Trạng thái</label><select className="input" id="crm-status" value={filters.status ?? ""} onChange={event => change("status", event.target.value)}><option value="">Tất cả</option>{Object.entries(statuses).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></div>
      {kind === "lead" && <div className="field"><label htmlFor="crm-offering">Sản phẩm/dịch vụ</label><select className="input" id="crm-offering" value={filters.offeringId ?? ""} onChange={event => change("offeringId", event.target.value)}><option value="">Tất cả</option>{offerings.data?.map(item => <option value={item.id} key={item.id}>{item.name}</option>)}</select>{offerings.isError && <p>Không tải được sản phẩm/dịch vụ để lọc.</p>}</div>}
      {kind === "customer" && <div className="field"><label htmlFor="crm-type">Loại khách hàng</label><select className="input" id="crm-type" value={filters.customerType ?? ""} onChange={event => change("customerType", event.target.value)}><option value="">Tất cả</option>{Object.entries(customerTypes).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></div>}
      {manager && <div className="field"><label htmlFor="crm-owner">Mã người phụ trách</label><input className="input" id="crm-owner" value={ownerDraft} aria-invalid={Boolean(ownerError)} aria-describedby={ownerError ? "crm-owner-error" : undefined} onChange={event => { setOwnerDraft(event.target.value); setOwnerError(""); }} onBlur={applyOwner} onKeyDown={event => { if (event.key === "Enter") { event.preventDefault(); applyOwner(); } }} placeholder="UUID người phụ trách" />{ownerError && <p id="crm-owner-error" className="offering-field-error">{ownerError}</p>}</div>}
      {hasFilters && <Button variant="ghost" onClick={() => { setSearch(""); setOwnerDraft(""); setOwnerError(""); setFilters({ page: "0", size: "20" }); }}>Xóa bộ lọc</Button>}
    </div></section>
    {(!ready || list.isPending) && <section className="offering-section" aria-busy="true"><p role="status">Đang tải danh sách…</p><div className="offering-skeleton" /><div className="offering-skeleton" /></section>}
    {list.isError && <section className="offering-section">{list.error instanceof ApiError && list.error.status === 403 ? <Alert>{crmErrorMessage(list.error)}</Alert> : <ErrorState inline message={crmErrorMessage(list.error)} reset={() => void list.refetch()} />}</section>}
    {list.data && !list.data.items.length && <section className="offering-section"><EmptyState title={hasFilters ? "Không tìm thấy kết quả" : `Chưa có ${kind === "lead" ? "Lead" : "khách hàng"}`}>{hasFilters ? "Thử đổi từ khóa hoặc xóa bộ lọc." : "Chưa có hồ sơ trong phạm vi của bạn."}</EmptyState></section>}
    {list.data && list.data.items.length > 0 && <section className="offering-section"><div className="offering-table-scroll"><table className="offering-table"><caption className="sr-only">Danh sách {crmTitle(kind)}</caption><thead><tr><th scope="col">Tên</th><th scope="col">Liên hệ</th><th scope="col">{kind === "lead" ? "Nguồn" : "Loại"}</th><th scope="col">Sản phẩm/dịch vụ</th><th scope="col">Người phụ trách</th><th scope="col">Trạng thái</th><th scope="col">Cập nhật</th></tr></thead><tbody>{list.data.items.map(item => <tr key={item.id}><th scope="row"><Link className="offering-name" href={`${crmBase(kind)}/${item.id}`}>{item.displayName}</Link></th><td>{displayValue(item.email)}<br />{displayValue(item.phone)}</td><td>{displayValue((kind === "lead" ? sources[item.source ?? ""] : customerTypes[item.customerType ?? ""]))}</td><td>{displayValue(item.offeringName)}</td><td>{displayValue(item.ownerName)}</td><td><Badge>{statuses[item.status] ?? item.status}</Badge></td><td>{new Date(item.updatedAt).toLocaleString("vi-VN")}</td></tr>)}</tbody></table></div><div className="offering-mobile-cards">{list.data.items.map(item => <article className="offering-mobile-card" key={item.id}><header><Link className="offering-name" href={`${crmBase(kind)}/${item.id}`}>{item.displayName}</Link><Badge>{statuses[item.status] ?? item.status}</Badge></header><dl><div><dt>Liên hệ</dt><dd>{displayValue(item.email)} · {displayValue(item.phone)}</dd></div><div><dt>{kind === "lead" ? "Nguồn" : "Loại"}</dt><dd>{displayValue(kind === "lead" ? sources[item.source ?? ""] : customerTypes[item.customerType ?? ""])}</dd></div><div><dt>Sản phẩm/dịch vụ</dt><dd>{displayValue(item.offeringName)}</dd></div><div><dt>Người phụ trách</dt><dd>{displayValue(item.ownerName)}</dd></div><div><dt>Cập nhật</dt><dd>{new Date(item.updatedAt).toLocaleString("vi-VN")}</dd></div></dl></article>)}</div><footer className="offering-pagination"><p>Trang {list.data.page + 1} / {Math.max(1, list.data.totalPages)}</p><div><Button variant="secondary" disabled={list.data.page <= 0} onClick={() => change("page", String(list.data.page - 1))}>Trước</Button><Button variant="secondary" disabled={list.data.page + 1 >= list.data.totalPages} onClick={() => change("page", String(list.data.page + 1))}>Sau</Button></div></footer></section>}
  </div>;
}
