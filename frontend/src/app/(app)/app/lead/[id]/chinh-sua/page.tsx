"use client";
import { useParams } from "next/navigation";
import { CrmForm } from "@/components/crm/CrmForm";
export default function EditLeadPage() { const params = useParams<{ id: string }>(); return params?.id ? <CrmForm kind="lead" id={params.id} /> : null; }
