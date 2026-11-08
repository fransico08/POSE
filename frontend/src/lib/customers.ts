import { api } from "@/lib/api";
import { crmEndpoint, crmKey, crmErrorMessage, type CrmPage, type CrmRecord, type OwnerCandidate } from "@/lib/crm";
import type { SessionUser } from "@/lib/session/types";
export type Customer = CrmRecord & { customerType: string | null; address: string | null; relatedOfferingId: string | null };
export type CustomerReassignResponse = Customer & {
  handoverId: string;
  movedOpportunityIds: string[];
  movedTaskIds: string[];
};
export const customerKey = (user: SessionUser) => crmKey("customer", user);
export const customerErrorMessage = crmErrorMessage;
export const getCustomers = (query: URLSearchParams, options?: { suppressForbiddenNavigation?: boolean }) => api.request<CrmPage>(`${crmEndpoint("customer")}?${query}`, undefined, options);
export const getCustomer = (id: string, options?: { suppressForbiddenNavigation?: boolean }) => api.request<Customer>(`${crmEndpoint("customer")}/${encodeURIComponent(id)}`, undefined, options);
export const getCustomerOwnerCandidates = (offeringId: string) => api.request<OwnerCandidate[]>(`${crmEndpoint("customer")}/owner-candidates?offeringId=${encodeURIComponent(offeringId)}`, undefined, { suppressForbiddenNavigation: true });
export const saveCustomer = (id: string | null, payload: Record<string, string | null | undefined>) => api.request<Customer>(`${crmEndpoint("customer")}${id ? `/${encodeURIComponent(id)}` : ""}`, { method: id ? "PATCH" : "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
export const reassignCustomer = (id: string, payload: { newOwnerUserId: string; reason: string }) =>
  api.request<CustomerReassignResponse>(
    `${crmEndpoint("customer")}/${encodeURIComponent(id)}/reassign`,
    { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) },
    { suppressForbiddenNavigation: true },
  );
