"use client";
import { useParams } from "next/navigation";
import { CrmForm } from "@/components/crm/CrmForm";
export default function EditCustomerPage() { const params = useParams<{ id: string }>(); return params?.id ? <CrmForm kind="customer" id={params.id} /> : null; }
