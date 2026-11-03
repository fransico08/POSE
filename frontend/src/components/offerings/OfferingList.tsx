"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { ArrowPathIcon, MagnifyingGlassIcon, PlusIcon } from "@heroicons/react/24/outline";
import { useSession } from "@/components/session/SessionGate";
import { api } from "@/lib/api";
import { Alert } from "@/components/ui/Alert";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { canManageOfferings, canReadOfferings, formatOfferingPrice, offeringListUrl, offeringErrorMessage, type Offering, type OfferingCategory, type OfferingFilters, type OfferingPage, type OfferingTeamPage } from "@/lib/offerings";

const EMPTY_FILTERS: OfferingFilters = { page: 0, search: "", kind: "", categoryId: "", active: "", teamId: "" };

function AccessState() {
  return <section className="offering-section"><EmptyState title="Bạn chưa có quyền xem danh mục">Liên hệ quản trị viên để được cấp quyền phù hợp.</EmptyState></section>;
}

export function OfferingList() {
  const { user } = useSession();
  const manager = canManageOfferings(user.roles, user.permissions);
  const allowed = canReadOfferings(user.roles, user.permissions);
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [searchText, setSearchText] = useState("");
  const list = useQuery({
    queryKey: ["offerings", user.organization.id, user.id, filters],
    queryFn: () => api.request<OfferingPage>(offeringListUrl(filters, manager)),
    enabled: allowed,
  });
  const categories = useQuery({
    queryKey: ["offering-categories", user.organization.id, user.id],
    queryFn: () => api.request<OfferingCategory[]>("/api/backend/offerings/categories"),
    enabled: manager,
  });
  const teams = useQuery({
    queryKey: ["offering-teams", user.organization.id, user.id],
    queryFn: () => api.request<OfferingTeamPage>("/api/backend/teams?page=0&size=100"),
    enabled: manager,
  });

  if (!allowed) return <AccessState />;

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFilters(current => ({ ...current, page: 0, search: searchText.trim() }));
  }

  function changeFilter(field: "kind" | "categoryId" | "active" | "teamId", value: string) {
    setFilters(current => ({ ...current, page: 0, [field]: value }));
  }

  function clearFilters() {
    setSearchText("");
    setFilters(EMPTY_FILTERS);
  }

  const hasFilters = Boolean(filters.search || filters.kind || filters.categoryId || filters.active || filters.teamId);

  return <div className="offering-page">
    <header className="offering-heading">
      <div><p className="eyebrow">Danh mục bán hàng</p><h1>Sản phẩm &amp; dịch vụ</h1><p>{manager ? "Tra cứu và quản lý danh mục trong phạm vi quyền truy cập của bạn." : "Xem sản phẩm, dịch vụ đang được phân công và còn hiệu lực cho bạn."}</p></div>
      {manager && <Link className="button button-primary offering-add" href="/app/san-pham/them"><PlusIcon aria-hidden="true" />Thêm sản phẩm hoặc dịch vụ</Link>}
    </header>

    <section className="offering-section" aria-label="Tìm kiếm và lọc danh mục">
      <form className="offering-filters" onSubmit={submitSearch}>
        <div className="field offering-search-field"><label htmlFor="offering-search">Tìm theo tên hoặc mã</label><div className="offering-search-control"><input id="offering-search" className="input" value={searchText} maxLength={200} onChange={event => setSearchText(event.target.value)} placeholder="Ví dụ: gói tư vấn CRM" /><Button type="submit" variant="secondary" aria-label="Tìm kiếm"><MagnifyingGlassIcon aria-hidden="true" /><span className="offering-search-label">Tìm</span></Button></div></div>
        <div className="field"><label htmlFor="offering-kind">Loại</label><select id="offering-kind" className="input" value={filters.kind} onChange={event => changeFilter("kind", event.target.value)}><option value="">Tất cả loại</option><option value="PRODUCT">Sản phẩm</option><option value="SERVICE">Dịch vụ</option></select></div>
        {manager && <>
          <div className="field"><label htmlFor="offering-category">Danh mục</label><select id="offering-category" className="input" value={filters.categoryId} onChange={event => changeFilter("categoryId", event.target.value)}><option value="">Tất cả danh mục</option>{(categories.data ?? []).map(category => <option value={category.id} key={category.id}>{category.name}</option>)}</select></div>
          <div className="field"><label htmlFor="offering-active">Trạng thái</label><select id="offering-active" className="input" value={filters.active} onChange={event => changeFilter("active", event.target.value)}><option value="">Tất cả trạng thái</option><option value="true">Đang hoạt động</option><option value="false">Ngừng hoạt động</option></select></div>
          <div className="field"><label htmlFor="offering-team">Team</label><select id="offering-team" className="input" value={filters.teamId} onChange={event => changeFilter("teamId", event.target.value)}><option value="">{user.roles.includes("ADMIN") ? "Tất cả team" : "Tất cả team tôi quản lý"}</option>{(teams.data?.items ?? []).map(team => <option value={team.id} key={team.id}>{team.name}{team.status === "INACTIVE" ? " (Ngừng hoạt động)" : ""}</option>)}</select></div>
          {(categories.isError || teams.isError) && <div className="offering-filter-error"><Alert>Không tải được danh mục hoặc team để lọc. Danh sách Offering vẫn dùng được.</Alert></div>}
        </>}
        {hasFilters && <Button type="button" variant="ghost" className="offering-clear" onClick={clearFilters}>Xóa bộ lọc</Button>}
      </form>
    </section>

    {list.isPending && <section className="offering-section" aria-busy="true"><p role="status">Đang tải danh sách sản phẩm và dịch vụ…</p><div className="offering-skeleton" /><div className="offering-skeleton" /><div className="offering-skeleton" /></section>}
    {list.isError && <section className="offering-section"><Alert>{offeringErrorMessage(list.error)}</Alert><Button className="offering-retry" variant="secondary" onClick={() => void list.refetch()}><ArrowPathIcon aria-hidden="true" />Thử lại</Button></section>}
    {list.data && list.data.items.length === 0 && <section className="offering-section">{hasFilters
      ? <EmptyState title="Không tìm thấy kết quả">Thử đổi từ khóa hoặc xóa bớt bộ lọc đang áp dụng.</EmptyState>
      : <EmptyState title="Chưa có sản phẩm hoặc dịch vụ">{manager ? "Thêm mục đầu tiên để bắt đầu xây dựng danh mục bán hàng." : "Danh mục được phân công cho bạn hiện chưa có mục đang hoạt động."}</EmptyState>}</section>}

    {list.data && list.data.items.length > 0 && <section className="offering-section offering-results" aria-label="Kết quả danh mục">
      <p className="offering-result-count">{list.data.totalItems} sản phẩm/dịch vụ</p>
      <div className="offering-table-scroll"><table className="offering-table"><caption className="sr-only">Danh sách sản phẩm và dịch vụ</caption><thead><tr><th scope="col">Tên / Mã</th><th scope="col">Loại</th><th scope="col">Danh mục</th><th scope="col">Giá tham khảo</th><th scope="col">Team</th><th scope="col">Trạng thái</th><th scope="col"><span className="sr-only">Thao tác</span></th></tr></thead>
        <tbody>{list.data.items.map((offering: Offering) => <tr key={offering.id}>
          <th scope="row"><Link href={`/app/san-pham/${offering.id}`} className="offering-name">{offering.name}</Link><small className="offering-code">{offering.code}</small></th>
          <td>{offering.kind === "PRODUCT" ? "Sản phẩm" : "Dịch vụ"}</td><td>{offering.categoryName ?? "Chưa có danh mục"}</td>
          <td>{formatOfferingPrice(offering.referencePrice, offering.currencyCode)}{offering.pricingUnit ? <small className="offering-code"> / {offering.pricingUnit}</small> : null}</td>
          <td>{offering.teamName ?? <span className="offering-muted">Chưa gán team</span>}</td>
          <td><Badge tone={offering.active ? "success" : "warning"}>{offering.active ? "Đang hoạt động" : "Ngừng hoạt động"}</Badge></td>
          <td><Link href={`/app/san-pham/${offering.id}`} className="offering-row-link" aria-label={`Xem ${offering.name}`}>Xem</Link>{manager && <> <span aria-hidden="true">·</span> <Link href={`/app/san-pham/${offering.id}/chinh-sua`} className="offering-row-link" aria-label={`Sửa ${offering.name}`}>Sửa</Link></>}</td>
        </tr>)}</tbody></table></div>
      <div className="offering-mobile-cards">{list.data.items.map((offering: Offering) => <article className="offering-mobile-card" key={offering.id}>
        <header><div><Link href={`/app/san-pham/${offering.id}`} className="offering-name">{offering.name}</Link><small className="offering-code">{offering.code}</small></div><Badge tone={offering.active ? "success" : "warning"}>{offering.active ? "Đang hoạt động" : "Ngừng hoạt động"}</Badge></header>
        <dl><div><dt>Loại</dt><dd>{offering.kind === "PRODUCT" ? "Sản phẩm" : "Dịch vụ"}</dd></div><div><dt>Danh mục</dt><dd>{offering.categoryName ?? "Chưa có danh mục"}</dd></div><div><dt>Giá tham khảo</dt><dd>{formatOfferingPrice(offering.referencePrice, offering.currencyCode)}</dd></div><div><dt>Team</dt><dd>{offering.teamName ?? "Chưa gán team"}</dd></div></dl>
        <footer><Link className="button button-secondary" href={`/app/san-pham/${offering.id}`} aria-label={`Xem chi tiết ${offering.name}`}>Xem chi tiết</Link>{manager && <Link className="button button-secondary" href={`/app/san-pham/${offering.id}/chinh-sua`} aria-label={`Chỉnh sửa ${offering.name}`}>Chỉnh sửa</Link>}</footer>
      </article>)}</div>
      <footer className="offering-pagination"><p>Trang {list.data.page + 1} / {Math.max(list.data.totalPages, 1)}</p><div><Button variant="secondary" disabled={list.data.page <= 0 || list.isFetching} onClick={() => setFilters(current => ({ ...current, page: current.page - 1 }))}>Trước</Button><Button variant="secondary" disabled={list.data.page + 1 >= list.data.totalPages || list.isFetching} onClick={() => setFilters(current => ({ ...current, page: current.page + 1 }))}>Sau</Button></div></footer>
    </section>}
  </div>;
}
