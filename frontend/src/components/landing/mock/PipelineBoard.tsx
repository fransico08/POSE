import { DealCard } from "./DealCard";
export function PipelineBoard() {
  return <div className="mock-pipeline"><div className="mock-window-bar"><span /><span /><span /><p>POSE CRM / Cơ hội</p></div><div className="mock-pipeline-columns">
    <div className="mock-column"><p className="mock-column-title">Mới</p><DealCard title="Gói dịch vụ mẫu" initials="DV" stage="Mới" task="Liên hệ hôm nay" /><DealCard title="Dịch vụ tư vấn mẫu" initials="TV" stage="Mới" task="Rà soát nhu cầu" /></div>
    <div className="mock-column"><p className="mock-column-title">Đủ điều kiện</p><DealCard title="Gói đào tạo cơ bản" initials="GĐ" stage="Đủ điều kiện" task="Theo dõi ngày mai" moving /><DealCard title="Gói hỗ trợ mẫu" initials="HT" stage="Đủ điều kiện" task="Liên hệ hôm nay" /></div>
    <div className="mock-column"><p className="mock-column-title">Đề xuất / Thắng</p><DealCard title="Gói chăm sóc cơ bản" initials="CS" stage="Đề xuất" task="Theo dõi ngày mai" /><DealCard title="Dịch vụ triển khai mẫu" initials="TK" stage="Đề xuất" task="Rà soát nhu cầu" /></div>
  </div></div>;
}
