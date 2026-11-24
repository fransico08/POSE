"use client";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { permittedNavigation } from "@/config/navigation";
import { displayRoles } from "@/config/roles";
import { api } from "@/lib/api";
import { dashboardKey, formatMetric, formatResponseDuration, getDashboardKpis, periodFromDates, unavailable, type DashboardMetric } from "@/lib/dashboard";
import { PageHeader } from "@/components/ui/PageHeader";
import { StatCard } from "@/components/ui/StatCard";
import { TimeGreeting } from "./TimeGreeting";
import { TodayWork } from "./TodayWork";

type Conversation = { id: string; channelType: string; channelName: string; lastMessageAt: string | null; lastMessagePreview: string | null };
const cards = [
  { id: "E01_OVERDUE_LEAD_RESPONSE", label: "Lead quá hạn phản hồi", href: "/app/bao-cao", tone: "default" as const },
  { id: "E02_UNATTENDED_OPPORTUNITY_VALUE", label: "Giá trị cơ hội chưa chăm sóc", href: "/app/bao-cao", tone: "accent" as const },
  { id: "B02_OVERDUE_OPEN_TASKS", label: "Nhiệm vụ quá hạn", href: "/app/nhiem-vu", tone: "danger" as const },
  { id: "E04_FIRST_MESSAGE_RESPONSE_TIME", label: "Thời gian phản hồi đầu tiên", href: "/app/bao-cao", tone: "default" as const },
];
const channelLabel = (channel: string) => ({ TELEGRAM: "Telegram", ZALO_SIM: "Zalo mô phỏng", MESSENGER: "Messenger" }[channel] ?? channel);
function dashboardParams() {
  const now = new Date();
  const date = (value: Date) => new Intl.DateTimeFormat("en-GB", { timeZone: "Asia/Ho_Chi_Minh", day: "2-digit", month: "2-digit", year: "numeric" }).format(value);
  const period = periodFromDates(date(new Date(now.getTime() - 29 * 86400000)), date(now));
  return new URLSearchParams({ ...period!, timezone: "Asia/Ho_Chi_Minh", metricIds: cards.map(card => card.id).join(",") });
}
function metricValue(metric: DashboardMetric) {
  const label = metric.dimensions.channelType ? channelLabel(String(metric.dimensions.channelType)) : "";
  const value = metric.unit === "seconds" && metric.value !== null ? formatResponseDuration(metric.value) : formatMetric(metric);
  return label ? `${label}: ${value}` : value;
}
export function Overview() {
  const { user, organization } = useSession();
  const shortcuts = permittedNavigation(user.permissions).filter(item => item.implemented && item.route !== "/app" && !(item.route === "/app/hoi-thoai" && user.roles.includes("DATA_STAFF")));
  const allowed = user.permissions.includes("dashboard.read");
  const kpis = useQuery({ queryKey: [...dashboardKey(user), "overview"], queryFn: () => getDashboardKpis(dashboardParams()), enabled: allowed, retry: false });
  const canReadConversations = user.permissions.includes("conversation.read") && !user.roles.includes("DATA_STAFF");
  const conversations = useQuery({ queryKey: ["overview-conversations", user.organization.id, user.id], queryFn: () => api.request<{ items: Conversation[] }>("/api/backend/conversations?size=5", undefined, { suppressForbiddenNavigation: true }), enabled: canReadConversations, retry: false });
  return <>
    <div className="overview-hero"><PageHeader title="Tổng quan" eyebrow="Không gian làm việc" description={<><p><TimeGreeting /> <strong>{user.fullName}</strong>, chúc bạn một ngày làm việc hiệu quả.</p><p>Tổ chức: {organization.name} · Vai trò hiện tại: {displayRoles(user.roles)}</p></>} /></div>
    {allowed && <section className="overview-metrics" aria-label="Chỉ số tổng quan">
      {kpis.isPending && <p role="status">Đang tải chỉ số…</p>}
      {kpis.isError && <div role="alert">Không thể tải chỉ số. <button type="button" onClick={() => void kpis.refetch()}>Thử lại</button></div>}
      {kpis.data && cards.map(card => { const values = kpis.data.metrics.filter(metric => metric.metricId === card.id && !unavailable(metric)); if (!values.length) return null; const href = card.href === "/app/nhiem-vu" && !user.permissions.includes("task.manage") ? "/app/bao-cao" : card.href; return <StatCard key={card.id} label={card.label} value={<>{values.map((metric, index) => <span key={index}>{metricValue(metric)}</span>)}</>} tone={card.tone} href={href} />; })}
    </section>}
    <div className="overview-columns"><TodayWork /><aside className="overview-aside">
      <section className="overview-panel" aria-labelledby="shortcuts-title"><h2 id="shortcuts-title">Thao tác nhanh</h2>{shortcuts.length ? <ul className="overview-shortcuts">{shortcuts.map(item => { const Icon = item.icon; return <li key={item.route}><Link href={item.route}><Icon aria-hidden="true" />{item.label}</Link></li>; })}</ul> : <p>Chưa có lối tắt</p>}</section>
      {canReadConversations && <section className="overview-panel" aria-labelledby="recent-conversations-title"><h2 id="recent-conversations-title">Hội thoại mới nhất</h2>{conversations.isPending && <p role="status">Đang tải hội thoại…</p>}{conversations.isError && <p role="alert">Không thể tải hội thoại. <button type="button" onClick={() => void conversations.refetch()}>Thử lại</button></p>}{conversations.data && (conversations.data.items.length ? <ul className="overview-conversations">{conversations.data.items.map(item => <li key={item.id}><Link href="/app/hoi-thoai"><strong>{item.lastMessagePreview ?? "Chưa có tin nhắn"}</strong><span>{channelLabel(item.channelType)} · {item.channelName}</span>{item.lastMessageAt && <time dateTime={item.lastMessageAt}>{new Intl.DateTimeFormat("vi-VN", { timeZone: "Asia/Ho_Chi_Minh", dateStyle: "short", timeStyle: "short" }).format(new Date(item.lastMessageAt))}</time>}</Link></li>)}</ul> : <p>Chưa có hội thoại.</p>)}</section>}
    </aside></div>
    <footer className="overview-footer">POSE CRM · Nền tảng quản lý khách hàng đa ngành</footer>
  </>;
}
