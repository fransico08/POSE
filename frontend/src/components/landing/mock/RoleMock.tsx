import { Customer360Card } from "./Customer360Card";
import { PipelineBoard } from "./PipelineBoard";
import { CheckCircleIcon, ClipboardDocumentCheckIcon, KeyIcon } from "@heroicons/react/20/solid";
export function RoleMock({ role }: { role: string }) {
  if (role === "sales") return <div className="role-mock" aria-hidden="true"><PipelineBoard /></div>;
  if (role === "care") return <div className="role-mock" aria-hidden="true"><Customer360Card /></div>;
  return <div className="role-mock mock-review" aria-hidden="true"><p className="mock-review-heading">{role === "manager" ? "Công việc cần điều phối" : role === "data" ? "Kiểm tra dữ liệu mẫu" : "Quyền trong tổ chức"}</p><ul>{(role === "manager" ? ["Nhiệm vụ cần phân công", "Bàn giao chờ duyệt", "Dữ liệu chờ duyệt"] : role === "data" ? ["Dòng hợp lệ", "Dòng cần sửa", "Bản ghi nghi trùng"] : ["Người dùng", "Vai trò và quyền", "Nhật ký thao tác"]).map((label, i) => <li key={label}>{i === 0 ? <CheckCircleIcon /> : role === "admin" ? <KeyIcon /> : <ClipboardDocumentCheckIcon />}<span>{label}</span><span className="mock-badge">{i === 0 ? "Đã kiểm tra" : "Cần xử lý"}</span></li>)}</ul></div>;
}
