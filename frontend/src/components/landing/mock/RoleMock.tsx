const roleActivities: Record<string, readonly string[]> = {
  manager: ["Phân công nhiệm vụ", "Bàn giao chờ duyệt", "Dữ liệu chờ duyệt", "Cơ hội cần theo dõi", "Công việc của đội"],
  sales: ["Lead cần liên hệ", "Cơ hội đang theo dõi", "Ghi nhận tương tác", "Nhiệm vụ đến hạn", "Hồ sơ được giao"],
  care: ["Hồ sơ được giao", "Phản hồi cần xử lý", "Nhiệm vụ đến hạn", "Yêu cầu hỗ trợ", "Lịch sử tương tác"],
  data: ["Chuẩn hóa dữ liệu", "Dòng hợp lệ", "Dòng cần sửa", "Bản ghi nghi trùng", "Gửi kết quả duyệt"],
  admin: ["Người dùng tổ chức", "Vai trò được cấp", "Quyền theo trách nhiệm", "Phạm vi dữ liệu", "Nhật ký thao tác"],
};
const labels: Record<string, string> = { manager: "Công việc cần điều phối", sales: "Công việc bán hàng", care: "Công việc chăm sóc", data: "Kiểm tra dữ liệu mẫu", admin: "Quyền trong tổ chức" };
export function RoleMock({ role }: { role: string }) {
  const rows = roleActivities[role] ?? roleActivities.manager;
  return <div className="role-mock mock-review" aria-hidden="true"><p className="mock-review-heading">{labels[role]}</p><ul>{rows.map((label, i) => <li key={label}><span className="mock-avatar">{label.split(" ").slice(0, 2).map(word => word[0]).join("")}</span><span className="mock-role-copy"><strong>{label}</strong><small>Hạn: {i % 3 === 0 ? "Hôm nay" : i % 3 === 1 ? "Ngày mai" : "Tuần này"}</small></span><span className="mock-badge">{i === 0 ? "Đã kiểm tra" : i === 1 ? "Chờ duyệt" : i === 2 ? "Cần xử lý" : i === 3 ? "Đang theo dõi" : "Được giao"}</span></li>)}</ul></div>;
}
