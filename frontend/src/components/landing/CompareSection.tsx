import { sectionCopy } from "@/content/landing";
import { SectionHeading } from "./SectionHeading";
import { CompareSlider } from "./CompareSlider";
import { PipelineBoard } from "./mock/PipelineBoard";
export function CompareSection() {
  const before = <div className="mock-scattered"><p className="compare-label">Chưa có POSE</p><article><strong>Bảng tính</strong><span>Khách hàng mẫu</span><span>Gói dịch vụ mẫu</span><span>Chưa rõ người nhận</span></article><article><strong>Ghi chú</strong><span>Liên hệ lại</span></article><article><strong>Công việc</strong><span>Chờ thông tin</span></article></div>;
  const after = <div className="mock-connected"><p className="compare-label">Với POSE</p><PipelineBoard /></div>;
  return <section className="landing-section landing-surface" aria-labelledby="compare-title"><div className="landing-container"><SectionHeading id="compare-title" {...sectionCopy.compare} /><CompareSlider before={before} after={after} /><div className="compare-descriptions"><p data-marketing>{sectionCopy.compare.before}</p><p data-marketing>{sectionCopy.compare.after}</p></div></div></section>;
}
