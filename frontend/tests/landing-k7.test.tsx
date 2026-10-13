import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { SecurityView } from "@/components/landing/SecurityView";

describe("K7 security illustration", () => {
  it("changes the labeled sample perspective without exposing technical enum names", async () => {
    const user = userEvent.setup(); const { container } = render(<SecurityView />);
    expect(screen.getByRole("button", { name: "Chăm sóc" })).toHaveAttribute("aria-pressed", "true");
    await user.click(screen.getByRole("button", { name: "Bán hàng" }));
    expect(screen.getByRole("button", { name: "Bán hàng" })).toHaveAttribute("aria-pressed", "true");
    expect(container.querySelector(".security-card")).not.toHaveAttribute("data-care");
    expect(container.textContent).not.toMatch(/SALES_ONLY|CARE_VISIBLE/);
  });
});
