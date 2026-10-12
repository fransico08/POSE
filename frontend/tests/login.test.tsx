import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { LoginForm } from "@/components/auth/LoginForm";
const router = vi.hoisted(() => ({ replace: vi.fn(), refresh: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => router }));
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });
describe("login behavior", () => {
  it("uses native required validation, visible labels and expired notice", async () => {
    const fetcher = vi.fn(); vi.stubGlobal("fetch", fetcher); const keyboard = userEvent.setup();
    render(<LoginForm returnTo="/app" expired checkExisting={false} />);
    await keyboard.click(screen.getByRole("button", { name: "Đăng nhập" }));
    expect(fetcher).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Mã tổ chức")).toHaveAttribute("autocomplete", "organization");
    expect(screen.getByLabelText("Email")).toHaveAttribute("autocomplete", "username");
    expect(screen.getByRole("status")).toHaveTextContent("Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.");
  });
  it("blocks duplicate submits, keeps organization and email, clears password without stealing focus", async () => {
    let complete!: (response: Response) => void;
    const fetcher = vi.fn(() => new Promise<Response>(resolve => { complete = resolve; }));
    vi.stubGlobal("fetch", fetcher); const keyboard = userEvent.setup();
    render(<LoginForm returnTo="/app" expired={false} checkExisting={false} />);
    const org = screen.getByLabelText("Mã tổ chức"), email = screen.getByLabelText("Email"), password = screen.getByLabelText("Mật khẩu");
    await keyboard.type(org, "fixture"); await keyboard.type(email, "person@example.test"); await keyboard.type(password, "synthetic input");
    await keyboard.keyboard("{Enter}{Enter}");
    expect(fetcher).toHaveBeenCalledOnce(); expect(screen.getByRole("button", { name: "Đang đăng nhập…" })).toBeDisabled();
    complete(Response.json({ code: "AUTH_INVALID_CREDENTIALS" }, { status: 401 }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Mã tổ chức, email hoặc mật khẩu không đúng."));
    expect(org).toHaveValue("fixture"); expect(email).toHaveValue("person@example.test"); expect(password).toHaveValue(""); expect(password).toHaveFocus();
    expect(router.replace).not.toHaveBeenCalled();
  });
});
