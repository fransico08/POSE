import { ArrowRightIcon, CheckCircleIcon, ClipboardDocumentCheckIcon, LockClosedIcon, UserCircleIcon } from "@heroicons/react/24/outline";

export function MockUiStage() {
  return <div className="concept-scene" aria-hidden="true">
    <div className="concept-device concept-float-one">
      <div className="concept-device-bar"><span className="concept-device-dot" /><span className="concept-device-dot" /><span className="concept-device-dot" /><span className="concept-device-line" /></div>
      <div className="concept-pipeline"><div className="concept-columns"><p>Đề xuất</p><p><CheckCircleIcon />Thắng</p></div>
        <div className="concept-deal"><span className="concept-deal-icon"><ClipboardDocumentCheckIcon /></span><p>Gói đào tạo cơ bản</p><span className="concept-deal-lines" /><small className="concept-closing-reason">Lý do: Đồng ý</small><ArrowRightIcon className="concept-deal-arrow" /></div>
        <div className="concept-empty-lines"><span /><span /><span /></div>
      </div>
    </div>
    <div className="concept-customer concept-float-two"><div className="concept-customer-title"><UserCircleIcon /><strong>Customer 360</strong></div><p>Khách hàng mẫu</p><div className="concept-customer-tabs"><span>Hồ sơ</span><span>Tương tác</span><span>Nhiệm vụ</span></div><span className="concept-customer-line" /><span className="concept-customer-line" /></div>
    <div className="concept-toast concept-float-three"><span className="concept-check"><CheckCircleIcon /></span><p>Bàn giao đã được<br />Quản lý duyệt</p></div>
    <p className="concept-private-note"><LockClosedIcon />Ghi chú chỉ dành cho bán hàng</p>
  </div>;
}
