import { api } from "@/lib/api";
import type { WorkPage } from "@/lib/interactions";
import type { SessionUser } from "@/lib/session/types";

export type Task = { id: string; customerId: string | null; leadId: string | null; opportunityId: string | null; ownerUserId: string; ownerName: string; createdByUserId: string; title: string | null; description: string | null; priority: string; status: "OPEN" | "IN_PROGRESS" | "DONE" | "CANCELLED"; dueAt: string; result: string | null; cancellationReason: string | null; origin: string; completedAt: string | null; createdAt: string; updatedAt: string };
export const taskKey = (user: SessionUser) => ["task", user.organization.id, user.id] as const;
export const canManageTask = (user: SessionUser) => user.permissions.includes("task.manage");
export const canCreateTask = (user: SessionUser) => canManageTask(user) && user.roles.some(role => ["MANAGER", "SALES_REP", "CUSTOMER_CARE"].includes(role));
export const canAssignTask = (user: SessionUser) => canManageTask(user) && user.roles.some(role => ["MANAGER", "ADMIN"].includes(role));
export const taskStatuses: Record<string, string> = { OPEN: "Chưa bắt đầu", IN_PROGRESS: "Đang thực hiện", DONE: "Hoàn tất", CANCELLED: "Đã hủy" };
export const taskPriorities: Record<string, string> = { LOW: "Thấp", NORMAL: "Bình thường", HIGH: "Cao", URGENT: "Khẩn cấp" };
export const listTasks = (query: URLSearchParams) => api.request<WorkPage<Task>>(`/api/backend/tasks?${query}`);
export const getTask = (id: string) => api.request<Task>(`/api/backend/tasks/${encodeURIComponent(id)}`);
export const saveTask = (id: string | null, body: object) => api.request<Task>(`/api/backend/tasks${id ? `/${encodeURIComponent(id)}` : ""}`, { method: id ? "PATCH" : "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
export const taskAction = (id: string, action: string, body: object) => api.request<Task>(`/api/backend/tasks/${encodeURIComponent(id)}/${action}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
export type TaskOwnerOption = { id: string; fullName: string; status: string; roles: string[] };
export async function getTaskOwnerOptions(): Promise<TaskOwnerOption[]> {
  const items: TaskOwnerOption[] = []; let page = 0, totalPages = 1;
  while (page < totalPages) {
    const response = await api.request<WorkPage<TaskOwnerOption>>(`/api/backend/users?page=${page}&size=100`, undefined, { suppressForbiddenNavigation: true });
    items.push(...response.items.filter(user => user.status === "ACTIVE" && user.roles.some(role => ["SALES_REP", "CUSTOMER_CARE"].includes(role))));
    page += 1; totalPages = response.totalPages;
  }
  return items;
}
