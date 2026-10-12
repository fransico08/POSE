import Link from "next/link";
import { MagnifyingGlassIcon } from "@heroicons/react/24/outline";
export default function NotFound() { return <main id="noi-dung" className="status-page"><MagnifyingGlassIcon aria-hidden="true" /><p className="eyebrow">Lỗi 404</p><h1>Không tìm thấy trang</h1><p>Trang bạn tìm có thể đã thay đổi hoặc không tồn tại.</p><Link className="button button-primary" href="/app">Về Tổng quan</Link></main>; }
