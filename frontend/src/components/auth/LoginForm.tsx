"use client";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { api, clearClientSession } from "@/lib/api";
import { safeReturnTo } from "@/lib/session/return-to";
import { TextField } from "@/components/ui/TextField";
import { PasswordField } from "@/components/ui/PasswordField";
import { Button } from "@/components/ui/Button";
import { Alert } from "@/components/ui/Alert";
import { Skeleton } from "@/components/ui/Skeleton";
const failure = "Mã tổ chức, email hoặc mật khẩu không đúng.";
const connectionFailure = "Không thể kết nối. Vui lòng thử lại.";
export function LoginForm({ returnTo, expired, checkExisting }: { returnTo: string; expired: boolean; checkExisting: boolean }) {
  const router = useRouter(), password = useRef<HTMLInputElement>(null), sending = useRef(false);
  const [checking, setChecking] = useState(checkExisting), [busy, setBusy] = useState(false), [error, setError] = useState<string | null>(null);
  useEffect(() => {
    if (!checkExisting) return;
    let active = true;
    void api.request("/api/session/me").then(() => { if (active) router.replace("/app"); }).catch(() => { if (active) setChecking(false); });
    return () => { active = false; };
  }, [checkExisting, router]);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending.current) return;
    sending.current = true; setBusy(true); setError(null);
    const form = new FormData(event.currentTarget);
    try {
      const response = await fetch("/api/session/login", { method: "POST", credentials: "same-origin", cache: "no-store", signal: AbortSignal.timeout(15000), headers: { "Content-Type": "application/json" }, body: JSON.stringify({ organizationCode: form.get("organizationCode"), email: form.get("email"), password: form.get("password") }) });
      if (!response.ok) {
        setError([400, 401].includes(response.status) ? failure : connectionFailure);
        return;
      }
      if (password.current) password.current.value = "";
      clearClientSession(); router.replace(safeReturnTo(returnTo)); router.refresh();
    } catch { setError(connectionFailure); }
    finally { if (password.current) password.current.value = ""; sending.current = false; setBusy(false); }
  }
  if (checking) return <Skeleton />;
  return <main id="noi-dung" className="login-main">
    <div className="login-form-container"><p className="eyebrow">POSE CRM</p><h1>Đăng nhập POSE CRM</h1><p className="login-intro">Chào mừng bạn trở lại. Đăng nhập để vào không gian làm việc của tổ chức.</p>
      {expired && <Alert tone="info">Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.</Alert>}
      {error && <Alert>{error}</Alert>}
      <form onSubmit={event => void submit(event)} aria-busy={busy} className="login-form">
        <TextField id="organization-code" name="organizationCode" label="Mã tổ chức" helper="Mã do quản trị viên cung cấp" autoComplete="organization" autoCapitalize="characters" required maxLength={100} readOnly={busy} />
        <TextField id="email" name="email" label="Email" type="email" autoComplete="username" required maxLength={254} readOnly={busy} />
        <PasswordField ref={password} id="password" name="password" label="Mật khẩu" required maxLength={256} readOnly={busy} />
        <Button type="submit" busy={busy}>{busy ? "Đang đăng nhập…" : "Đăng nhập"}</Button>
      </form><p className="login-help">Cần quyền truy cập? Liên hệ quản trị viên của tổ chức.</p>
    </div>
  </main>;
}
