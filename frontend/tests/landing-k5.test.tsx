import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { BentoSection } from "@/components/landing/BentoSection";
import { capabilities } from "@/content/landing";
import { installMotionFixture } from "./landing-motion-fixture";
const motion = installMotionFixture();

describe("K5 bento", () => {
  it("shows a four-event customer timeline and six compact industry chips", () => {
    const { container } = render(<BentoSection />);
    expect(container.querySelectorAll(".mock-customer-timeline .mock-activities li")).toHaveLength(4);
    expect(container.querySelectorAll(".mock-industry-chips li")).toHaveLength(6);
  });
  it("contains six sourced capabilities with natural short descriptions", () => {
    render(<BentoSection />);
    expect(screen.getAllByRole("heading", { level: 3 })).toHaveLength(6);
    for (const capability of capabilities) {
      expect(screen.getByText(capability.description)).toBeVisible();
      expect(capability.description.split(/\s+/).length).toBeGreaterThanOrEqual(6);
      expect(capability.description.split(/\s+/).length).toBeLessThanOrEqual(15);
    }
  });
  it("removes running and reveal activation states when reduced motion changes", () => {
    const { container } = render(<BentoSection />); motion.makeVisible();
    expect(container.querySelectorAll('[data-running]')).toHaveLength(6);
    motion.setReduced(true);
    expect(container.querySelector('[data-running], [data-seen]')).toBeNull();
  });
});
