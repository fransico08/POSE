import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { PasswordField } from "@/components/ui/PasswordField";
import { Dialog } from "@/components/ui/Dialog";
import { MobileNavDrawer } from "@/components/layout/MobileNavDrawer";
import { UserMenu } from "@/components/layout/UserMenu";
import type { SessionUser } from "@/lib/session/types";

const user: SessionUser = { id: "fixture-user", fullName: "Nguyễn An", email: "person@example.test", status: "ACTIVE", organization: { id: "fixture-org", name: "Tổ chức kiểm thử" }, roles: ["SALES_REP"], permissions: [], teamIds: [] };
describe("PasswordField", () => {
  it("toggles with the keyboard, exposes state and preserves pasted input", async () => {
    const keyboard = userEvent.setup();
    render(<PasswordField id="password" label="Mật khẩu" />);
    const field = screen.getByLabelText("Mật khẩu");
    await keyboard.click(field); await keyboard.paste("synthetic input");
    await keyboard.tab(); await keyboard.keyboard("{Enter}");
    expect(field).toHaveAttribute("type", "text");
    expect(field).toHaveValue("synthetic input");
    expect(screen.getByRole("button", { name: "Ẩn mật khẩu" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "Ẩn mật khẩu" })).toHaveAttribute("aria-controls", "password");
    await keyboard.keyboard(" ");
    expect(field).toHaveAttribute("type", "password");
    expect(field).toHaveAttribute("autocomplete", "current-password");
  });
});
function ModalHarness({ drawer = false }: { drawer?: boolean }) {
  const [open, setOpen] = useState(false);
  return <><button onClick={() => setOpen(true)}>Mở</button>{drawer
    ? <MobileNavDrawer open={open} onClose={() => setOpen(false)}><nav aria-label="Điều hướng chính"><a href="/app">Tổng quan</a></nav></MobileNavDrawer>
    : <Dialog open={open} onClose={() => setOpen(false)} onConfirm={() => setOpen(false)} />}</>;
}
describe("modal keyboard behavior", () => {
  it("focuses cancel, wraps Tab both ways, cancels with Escape and restores focus", async () => {
    const keyboard = userEvent.setup(); render(<ModalHarness />);
    const opener = screen.getByRole("button", { name: "Mở" });
    await keyboard.click(opener);
    const dialog = screen.getByRole("dialog", { name: "Đăng xuất" });
    expect(dialog).toHaveAttribute("aria-modal", "true");
    const cancel = screen.getByRole("button", { name: "Hủy" });
    const confirm = screen.getByRole("button", { name: "Đăng xuất" });
    expect(cancel).toHaveFocus(); await keyboard.tab({ shift: true }); expect(confirm).toHaveFocus();
    await keyboard.tab(); expect(cancel).toHaveFocus();
    await keyboard.keyboard("{Escape}"); expect(opener).toHaveFocus();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
  it("drawer traps focus, closes on Escape, returns to opener", async () => {
    const keyboard = userEvent.setup(); render(<ModalHarness drawer />);
    const opener = screen.getByRole("button", { name: "Mở" }); await keyboard.click(opener);
    const close = screen.getByRole("button", { name: "Đóng điều hướng" }); expect(close).toHaveFocus();
    await keyboard.tab({ shift: true }); expect(screen.getByRole("link", { name: "Tổng quan" })).toHaveFocus();
    await keyboard.tab(); expect(close).toHaveFocus();
    await keyboard.keyboard("{Escape}"); expect(opener).toHaveFocus();
  });
});
describe("UserMenu", () => {
  it("opens with ArrowDown, handles menu keys, Escape and keyboard activation", async () => {
    const keyboard = userEvent.setup(), logout = vi.fn(); render(<UserMenu user={user} onLogout={logout} />);
    await keyboard.tab(); const trigger = screen.getByRole("button", { name: "Menu người dùng: Nguyễn An" });
    expect(trigger).toHaveAttribute("aria-haspopup", "menu");
    await keyboard.keyboard("{ArrowDown}"); const item = screen.getByRole("menuitem", { name: "Đăng xuất" });
    expect(item).toHaveFocus(); expect(trigger).toHaveAttribute("aria-expanded", "true");
    await keyboard.keyboard("{End}{Home}{ArrowUp}{Escape}"); expect(trigger).toHaveFocus();
    expect(trigger).toHaveAttribute("aria-expanded", "false");
    await keyboard.keyboard("{ArrowUp}{Enter}"); expect(logout).toHaveBeenCalledOnce(); expect(trigger).toHaveFocus();
  });
  it("Tab leaves the menu naturally without a focus trap", async () => {
    const keyboard = userEvent.setup(); render(<><UserMenu user={user} onLogout={() => {}} /><button>Tiếp theo</button></>);
    await keyboard.tab(); await keyboard.keyboard("{Enter}"); await keyboard.tab();
    expect(screen.getByRole("button", { name: "Tiếp theo" })).toHaveFocus(); expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });
});
