import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { AppSidebar } from "@/components/layout/AppSidebar";
import type { SessionUser } from "@/lib/session/types";

const route = vi.hoisted(() => ({ pathname: "/app" }));
vi.mock("next/navigation", () => ({ usePathname: () => route.pathname }));

const user: SessionUser = { id: "fixture-user", fullName: "Nguyễn An", email: "person@example.test", status: "ACTIVE", organization: { id: "fixture-org", name: "Tổ chức kiểm thử" }, roles: ["MANAGER"], permissions: [], teamIds: [] };

describe("sidebar active indicator", () => {
  it.each([["/app", "0", "1"], ["/app/khong-co-quyen", "-1", "0"]])("uses a CSS index and preserves active visibility on %s", (pathname, index, opacity) => {
    route.pathname = pathname;
    const { container } = render(<AppSidebar user={user} />);
    const indicator = container.querySelector<HTMLElement>(".navigation-indicator")!;
    expect(indicator.style.getPropertyValue("--active-index")).toBe(index);
    expect(indicator.style.transform).toBe("");
    expect(indicator.style.opacity).toBe(opacity);
    expect(indicator).toHaveAttribute("aria-hidden", "true");
    const overview = screen.getByRole("link", { name: "Tổng quan" });
    if (pathname === "/app") expect(overview).toHaveAttribute("aria-current", "page");
    else expect(overview).not.toHaveAttribute("aria-current");
  });

  it("keeps collapsed navigation and its toggle keyboard accessible", async () => {
    route.pathname = "/app";
    const keyboard = userEvent.setup(), toggle = vi.fn();
    render(<AppSidebar user={user} collapsed onToggle={toggle} />);
    await keyboard.tab();
    expect(screen.getByRole("button", { name: "Mở rộng sidebar" })).toHaveFocus();
    await keyboard.keyboard("{Enter}");
    expect(toggle).toHaveBeenCalledOnce();
    await keyboard.tab();
    expect(screen.getByRole("link", { name: "Tổng quan" })).toHaveFocus();
  });
});
