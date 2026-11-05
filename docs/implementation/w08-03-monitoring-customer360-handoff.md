# W08-03 — Monitoring Data Dictionary và Customer 360 handoff

## Phạm vi và luồng dữ liệu

`CRM sources (customers/leads/interactions/crm_tasks/outbox/workflow) → read-only
monitoring + Customer 360 SQL → future Data Mart → RFM → Predictive CLV → Customer
Priority → rule-based NBA → evaluation → personalized automation`.

W08-03 chỉ là lớp read-only ở giữa. Không tạo Data Mart, RFM, CLV, model, priority,
automation hay kết quả evaluation. Mọi join tenant dùng cả `organization_id` và ID;
không xuất PII, nội dung chăm sóc hay dữ liệu nhạy cảm y tế.

## Monitoring Data Dictionary

| Metric | Grain | Nguồn / cách tính | Timezone / null rule | Tenant + đối soát | Giới hạn |
|---|---|---|---|---|---|
| `outbox_status_count` | org/event_type/status | `outbox_events`, `count(*)` | Stored `TIMESTAMPTZ`; 0 row = INFO | group `organization_id`; compare status distribution | Không là evidence worker runtime |
| `execution_status_count` | org/workflow_type/status | `workflow_executions`, `count(*)` | `TIMESTAMPTZ`; 0 = INFO | group `organization_id`; compare retry max | Không có scheduler telemetry |
| `workflow_success_rate` | org/workflow/window | DONE / (DONE + FAILED), chỉ execution có terminal status | mẫu số 0 → NULL/NOT AVAILABLE | scope org/workflow; reconcile with status count | Không tính PENDING/RUNNING là failure |
| `workflow_failure_rate` | org/workflow/window | FAILED / (DONE + FAILED) | mẫu số 0 → NULL/NOT AVAILABLE | scope org/workflow | Không suy ra policy retry |
| `retry_count_distribution` | org/workflow/status | `retry_count` distribution | 0 là hợp lệ | scope org | `maxAttempts` chưa persisted |
| `queue_age` | outbox/execution row | `now - occurred_at` PENDING; `now - started_at` RUNNING | `started_at NULL` không stale | scope org; query threshold 30m proposal | Không có lease/next_retry_at |
| `task_linkage_coverage` | Lead-qualified event | outbox → execution → Task với org/key/origin hợp lệ | thiếu event = NOT RUN/INFO | join cùng org + FK | Chỉ contract `LEAD_QUALIFIED_FOLLOW_UP` |
| `integrity_violation_count` | check | duplicate/orphan/tenant/key checks | 0 = persisted-data PASS | all checks tenant-scoped | Không chứng minh concurrency |

## Customer 360 Feature Dictionary

Customer grain là một `(organization_id, customer_id)`. Interaction/Task của Lead
được tính vào Customer chỉ khi Lead có `converted_customer_id` cùng tenant; Lead
chưa convert được báo cáo trong result set lead-grain riêng, không UNION vào Customer.

| Feature | Grain | Nguồn / công thức | Observation / null | Multi-tenant + đối soát | Giới hạn |
|---|---|---|---|---|---|
| `interaction_count` | customer | count Interaction direct Customer + Lead đã convert | all stored history; 0 nếu không row | join subject ID + org | Không là engagement quality |
| `last_interaction_at` | customer | max `interactions.occurred_at` | NULL nếu chưa có interaction | join subject ID + org | Không bao gồm channel ngoài schema |
| `task_count` | customer | count Task direct Customer + Lead đã convert | all stored history; 0 nếu không row | join subject ID + org | Không đo completed outcome |
| `open_task_count` | customer | Task status OPEN/IN_PROGRESS | snapshot at query time; 0 nếu none | same | Status policy backend quyết định |
| `overdue_task_count` | customer | open task với `due_at < clock_timestamp()` | query timestamp timezone-aware | same | Không áp dụng SLA ngành |
| `last_activity_at` | customer | greatest last interaction/task activity | NULL nếu không activity | same | Không có external events |

## Mapping đa ngành minh họa (đề xuất, chưa tích hợp)

| Ngành | Business event | Target outcome | Allowed next action |
|---|---|---|---|
| Bán lẻ | Customer hỏi hàng / task quá hạn | Follow-up đúng hạn | Nhắc Sales review task đã được phân quyền |
| Y tế | Yêu cầu chăm sóc đã ghi nhận | Care follow-up an toàn | Chỉ tạo review nội bộ theo consent/policy, không suy diễn bệnh hay CLV |
| Giáo dục | Lead yêu cầu tư vấn | Tư vấn đúng hạn | Task tư vấn viên, không hứa kết quả học tập |
| Logistics | Ticket/interaction vận hành | Cập nhật xử lý đúng hạn | Nhắc owner kiểm tra task |
| Bất động sản | Lead inquiry | Phản hồi theo consent | Task tư vấn, không tự gửi marketing |

## KLTN Extension Contract

Trước khi triển khai Data Mart/RFM/CLV/NBA cần lịch sử giao dịch hoặc giá trị dịch
vụ có timestamp, currency/amount semantics, snapshot/observation/prediction windows,
retention/outcome labels, consent/contact policy, action reason, model_version và
outcome tracking. Y tế phải có guardrail riêng: không dùng giá trị chi tiêu để ưu
tiên nhu cầu thiết yếu. Model evaluation chỉ bắt đầu khi label, cohort và outcome
tracking đã được duyệt.

## Runbook, ownership và acceptance

1. Nhi mở `database/scripts/monitor_w08_03_workflow_customer360_gui.sql` trong
   pgAdmin 4/SQLTools của `pose_crm_v2`, chạy từng block và kiểm tra integrity
   `violation_count=0`; `INFO_NO_*`/`NOT_RUN_OR_NOT_INTEGRATED` không phải PASS runtime.
2. Tài review per-tenant backend/API access, publisher/worker integration, service
   actor và retry/lease migration. Thắng chạy runtime/concurrency evidence sau tích hợp.
3. Acceptance W08-03: ba file tồn tại, SQL manual PASS, Customer grain không nhân bản,
   limitation/runtime result ghi trung thực. Runtime workflow, notifications và KLTN
   features vẫn NOT RUN/NOT IMPLEMENTED.
