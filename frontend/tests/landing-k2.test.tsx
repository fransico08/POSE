import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CompareSection } from "@/components/landing/CompareSection";
import { CompareSlider } from "@/components/landing/CompareSlider";

describe("K2 comparison", () => {
  it("shows a detailed scattered-work scene before the connected pipeline", () => {
    const { container } = render(<CompareSection />);
    expect(container.querySelectorAll(".compare-before .mock-sheet-row")).toHaveLength(6);
    expect(container.querySelectorAll(".mock-sheet-row[data-duplicate]")).toHaveLength(2);
    expect(container.querySelectorAll(".scattered-notes p")).toHaveLength(4);
    expect(container.querySelectorAll(".scattered-messages p")).toHaveLength(2);
    expect(container.querySelector(".compare-before")).toHaveTextContent("Thiếu người phụ trách");
    expect(container.querySelector(".compare-after")).toHaveTextContent("Với POSE");
  });
  it("uses one labeled native range with a Vietnamese value description", () => {
    const { container } = render(<CompareSlider before={<p>Before</p>} after={<p>After</p>} />);
    const slider = screen.getByRole("slider", { name: "So sánh hai cách làm việc" });
    expect(slider).toHaveAttribute("type", "range"); expect(slider).toHaveValue("50");
    expect(slider).toHaveAttribute("min", "0"); expect(slider).toHaveAttribute("max", "100");
    fireEvent.change(slider, { target: { value: "67" } });
    expect(slider).toHaveAttribute("aria-valuetext", "Chưa có POSE: 67 phần trăm bên trái; Với POSE: 33 phần trăm bên phải");
    expect(container.querySelector(".compare-stage")).toHaveAttribute("data-position", "67");
  });
  it("offers click alternatives to dragging", async () => {
    const user = userEvent.setup(); render(<CompareSlider before={<p>Before</p>} after={<p>After</p>} />);
    await user.click(screen.getByRole("button", { name: "Với POSE" })); expect(screen.getByRole("slider")).toHaveValue("0");
    await user.click(screen.getByRole("button", { name: "Chưa có POSE" })); expect(screen.getByRole("slider")).toHaveValue("100");
  });
});
