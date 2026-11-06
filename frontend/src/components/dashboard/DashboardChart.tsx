"use client";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

export type ChartRow = { name: string; [key: string]: string | number };
const colors = ["var(--pose-primary)", "var(--pose-info)", "var(--pose-success)", "var(--pose-warning)", "var(--pose-text-muted)"];

// The legend is plain HTML below the plot: Recharts' built-in legend is measured inside the 240px
// surface, and long wrapped Vietnamese labels left a negative plot height (no axis, grid or bars).
export default function DashboardChart({ rows, series, stacked }: { rows: ChartRow[]; series: string[]; stacked: boolean }) {
  return <div className="dashboard-chart" aria-hidden="true">
    <ResponsiveContainer width="100%" height={240}>
      <BarChart data={rows} margin={{ top: 8, right: 16, left: 0, bottom: 8 }}>
        <CartesianGrid stroke="var(--pose-border)" vertical={false} />
        <XAxis dataKey="name" tick={{ fontSize: 11 }} interval={0} />
        <YAxis allowDecimals={false} width={36} tick={{ fontSize: 11 }} />
        <Tooltip />
        {series.map((key, index) => <Bar key={key} dataKey={key} name={key === "value" ? "Số lượng" : key} stackId={stacked ? "total" : undefined} fill={colors[index % colors.length]} isAnimationActive={false} />)}
      </BarChart>
    </ResponsiveContainer>
    {series.length > 1 && <ul className="dashboard-legend">
      {series.map((key, index) => <li key={key}><span className="dashboard-legend-swatch" style={{ background: colors[index % colors.length] }} />{key}</li>)}
    </ul>}
  </div>;
}
