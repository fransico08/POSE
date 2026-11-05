"use client";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { Alert } from "@/components/ui/Alert";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { customerStatuses, customerTypes } from "@/lib/crm";
import { getCustomers } from "@/lib/customers";
import { canReadCustomer360, canReconcileCustomer360, customer360Error, customer360Key, getReconciliation, reconciliationColumns } from "@/lib/customer360";
import { useDebouncedValue } from "@/lib/use-debounced-value";

export function Customer360Hub() {
  const { user } = useSession(); const headingRef = useRef<HTMLHeadingElement>(null); const reconciliationHeadingRef = useRef<HTMLHeadingElement>(null);
  const [search, setSearch] = useState(""); const term = useDebouncedValue(search); const [page, setPage] = useState(0);
  const allowed = canReadCustomer360(user), isDataStaff = user.roles.includes("DATA_STAFF");
  const canSearch = allowed && user.permissions.includes("customer.read") && !isDataStaff;
  const canReconcile = canReconcileCustomer360(user);
  useEffect(() => { document.title = "Hồ sơ khách hàng 360 | POSE CRM"; }, []);
  const customers = useQuery({
    queryKey: [...customer360Key(user), "customers", term],
    queryFn: () => getCustomers(new URLSearchParams({ search: term, size: "20" }), { suppressForbiddenNavigation: true }),
    enabled: canSearch,
  });
  const reconciliation = useQuery({
    queryKey: [...customer360Key(user), "reconciliation", page],
    queryFn: () => getReconciliation(page, 20),
    enabled: canReconcile,
    placeholderData: keepPreviousData,
  });
  if (!allowed) return <section className="offering-section"><EmptyState title="Bạn không có quyền truy cập">Liên hệ quản trị viên để được cấp quyền phù hợp.</EmptyState></section>;
  const formatCols = (cols: string[]) => cols.map(c => reconciliationColumns[c] ? `${reconciliationColumns[c]} (${c})` : c).join(", ");
  return <div className="offering-page">
    <header className="offering-detail-heading"><div><h1 ref={headingRef} tabIndex={-1}>Hồ sơ khách hàng 360</h1><p>Tra cứu tổng hợp hồ sơ khách hàng và đối soát toàn vẹn dữ liệu theo quyền.</p></div></header>
    {isDataStaff && <Alert tone="info">Vai trò Nhân viên dữ liệu dùng trang này để đối soát. Hồ sơ 360 mở được từ danh sách khách hàng lệch.</Alert>}
    {canSearch && <section className="offering-section" aria-labelledby="c360-search-title">
      <h2 id="c360-search-title">Tra cứu khách hàng trong phạm vi</h2>
      <div className="opportunity-form-grid">
        <div className="field"><label htmlFor="c360-search">Tìm khách hàng</label><input id="c360-search" className="input" value={search} onChange={e => setSearch(e.target.value)} placeholder="Nhập tên, email hoặc số điện thoại…" /></div>
      </div>
      {customers.isPending ? <p role="status">Đang tải danh sách khách hàng…</p> : customers.isError ? <div><Alert>{customer360Error(customers.error)}</Alert><Button variant="secondary" onClick={() => void customers.refetch()}>Thử lại</Button></div> : !customers.data.items.length ? <EmptyState title="Không tìm thấy khách hàng phù hợp">Thử từ khóa tìm kiếm khác.</EmptyState> : <>
        <div className="offering-table-scroll"><table className="offering-table"><caption className="sr-only">Danh sách khách hàng</caption><thead><tr><th>Khách hàng</th><th>Loại</th><th>Trạng thái</th><th>Người phụ trách</th><th>Hồ sơ 360</th></tr></thead><tbody>{customers.data.items.map(c => <tr key={c.id}><td>{c.displayName}</td><td>{customerTypes[c.customerType ?? ""] ?? (c.customerType || "Chưa cập nhật")}</td><td><Badge>{customerStatuses[c.status] ?? c.status}</Badge></td><td>{c.ownerName || "Chưa cập nhật"}</td><td><Link className="offering-row-link" href={`/app/khach-hang-360/${encodeURIComponent(c.id)}`}>Mở hồ sơ 360</Link></td></tr>)}</tbody></table></div>
        <div className="offering-mobile-cards">{customers.data.items.map(c => <article className="offering-mobile-card" key={c.id}><h3><Link href={`/app/khach-hang-360/${encodeURIComponent(c.id)}`}>{c.displayName}</Link></h3><p>{customerTypes[c.customerType ?? ""] ?? (c.customerType || "Chưa cập nhật")} · {customerStatuses[c.status] ?? c.status}</p><p>Người phụ trách: {c.ownerName || "Chưa cập nhật"}</p></article>)}</div>
      </>}
    </section>}
    {canReconcile && <section className="offering-section" aria-labelledby="c360-reconcile-title">
      <h2 id="c360-reconcile-title" ref={reconciliationHeadingRef} tabIndex={-1}>Đối soát dữ liệu</h2>
      {reconciliation.isPending ? <p role="status">Đang tải đối soát dữ liệu…</p> : reconciliation.isError ? <div><Alert>{customer360Error(reconciliation.error)}</Alert><Button variant="secondary" onClick={() => void reconciliation.refetch()}>Thử lại</Button></div> : <>
        <dl className="offering-detail-grid">
          <div><dt>Khách hàng khớp</dt><dd>{reconciliation.data.matched} khách hàng</dd></div>
          <div><dt>Khách hàng lệch</dt><dd>{reconciliation.data.mismatched} khách hàng</dd></div>
          {Object.entries(reconciliation.data.totals).map(([col, val]) => <div key={col}><dt>{reconciliationColumns[col] ?? col} ({col})</dt><dd>{val}</dd></div>)}
        </dl>
        {reconciliation.data.mismatched === 0 ? <div role="status" className="offering-notice"><p>Dữ liệu khớp với view tổng hợp</p>{isDataStaff && <p>Hiện không có hồ sơ cần kiểm tra.</p>}</div> : <>
          <div className="offering-table-scroll"><table className="offering-table"><caption className="sr-only">Danh sách khách hàng lệch đối soát</caption><thead><tr><th>Mã khách hàng (ID)</th><th>Các cột lệch</th></tr></thead><tbody>{reconciliation.data.differences.items.map(diff => <tr key={diff.customerId}><td><Link className="offering-row-link" href={`/app/khach-hang-360/${encodeURIComponent(diff.customerId)}`}>{diff.customerId}</Link></td><td>{formatCols(diff.columns)}</td></tr>)}</tbody></table></div>
          <div className="offering-mobile-cards">{reconciliation.data.differences.items.map(diff => <article className="offering-mobile-card" key={diff.customerId}><h3><Link href={`/app/khach-hang-360/${encodeURIComponent(diff.customerId)}`}>{diff.customerId}</Link></h3><p>Cột lệch: {formatCols(diff.columns)}</p></article>)}</div>
          <footer className="offering-pagination"><p>Trang {reconciliation.data.differences.page + 1} / {Math.max(1, reconciliation.data.differences.totalPages)}</p><div><Button variant="secondary" disabled={reconciliation.data.differences.page <= 0} onClick={() => { reconciliationHeadingRef.current?.focus(); setPage(p => Math.max(0, p - 1)); }}>Trước</Button><Button variant="secondary" disabled={reconciliation.data.differences.page + 1 >= reconciliation.data.differences.totalPages} onClick={() => { reconciliationHeadingRef.current?.focus(); setPage(p => p + 1); }}>Sau</Button></div></footer>
        </>}
      </>}
    </section>}
  </div>;
}
