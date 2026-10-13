import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CompareSlider } from "@/components/landing/CompareSlider";

describe("K2 comparison", () => {
  it("uses one labeled native range with a Vietnamese value description", () => {
    const { container } = render(<CompareSlider before={<p>Before</p>} after={<p>After</p>} />);
    const slider = screen.getByRole("slider", { name: "So sánh hai cách làm việc" });
    expect(slider).toHaveAttribute("type", "range"); expect(slider).toHaveValue("50");
    expect(slider).toHaveAttribute("min", "0"); expect(slider).toHaveAttribute("max", "100");
    fireEvent.change(slider, { target: { value: "67" } });
    expect(slider).toHaveAttribute("aria-valuetext", "Phần với POSE: 67 phần trăm");
    expect(container.querySelector(".compare-stage")).toHaveAttribute("data-position", "67");
  });
  it("offers click alternatives to dragging", async () => {
    const user = userEvent.setup(); render(<CompareSlider before={<p>Before</p>} after={<p>After</p>} />);
    await user.click(screen.getByRole("button", { name: "Với POSE" })); expect(screen.getByRole("slider")).toHaveValue("100");
    await user.click(screen.getByRole("button", { name: "Chưa có POSE" })); expect(screen.getByRole("slider")).toHaveValue("0");
  });
});
