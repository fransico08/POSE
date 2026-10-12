import { notFound } from "next/navigation";
import { HomeIcon, UserCircleIcon } from "@heroicons/react/24/outline";

const probe = "Đăng nhập Quản lý Khách hàng ạ ả ấ ầ ẩ ẫ ậ ắ ằ ẳ ẵ ặ ẹ ẻ ẽ ế ề ể ễ ệ ỉ ị ọ ỏ ố ồ ổ ỗ ộ ớ ờ ở ỡ ợ ụ ủ ứ ừ ử ữ ự ỳ ỵ ỷ ỹ đ Đ ơ ư";
export const metadata = { robots: { index: false, follow: false } };

export default async function FontProbe({ searchParams }: { searchParams: Promise<{ weight?: string }> }) {
  if (process.env.POSE_FONT_PROBE !== "1" || process.env.NODE_ENV === "production") notFound();
  const params = await searchParams;
  const weight = ["400", "500", "600", "700"].includes(params.weight ?? "") ? params.weight : "400";
  return <section className={`probe-layout weight-${weight}`}>
    <aside className="probe-sidebar" aria-label="Mẫu sidebar kiểm font">
      <p>POSE CRM</p><p>CHUNG</p>
      <p className="probe-navigation"><HomeIcon aria-hidden="true" />Tổng quan</p>
      <p>Khách hàng · Quản lý</p><p className="probe-navigation"><UserCircleIcon aria-hidden="true" />Người dùng</p>
    </aside>
    <main className="probe-main">
      <h1>Đăng nhập POSE CRM</h1><p>Mẫu kiểm next/font · độ đậm {weight}</p>
      <form>
        <label>Mã tổ chức<input autoComplete="organization" readOnly /></label>
        <label>Email<input type="email" autoComplete="username" readOnly /></label>
        <label>Mật khẩu<input type="password" autoComplete="current-password" readOnly /></label>
        <button type="button" className="probe-button">Đăng nhập</button>
      </form>
      <section className="probe-string"><h2>Chuỗi kiểm glyph</h2><p>{probe}</p>
        <p className="probe-word">Đ<span>ă</span>ng nh<span>ậ</span>p · Qu<span>ả</span>n lý · Khách hàng · ch<span>ă</span>m sóc</p>
      </section>
    </main>
  </section>;
}
