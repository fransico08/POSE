import { taskPriorities, taskStatuses, type Task } from "@/lib/tasks";

const statusTone: Record<Task["status"], string> = {
  OPEN: "open",
  IN_PROGRESS: "in-progress",
  DONE: "done",
  CANCELLED: "cancelled",
};

const priorityTone: Record<string, string> = {
  LOW: "low",
  MEDIUM: "medium",
  HIGH: "high",
  URGENT: "urgent",
};

export function TaskStatusBadge({ status }: { status: Task["status"] }) {
  const tone = statusTone[status] ?? "open";

  return (
    <span className={`badge task-status-badge task-status-${tone}`}>
      <span className="task-status-dot" aria-hidden="true" />
      {taskStatuses[status] ?? status}
    </span>
  );
}

export function TaskPriorityBadge({ priority }: { priority: string }) {
  const tone = priorityTone[priority] ?? "unknown";

  return (
    <span className={`badge task-priority-badge task-priority-${tone}`}>
      {taskPriorities[priority] ?? priority}
    </span>
  );
}
