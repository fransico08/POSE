import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { IndustrySwitcher } from "@/components/landing/IndustrySwitcher";
import { industries } from "@/content/landing";

describe("K6 industry switcher", () => {
  it("switches sectors through named dot controls", async () => {
    const user = userEvent.setup(); render(<IndustrySwitcher items={industries} icons={industries.map(item => <span key={item.id} />)} />);
    await user.click(screen.getByRole("button", { name: "Xem ngành Logistics" }));
    expect(screen.getByRole("button", { name: "Xem ngành Logistics" })).toHaveAttribute("aria-pressed", "true");
    expect(within(screen.getByRole("tabpanel")).getByText("Nội vùng")).toBeVisible();
  });
  it("switches sample attributes with tab keyboard input", async () => {
    const user = userEvent.setup(); render(<IndustrySwitcher items={industries} icons={industries.map(item => <span key={item.id} />)} />);
    const tabs = screen.getAllByRole("tab"); tabs[0].focus(); await user.keyboard("{ArrowRight}");
    expect(tabs[1]).toHaveFocus(); expect(tabs[1]).toHaveAttribute("aria-selected", "true");
    expect(within(screen.getByRole("tabpanel")).getByText("Nội thành")).toBeVisible();
    await user.keyboard("{End}"); expect(within(screen.getByRole("tabpanel")).getByText("Trực tiếp")).toBeVisible();
    await user.keyboard("{Home}"); expect(within(screen.getByRole("tabpanel")).getByText("Trực tuyến")).toBeVisible();
  });
  it("supports previous/next controls and wraps through all six sectors", async () => {
    const user = userEvent.setup(); render(<IndustrySwitcher items={industries} icons={industries.map(item => <span key={item.id} />)} />);
    await user.click(screen.getByRole("button", { name: "Ngành trước" })); expect(screen.getAllByRole("tab")[5]).toHaveAttribute("aria-selected", "true");
    await user.click(screen.getByRole("button", { name: "Ngành tiếp theo" })); expect(screen.getAllByRole("tab")[0]).toHaveAttribute("aria-selected", "true");
  });
});
