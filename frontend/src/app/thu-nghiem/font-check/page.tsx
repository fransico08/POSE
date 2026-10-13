import { Fraunces, Plus_Jakarta_Sans } from "next/font/google";
import { notFound } from "next/navigation";
import "./font-check.css";

const jakarta = Plus_Jakarta_Sans({ weight: "800", subsets: ["vietnamese", "latin"], display: "swap", adjustFontFallback: false, fallback: [] });
const fraunces = Fraunces({ weight: ["700", "800"], subsets: ["vietnamese", "latin"], display: "swap", adjustFontFallback: false, fallback: [] });
const glyphText = "Đăng nhập Quản lý Khách hàng ạ ả ấ ầ ẩ ẫ ậ ắ ằ ẳ ẵ ặ ẹ ẻ ẽ ế ề ể ễ ệ ỉ ị ọ ỏ ố ồ ổ ỗ ộ ớ ờ ở ỡ ợ ụ ủ ứ ừ ử ữ ự ỳ ỵ ỷ ỹ đ Đ ơ ư";
export const metadata = { title: "Kiểm chữ tiếng Việt", robots: { index: false, follow: false } };

export default function FontCheckPage() {
  if (process.env.NODE_ENV !== "development") notFound();
  return <main className="font-check"><h1>Kiểm chữ tiếng Việt</h1>
    <section aria-labelledby="jakarta-title"><h2 id="jakarta-title">Plus Jakarta Sans 800</h2><p className={`${jakarta.className} font-check-jakarta`} id="jakarta-glyphs">{glyphText}</p></section>
    <section aria-labelledby="fraunces-title"><h2 id="fraunces-title">Fraunces tĩnh 700/800 · không axes</h2>{[700, 800].map(weight => <p key={weight} id={`fraunces-${weight}`} className={`${fraunces.className} font-check-fraunces font-check-weight-${weight}`}>{glyphText}</p>)}</section>
  </main>;
}
