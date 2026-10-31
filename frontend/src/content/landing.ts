export type LandingIconName = "IdentificationIcon" | "KeyIcon" | "ArrowUpTrayIcon" | "ArrowsRightLeftIcon" | "ClipboardDocumentCheckIcon" | "CubeIcon" | "AcademicCapIcon" | "HomeModernIcon" | "ComputerDesktopIcon" | "ShoppingBagIcon" | "TruckIcon" | "HeartIcon";
export interface FaqEntry { id: string; question: string; answer: string }
export interface ContentCard { id: string; title: string; description: string; icon: LandingIconName }
export interface Industry { id: string; label: string; icon: LandingIconName; sample: string; attributes: readonly { label: string; value: string; type: "Lựa chọn" | "Văn bản" | "Số" }[] }

export const contentUpdatedAt = "2026-10-31";

export const heroFrames = [
  { id: "education", label: "Giáo dục", description: "Theo dõi Lead, hẹn tư vấn và ghi nhận nhu cầu học." },
  { id: "property", label: "Bất động sản", description: "Theo sát nhu cầu nhà ở và từng Cơ hội bán hàng." },
  { id: "software", label: "Phần mềm/SaaS", description: "Kết nối nhu cầu phần mềm với tư vấn và chăm sóc." },
  { id: "retail", label: "Bán lẻ/F&B", description: "Ghi nhận tương tác để tiếp nối việc chăm sóc khách hàng." },
  { id: "logistics", label: "Logistics", description: "Phối hợp tư vấn dịch vụ giao nhận trên cùng hồ sơ." },
  { id: "clinic", label: "Phòng khám", description: "Theo dõi nhu cầu tư vấn và nhiệm vụ chăm sóc." },
] as const;

export const industryDefinitionCopy = {
  title: "Thuộc tính của danh mục",
  description: "Danh mục quyết định thuộc tính."
};

export const landingMeta = {
  "title": "POSE CRM — kết nối bán hàng và chăm sóc khách hàng",
  "description": "Kết nối Lead, Cơ hội bán hàng và Khách hàng trên POSE CRM. Phối hợp chăm sóc, bàn giao và nhập dữ liệu có duyệt, với quyền theo vai trò và phạm vi.",
  "imageAlt": "Giao diện minh họa bán hàng và chăm sóc khách hàng trên POSE CRM"
};

export const commonCopy = {
  "brand": "POSE CRM",
  "login": "Đăng nhập",
  "demo": "Giao diện minh họa"
};

export const landingNavigation = [
  {
    "label": "Tính năng",
    "href": "#nang-luc"
  },
  {
    "label": "Cách hoạt động",
    "href": "#cach-hoat-dong"
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
  "eyebrow": "CRM cho đội bán hàng và chăm sóc khách hàng",
  "title": [
    "Bán nhiều hơn.",
    "Chăm sóc tốt hơn.",
    "Trên một nền tảng."
  ],
  "description": "Lead, Cơ hội bán hàng và hồ sơ Khách hàng cùng kết nối, từ lần liên hệ đầu tiên đến chăm sóc lâu dài.",
  "phrases": [
    "đội bán hàng",
    "bộ phận chăm sóc",
    "người quản lý"
  ],
  "phraseSummary": "Dành cho đội bán hàng, bộ phận chăm sóc và người quản lý.",
  "caption": "Cơ hội, hồ sơ khách hàng và bàn giao trên cùng một màn hình."
};

export const sectionCopy = {
  "industriesStrip": {
    "title": "Một nền tảng, nhiều ngành"
  },
  "compare": {
    "title": "Khách hàng không rơi giữa phòng ban",
    "introduction": "Thông tin liền mạch giúp các bộ phận tiếp nối công việc.",
    "before": "Ghi chú rời khiến người tiếp nhận thiếu ngữ cảnh khách hàng.",
    "after": "Hồ sơ, tương tác và trách nhiệm được nối theo khách hàng."
  },
  "roles": {
    "title": "Đúng việc cho từng vai trò",
    "introduction": "Cả đội phối hợp, mỗi người rõ phần việc."
  },
  "journey": {
    "title": "Liền mạch từ Lead đến chăm sóc"
  },
  "bento": {
    "title": "Một nơi cho công việc"
  },
  "industries": {
    "title": "Phù hợp cách bạn kinh doanh",
    "introduction": "Thuộc tính sản phẩm, dịch vụ theo danh mục."
  },
  "trust": {
    "title": "Ai cần thấy gì, thấy đúng thứ đó"
  },
  "faq": {
    "title": "Câu hỏi thường gặp"
  },
  "cta": {
    "title": "Đội ngũ của bạn đã sẵn sàng?",
    "introduction": "Đăng nhập để bắt đầu làm việc cùng đội ngũ của bạn."
  }
};

export const roles = [
  {
    "id": "manager",
    "label": "Quản lý",
    "title": "Điều phối cả đội",
    "responsibilities": [
      "Phân công sản phẩm và công việc cho đúng người phụ trách.",
      "Theo dõi Cơ hội bán hàng trong phạm vi đội quản lý.",
      "Duyệt dữ liệu nhập và Bàn giao khách hàng cần xử lý."
    ]
  },
  {
    "id": "sales",
    "label": "Kinh doanh",
    "title": "Theo sát từng cơ hội",
    "responsibilities": [
      "Làm việc với Lead và Khách hàng trong phạm vi được giao.",
      "Ghi nhận tương tác để nối tiếp thông tin khách hàng.",
      "Theo dõi cơ hội và hoàn thành nhiệm vụ của mình."
    ]
  },
  {
    "id": "care",
    "label": "Chăm sóc",
    "title": "Chăm sóc có ngữ cảnh",
    "responsibilities": [
      "Xem hồ sơ và lịch sử của khách hàng được giao.",
      "Ghi nhận phản hồi để đội ngũ tiếp tục chăm sóc.",
      "Xử lý nhiệm vụ và yêu cầu hỗ trợ được phân công."
    ]
  },
  {
    "id": "data",
    "label": "Dữ liệu",
    "title": "Chuẩn bị dữ liệu đúng",
    "responsibilities": [
      "Chuẩn hóa dữ liệu từ file trước khi đưa vào CRM.",
      "Kiểm tra lỗi và rà soát các bản ghi nghi trùng.",
      "Gửi kết quả để Quản lý duyệt trước khi nhập dữ liệu."
    ]
  },
  {
    "id": "admin",
    "label": "Quản trị",
    "title": "Quản trị trong tổ chức",
    "responsibilities": [
      "Quản lý người dùng và vai trò trong tổ chức của mình.",
      "Cấp quyền phù hợp với trách nhiệm của từng người dùng.",
      "Xem nhật ký để theo dõi các thao tác trong tổ chức."
    ]
  }
];

export const journeySteps = [
  {
    "id": "lead",
    "title": "Lead",
    "description": "Xác nhận đủ thông tin để chuyển Lead thành Khách hàng."
  },
  {
    "id": "opportunity",
    "title": "Cơ hội bán hàng",
    "description": "Gắn cơ hội với Lead hoặc Khách hàng và một sản phẩm/dịch vụ."
  },
  {
    "id": "customer",
    "title": "Khách hàng",
    "description": "Liên kết hồ sơ với tương tác, nhiệm vụ và cơ hội."
  },
  {
    "id": "care",
    "title": "Chăm sóc",
    "description": "Bàn giao khách hàng cho chăm sóc sau khi Quản lý duyệt."
  }
];

export const capabilities: readonly ContentCard[] = [
  {
    "id": "customer",
    "title": "Customer 360",
    "description": "Xem tương tác, nhiệm vụ và cơ hội của khách hàng ở một nơi.",
    "icon": "IdentificationIcon"
  },
  {
    "id": "permission",
    "title": "Phân quyền theo vai trò",
    "description": "Mỗi người xem đúng thông tin trong phạm vi được giao.",
    "icon": "KeyIcon"
  },
  {
    "id": "import",
    "title": "Nhập dữ liệu có duyệt",
    "description": "Kiểm tra lỗi và dữ liệu trùng trước khi Quản lý duyệt.",
    "icon": "ArrowUpTrayIcon"
  },
  {
    "id": "handover",
    "title": "Bàn giao khách hàng",
    "description": "Phân công người tiếp nhận sau khi yêu cầu được duyệt.",
    "icon": "ArrowsRightLeftIcon"
  },
  {
    "id": "tasks",
    "title": "Nhiệm vụ và nhắc việc",
    "description": "Theo dõi công việc, thời hạn và việc cần làm tiếp theo.",
    "icon": "ClipboardDocumentCheckIcon"
  },
  {
    "id": "industry",
    "title": "Đa ngành",
    "description": "Sắp xếp sản phẩm, dịch vụ theo danh mục và thuộc tính.",
    "icon": "CubeIcon"
  }
];

export const industries: readonly Industry[] = [
  {
    "id": "education",
    "label": "Giáo dục",
    "icon": "AcademicCapIcon",
    "sample": "Gói đào tạo cơ bản",
    "attributes": [
      {
        "label": "Hình thức",
        "value": "Trực tuyến",
        "type": "Lựa chọn"
      },
      {
        "label": "Cấp độ",
        "value": "Cơ bản",
        "type": "Lựa chọn"
      },
      {
        "label": "Số buổi",
        "value": "8",
        "type": "Số"
      }
    ]
  },
  {
    "id": "property",
    "label": "Bất động sản",
    "icon": "HomeModernIcon",
    "sample": "Dịch vụ tư vấn nhà ở",
    "attributes": [
      {
        "label": "Loại",
        "value": "Tư vấn",
        "type": "Lựa chọn"
      },
      {
        "label": "Khu vực",
        "value": "Nội thành",
        "type": "Văn bản"
      }
    ]
  },
  {
    "id": "software",
    "label": "Phần mềm/SaaS",
    "icon": "ComputerDesktopIcon",
    "sample": "Gói phần mềm cơ bản",
    "attributes": [
      {
        "label": "Quy mô",
        "value": "Nhóm nhỏ",
        "type": "Lựa chọn"
      },
      {
        "label": "Kỳ hạn",
        "value": "Theo tháng",
        "type": "Lựa chọn"
      }
    ]
  },
  {
    "id": "retail",
    "label": "Bán lẻ/F&B",
    "icon": "ShoppingBagIcon",
    "sample": "Gói đồ uống mẫu",
    "attributes": [
      {
        "label": "Danh mục",
        "value": "Đồ uống",
        "type": "Lựa chọn"
      },
      {
        "label": "Hình thức",
        "value": "Mang đi",
        "type": "Lựa chọn"
      }
    ]
  },
  {
    "id": "logistics",
    "label": "Logistics",
    "icon": "TruckIcon",
    "sample": "Dịch vụ giao nhận mẫu",
    "attributes": [
      {
        "label": "Phạm vi",
        "value": "Nội vùng",
        "type": "Lựa chọn"
      },
      {
        "label": "Loại hàng",
        "value": "Hàng mẫu",
        "type": "Văn bản"
      }
    ]
  },
  {
    "id": "clinic",
    "label": "Phòng khám",
    "icon": "HeartIcon",
    "sample": "Gói tư vấn sức khỏe",
    "attributes": [
      {
        "label": "Loại",
        "value": "Tư vấn",
        "type": "Lựa chọn"
      },
      {
        "label": "Hình thức",
        "value": "Trực tiếp",
        "type": "Lựa chọn"
      }
    ]
  }
];

export const trustStatements = [
  "Dữ liệu của mỗi tổ chức được tách riêng khi truy cập.",
  "Quyền theo vai trò luôn đi cùng phạm vi dữ liệu được giao.",
  "Nhập dữ liệu cần duyệt, các thao tác được lưu trong nhật ký."
];

export const faqEntries: readonly FaqEntry[] = [
  {
    "id": "purpose",
    "question": "POSE CRM giúp doanh nghiệp thế nào?",
    "answer": "POSE CRM giúp đội bán hàng và chăm sóc kết nối Lead, Cơ hội bán hàng và hồ sơ Khách hàng. Các giao diện trên trang minh họa cách phối hợp."
  },
  {
    "id": "industry",
    "question": "Có phù hợp nhiều ngành không?",
    "answer": "Có. Mô hình sản phẩm, dịch vụ chung hỗ trợ danh mục và thuộc tính theo nhu cầu. Sáu ngành trên trang là ví dụ minh họa, không phải cam kết tính năng chuyên ngành."
  },
  {
    "id": "organization",
    "question": "Dữ liệu tổ chức có tách biệt không?",
    "answer": "Có. Bản ghi thuộc từng tổ chức; quyền và phạm vi được kiểm tra trước khi truy cập. Việc có nhiều vai trò không cho phép truy cập tổ chức khác."
  },
  {
    "id": "permissions",
    "question": "Phân quyền hoạt động thế nào?",
    "answer": "Quyền theo vai trò đi cùng nhóm và phạm vi từng hồ sơ. Ghi chú chỉ dành cho bán hàng không hiển thị với nhân viên chăm sóc khách hàng; thông tin chia sẻ vẫn cần quyền và phạm vi phù hợp."
  },
  {
    "id": "account",
    "question": "Làm sao để có tài khoản?",
    "answer": "Quản trị viên của tổ chức cấp tài khoản và mã tổ chức. Bạn đăng nhập bằng mã tổ chức, email và mật khẩu."
  }
];

