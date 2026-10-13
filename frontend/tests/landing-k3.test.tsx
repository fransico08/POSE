import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { RoleSection } from "@/components/landing/RoleSection";
import { RoleTabs } from "@/components/landing/RoleTabs";
import { roles } from "@/content/landing";
import { installMotionFixture } from "./landing-motion-fixture";
const motion = installMotionFixture();
const roleProps = () => ({ labels: roles.map(role => role.label), panels: roles.map(role => <h3 key={role.id}>{role.title}</h3>) });

describe("K3 role tabs", () => {
  it("server renders five populated work rows for each role", () => {
    const { container } = render(<RoleSection />);
    for (const panel of container.querySelectorAll('[role="tabpanel"]')) {
      expect(panel.querySelectorAll(".mock-review li")).toHaveLength(5);
      expect(panel.querySelectorAll(".mock-avatar")).toHaveLength(5);
      expect(panel.querySelectorAll(".mock-role-copy small")).toHaveLength(5);
    }
  });
  it("supports arrows, Home/End, roving tabindex, selected state and hidden panels", async () => {
    const user = userEvent.setup(); const { container } = render(<RoleTabs {...roleProps()} />);
    const tabs = screen.getAllByRole("tab"); tabs[0].focus();
    await user.keyboard("{ArrowRight}"); expect(tabs[1]).toHaveFocus(); expect(tabs[1]).toHaveAttribute("aria-selected", "true");
    expect(tabs[0]).toHaveAttribute("tabindex", "-1");
    expect(document.getElementById("role-panel-0")).toHaveAttribute("hidden");
    expect(screen.getAllByRole("tabpanel")).toHaveLength(1);
    await user.keyboard("{End}"); expect(tabs[4]).toHaveFocus();
    await user.keyboard("{Home}"); expect(tabs[0]).toHaveFocus();
    await user.keyboard("{ArrowLeft}"); expect(tabs[4]).toHaveFocus();
    expect(container.querySelectorAll('[role="tabpanel"]')).toHaveLength(5);
  });
  it("advances after six seconds, stops on focus and waits for an explicit restart", () => {
    vi.useFakeTimers(); render(<RoleTabs {...roleProps()} />); motion.makeVisible();
    act(() => vi.advanceTimersByTime(6000)); expect(screen.getAllByRole("tab")[1]).toHaveAttribute("aria-selected", "true");
    fireEvent.focus(screen.getAllByRole("tab")[1]);
    act(() => vi.advanceTimersByTime(12000)); expect(screen.getAllByRole("tab")[1]).toHaveAttribute("aria-selected", "true");
    fireEvent.click(screen.getByRole("button", { name: "Tiếp tục chuyển vai trò" }));
    act(() => vi.advanceTimersByTime(6000)); expect(screen.getAllByRole("tab")[2]).toHaveAttribute("aria-selected", "true");
  });
  it("does not auto-rotate or activate progress animation with reduced motion", () => {
    vi.useFakeTimers(); motion.setReduced(true); const { container } = render(<RoleTabs {...roleProps()} />); motion.makeVisible();
    act(() => vi.advanceTimersByTime(18000)); expect(screen.getAllByRole("tab")[0]).toHaveAttribute("aria-selected", "true");
    expect(container.querySelector(".role-progress")).toBeNull();
    expect(screen.getByRole("button", { name: "Tự chuyển vai trò đã tắt" })).toBeDisabled();
  });
});
