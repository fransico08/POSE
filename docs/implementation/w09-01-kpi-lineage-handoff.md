# W09-01 — KPI lineage và handoff API/Frontend

## Lineage thực thi

| Source column | Join/filter/aggregation | KPI | API field | Widget | Owner / verification |
|---|---|---|---|---|---|
| `leads.organization_id,status,converted_customer_id,created_at` | scope org; group status; `converted_customer_id IS NOT NULL` | A01–A04 | `leadTotal,statuses,qualifiedRate,conversionSnapshotRate` | Cards/donut | Nhi SQL; schema verified |
| `interactions.organization_id,lead_id,customer_id,recorded_by_user_id,channel,occurred_at` | ID + org joins; period `occurred_at`; min/max/count | A05,A06,D01,D02 | `firstResponse,engagementSegments,channels,recorders` | Histogram/bar/line | Nhi SQL; schema verified |
| `crm_tasks.organization_id,owner_user_id,status,priority,due_at,completed_at` | org scope; OPEN/IN_PROGRESS; completed period | B01–B05 | `openTasks,overdueTasks,priorityBuckets,ownerWorkload,onTimeRate` | Cards/bar/table | Nhi SQL; schema verified |
| `workflow_executions.organization_id,workflow_type,status,retry_count,last_error,started_at,finished_at` | terminal DONE/FAILED; valid time intervals | C01–C03 | `workflowReliability` | Card/table/line | Nhi SQL; persisted-state only |
| `users.organization_id,id,status` | join ID + org for owner/recorder integrity | B04,D02 | `ownerUserId,recorderUserId` | Table/bar | Tài auth scope; conditional API integration |

## Reconciliation evidence template

| KPI/check | SQL block | Observed value | Expected condition | Result | Environment / UTC / reviewer |
|---|---|---|---|---|---|
| A01–A04 | Lead KPI + `lead_status_partition` | NOT RUN | partition equals total | NOT RUN | pgAdmin `pose_crm_v2` / Nhi |
| B01–B05 | Task KPI + `task_status_partition` | NOT RUN | buckets reconcile | NOT RUN | pgAdmin `pose_crm_v2` / Nhi |
| A06 | Customer engagement + partition check | NOT RUN | one bucket/customer | NOT RUN | pgAdmin `pose_crm_v2` / Nhi |
| C01–C03 | Workflow KPI + terminal check | NOT RUN | numerator+denominator valid | NOT RUN | pgAdmin `pose_crm_v2` / Nhi |
| D01–D02 | Interaction KPI + recorder scope | NOT RUN | total/channel/user reconcile | NOT RUN | pgAdmin `pose_crm_v2` / Nhi |

## Semantics for empty and snapshot output

- `period_or_as_of` on snapshot KPI is the SQL-generated `as_of` timestamp. It
  identifies the snapshot, not a source record update time.
- `ZERO` means the measurable population exists but its count is zero;
  `NO_DATA_IN_PERIOD` means the selected `[start,end)` has no qualifying row;
  `NOT_AVAILABLE` means a required source or denominator is absent; and
  `NOT_APPLICABLE` means the KPI does not apply to the requested scope.
- Keep raw numeric rates in the API/reconciliation response. UI may format them
  as percentages with two decimals only; it must not round stored/calculated raw values.
- Required final manual check: run A05, B05, C01–C03 and D01–D02 with both the
  default 30-day window and a window covering seed dates. The first may return
  `NO_DATA_IN_PERIOD`; the latter must be reconciled against source counts.

## API/Frontend contract for Tài and Thắng

- Require authenticated tenant context; `organization_id` comes from authorization,
  never a client-selectable unrestricted query parameter.
- Accept `asOf`, `start`, `end`, `timezone`; validate `start < end`, use start
  inclusive/end exclusive, and return these values in each response.
- Keep KPI IDs stable, return `availability`, `reason`, numerator/denominator and
  `asOf`/`generatedAt`; snapshot `period_or_as_of` là generated-at, không phải
  source event time. Avoid silent zero/default on unavailable source/denominator.
- API phân biệt: `ZERO` = population có thể đo nhưng count bằng 0;
  `NO_DATA_IN_PERIOD` = time window không có record; `NOT_AVAILABLE` = thiếu
  source/denominator; `NOT_APPLICABLE` = KPI không đúng scope/phân khúc. Raw rate
  giữ numeric precision; UI mới format phần trăm 2 chữ số.
- Detail widgets require pagination and same filters as cards. Thắng reconciles
  API/UI filters with SQL output; Tài reviews RBAC and query performance.

## KLTN compatibility and handoff

When Data Mart/RFM/CLV/NBA arrives, keep KPI IDs backward compatible; version a
contract if formula, event time or provenance changes. New layers require revenue
or service-value history, consent, outcomes, `model_version`, action reason and
evaluation labels; none exists now. Nhi owns SQL/KPI/lineage, Tài backend review,
Thắng API/UI reconciliation QA. Runtime/API evidence remains NOT RUN.
