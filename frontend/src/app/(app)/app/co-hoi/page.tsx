"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useSession } from "@/components/session/SessionGate";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { OpportunityPipeline } from "@/components/opportunities/OpportunityPipeline";
import type { OpportunityCustomer, OpportunityOffering, OpportunityRecord } from "@/components/opportunities/types";

type PreviewData = { customers: OpportunityCustomer[]; offerings: OpportunityOffering[]; opportunities: OpportunityRecord[] };
const developmentPreview = process.env.NODE_ENV === "development";

export default function OpportunityPipelinePage() {
  const { user } = useSession();
  const [preview, setPreview] = useState<PreviewData | null>(null);
  const hasPermission = user.permissions.includes("opportunity.read") || user.permissions.includes("opportunity.write");

  useEffect(() => {
    if (!developmentPreview) return;
    let current = true;
    void import("@/components/opportunities/preview-data").then(data => {
      if (current) setPreview({ customers: data.previewCustomers, offerings: data.previewOfferings, opportunities: data.previewOpportunities });
    });
    return () => { current = false; };
  }, []);

  if (!hasPermission) return <section className="status-content"><h1>Không có quyền truy cập</h1><Alert>Bạn chưa được cấp quyền xem cơ hội bán hàng.</Alert><Link className="button button-primary" href="/app">Về Tổng quan</Link></section>;

  if (developmentPreview && !preview) return <section className="opportunity-loading" aria-busy="true"><p role="status">Đang tải bản xem trước…</p><span className="skeleton skeleton-title" aria-hidden="true" /><span className="skeleton" aria-hidden="true" /></section>;

  if (developmentPreview && preview) return <OpportunityPipeline initialOpportunities={preview.opportunities} customers={preview.customers} offerings={preview.offerings} canWrite={user.permissions.includes("opportunity.write")} preview />;

  return <section className="offering-section opportunity-unavailable">
    <h1>Pipeline cơ hội bán hàng</h1>
    <Alert tone="info">Giao diện pipeline đã sẵn sàng, nhưng API Opportunity chưa được triển khai nên trang chưa tải hoặc lưu được cơ hội. Có thể xem trước giao diện khi chạy frontend ở chế độ development.</Alert>
    <p>Đợt này bao gồm giao diện và acceptance test phía frontend. Kết nối dữ liệu thật cần API/backend riêng.</p>
    <Button variant="secondary" onClick={() => window.location.reload()}>Thử lại</Button>
  </section>;
}
