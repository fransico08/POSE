"use client";

import { useParams } from "next/navigation";
import { OfferingForm } from "@/components/offerings/OfferingForm";

export default function EditOfferingPage() {
  const params = useParams<{ id: string }>();
  return <OfferingForm id={params?.id ?? null} />;
}
