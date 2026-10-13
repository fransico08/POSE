import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { JourneySection } from "@/components/landing/JourneySection";
import { journeySteps } from "@/content/landing";
import { installMotionFixture } from "./landing-motion-fixture";
const motion = installMotionFixture();

describe("K4 journey", () => {
  it("keeps the four business steps readable and ordered without requiring animation", () => {
    motion.setReduced(true); const { container } = render(<JourneySection />);
    expect(container.querySelector("ol")).toHaveAttribute("data-reduced");
    const headings = screen.getAllByRole("heading", { level: 3 });
    expect(headings.map(heading => heading.textContent)).toEqual(journeySteps.map(step => step.title));
    expect(screen.getByText(/sau khi Quản lý duyệt/)).toBeVisible();
    expect(screen.getByText(/Lead hoặc Khách hàng/)).toBeVisible();
  });
});
