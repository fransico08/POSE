"use client";

import { useState, type FormEvent } from "react";
import { ArrowPathIcon, MagnifyingGlassIcon, PlusIcon } from "@heroicons/react/24/outline";
import { useQuery } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { api } from "@/lib/api";
import { type OfferingPage } from "@/lib/offerings";
import {
  assignmentEffectiveLabel, assignmentStatusLabel, canManageSalesAssignments, formatAssignmentDate,
  salesAssignmentErrorMessage, salesAssignmentListUrl, type AssignmentTeamPage,
  type SalesAssignment, type SalesAssignmentFilters, type SalesAssignmentPage as AssignmentPageData,
} from "@/lib/sales-assignments";
import { SalesAssignmentFormDialog } from "./SalesAssignmentFormDialog";
import { SalesAssignmentRevokeDialog } from "./SalesAssignmentRevokeDialog";

const EMPTY_FILTERS: SalesAssignmentFilters = { page: 0, search: "", teamId: "", offeringId: "", salesSearch: "", status: "", validity: "" };

function effectiveTone(state: SalesAssignment["effectiveState"]) {
  return `assignment-state-${state.toLowerCase()}`;
}

function AccessState() {
  return <section className="sales-assignment-section"><EmptyState title="Bạn chưa có quyền quản lý phân công">Liên hệ quản trị viên để được cấp quyền phù hợp.</EmptyState></section>;
}

export function SalesAssignmentPage() {
  const { user } = useSession();
  const allowed = canManageSalesAssignments(user.roles, user.permissions);
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [searchText, setSearchText] = useState("");
  const [salesSearchText, setSalesSearchText] = useState("");
  const [formOpen, setFormOpen] = useState(false);
  const [formAssignment, setFormAssignment] = useState<SalesAssignment | null>(null);
  const [revokeAssignment, setRevokeAssignment] = useState<SalesAssignment | null>(null);
  const [notice, setNotice] = useState("");

  const list = useQuery({
    queryKey: ["sales-assignments", user.organization.id, user.id, filters],
    queryFn: () => api.request<AssignmentPageData>(salesAssignmentListUrl(filters)),
    enabled: allowed,
  });
  const teams = useQuery({
    queryKey: ["sales-assignment-teams", user.organization.id, user.id],
    queryFn: () => api.request<AssignmentTeamPage>("/api/backend/teams?page=0&size=100"),
    enabled: allowed,
  });
  const offerings = useQuery({
    queryKey: ["sales-assignment-offerings", user.organization.id, user.id],
    queryFn: () => api.request<OfferingPage>("/api/backend/offerings?page=0&size=100"),
    enabled: allowed,
  });

  if (!allowed) return <AccessState />;

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFilters(current => ({ ...current, page: 0, search: searchText.trim(), salesSearch: salesSearchText.trim() }));
  }

  function changeFilter(field: "teamId" | "offeringId" | "status" | "validity", value: string) {
    setFilters(current => ({ ...current, page: 0, [field]: value }));
  }

  function clearFilters() {
    setSearchText("");
    setSalesSearchText("");
    setFilters(EMPTY_FILTERS);
  }

  function closeForm() {
    setFormOpen(false);
    setFormAssignment(null);
  }

  function saveSucceeded() {
    setNotice(formAssignment ? "Gia hạn phân công thành công." : "Phân công sản phẩm/dịch vụ thành công.");
    closeForm();
  }

  const hasFilters = Boolean(filters.search || filters.teamId || filters.offeringId || filters.salesSearch || filters.status || filters.validity);
  const activeOfferings = (offerings.data?.items ?? []).filter(offering => offering.active && offering.teamId);
  const rows = list.data?.items ?? [];

  return <div className="sales-assignment-page">
    <header className="sales-assignment-heading">
      <div><p className="eyebrow">Điều phối bán hàng</p><h1>Phân công sản phẩm</h1><p>Gán sản phẩm hoặc dịch vụ đang hoạt động cho Sales trong đúng team sở hữu Offering.</p></div>
      <Button disabled={offerings.isPending || activeOfferings.length === 0} onClick={() => { setFormAssignment(null); setFormOpen(true); }}><PlusIcon aria-hidden="true" />Phân công sản phẩm/dịch vụ</Button>
    </header>

    {notice && <p className="sales-assignment-notice" role="status" aria-live="polite">{notice}</p>}
    {offerings.isError && <div className="sales-assignment-inline-error"><Alert>{salesAssignmentErrorMessage(offerings.error)}</Alert><Button variant="secondary" onClick={() => void offerings.refetch()}>Thử tải lại danh mục</Button></div>}
    {teams.isError && <div className="sales-assignment-inline-error"><Alert>Không tải được team để lọc phân công.</Alert><Button variant="secondary" onClick={() => void teams.refetch()}>Thử tải lại team</Button></div>}

    <section className="sales-assignment-section" aria-label="Tìm và lọc phân công">
      <form className="sales-assignment-filters" onSubmit={submitSearch}>
        <div className="field sales-assignment-filter-search"><label htmlFor="assignment-search">Tìm Offering theo tên hoặc mã</label><div className="sales-assignment-search-control"><input id="assignment-search" className="input" value={searchText} maxLength={200} onChange={event => setSearchText(event.target.value)} placeholder="Ví dụ: khóa học tiếng Anh" /><Button type="submit" variant="secondary"><MagnifyingGlassIcon aria-hidden="true" /><span>Tìm</span></Button></div></div>
        <div className="field"><label htmlFor="assignment-sales-search">Tìm theo Sale</label><input id="assignment-sales-search" className="input" value={salesSearchText} maxLength={200} onChange={event => setSalesSearchText(event.target.value)} placeholder="Tên hoặc email" /></div>
        <div className="field"><label htmlFor="assignment-team">Team</label><select id="assignment-team" className="input" value={filters.teamId} onChange={event => changeFilter("teamId", event.target.value)}><option value="">Tất cả team trong phạm vi</option>{(teams.data?.items ?? []).map(team => <option key={team.id} value={team.id}>{team.name}</option>)}</select></div>
        <div className="field"><label htmlFor="assignment-offering">Sản phẩm/dịch vụ</label><select id="assignment-offering" className="input" value={filters.offeringId} onChange={event => changeFilter("offeringId", event.target.value)}><option value="">Tất cả Offering</option>{(offerings.data?.items ?? []).map(offering => <option key={offering.id} value={offering.id}>{offering.name}</option>)}</select></div>
        <div className="field"><label htmlFor="assignment-status">Trạng thái</label><select id="assignment-status" className="input" value={filters.status} onChange={event => changeFilter("status", event.target.value)}><option value="">Tất cả trạng thái</option><option value="ACTIVE">Đang phân công</option><option value="EXPIRED">Đã hết hạn</option><option value="REVOKED">Đã thu hồi</option></select></div>
        <div className="field"><label htmlFor="assignment-validity">Hiệu lực</label><select id="assignment-validity" className="input" value={filters.validity} onChange={event => changeFilter("validity", event.target.value)}><option value="">Tất cả</option><option value="CURRENT">Đang có hiệu lực</option><option value="UPCOMING">Sắp có hiệu lực</option><option value="EXPIRED">Đã hết hạn</option><option value="REVOKED">Đã thu hồi</option></select></div>
        {hasFilters && <Button type="button" variant="ghost" className="sales-assignment-clear" onClick={clearFilters}>Xóa bộ lọc</Button>}
      </form>
    </section>

    {list.isPending && <section className="sales-assignment-section" aria-busy="true"><p role="status">Đang tải phân công…</p><div className="sales-assignment-skeleton" /><div className="sales-assignment-skeleton" /><div className="sales-assignment-skeleton" /></section>}
    {list.isError && <section className="sales-assignment-section"><Alert>{salesAssignmentErrorMessage(list.error)}</Alert><Button variant="secondary" className="sales-assignment-retry" onClick={() => void list.refetch()}><ArrowPathIcon aria-hidden="true" />Thử lại</Button></section>}
    {list.data && rows.length === 0 && <section className="sales-assignment-section">{hasFilters
      ? <EmptyState title="Không tìm thấy phân công">Thử đổi từ khóa hoặc bộ lọc đang áp dụng.</EmptyState>
      : <EmptyState title="Chưa có phân công sản phẩm">Tạo phân công đầu tiên để Sales có thể xem Offering được giao.</EmptyState>}</section>}

    {list.data && rows.length > 0 && <section className="sales-assignment-section sales-assignment-results" aria-label="Danh sách phân công">
      <p className="sales-assignment-result-count">{list.data.totalItems} phân công</p>
      <div className="sales-assignment-table-scroll"><table className="sales-assignment-table"><caption className="sr-only">Phân công sản phẩm hoặc dịch vụ cho nhân viên kinh doanh</caption>
        <thead><tr><th scope="col">Sale</th><th scope="col">Sản phẩm/dịch vụ</th><th scope="col">Team</th><th scope="col">Hiệu lực từ</th><th scope="col">Hiệu lực đến</th><th scope="col">Trạng thái</th><th scope="col"><span className="sr-only">Thao tác</span></th></tr></thead>
        <tbody>{rows.map(assignment => <tr key={assignment.id}>
          <th scope="row">{assignment.salesUserName}</th>
          <td><strong>{assignment.offeringName}</strong><small>{assignment.offeringCode}</small></td>
          <td>{assignment.teamName ?? "Team không còn khả dụng"}</td>
          <td>{formatAssignmentDate(assignment.effectiveFrom)}</td>
          <td>{formatAssignmentDate(assignment.effectiveTo)}</td>
          <td><span className={`sales-assignment-state ${effectiveTone(assignment.effectiveState)}`}>{assignmentEffectiveLabel(assignment.effectiveState)}</span><small>{assignmentStatusLabel(assignment.status)}</small></td>
          <td className="sales-assignment-actions">{assignment.effectiveState !== "REVOKED" && <><Button variant="secondary" onClick={() => { setFormAssignment(assignment); setFormOpen(true); }}>Gia hạn</Button><Button variant="danger" onClick={() => setRevokeAssignment(assignment)}>Thu hồi</Button></>}</td>
        </tr>)}</tbody>
      </table></div>
      <div className="sales-assignment-mobile-cards">{rows.map(assignment => <article className="sales-assignment-mobile-card" key={assignment.id}>
        <header><div><h2>{assignment.offeringName}</h2><p>{assignment.offeringCode}</p></div><span className={`sales-assignment-state ${effectiveTone(assignment.effectiveState)}`}>{assignmentEffectiveLabel(assignment.effectiveState)}</span></header>
        <dl><div><dt>Sale</dt><dd>{assignment.salesUserName}</dd></div><div><dt>Team</dt><dd>{assignment.teamName ?? "Team không còn khả dụng"}</dd></div><div><dt>Hiệu lực từ</dt><dd>{formatAssignmentDate(assignment.effectiveFrom)}</dd></div><div><dt>Hiệu lực đến</dt><dd>{formatAssignmentDate(assignment.effectiveTo)}</dd></div></dl>
        {assignment.effectiveState !== "REVOKED" && <footer><Button variant="secondary" onClick={() => { setFormAssignment(assignment); setFormOpen(true); }}>Gia hạn</Button><Button variant="danger" onClick={() => setRevokeAssignment(assignment)}>Thu hồi</Button></footer>}
      </article>)}</div>
      <footer className="sales-assignment-pagination"><p>Trang {list.data.page + 1} / {Math.max(list.data.totalPages, 1)}</p><div><Button variant="secondary" disabled={list.data.page <= 0 || list.isFetching} onClick={() => setFilters(current => ({ ...current, page: current.page - 1 }))}>Trước</Button><Button variant="secondary" disabled={list.data.page + 1 >= list.data.totalPages || list.isFetching} onClick={() => setFilters(current => ({ ...current, page: current.page + 1 }))}>Sau</Button></div></footer>
    </section>}

    <SalesAssignmentFormDialog key={`${formAssignment?.id ?? "new"}-${formOpen ? "open" : "closed"}`} open={formOpen} assignment={formAssignment} offerings={offerings.data?.items ?? []} onClose={closeForm} onSaved={saveSucceeded} />
    <SalesAssignmentRevokeDialog assignment={revokeAssignment} onClose={() => setRevokeAssignment(null)} onRevoked={() => { setNotice("Thu hồi phân công thành công."); setRevokeAssignment(null); }} />
  </div>;
}
