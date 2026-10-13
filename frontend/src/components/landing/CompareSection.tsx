import { sectionCopy } from "@/content/landing";
import { SectionHeading } from "./SectionHeading";
import { CompareSlider } from "./CompareSlider";
import { PipelineBoard } from "./mock/PipelineBoard";
export function CompareSection() {
  const before = <div className="mock-scattered"><p className="compare-label">Chưa có POSE</p><div className="mock-sheet"><strong>Bảng tính</strong><div className="mock-sheet-header"><span>Khách hàng</span><span>Dịch vụ</span><span>Phụ trách</span></div>{Array.from({ length: 6 }, (_, i) => <div className="mock-sheet-row" key={i} data-duplicate={i === 1 || i === 3 || undefined}><span>Khách hàng mẫu {i === 3 ? "B" : String.fromCharCode(65 + i)}</span><span>Dịch vụ mẫu</span><span>{i === 1 || i === 3 ? "Trùng" : "Chưa phân công"}</span></div>)}</div><div className="scattered-notes">{["Liên hệ lại", "Cần xác nhận", "Chờ thông tin", "Chưa ghi nhận"].map(text => <p key={text}>{text}</p>)}</div><div className="scattered-messages"><p>Đã nhắn lại</p><p>Ai tiếp nhận?</p></div><p className="scattered-warning">Thiếu người phụ trách</p></div>;
  const after = <div className="mock-connected"><p className="compare-label">Với POSE</p><PipelineBoard /></div>;
  return <section className="landing-section landing-surface" aria-labelledby="compare-title"><div className="landing-container"><SectionHeading id="compare-title" {...sectionCopy.compare} /><CompareSlider before={before} after={after} /><div className="compare-descriptions"><p data-marketing>{sectionCopy.compare.before}</p><p data-marketing>{sectionCopy.compare.after}</p></div></div></section>;
}
