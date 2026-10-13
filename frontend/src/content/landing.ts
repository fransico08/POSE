export type LandingIconName = "FunnelIcon" | "IdentificationIcon" | "LifebuoyIcon" | "ArrowUpTrayIcon" | "KeyIcon" | "UserCircleIcon" | "UserGroupIcon" | "BriefcaseIcon" | "ChatBubbleLeftRightIcon" | "AcademicCapIcon" | "HomeModernIcon" | "ComputerDesktopIcon" | "ShoppingBagIcon" | "TruckIcon" | "HeartIcon";
export interface ContentCard { title: string; description: string; icon: LandingIconName }
export interface RoleCard { title: string; responsibilities: readonly string[]; icon: LandingIconName }
export interface FaqEntry { id: string; question: string; answer: string }

export const contentUpdatedAt = "2026-10-13";
export const scopeNote = "Nội dung dưới đây mô tả phạm vi thiết kế của đồ án. Giao diện hiện có đăng nhập và Tổng quan; các màn hình nghiệp vụ sẽ được triển khai ở giai đoạn sau.";
export const productCaption = "Tổng quan hiện có các lối tắt theo quyền. Những màn hình nghiệp vụ trong ảnh sẽ có ở giai đoạn sau.";

export const landingMeta = {
  "title": "POSE CRM — đồ án CRM bán hàng và chăm sóc khách hàng",
  "description": "POSE CRM là đồ án CRM đa ngành, thiết kế cho quản lý Lead, Cơ hội bán hàng, Customer 360, chăm sóc khách hàng và nhập dữ liệu có duyệt theo quyền.",
  "imageAlt": "Màn hình Tổng quan của POSE CRM với lối tắt theo quyền của vai trò Quản lý"
};

export const commonCopy = {
  "brand": "POSE CRM",
  "login": "Đăng nhập",
  "accountNote": "Tài khoản do quản trị viên tổ chức cấp",
  "footerDescription": "Đồ án Tiểu luận Chuyên ngành năm 2026 của nhóm POSE tại Trường Đại học Sư phạm Kỹ thuật TP. Hồ Chí Minh."
};

export const landingNavigation = [
  {
    "label": "Năng lực",
    "href": "#nang-luc"
  },
  {
    "label": "Quy trình",
    "href": "#quy-trinh"
  },
  {
    "label": "Vai trò",
    "href": "#vai-tro"
  },
  {
    "label": "Bảo mật",
    "href": "#bao-mat"
  },
  {
    "label": "Câu hỏi",
    "href": "#cau-hoi"
  }
];

export const heroCopy = {
  "eyebrow": "Đồ án CRM đa ngành",
  "title": "POSE CRM — quản lý bán hàng và chăm sóc khách hàng đa ngành",
  "introduction": "POSE CRM là đồ án quản lý quan hệ khách hàng cho doanh nghiệp đa ngành, dành cho Quản trị hệ thống, Quản lý, Nhân viên kinh doanh, Nhân viên chăm sóc khách hàng và Nhân viên dữ liệu.",
  "description": "Phạm vi thiết kế kết nối Lead, Cơ hội bán hàng, hồ sơ Khách hàng và hoạt động chăm sóc, với quyền truy cập theo tổ chức và phần việc được giao."
};

export const sectionCopy = {
  "capabilities": {
    "title": "Năm nhóm năng lực chính",
    "introduction": "Thiết kế POSE CRM tập trung vào bán hàng, hồ sơ khách hàng, chăm sóc, nhập dữ liệu và phân quyền. Các nhóm này cùng sử dụng dữ liệu nghiệp vụ của từng tổ chức."
  },
  "problems": {
    "title": "Những khó khăn POSE CRM hướng tới",
    "introduction": "POSE CRM hướng tới việc nối thông tin khách hàng với người phụ trách và công việc liên quan. Các quy tắc dưới đây định hướng cách tổ chức dữ liệu, bàn giao và kiểm soát thay đổi."
  },
  "process": {
    "title": "Quy trình bán hàng và chăm sóc trên POSE",
    "introduction": "Thiết kế kết nối việc tiếp nhận Lead, theo dõi Cơ hội bán hàng và phân công chăm sóc. Cơ hội có thể gắn với Lead hoặc Khách hàng; việc chuyển Lead thành Khách hàng dựa trên xác nhận đủ thông tin."
  },
  "roles": {
    "title": "Mỗi vai trò làm gì trên POSE",
    "introduction": "POSE CRM thiết kế năm vai trò với trách nhiệm khác nhau. Quyền thực tế còn phụ thuộc quyền được cấp và phạm vi dữ liệu, kể cả khi một người có nhiều vai trò."
  },
  "industries": {
    "title": "Một nền tảng cho nhiều ngành",
    "introduction": "POSE CRM dùng mô hình chung cho sản phẩm và dịch vụ, có danh mục và thuộc tính mở rộng. Dữ liệu demo minh họa sáu ngành; mỗi tổ chức được tách riêng dữ liệu."
  },
  "trust": {
    "title": "Phân quyền và an toàn dữ liệu",
    "introduction": "Thiết kế POSE CRM kiểm tra tổ chức, quyền và phạm vi bản ghi trước khi cho phép thao tác. Quy tắc nhập dữ liệu, ghi chú nội bộ và nhật ký giúp kiểm soát việc sử dụng dữ liệu."
  },
  "faq": {
    "title": "Câu hỏi thường gặp",
    "introduction": "Các câu trả lời dưới đây giải thích phạm vi thiết kế, cách truy cập và trách nhiệm sử dụng dữ liệu của POSE CRM. Tài khoản do quản trị viên tổ chức cấp."
  }
};

export const capabilities = [
  {
    "title": "Lead và Cơ hội bán hàng",
    "description": "Theo dõi Lead và giai đoạn Cơ hội bán hàng gắn với một sản phẩm hoặc dịch vụ.",
    "icon": "FunnelIcon"
  },
  {
    "title": "Khách hàng và Customer 360",
    "description": "Tổng hợp hồ sơ Khách hàng cùng Lead, Cơ hội bán hàng, tương tác và nhiệm vụ liên quan.",
    "icon": "IdentificationIcon"
  },
  {
    "title": "Chăm sóc và yêu cầu hỗ trợ",
    "description": "Ghi nhận chăm sóc, nhiệm vụ và quá trình xử lý yêu cầu hỗ trợ được giao.",
    "icon": "LifebuoyIcon"
  },
  {
    "title": "Dữ liệu và nhập liệu có duyệt",
    "description": "Kiểm tra, chuẩn hóa dữ liệu từ file trước một bước duyệt để đưa vào CRM.",
    "icon": "ArrowUpTrayIcon"
  },
  {
    "title": "Phân quyền theo vai trò",
    "description": "Quyền theo vai trò đi cùng kiểm tra tổ chức, nhóm và phạm vi từng bản ghi.",
    "icon": "KeyIcon"
  }
] as const satisfies readonly ContentCard[];

export const problems = [
  {
    "title": "Hồ sơ và công việc thiếu liên kết",
    "description": "Khi thông tin được ghi rời nhau, việc theo dõi khách hàng dễ thiếu ngữ cảnh. Thiết kế POSE gắn tương tác và nhiệm vụ với Lead hoặc Khách hàng, rồi tổng hợp vào Customer 360.",
    "icon": "FunnelIcon"
  },
  {
    "title": "Bàn giao chưa rõ trách nhiệm",
    "description": "Việc đổi người phụ trách cần người nhận phù hợp và quyết định rõ ràng. Yêu cầu Bàn giao có lý do, được Quản lý duyệt; trách nhiệm hiện tại giữ nguyên trong lúc chờ.",
    "icon": "IdentificationIcon"
  },
  {
    "title": "Truy cập vượt phần việc được giao",
    "description": "Có vai trò chưa đủ để truy cập mọi hồ sơ. Thiết kế kiểm tra quyền, tổ chức và phạm vi được giao trước khi cho phép xem hoặc sửa.",
    "icon": "LifebuoyIcon"
  },
  {
    "title": "File nhập có lỗi hoặc nghi ngờ trùng",
    "description": "Dữ liệu cần được chuẩn hóa và rà soát trước khi sử dụng. Kết quả kiểm tra được gửi duyệt; bản ghi nghi ngờ trùng không tự gộp vào dữ liệu CRM.",
    "icon": "ArrowUpTrayIcon"
  },
  {
    "title": "Cơ hội đã đóng vẫn bị sửa",
    "description": "Kết quả bán hàng cần lưu lý do và kiểm soát việc mở lại. Cơ hội Thắng hoặc Thua được khóa với Nhân viên kinh doanh; Quản lý có quyền mở lại về Đủ điều kiện.",
    "icon": "KeyIcon"
  }
] as const satisfies readonly ContentCard[];

export const processSteps = [
  {
    "title": "Tiếp nhận Lead",
    "description": "Nhân viên kinh doanh tạo Lead trong phạm vi được giao, ghi nguồn, thông tin liên hệ và sản phẩm hoặc dịch vụ quan tâm."
  },
  {
    "title": "Xác nhận đủ điều kiện",
    "description": "Lead được đánh giá và chuyển sang Đủ điều kiện khi phù hợp; Lead không đủ điều kiện được loại với lý do."
  },
  {
    "title": "Theo dõi Cơ hội bán hàng",
    "description": "Mỗi Cơ hội gắn với một Lead hoặc Khách hàng và đúng một sản phẩm hoặc dịch vụ. Giai đoạn đi từ Mới → Đủ điều kiện → Đề xuất → Thắng hoặc Thua; khi đóng phải lưu lý do."
  },
  {
    "title": "Chuyển Lead thành Khách hàng",
    "description": "Quản lý hoặc Nhân viên kinh doanh phụ trách xác nhận đủ thông tin để chuyển Lead. Hệ thống tái sử dụng Khách hàng khớp định danh nếu đã tồn tại và liên kết tương tác, nhiệm vụ, Cơ hội của Lead."
  },
  {
    "title": "Đề nghị Bàn giao chăm sóc",
    "description": "Nhân viên kinh doanh phụ trách gửi yêu cầu có lý do. Quản lý duyệt để phân công Khách hàng cho Nhân viên chăm sóc khách hàng phù hợp."
  },
  {
    "title": "Chăm sóc và xử lý hỗ trợ",
    "description": "Nhân viên chăm sóc khách hàng xem hồ sơ được giao, ghi nhận chăm sóc, nhiệm vụ và yêu cầu hỗ trợ. Yêu cầu chuyển cấp cần lý do và được Quản lý xử lý trong phạm vi nhóm."
  }
];

export const roleCards = [
  {
    "title": "Quản trị hệ thống",
    "responsibilities": [
      "Quản lý người dùng, vai trò và quyền trong tổ chức.",
      "Xem nhật ký thao tác theo quyền.",
      "Hỗ trợ dữ liệu CRM khi được cấp quyền phù hợp."
    ],
    "icon": "UserCircleIcon"
  },
  {
    "title": "Quản lý",
    "responsibilities": [
      "Quản lý sản phẩm, dịch vụ và phân công trong nhóm phụ trách.",
      "Điều phối Lead, Khách hàng, Cơ hội bán hàng và nhiệm vụ.",
      "Duyệt nhập dữ liệu, Bàn giao và xử lý yêu cầu hỗ trợ chuyển cấp."
    ],
    "icon": "UserGroupIcon"
  },
  {
    "title": "Nhân viên kinh doanh",
    "responsibilities": [
      "Làm việc với sản phẩm, dịch vụ có phân công hiệu lực.",
      "Quản lý Lead, Khách hàng và Cơ hội bán hàng trong phạm vi phụ trách.",
      "Ghi tương tác, thực hiện nhiệm vụ và đề nghị Bàn giao Khách hàng."
    ],
    "icon": "BriefcaseIcon"
  },
  {
    "title": "Nhân viên chăm sóc khách hàng",
    "responsibilities": [
      "Xem hồ sơ và Customer 360 của Khách hàng được giao.",
      "Ghi nhận tương tác, phản hồi và nhiệm vụ chăm sóc.",
      "Xử lý hoặc chuyển cấp yêu cầu hỗ trợ được giao."
    ],
    "icon": "ChatBubbleLeftRightIcon"
  },
  {
    "title": "Nhân viên dữ liệu",
    "responsibilities": [
      "Chuẩn bị file, kiểm tra và chuẩn hóa dữ liệu nhập.",
      "Rà soát bản ghi lỗi hoặc nghi ngờ trùng để gửi kết quả duyệt.",
      "Theo dõi chất lượng dữ liệu và báo cáo theo quyền."
    ],
    "icon": "ArrowUpTrayIcon"
  }
] as const satisfies readonly RoleCard[];

export const industries = [
  {
    "title": "Giáo dục",
    "icon": "AcademicCapIcon"
  },
  {
    "title": "Bất động sản",
    "icon": "HomeModernIcon"
  },
  {
    "title": "Phần mềm/SaaS",
    "icon": "ComputerDesktopIcon"
  },
  {
    "title": "Bán lẻ/F&B",
    "icon": "ShoppingBagIcon"
  },
  {
    "title": "Logistics",
    "icon": "TruckIcon"
  },
  {
    "title": "Phòng khám",
    "icon": "HeartIcon"
  }
] as const satisfies readonly Pick<ContentCard, "title" | "icon">[];

export const industryNote = "Dữ liệu minh họa trong bản demo, mỗi tổ chức được tách riêng dữ liệu.";

export const trustItems = [
  {
    "title": "Tách theo tổ chức",
    "description": "Mỗi bản ghi nghiệp vụ thuộc một tổ chức. Vai trò không cho phép truy cập sang tổ chức khác."
  },
  {
    "title": "Vai trò đi cùng quyền",
    "description": "Quyền của các vai trò được kết hợp, nhưng thao tác vẫn phải qua kiểm tra phạm vi dữ liệu."
  },
  {
    "title": "Phần việc được giao",
    "description": "Nhân viên kinh doanh cần phân công sản phẩm hoặc dịch vụ hiệu lực và phạm vi phụ trách phù hợp. Nhân viên chăm sóc thao tác trên hồ sơ hoặc công việc được giao."
  },
  {
    "title": "Ghi chú nội bộ bán hàng",
    "description": "Ghi chú chỉ dành cho bán hàng không hiển thị với nhân viên chăm sóc khách hàng; ghi chú chia sẻ với bộ phận chăm sóc chỉ được xem khi có quyền và phạm vi phù hợp."
  },
  {
    "title": "Nhập dữ liệu có duyệt",
    "description": "Nhân viên dữ liệu chuẩn bị kết quả kiểm tra; Quản lý hoặc Quản trị hệ thống duyệt một bước trước khi dữ liệu được ghi vào CRM."
  },
  {
    "title": "Nhật ký thao tác",
    "description": "Thiết kế ghi người thao tác, hành động, đối tượng và thời điểm cho các thay đổi cần truy vết."
  }
];

export const faqEntries = [
  {
    "id": "faq-01",
    "question": "POSE CRM là gì?",
    "answer": "POSE CRM là đồ án hệ thống quản lý quan hệ khách hàng dành cho doanh nghiệp đa ngành. Phạm vi thiết kế gồm bán hàng, hồ sơ Khách hàng, chăm sóc và nhập dữ liệu có kiểm soát."
  },
  {
    "id": "faq-02",
    "question": "Ai có thể sử dụng POSE CRM?",
    "answer": "POSE CRM thiết kế cho Quản trị hệ thống, Quản lý, Nhân viên kinh doanh, Nhân viên chăm sóc khách hàng và Nhân viên dữ liệu. Chức năng truy cập phụ thuộc quyền và phạm vi được giao."
  },
  {
    "id": "faq-03",
    "question": "Làm thế nào để có tài khoản?",
    "answer": "Tài khoản do quản trị viên tổ chức cấp. Người dùng liên hệ quản trị viên của tổ chức để nhận thông tin truy cập."
  },
  {
    "id": "faq-04",
    "question": "Đăng nhập cần những thông tin gì?",
    "answer": "Đăng nhập cần mã tổ chức, email và mật khẩu của tài khoản được cấp. Người dùng lấy mã tổ chức từ quản trị viên, không tự đăng ký trên trang giới thiệu."
  },
  {
    "id": "faq-05",
    "question": "Dữ liệu các tổ chức có tách biệt không?",
    "answer": "Có. Theo thiết kế, mỗi bản ghi nghiệp vụ thuộc một tổ chức và quyền truy cập phải được kiểm tra trong tổ chức đó. Có nhiều vai trò không cho phép xem dữ liệu của tổ chức khác."
  },
  {
    "id": "faq-06",
    "question": "Nhân viên chăm sóc có xem được ghi chú nội bộ của bán hàng không?",
    "answer": "Ghi chú chỉ dành cho bán hàng không hiển thị với nhân viên chăm sóc khách hàng; ghi chú chia sẻ với bộ phận chăm sóc chỉ được xem khi có quyền và phạm vi phù hợp."
  },
  {
    "id": "faq-07",
    "question": "Nhập dữ liệu từ file hoạt động thế nào?",
    "answer": "Theo thiết kế, Nhân viên dữ liệu chuẩn bị file CSV/Excel, kiểm tra và chuẩn hóa dữ liệu. Kết quả cần Quản lý hoặc Quản trị hệ thống duyệt một bước trước khi ghi vào CRM; bản ghi lỗi hoặc nghi ngờ trùng được rà soát thay vì tự gộp."
  },
  {
    "id": "faq-08",
    "question": "Khi nào cần Bàn giao Khách hàng?",
    "answer": "Khi cần chuyển trách nhiệm bán hàng hoặc giao Khách hàng cho bộ phận chăm sóc, Nhân viên kinh doanh phụ trách gửi yêu cầu Bàn giao có lý do. Quản lý duyệt người nhận phù hợp; người phụ trách hiện tại giữ nguyên trong lúc chờ."
  }
] as const satisfies readonly FaqEntry[];

export const ctaCopy = {
  "title": "Vào không gian làm việc của tổ chức",
  "description": "Nếu đã được cấp tài khoản, hãy đăng nhập để vào Tổng quan và xem lối tắt theo quyền của bạn."
};
