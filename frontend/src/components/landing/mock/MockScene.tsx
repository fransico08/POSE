import { CheckCircleIcon, LockClosedIcon } from "@heroicons/react/20/solid";
import { PipelineBoard } from "./PipelineBoard";
import { Customer360Card } from "./Customer360Card";
export function MockScene() {
  return <div className="hero-scene" aria-hidden="true"><div className="hero-device-float"><div className="hero-device"><PipelineBoard /></div></div><div className="hero-floating-row"><div className="hero-customer-float"><Customer360Card /></div><div className="hero-side-stack"><p className="hero-toast"><CheckCircleIcon />Bàn giao đã được Quản lý duyệt</p><p className="hero-private-note"><LockClosedIcon />Ghi chú chỉ dành cho bán hàng</p></div></div></div>;
}
