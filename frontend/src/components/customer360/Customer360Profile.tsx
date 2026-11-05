"use client";
import Link from "next/link";
import { useEffect, useRef } from "react";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { Alert } from "@/components/ui/Alert";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { customerStatuses, customerTypes, leadStatuses } from "@/lib/crm";
import { canReadCustomer360, customer360Error, customer360Key, getCustomer360, type Customer360Response } from "@/lib/customer360";
import { formatIsoDateAsDdMmYyyy } from "@/lib/date-format";
import { formatVnd, stages, type OpportunityStage } from "@/lib/opportunities";
import { taskPriorities, taskStatuses } from "@/lib/tasks";
import { instantToVietnamDateTime } from "@/lib/work-time";

const RESTRICTED_NOTE = "Bạn không có quyền xem phần này";
const CONTENT_HIDDEN = "Nội dung bị ẩn theo quyền";
const CONTACT_HIDDEN = "Ẩn theo quyền";

function ProfileSummary({ data, isDataStaff }: { data: Customer360Response; isDataStaff: boolean }) {
  const { profile, owner, ownerTeams, careAssignees } = data.customer;
  const contactText = (val: string | null) => val ? val : isDataStaff ? CONTACT_HIDDEN : "Chưa cập nhật";
  const rows: Array<[string, string]> = [
    ["Loại khách hàng", customerTypes[profile.customerType ?? ""] ?? (profile.customerType || "Chưa cập nhật")],
    ["Trạng thái", customerStatuses[profile.status] ?? profile.status],
    ["Sản phẩm/dịch vụ quan tâm", profile.offeringName ? (profile.offeringCode ? `${profile.offeringName} (${profile.offeringCode})` : profile.offeringName) : "Chưa cập nhật"],
    ["Người phụ trách (Owner)", owner?.name || profile.ownerName || "Chưa cập nhật"],
    ["Nhóm của Owner", ownerTeams.length ? ownerTeams.map(team => team.name).filter(Boolean).join(", ") : "Chưa có nhóm"],
    ["Người được giao chăm sóc (CARE)", careAssignees.length ? careAssignees.map(person => person.name).filter(Boolean).join(", ") : "Chưa phân công"],
    ["Email", contactText(profile.email)],
    ["Điện thoại", contactText(profile.phone)],
    ["Địa chỉ", contactText(profile.address)],
    ["Ghi chú", contactText(profile.notes)],
  ];
  return <section className="offering-section" aria-labelledby="c360-identity-title">
    <h2 id="c360-identity-title">Định danh và phân công</h2>
    <dl className="offering-detail-grid">{rows.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
  </section>;
}

function MetricCards({ data }: { data: Customer360Response }) {
  const { opportunities, tasks, feedback, support, sources } = data;
  const stageEntries = opportunities.stages ? Object.entries(opportunities.stages) : [];
  const taskEntries = tasks.statuses ? Object.entries(tasks.statuses) : [];
  const ratingText = feedback.averageRating !== null ? `${new Intl.NumberFormat("vi-VN", { minimumFractionDigits: 1, maximumFractionDigits: 2 }).format(feedback.averageRating)} / 5 điểm` : "Chưa có điểm";
  return <section className="offering-section" aria-labelledby="c360-metrics-title">
    <h2 id="c360-metrics-title">Chỉ số tổng hợp</h2>
    <div className="offering-detail-grid">
      <div><h3>Cơ hội bán hàng</h3>{opportunities.restricted ? <p>{RESTRICTED_NOTE}</p> : <dl><div><dt>Theo giai đoạn</dt><dd>{stageEntries.map(([stage, count]) => `${stages[stage as OpportunityStage] ?? stage}: ${count} cơ hội`).join(" · ")}</dd></div><div><dt>Giá trị đang mở</dt><dd>{opportunities.openExpectedValue === null ? CONTENT_HIDDEN : formatVnd(opportunities.openExpectedValue)}</dd></div><div><dt>Giá trị đã chốt (WON)</dt><dd>{opportunities.wonFinalValue === null ? CONTENT_HIDDEN : formatVnd(opportunities.wonFinalValue)}</dd></div></dl>}</div>
      <div><h3>Nhiệm vụ</h3>{tasks.restricted ? <p>{RESTRICTED_NOTE}</p> : <dl><div><dt>Theo trạng thái</dt><dd>{taskEntries.map(([status, count]) => `${taskStatuses[status] ?? status}: ${count} nhiệm vụ`).join(" · ")}</dd></div><div><dt>Quá hạn</dt><dd>{tasks.overdue} nhiệm vụ</dd></div></dl>}</div>
      <div><h3>Phản hồi (Feedback)</h3>{feedback.restricted ? <p>{RESTRICTED_NOTE}</p> : <dl><div><dt>Số phản hồi</dt><dd>{feedback.count} phản hồi</dd></div><div><dt>Điểm trung bình</dt><dd>{ratingText}</dd></div></dl>}</div>
      <div><h3>Hỗ trợ (Support)</h3>{support.restricted ? <p>{RESTRICTED_NOTE}</p> : <dl><div><dt>Yêu cầu đang mở</dt><dd>{support.count} yêu cầu</dd></div></dl>}</div>
      <div><h3>Nguồn dữ liệu</h3><dl><div><dt>Số nguồn</dt><dd>{sources.count} nguồn</dd></div><div><dt>Hệ thống</dt><dd>{sources.systems.length ? sources.systems.join(", ") : "Chưa có mã hệ thống"}</dd></div></dl></div>
    </div>
  </section>;
}

export function Customer360Profile() {
  const { user } = useSession(); const id = useParams<{ id: string }>()?.id ?? ""; const headingRef = useRef<HTMLHeadingElement>(null);
  const allowed = canReadCustomer360(user), isDataStaff = user.roles.includes("DATA_STAFF");
  const query = useQuery({ queryKey: [...customer360Key(user), "profile", id], queryFn: () => getCustomer360(id), enabled: allowed && Boolean(id) });
  useEffect(() => {
    if (!query.data) return;
    document.title = `${query.data.customer.profile.displayName} — Hồ sơ khách hàng 360 | POSE CRM`;
    headingRef.current?.focus();
  }, [query.data]);
  if (!allowed) return <section className="offering-section"><EmptyState title="Bạn không có quyền truy cập">Liên hệ quản trị viên để được cấp quyền phù hợp.</EmptyState></section>;
  if (query.isPending) return <section className="offering-section" aria-busy="true"><p role="status">Đang tải hồ sơ 360…</p><div className="offering-skeleton" /></section>;
  if (query.isError) return <section className="offering-section"><Alert>{customer360Error(query.error)}</Alert><Button variant="secondary" onClick={() => void query.refetch()}>Thử lại</Button></section>;
  const data = query.data, { profile } = data.customer, refNow = Date.parse(data.refreshedAt);
  const canLead = user.permissions.includes("lead.read") && !isDataStaff, canOpp = user.permissions.includes("opportunity.read") && !isDataStaff;
  const canInter = user.permissions.includes("interaction.read") && !isDataStaff, canTask = user.permissions.includes("task.manage") && !isDataStaff;
  const leadItems = data.leads.items.slice(0, 10), oppItems = data.opportunities.items.slice(0, 10);
  const interItems = data.interactions.items.slice(0, 10), taskItems = data.tasks.items.slice(0, 10);
  const visLabel = (v: string) => v === "SALES_ONLY" ? "Chỉ bán hàng (SALES_ONLY)" : "Chia sẻ với chăm sóc (CARE_VISIBLE)";
  return <div className="offering-page"><header className="offering-detail-heading"><div><div className="offering-detail-title"><h1 ref={headingRef} tabIndex={-1}>{profile.displayName}</h1><Badge>{customerStatuses[profile.status] ?? profile.status}</Badge></div><p className="offering-last-updated">Cập nhật lúc {instantToVietnamDateTime(data.refreshedAt)}</p></div></header>
    <ProfileSummary data={data} isDataStaff={isDataStaff} /><MetricCards data={data} />
    <section className="offering-section" aria-labelledby="c360-leads-title"><header className="offering-detail-heading"><h2 id="c360-leads-title">Lead đã chuyển</h2>{!data.leads.restricted && canLead && <Link className="offering-row-link" href="/app/lead">Xem tất cả</Link>}</header>
      {data.leads.restricted ? <p>{RESTRICTED_NOTE}</p> : !leadItems.length ? <EmptyState title="Chưa có Lead chuyển đổi">Khách hàng chưa có Lead đã chuyển đổi.</EmptyState> : <><p className="offering-result-count">Tổng số: {data.leads.count} Lead</p><div className="offering-table-scroll"><table className="offering-table"><caption className="sr-only">Lead đã chuyển</caption><thead><tr><th>Tên Lead</th><th>Trạng thái</th><th>Thời điểm chuyển</th></tr></thead><tbody>{leadItems.map(item => <tr key={item.id}><td>{canLead && item.name !== null ? <Link href={`/app/lead/${item.id}`}>{item.name}</Link> : item.name ?? CONTENT_HIDDEN}</td><td>{leadStatuses[item.status] ?? item.status}</td><td>{item.convertedAt ? instantToVietnamDateTime(item.convertedAt) : "Chưa cập nhật"}</td></tr>)}</tbody></table></div><div className="offering-mobile-cards">{leadItems.map(item => <article className="offering-mobile-card" key={item.id}><h3>{canLead && item.name !== null ? <Link href={`/app/lead/${item.id}`}>{item.name}</Link> : item.name ?? CONTENT_HIDDEN}</h3><p>{leadStatuses[item.status] ?? item.status} · {item.convertedAt ? instantToVietnamDateTime(item.convertedAt) : "Chưa cập nhật"}</p></article>)}</div></>}
    </section>
    <section className="offering-section" aria-labelledby="c360-opps-title"><header className="offering-detail-heading"><h2 id="c360-opps-title">Cơ hội</h2>{!data.opportunities.restricted && canOpp && <Link className="offering-row-link" href={`/app/co-hoi?customerId=${encodeURIComponent(profile.id)}`}>Xem tất cả</Link>}</header>
      {data.opportunities.restricted ? <p>{RESTRICTED_NOTE}</p> : !oppItems.length ? <EmptyState title="Chưa có cơ hội">Khách hàng chưa có cơ hội trong phạm vi.</EmptyState> : <><div className="offering-table-scroll"><table className="offering-table"><caption className="sr-only">Danh sách cơ hội</caption><thead><tr><th>Tiêu đề</th><th>Giai đoạn</th><th>Giá trị dự kiến</th><th>Ngày dự kiến chốt</th></tr></thead><tbody>{oppItems.map(item => <tr key={item.id}><td>{canOpp && item.title !== null ? <Link href={`/app/co-hoi/${item.id}`}>{item.title}</Link> : item.title ?? CONTENT_HIDDEN}</td><td>{stages[item.stage as OpportunityStage] ?? item.stage}</td><td>{item.expectedValue === null ? CONTENT_HIDDEN : formatVnd(item.expectedValue)}</td><td>{item.expectedCloseDate ? formatIsoDateAsDdMmYyyy(item.expectedCloseDate) : "Chưa cập nhật"}</td></tr>)}</tbody></table></div><div className="offering-mobile-cards">{oppItems.map(item => <article className="offering-mobile-card" key={item.id}><h3>{canOpp && item.title !== null ? <Link href={`/app/co-hoi/${item.id}`}>{item.title}</Link> : item.title ?? CONTENT_HIDDEN}</h3><p>{stages[item.stage as OpportunityStage] ?? item.stage} · {item.expectedValue === null ? CONTENT_HIDDEN : formatVnd(item.expectedValue)}</p><p>{item.expectedCloseDate ? formatIsoDateAsDdMmYyyy(item.expectedCloseDate) : "Chưa cập nhật"}</p></article>)}</div></>}
    </section>
    <section className="offering-section" aria-labelledby="c360-interactions-title"><header className="offering-detail-heading"><h2 id="c360-interactions-title">Tương tác</h2>{!data.interactions.restricted && canInter && <Link className="offering-row-link" href={`/app/tuong-tac?customerId=${encodeURIComponent(profile.id)}`}>Xem tất cả</Link>}</header>
      {data.interactions.restricted ? <p>{RESTRICTED_NOTE}</p> : !interItems.length ? <EmptyState title="Chưa có tương tác">Khách hàng chưa có tương tác trong phạm vi.</EmptyState> : <><p className="offering-result-count">Tổng số: {data.interactions.count} tương tác{data.interactions.lastInteractionAt ? ` · Gần nhất: ${instantToVietnamDateTime(data.interactions.lastInteractionAt)}` : ""}</p><div className="offering-table-scroll"><table className="offering-table"><caption className="sr-only">Danh sách tương tác</caption><thead><tr><th>Tiêu đề</th><th>Loại / Kênh</th><th>Phạm vi hiển thị</th><th>Người ghi</th><th>Thời điểm</th></tr></thead><tbody>{interItems.map(item => <tr key={item.id}><td>{canInter && item.subject !== null ? <Link href={`/app/tuong-tac/${item.id}`}>{item.subject}</Link> : item.subject ?? CONTENT_HIDDEN}</td><td>{item.type} · {item.channel}</td><td><Badge tone={item.visibility === "SALES_ONLY" ? "warning" : "info"}>{visLabel(item.visibility)}</Badge></td><td>{item.recordedByName}</td><td>{instantToVietnamDateTime(item.occurredAt)}</td></tr>)}</tbody></table></div><div className="offering-mobile-cards">{interItems.map(item => <article className="offering-mobile-card" key={item.id}><h3>{canInter && item.subject !== null ? <Link href={`/app/tuong-tac/${item.id}`}>{item.subject}</Link> : item.subject ?? CONTENT_HIDDEN}</h3><p>{item.type} · {item.channel} · {visLabel(item.visibility)}</p><p>{item.recordedByName} · {instantToVietnamDateTime(item.occurredAt)}</p></article>)}</div></>}
    </section>
    <section className="offering-section" aria-labelledby="c360-tasks-title"><header className="offering-detail-heading"><h2 id="c360-tasks-title">Nhiệm vụ</h2>{!data.tasks.restricted && canTask && <Link className="offering-row-link" href={`/app/nhiem-vu?customerId=${encodeURIComponent(profile.id)}`}>Xem tất cả</Link>}</header>
      {data.tasks.restricted ? <p>{RESTRICTED_NOTE}</p> : !taskItems.length ? <EmptyState title="Chưa có nhiệm vụ">Khách hàng chưa có nhiệm vụ đang mở trong phạm vi.</EmptyState> : <><div className="offering-table-scroll"><table className="offering-table"><caption className="sr-only">Danh sách nhiệm vụ</caption><thead><tr><th>Tiêu đề</th><th>Người phụ trách</th><th>Ưu tiên</th><th>Trạng thái</th><th>Hạn</th></tr></thead><tbody>{taskItems.map(item => { const overdue = !["DONE", "CANCELLED"].includes(item.status) && Date.parse(item.dueAt) < refNow; return <tr key={item.id}><td>{canTask && item.title !== null ? <Link href={`/app/nhiem-vu/${item.id}`}>{item.title}</Link> : item.title ?? CONTENT_HIDDEN}</td><td>{item.ownerName}</td><td>{taskPriorities[item.priority] ?? item.priority}</td><td>{taskStatuses[item.status] ?? item.status}</td><td>{instantToVietnamDateTime(item.dueAt)} {overdue && <span>⚠ Quá hạn</span>}</td></tr>; })}</tbody></table></div><div className="offering-mobile-cards">{taskItems.map(item => { const overdue = !["DONE", "CANCELLED"].includes(item.status) && Date.parse(item.dueAt) < refNow; return <article className="offering-mobile-card" key={item.id}><h3>{canTask && item.title !== null ? <Link href={`/app/nhiem-vu/${item.id}`}>{item.title}</Link> : item.title ?? CONTENT_HIDDEN}</h3><p>{taskStatuses[item.status] ?? item.status} · {taskPriorities[item.priority] ?? item.priority}</p><p>{instantToVietnamDateTime(item.dueAt)} {overdue && "⚠ Quá hạn"}</p></article>; })}</div></>}
    </section>
  </div>;
}
