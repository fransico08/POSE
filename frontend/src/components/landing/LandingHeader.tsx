import Link from "next/link";
import { landingNavigation } from "@/content/landing";
import { HeaderScrollState } from "./HeaderScrollState";
export function LandingHeader() {
  return <HeaderScrollState><Link className="landing-brand" href="/">POSE CRM</Link><nav className="landing-desktop-navigation" aria-label="Điều hướng trang giới thiệu"><ul>{landingNavigation.map(item => <li key={item.href}><a href={item.href}>{item.label}</a></li>)}</ul></nav><Link className="landing-button landing-primary header-login" href="/dang-nhap" prefetch={false}>Đăng nhập</Link></HeaderScrollState>;
}
