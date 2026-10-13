import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MockScene } from "@/components/landing/mock/MockScene";

describe("K1 live illustration", () => {
  it("contains three pipeline columns with neutral cards and three real activity labels", () => {
    const { container } = render(<MockScene />);
    expect(container.querySelectorAll(".mock-column")).toHaveLength(3);
    expect(container.querySelectorAll(".mock-deal")).toHaveLength(7);
    expect(container.querySelectorAll(".mock-avatar")).toHaveLength(7);
    expect(container.querySelectorAll(".mock-activities li")).toHaveLength(3);
    expect(container.querySelector(".hero-scene")).toHaveAttribute("aria-hidden", "true");
    expect(container.textContent).not.toMatch(/@|\$|₫|doanh thu|KPI/i);
  });
});
