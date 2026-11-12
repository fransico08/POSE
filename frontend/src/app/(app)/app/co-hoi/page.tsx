import { Suspense } from "react";
import { OpportunityPipeline } from "@/components/opportunities/OpportunityPipeline";
export default function Page() { return <Suspense fallback={<section className="offering-section" aria-busy="true"><p role="status">Đang tải cơ hội…</p><div className="offering-skeleton" /></section>}><OpportunityPipeline /></Suspense>; }
