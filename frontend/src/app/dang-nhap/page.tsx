import "@/styles/components.css";
import "@/styles/pages.css";
import { cookies } from "next/headers";
import { LoginIllustration } from "@/components/auth/LoginIllustration";
import { Customer360Card } from "@/components/landing/mock/Customer360Card";
import { LoginForm } from "@/components/auth/LoginForm";
import { safeReturnTo } from "@/lib/session/return-to";
import { Providers } from "@/components/session/Providers";
export const metadata = { title: "Đăng nhập", robots: { index: false, follow: false } };
export default async function LoginPage({ searchParams }: { searchParams: Promise<{ returnTo?: string; reason?: string }> }) {
  const params = await searchParams, store = await cookies();
  return <Providers><div className="login-layout"><LoginForm returnTo={safeReturnTo(params.returnTo)} expired={params.reason === "expired"} checkExisting={store.has("pose_session_present")} /><aside className="login-panel" aria-labelledby="login-panel-title">
    <p className="login-brand"><span className="brand-symbol" aria-hidden="true">P</span>POSE CRM</p>
    <div className="login-panel-content"><p className="eyebrow">Kết nối để chăm sóc tốt hơn</p><h2 id="login-panel-title">Một không gian chung.<br />Hiểu khách hàng hơn.</h2><p>Giữ thông tin và công việc liền mạch trong từng bước của hành trình khách hàng.</p>
      <LoginIllustration><Customer360Card /></LoginIllustration>
    </div><footer>Dành cho đội ngũ của tổ chức bạn.</footer>
  </aside></div></Providers>;
}
