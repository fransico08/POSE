import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { PageHeader } from "@/components/ui/PageHeader";
import { StatCard } from "@/components/ui/StatCard";
import { StatusPill } from "@/components/ui/StatusPill";
import { SegmentedTabs } from "@/components/ui/SegmentedTabs";
import { Avatar } from "@/components/ui/Avatar";
import userEvent from "@testing-library/user-event";

describe("workspace components", () => {
  it("renders a page heading and supporting text", () => { render(<PageHeader title="Tổng quan" eyebrow="Công việc" description="Hôm nay" />); expect(screen.getByRole("heading", { name:"Tổng quan", level:1 })).toBeInTheDocument(); expect(screen.getByText("Hôm nay")).toBeInTheDocument(); });
  it("makes the whole statistic a named link", () => { render(<StatCard label="Lead" value="12" href="/app/lead" tone="accent" />); expect(screen.getByRole("link", { name:"Lead 12" })).toHaveAttribute("href", "/app/lead"); });
  it("keeps status text visible", () => { render(<StatusPill tone="danger">Quá hạn</StatusPill>); expect(screen.getByText("Quá hạn")).toBeVisible(); });
  it("exposes pressed state and supports keyboard activation", async () => { const change=vi.fn(); const keyboard=userEvent.setup(); render(<SegmentedTabs label="Bộ lọc" options={[{label:"Tất cả",value:"all"},{label:"Mở",value:"open"}]} value="all" onChange={change} />); expect(screen.getByRole("button",{name:"Tất cả"})).toHaveAttribute("aria-pressed","true"); await keyboard.tab(); await keyboard.tab(); await keyboard.keyboard("{Enter}"); expect(change).toHaveBeenCalledWith("open"); });
  it("renders an accessible initials avatar", () => { render(<Avatar name="Nguyễn An" />); expect(screen.getByLabelText("Nguyễn An")).toHaveTextContent("NA"); });
});
