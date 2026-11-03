export const opportunityStages = ["NEW", "QUALIFIED", "PROPOSAL", "WON", "LOST"] as const;
export type OpportunityStage = typeof opportunityStages[number];

export type OpportunityLine = {
  id: string;
  offeringId: string;
  offeringName: string;
  quantity: number;
  unitPrice: number;
  discountPercent: number;
};

export type OpportunityRecord = {
  id: string;
  title: string;
  customerId: string;
  customerName: string;
  stage: OpportunityStage;
  expectedCloseDate: string;
  expectedValue: number;
  actualValue?: number;
  actualCloseDate?: string;
  notes: string;
  closingReason?: string;
  lines: OpportunityLine[];
};

export type OpportunityCustomer = { id: string; name: string };
export type OpportunityOffering = { id: string; name: string; referencePrice: number; unit: string };

export const stageLabels: Record<OpportunityStage, string> = {
  NEW: "Mới",
  QUALIFIED: "Đủ điều kiện",
  PROPOSAL: "Đề xuất",
  WON: "Thành công",
  LOST: "Thất bại",
};

export function expectedValue(lines: Pick<OpportunityLine, "quantity" | "unitPrice" | "discountPercent">[]) {
  const total = lines.reduce((sum, line) => sum + line.quantity * line.unitPrice * (1 - line.discountPercent / 100), 0);
  return Math.round(total * 100) / 100;
}

export function formatVnd(value: number) {
  return new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", minimumFractionDigits: 0, maximumFractionDigits: 2 }).format(value);
}

export function allowedNextStages(stage: OpportunityStage): OpportunityStage[] {
  switch (stage) {
    case "NEW": return ["NEW", "QUALIFIED"];
    case "QUALIFIED": return ["QUALIFIED", "PROPOSAL"];
    case "PROPOSAL": return ["PROPOSAL", "WON", "LOST"];
    case "WON":
    case "LOST": return [stage];
  }
}
