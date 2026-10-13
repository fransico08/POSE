import { commonCopy, contentUpdatedAt } from "@/content/landing";

export function LandingFooter() {
  return <footer className="landing-footer landing-surface"><div className="landing-container">
    <p className="landing-brand">{commonCopy.brand}</p><p>{commonCopy.footerDescription}</p>
    <nav aria-label="Điều hướng cuối trang"><ul><li><a href="/dang-nhap">{commonCopy.login}</a></li><li><a href="#cau-hoi">Câu hỏi thường gặp</a></li></ul></nav>
    <p>Cập nhật lần cuối: <time dateTime={contentUpdatedAt}>{contentUpdatedAt.split("-").reverse().join("/")}</time></p>
  </div></footer>;
}
