"use client";
import dynamic from "next/dynamic";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { DateInput } from "@/components/ui/DateInput";
import { ErrorState } from "@/components/ui/ErrorState";
import { Skeleton } from "@/components/ui/Skeleton";
import { ApiError } from "@/lib/api/client";
import { api } from "@/lib/api";
import { dashboardKey, engagementRows, formatMetric, getDashboardKpis, periodFromDates, unavailable, type DashboardMetric } from "@/lib/dashboard";
import { taskPriorities, taskStatuses } from "@/lib/tasks";

const Chart = dynamic(() => import("./DashboardChart"), { ssr: false });
const labels: Record<string, string> = {
  NEW: "Mới", QUALIFIED: "Đủ điều kiện", CONVERTED: "Đã chuyển đổi", DISQUALIFIED: "Không đủ điều kiện",
  ...taskStatuses, ...taskPriorities, PHONE: "Điện thoại", EMAIL: "Email", ZALO: "Zalo", IN_PERSON: "Trực tiếp", CRM: "CRM",
  ACTIVE: "Đang tương tác", AT_RISK: "Có nguy cơ", INACTIVE: "Không hoạt động", NEVER_ENGAGED: "Chưa từng tương tác",
  ASSIGNMENT_CREATED: "Phân công được tạo", IMPORT_COMMITTED: "Nhập dữ liệu hoàn tất", TASK_DUE_SOON: "Nhiệm vụ sắp đến hạn", LEAD_QUALIFIED: "Lead đủ điều kiện",
};
const titles: Record<string, string> = {
  A01: "Tổng Lead", A02: "Lead theo trạng thái", A03: "Tỷ lệ Lead đủ điều kiện",
  A04: "Tỷ lệ chuyển thành khách hàng", A05: "Phản hồi đầu tiên", A06: "Mức độ tương tác khách hàng",
  B01: "Nhiệm vụ đang mở", B02: "Nhiệm vụ quá hạn", B03: "Nhiệm vụ theo ưu tiên và trạng thái",
  B04: "Nhiệm vụ theo người phụ trách", B05: "Hoàn tất đúng hạn",
  C01: "Workflow thành công", C02: "Workflow lỗi và số lần thử lại", C03: "Thời gian xử lý workflow",
  D01: "Tương tác theo kênh", D02: "Tương tác theo người ghi",
};
type UserOption = { id: string; fullName: string };
type UserPage = { items: UserOption[]; totalPages: number };
async function users(): Promise<UserOption[]> {
  const result: UserOption[] = []; let page = 0, pages = 1;
  while (page < pages) {
    const response = await api.request<UserPage>(`/api/backend/users?page=${page}&size=100`, undefined, { suppressForbiddenNavigation: true });
    result.push(...response.items); pages = response.totalPages; page++;
  }
  return result;
}
function vnDate(date: Date): string {
  const parts = new Intl.DateTimeFormat("en-GB", { timeZone: "Asia/Ho_Chi_Minh", day: "2-digit", month: "2-digit", year: "numeric" }).format(date);
  return parts;
}
function defaultDates(days: number) {
  const now = new Date(); return { from: vnDate(new Date(now.getTime() - (days - 1) * 86400000)), to: vnDate(now) };
}
function ids(list: DashboardMetric[], prefix: string) { return list.filter(m => m.metricId.startsWith(prefix)); }
function displayName(id: string, names: UserOption[], self: string, mayRead: boolean, all: string[]) {
  if (!id) return "Chưa cập nhật";
  if (mayRead) return names.find(user => user.id === id)?.fullName ?? (id === self ? "Bạn" : `Người dùng khác ${all.indexOf(id) + 1}`);
  return id === self ? "Bạn" : `Người dùng khác ${all.filter(value => value !== self).indexOf(id) + 1}`;
}
function dimension(metric: DashboardMetric, names: UserOption[], self: string, mayRead: boolean, all: string[]) {
  return Object.entries(metric.dimensions).map(([key, raw]) => {
    const value = String(raw);
    if (key === "userId" || key === "ownerUserId") return displayName(value, names, self, mayRead, all);
    if (key === "retryCount") return `${value} lần thử lại`;
    if (key === "kind") return value === "RESPONDED" ? "Đã phản hồi" : "Chưa phản hồi";
    return labels[value] ?? value;
  }).join(" · ") || "Tổng";
}
function PeriodHint({ metric }: { metric?: DashboardMetric }) {
  return metric?.availability === "NO_DATA_IN_PERIOD" ? <p className="dashboard-note">Hãy thử đổi khoảng thời gian.</p> : null;
}
function MetricCard({ code, metrics }: { code: string; metrics: DashboardMetric[] }) {
  const rows = ids(metrics, code), first = rows[0];
  return <article className="dashboard-kpi" data-metric={code}><h3>{titles[code]}</h3>
    {rows.length > 1 && code === "C01" ? rows.map((m, index) => <p key={index}>{labels[String(m.dimensions.workflowType)] ?? String(m.dimensions.workflowType ?? "Tổng")}: <strong>{formatMetric(m)}</strong>{m.numerator !== null && m.denominator !== null && <span className="dashboard-note"> ({m.numerator.toLocaleString("vi-VN")}/{m.denominator.toLocaleString("vi-VN")})</span>}</p>) : <div className="dashboard-value">{formatMetric(first)}</div>}
    {rows.length === 1 && first?.denominator !== null && first?.denominator !== undefined && <p className="dashboard-note">Mẫu số: {first.denominator.toLocaleString("vi-VN")}</p>}
    <PeriodHint metric={first} />
  </article>;
}
function MetricPanel({ code, metrics, names, self, mayRead, all }: { code: string; metrics: DashboardMetric[]; names: UserOption[]; self: string; mayRead: boolean; all: string[] }) {
  const items = ids(metrics, code), first = items[0], message = items.some(item => !unavailable(item)) ? null : unavailable(first);
  const rows = code === "A06" && !message ? engagementRows(items) : items.filter(item => !unavailable(item)).map(item => ({ name: dimension(item, names, self, mayRead, all), value: item.value! }));
  const hasChart = ["A02", "A06", "B03", "D01"].includes(code);
  let chartRows: { name: string; [key: string]: string | number }[] = rows;
  let series = ["value"], stacked = false;
  if (code === "A06" || code === "D01") {
    stacked = true; series = rows.map(row => row.name);
    chartRows = [{ name: code === "A06" ? "Khách hàng" : "Tương tác", ...Object.fromEntries(rows.map(row => [row.name, row.value])) }];
  } else if (code === "B03") {
    series = [...new Set(items.map(item => labels[String(item.dimensions.status)] ?? String(item.dimensions.status ?? "")))].filter(Boolean);
    const priorities = [...new Set(items.map(item => String(item.dimensions.priority ?? "")))].filter(Boolean);
    chartRows = priorities.map(priority => ({ name: labels[priority] ?? priority, ...Object.fromEntries(items.filter(item => item.dimensions.priority === priority && item.value !== null).map(item => [labels[String(item.dimensions.status)] ?? String(item.dimensions.status), item.value!])) }));
  }
  return <article className="dashboard-panel" data-metric={code}><h3>{titles[code]}</h3>
    {message ? <><p>{message}</p><PeriodHint metric={first} /></> : <>
      {hasChart && rows.length > 0 && <Chart rows={chartRows} series={series} stacked={stacked} />}
      <div className="dashboard-table-wrap"><table className="dashboard-table"><caption className="sr-only">Số liệu {titles[code]}</caption><thead><tr><th>Phân loại</th><th>Giá trị</th></tr></thead><tbody>
        {code === "A06" ? rows.map(row => <tr key={row.name}><td>{row.name}</td><td>{row.value.toLocaleString("vi-VN")}</td></tr>) : items.map((item, index) => <tr key={index}><td>{dimension(item, names, self, mayRead, all)}</td><td>{formatMetric(item)}{item.denominator !== null && item.numerator !== null && ` (${item.numerator}/${item.denominator})`}</td></tr>)}
      </tbody></table></div>
    </>}
  </article>;
}

export function Dashboard() {
  const { user } = useSession(), allowed = user.permissions.includes("dashboard.read");
  const mayReadUsers = user.permissions.includes("identity.user.read");
  const canChooseOwner = mayReadUsers && user.roles.some(role => ["ADMIN", "MANAGER", "DATA_STAFF"].includes(role));
  const [ready, setReady] = useState(false), [params, setParams] = useState<Record<string, string>>({});
  const [preset, setPreset] = useState("30"), [from, setFrom] = useState(""), [to, setTo] = useState("");
  const [error, setError] = useState("");
  useEffect(() => {
    const timer = window.setTimeout(() => {
    const query = new URLSearchParams(window.location.search), start = query.get("start"), end = query.get("end");
    const defaults = defaultDates(30), values: Record<string, string> = {};
    for (const key of ["start", "end", "asOf", "ownerUserId", "workflowType"]) { const value = query.get(key); if (value) values[key] = value; }
    if (start && end && !Number.isNaN(Date.parse(start)) && !Number.isNaN(Date.parse(end))) {
      const savedPreset = query.get("range");
      setFrom(vnDate(new Date(start))); setTo(vnDate(new Date(Date.parse(end) - 1))); setPreset(savedPreset && ["7", "30", "90", "custom"].includes(savedPreset) ? savedPreset : "custom");
    } else {
      setFrom(defaults.from); setTo(defaults.to); Object.assign(values, periodFromDates(defaults.from, defaults.to));
    }
    setParams(values); setReady(true);
    }, 0);
    return () => window.clearTimeout(timer);
  }, []);
  useEffect(() => {
    if (!ready) return;
    const query = new URLSearchParams(Object.entries(params).filter(([, value]) => Boolean(value)));
    query.set("range", preset);
    window.history.replaceState(window.history.state, "", `${window.location.pathname}?${query}`);
  }, [params, preset, ready]);
  const query = useMemo(() => new URLSearchParams({ ...Object.fromEntries(Object.entries(params).filter(([, value]) => Boolean(value))), timezone: "Asia/Ho_Chi_Minh" }), [params]);
  const baseQuery = useMemo(() => { const copy = new URLSearchParams(query); copy.delete("workflowType"); copy.delete("ownerUserId"); return copy; }, [query]);
  const dashboard = useQuery({ queryKey: [...dashboardKey(user), query.toString()], queryFn: () => getDashboardKpis(query), enabled: ready && allowed, retry: false });
  const unfiltered = useQuery({ queryKey: [...dashboardKey(user), "workflow-options", baseQuery.toString()], queryFn: () => getDashboardKpis(baseQuery), enabled: ready && allowed && Boolean(params.workflowType || params.ownerUserId), retry: false });
  const people = useQuery({ queryKey: [...dashboardKey(user), "users"], queryFn: users, enabled: allowed && mayReadUsers, retry: false });
  const metrics = dashboard.data?.metrics ?? [], optionMetrics = unfiltered.data?.metrics ?? metrics;
  const serverFields = dashboard.error instanceof ApiError && dashboard.error.status === 400 ? dashboard.error.fieldErrors : [];
  const startError = serverFields.find(field => field.field === "start")?.message;
  const endError = serverFields.find(field => field.field === "end")?.message;
  const workflowOptions = [...new Set(ids(optionMetrics, "C01").map(m => String(m.dimensions.workflowType ?? "")).filter(Boolean))];
  const canWorkflow = ids(optionMetrics, "C01").some(m => m.availability !== "NOT_APPLICABLE");
  const allUserIds = [...new Set(metrics.flatMap(m => [m.dimensions.ownerUserId, m.dimensions.userId].filter(Boolean).map(String)))].sort();
  const change = (key: string, value: string) => setParams(old => ({ ...old, [key]: value }));
  function applyDates(nextFrom = from, nextTo = to, snapshotAtEnd = preset === "custom") {
    const period = periodFromDates(nextFrom, nextTo);
    if (!period) { setError("Khoảng ngày không hợp lệ hoặc vượt quá 366 ngày."); return; }
    setError(""); setParams(old => ({ ...old, ...period, asOf: snapshotAtEnd ? period.end : "" }));
  }
  if (!allowed) return <section className="offering-section"><h1>Báo cáo</h1><p>Bạn không có quyền xem báo cáo.</p><Link href="/app">Về Tổng quan</Link></section>;
  return <div className="dashboard-page">
    <header className="dashboard-heading"><div><h1>Báo cáo</h1><p>Chỉ số trong phạm vi được phân quyền · Múi giờ Việt Nam</p></div><Button variant="secondary" onClick={() => void dashboard.refetch()}>Làm mới</Button></header>
    <section className="offering-section" aria-labelledby="dashboard-filter-title"><h2 id="dashboard-filter-title">Bộ lọc</h2><div className="dashboard-filters">
      <div className="field"><label htmlFor="dashboard-preset">Khoảng thời gian</label><select id="dashboard-preset" className="input" value={preset} onChange={event => {
        const value = event.target.value; setPreset(value);
        if (value !== "custom") { const dates = defaultDates(Number(value)); setFrom(dates.from); setTo(dates.to); applyDates(dates.from, dates.to, false); }
      }}><option value="7">7 ngày gần nhất</option><option value="30">30 ngày gần nhất</option><option value="90">90 ngày gần nhất</option><option value="custom">Tùy chọn</option></select></div>
      {preset === "custom" && <><div className="field"><label htmlFor="dashboard-from">Từ ngày</label><DateInput key={`from-${ready}`} id="dashboard-from" value={from} onChange={setFrom} aria-invalid={Boolean(error || startError)} aria-describedby={startError ? "dashboard-start-server-error" : error ? "dashboard-date-error" : undefined} />{startError && <p id="dashboard-start-server-error">{startError}</p>}</div>
        <div className="field"><label htmlFor="dashboard-to">Đến ngày</label><DateInput key={`to-${ready}`} id="dashboard-to" value={to} onChange={setTo} aria-invalid={Boolean(error || endError)} aria-describedby={endError ? "dashboard-end-server-error" : error ? "dashboard-date-error" : undefined} />{endError && <p id="dashboard-end-server-error">{endError}</p>}</div><Button onClick={() => applyDates()}>Áp dụng</Button></>}
      {canChooseOwner && <div className="field"><label htmlFor="dashboard-owner">Người phụ trách</label><select id="dashboard-owner" className="input" value={params.ownerUserId ?? ""} onChange={e => change("ownerUserId", e.target.value)}><option value="">Tất cả</option>{people.data?.map(person => <option key={person.id} value={person.id}>{person.fullName}</option>)}</select></div>}
      {canWorkflow && <div className="field"><label htmlFor="dashboard-workflow">Loại workflow</label><select id="dashboard-workflow" className="input" value={params.workflowType ?? ""} onChange={e => change("workflowType", e.target.value)}><option value="">Tất cả</option>{workflowOptions.map(value => <option key={value} value={value}>{labels[value] ?? value}</option>)}</select></div>}
    </div>{error && <p id="dashboard-date-error" role="alert">{error}</p>}</section>
    {(!ready || dashboard.isPending) && <section className="offering-section" aria-busy="true"><p role="status">Đang tải báo cáo…</p><Skeleton /></section>}
    {dashboard.isError && <section className="offering-section">{dashboard.error instanceof ApiError && dashboard.error.status === 403 ? <><Alert>Bạn không có quyền xem báo cáo trong phạm vi này.</Alert><Link href="/app">Về Tổng quan</Link></> : dashboard.error instanceof ApiError && dashboard.error.status === 400 ? <Alert>{dashboard.error.fieldErrors.map(item => `${item.field}: ${item.message}`).join("; ") || "Bộ lọc không hợp lệ."}</Alert> : <ErrorState inline message="Không thể tải báo cáo." reset={() => void dashboard.refetch()} />}</section>}
    {dashboard.data && <><p className="dashboard-badge">Cập nhật lúc {new Intl.DateTimeFormat("vi-VN", { timeZone:"Asia/Ho_Chi_Minh", dateStyle:"short", timeStyle:"short" }).format(new Date(dashboard.data.metrics[0]?.generatedAt ?? dashboard.data.asOf))} · Đang xem {vnDate(new Date(dashboard.data.period.start))}–{vnDate(new Date(Date.parse(dashboard.data.period.end)-1))} · Mốc phân nhóm tương tác {new Intl.DateTimeFormat("vi-VN", { timeZone:"Asia/Ho_Chi_Minh", dateStyle:"short", timeStyle:"short" }).format(new Date(dashboard.data.asOf))}</p>
      <section aria-labelledby="dashboard-overview"><h2 id="dashboard-overview">Tổng quan</h2><div className="dashboard-kpis">{["A01","A03","A04","B01","B02","C01"].map(code => <MetricCard key={code} code={code} metrics={metrics} />)}</div></section>
      <div className="dashboard-groups">{Object.entries({ A: ["Lead và khách hàng", ["A02","A05","A06"]], B: ["Nhiệm vụ", ["B03","B04","B05"]], C: ["Workflow", ["C02","C03"]], D: ["Tương tác", ["D01","D02"]] } as Record<string, [string, string[]]>).map(([group, [title, codes]]) => {
        const groupMetrics = metrics.filter(metric => metric.metricId.startsWith(group)), restricted = groupMetrics.length > 0 && groupMetrics.every(metric => metric.availability === "NOT_APPLICABLE");
        return <section className="offering-section" key={group} aria-labelledby={`dashboard-group-${group}`}><h2 id={`dashboard-group-${group}`} className="dashboard-section-title">{title}</h2>
          {restricted ? <><p>{unavailable(groupMetrics[0])}</p>{groupMetrics[0]?.reason === "OWNER_FILTER_NOT_SUPPORTED" && <Button variant="secondary" onClick={() => change("ownerUserId", "")}>Xóa bộ lọc</Button>}</> : <div className="dashboard-panels">{codes.map(code => <MetricPanel key={code} code={code} metrics={metrics} names={people.data ?? []} self={user.id} mayRead={mayReadUsers} all={allUserIds} />)}</div>}
        </section>;
      })}</div>
    </>}
  </div>;
}
