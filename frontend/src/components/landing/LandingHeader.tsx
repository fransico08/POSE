import { commonCopy, landingNavigation } from "@/content/landing";
import { MobileNavDisclosure } from "./MobileNavDisclosure";

export function LandingHeader() {
  return <header className="landing-header">
    <div className="landing-desktop-bar"><div className="landing-header-inner landing-container">
      {/* Use a native document link to avoid adding client routing to the public page. */}
      {/* eslint-disable-next-line @next/next/no-html-link-for-pages */}
      <a className="landing-brand" href="/">{commonCopy.brand}</a>
      <nav className="landing-desktop-navigation" aria-label="Điều hướng trang giới thiệu">
        <ul>{landingNavigation.map(item => <li key={item.href}><a href={item.href}>{item.label}</a></li>)}</ul>
      </nav>
      <a className="button button-primary landing-desktop-login" href="/dang-nhap">{commonCopy.login}</a>
    </div></div>
    <MobileNavDisclosure />
  </header>;
}
