import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { IndustrySwitcher } from "@/components/landing/IndustrySwitcher";
import { IndustryPanel } from "@/components/landing/IndustryPanel";
import { industries } from "@/content/landing";

describe("K6 industry switcher", () => {
  it("explains category definitions without repeating product values", () => {
    render(<IndustrySwitcher items={industries.map(({ id, label }) => ({ id, label }))} panels={industries.map(item => <IndustryPanel key={item.id} item={item} icon={<span />} />)} />);
    const panel = screen.getByRole("tabpanel"), definition = panel.querySelector<HTMLElement>(".industry-attributes")!;
    expect(definition.querySelector("dl")).toHaveTextContent("Số buổiSố");
    expect(within(definition).getByText("Danh mục quyết định thuộc tính.")).toBeVisible();
    expect(within(definition).queryByText("Gói đào tạo cơ bản")).toBeNull();
    expect(within(definition).queryByText("Trực tuyến")).toBeNull();
    expect(panel.querySelector(".industry-product-card")).toHaveTextContent("Trực tuyến");
    expect(panel.querySelector(".industry-product-card")).toHaveTextContent("Số buổi8");
  });
  it("switches sectors through named dot controls", async () => {
    const user = userEvent.setup(); render(<IndustrySwitcher items={industries.map(({ id, label }) => ({ id, label }))} panels={industries.map(item => <IndustryPanel key={item.id} item={item} icon={<span />} />)} />);
    await user.click(screen.getByRole("button", { name: "Xem ngành Logistics" }));
    expect(screen.getByRole("button", { name: "Xem ngành Logistics" })).toHaveAttribute("aria-pressed", "true");
    expect(within(screen.getByRole("tabpanel").querySelector<HTMLElement>(".industry-attributes")!).getByRole("heading", { name: "Thuộc tính của danh mục Logistics" })).toBeVisible();
  });
  it("switches sample attributes with tab keyboard input", async () => {
    const user = userEvent.setup(); render(<IndustrySwitcher items={industries.map(({ id, label }) => ({ id, label }))} panels={industries.map(item => <IndustryPanel key={item.id} item={item} icon={<span />} />)} />);
    const tabs = screen.getAllByRole("tab"); tabs[0].focus(); await user.keyboard("{ArrowRight}");
    expect(screen.getByRole("tabpanel").querySelector(".industry-product-card")).toHaveTextContent("Nội thành");
    expect(tabs[1]).toHaveFocus(); expect(tabs[1]).toHaveAttribute("aria-selected", "true");
    expect(within(screen.getByRole("tabpanel").querySelector<HTMLElement>(".industry-attributes")!).getByRole("heading", { name: "Thuộc tính của danh mục Bất động sản" })).toBeVisible();
    await user.keyboard("{End}"); expect(within(screen.getByRole("tabpanel").querySelector<HTMLElement>(".industry-attributes")!).getByRole("heading", { name: "Thuộc tính của danh mục Phòng khám" })).toBeVisible();
    await user.keyboard("{Home}"); expect(within(screen.getByRole("tabpanel").querySelector<HTMLElement>(".industry-attributes")!).getByRole("heading", { name: "Thuộc tính của danh mục Giáo dục" })).toBeVisible();
  });
  it("supports previous/next controls and wraps through all six sectors", async () => {
    const user = userEvent.setup(); render(<IndustrySwitcher items={industries.map(({ id, label }) => ({ id, label }))} panels={industries.map(item => <IndustryPanel key={item.id} item={item} icon={<span />} />)} />);
    await user.click(screen.getByRole("button", { name: "Ngành trước" })); expect(screen.getAllByRole("tab")[5]).toHaveAttribute("aria-selected", "true");
    await user.click(screen.getByRole("button", { name: "Ngành tiếp theo" })); expect(screen.getAllByRole("tab")[0]).toHaveAttribute("aria-selected", "true");
  });
});
