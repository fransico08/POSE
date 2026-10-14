import { LockClosedIcon, ShieldCheckIcon } from "@heroicons/react/20/solid";
export function SecurityCard() {
  return <><p><ShieldCheckIcon />Khách hàng mẫu</p><p className="security-note"><LockClosedIcon /><span>Ghi chú bán hàng</span></p><p className="security-note-state security-state-care">Không hiển thị</p><p className="security-note-state security-state-sales">Trong phạm vi được giao</p></>;
}
