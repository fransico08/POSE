import Link from "next/link";
import { ShieldExclamationIcon } from "@heroicons/react/24/outline";
export const metadata = { title: "Không có quyền truy cập" };
export default function ForbiddenPage() { return <section className="status-content"><ShieldExclamationIcon aria-hidden="true" /><p className="eyebrow">Lỗi 403</p><h1>Không có quyền truy cập</h1><p>Bạn chưa được cấp quyền sử dụng chức năng này. Liên hệ quản trị viên nếu cần hỗ trợ.</p><Link className="button button-primary" href="/app">Về Tổng quan</Link></section>; }
