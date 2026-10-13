import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { LoginForm } from "@/components/auth/LoginForm";
const router = vi.hoisted(() => ({ replace: vi.fn(), refresh: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => router }));
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });
describe("login behavior", () => {
  it.each([
    { name: "HTTP 400", status: 400, message: "Mã tổ chức, email hoặc mật khẩu không đúng." },
    { name: "HTTP 401", status: 401, message: "Mã tổ chức, email hoặc mật khẩu không đúng." },
    { name: "HTTP 500", status: 500, message: "Không thể kết nối. Vui lòng thử lại." },
    { name: "HTTP 503", status: 503, message: "Không thể kết nối. Vui lòng thử lại." },
    { name: "forbidden origin", status: 403, message: "Không thể kết nối. Vui lòng thử lại." },
    { name: "offline", status: 0, message: "Không thể kết nối. Vui lòng thử lại." },
    { name: "timeout", status: 0, message: "Không thể kết nối. Vui lòng thử lại." },
  ])("$name shows the right alert and retains only organization/email", async ({ name, status, message }) => {
    const fetcher = vi.fn();
    if (status) fetcher.mockResolvedValue(Response.json({ code: status === 403 ? "FORBIDDEN_ORIGIN" : "UNKNOWN_ERROR", message: "Private backend detail" }, { status }));
    else fetcher.mockRejectedValue(name === "timeout" ? new DOMException("Timed out", "TimeoutError") : new TypeError("Offline"));
    vi.stubGlobal("fetch", fetcher);
    const keyboard = userEvent.setup(); render(<LoginForm returnTo="/app" expired={false} checkExisting={false} />);
    const org = screen.getByLabelText("Mã tổ chức"), email = screen.getByLabelText("Email"), password = screen.getByLabelText("Mật khẩu");
    await keyboard.type(org, "fixture"); await keyboard.type(email, "person@example.test"); await keyboard.type(password, "synthetic input");
    await keyboard.keyboard("{Enter}");
    await waitFor(() => expect(screen.getByRole("alert").textContent).toBe(message));
    expect(org).toHaveValue("fixture"); expect(email).toHaveValue("person@example.test"); expect(password).toHaveValue(""); expect(password).toHaveFocus();
    expect(screen.queryByText("Private backend detail")).not.toBeInTheDocument(); expect(router.replace).not.toHaveBeenCalled();
    expect(fetcher).toHaveBeenCalledOnce();
  });
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
