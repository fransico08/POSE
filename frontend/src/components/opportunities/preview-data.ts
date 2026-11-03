import type { OpportunityCustomer, OpportunityOffering, OpportunityRecord } from "./types";

export const previewCustomers: OpportunityCustomer[] = [
  { id: "customer-1", name: "Công ty Ánh Dương" },
  { id: "customer-2", name: "Nguyễn Minh Anh" },
  { id: "customer-3", name: "Công ty Sao Mai" },
];

export const previewOfferings: OpportunityOffering[] = [
  { id: "offering-1", name: "Khóa học Business English", referencePrice: 6_900_000, unit: "khóa" },
  { id: "offering-2", name: "Tư vấn CRM", referencePrice: 12_500_000, unit: "gói" },
  { id: "offering-3", name: "Data Analytics Bootcamp", referencePrice: 12_900_000, unit: "chương trình" },
];

export const previewOpportunities: OpportunityRecord[] = [
  {
    id: "opportunity-1", title: "Đào tạo tiếng Anh cho đội ngũ kinh doanh", customerId: "customer-1", customerName: "Công ty Ánh Dương",
    stage: "NEW", expectedCloseDate: "2026-11-21", expectedValue: 24_920_000, notes: "Dự kiến triển khai trong quý IV.",
    lines: [
      { id: "line-1", offeringId: "offering-1", offeringName: "Khóa học Business English", quantity: 2, unitPrice: 6_900_000, discountPercent: 10 },
      { id: "line-2", offeringId: "offering-2", offeringName: "Tư vấn CRM", quantity: 1, unitPrice: 12_500_000, discountPercent: 0 },
    ],
  },
  {
    id: "opportunity-2", title: "Bồi dưỡng kỹ năng giao tiếp", customerId: "customer-2", customerName: "Nguyễn Minh Anh",
    stage: "QUALIFIED", expectedCloseDate: "2026-11-28", expectedValue: 8_500_000, notes: "Đã xác nhận nhu cầu.",
    lines: [{ id: "line-3", offeringId: "offering-1", offeringName: "Khóa học Business English", quantity: 1, unitPrice: 8_500_000, discountPercent: 0 }],
  },
  {
    id: "opportunity-3", title: "Đào tạo phân tích dữ liệu", customerId: "customer-3", customerName: "Công ty Sao Mai",
    stage: "PROPOSAL", expectedCloseDate: "2026-12-05", expectedValue: 25_800_000, notes: "Đã gửi đề xuất.",
    lines: [{ id: "line-4", offeringId: "offering-3", offeringName: "Data Analytics Bootcamp", quantity: 2, unitPrice: 12_900_000, discountPercent: 0 }],
  },
  {
    id: "opportunity-4", title: "Gói tư vấn triển khai CRM", customerId: "customer-1", customerName: "Công ty Ánh Dương",
    stage: "WON", expectedCloseDate: "2026-10-29", expectedValue: 12_500_000, actualValue: 11_800_000, actualCloseDate: "2026-10-27", notes: "", closingReason: "Khách hàng đã ký đề xuất.",
    lines: [{ id: "line-5", offeringId: "offering-2", offeringName: "Tư vấn CRM", quantity: 1, unitPrice: 12_500_000, discountPercent: 0 }],
  },
  {
    id: "opportunity-5", title: "Khóa tiếng Anh cho nhóm kỹ thuật", customerId: "customer-2", customerName: "Nguyễn Minh Anh",
    stage: "LOST", expectedCloseDate: "2026-10-18", expectedValue: 6_900_000, notes: "", closingReason: "Chưa phù hợp thời điểm.",
    lines: [{ id: "line-6", offeringId: "offering-1", offeringName: "Khóa học Business English", quantity: 1, unitPrice: 6_900_000, discountPercent: 0 }],
  },
];
