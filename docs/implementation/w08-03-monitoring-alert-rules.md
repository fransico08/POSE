# W08-03 — Workflow monitoring và alert rules

Các rule dưới đây là **quan sát read-only**, không phải notification engine, SLA,
realtime alert hoặc retry tự động. Threshold là đề xuất mặc định cần cấu hình lại
theo tenant/ngành và tải vận hành thực tế.

| rule_id | Tên / ý nghĩa nghiệp vụ | Scope | Logic SQL/feature | Threshold đề xuất | Severity | Cooldown / dedup key đề xuất | Owner xử lý | Loại trừ | Evidence | Trạng thái |
|---|---|---|---|---|---|---|---|---|---|---|
| WF-01 | Workflow failed: event/execution không hoàn tất | tenant + workflow | `delivery_status='FAILED'` hoặc `status='FAILED'`, kèm `last_error` | ≥1 trong 15 phút | CRITICAL | `org:workflow_type:error_class:15m` | Tài vận hành | Test fixture/maintenance đã ghi nhận | UUID, retry, error, UTC log | OBSERVATION_ONLY |
| WF-02 | Pending/Running già: backlog hoặc worker kẹt | tenant + workflow | Query `PENDING_OUTBOX`/`RUNNING_EXECUTION` trong SQL monitor | >30 phút, cấu hình | WARNING | `org:status:30m` | Tài | Không có `started_at` là INFO, không stale | count, oldest age, scheduler state | OBSERVATION_ONLY |
| WF-03 | Backlog tăng | tenant + workflow | So sánh count PENDING theo hai snapshot thời gian | Tăng 3 snapshot liên tiếp | WARNING | `org:workflow_type:backlog-window` | Tài | Chưa có bảng snapshot: chưa tự tính | Snapshot counts/timestamps | FUTURE_INTEGRATION |
| WF-04 | Retry vượt policy | tenant + workflow | `retry_count` distribution; dùng policy runtime đã cấu hình | ≥ `maxAttempts - 1` | WARNING | `org:event_id:retry_count` | Tài | Không suy ra maxAttempts từ seed | retry history, config | PARTIAL_SCHEMA |
| WF-05 | Failure lặp lại | tenant + workflow | Group failure reason/status trong khoảng quan sát | ≥3 cùng error class/15 phút | WARNING | `org:workflow:error_class:15m` | Tài | `last_error` null không gom thành lỗi | Error class, event UUID | OBSERVATION_ONLY |
| WF-06 | Idempotency / tenant / FK integrity | tenant | Các `violation_count` integrity trong SQL monitor | >0 | CRITICAL | `check_name:organization_id` | Tài + Nhi | Không áp dụng historical relation không ghi `created_task_id` | Query output, offending UUID | OBSERVATION_ONLY |
| WF-07 | DONE workflow Lead-qualified thiếu Task | tenant + workflow | `LEAD_QUALIFIED_FOLLOW_UP` DONE và `created_task_id IS NULL` | >0 | CRITICAL | `org:execution_id` | Tài | Chỉ contract workflow W08-01 mới | execution/outbox/task rows | PENDING_INTEGRATION |
| C360-01 | Customer lâu chưa follow-up | tenant + customer | `last_activity_at` từ Interaction/Task; NULL là chưa có activity | >30 ngày hoặc NULL, cấu hình | INFO | `org:customer_id:activity-window` | Sales/Care | Archived customer; consent/policy ngành | customer UUID, activity timestamps | OBSERVATION_ONLY |
| C360-02 | Task quá hạn chưa đóng | tenant + customer/lead | OPEN/IN_PROGRESS và `due_at < now()` | ≥1 | WARNING | `org:task_id` | Sales/Care | Task CANCELLED/DONE | task UUID, due_at, status | OBSERVATION_ONLY |

## Nguyên tắc an toàn đa ngành

- Rule không suy ra mua hàng, doanh thu, mức độ hài lòng, urgency y tế hoặc quyền
  liên hệ. Mọi next action cần consent, authorization và policy từng ngành trước
  khi tự động hóa.
- `organization_id` là ranh giới bắt buộc cho aggregate, dedup và evidence; không
  dùng email/phone/display name làm key alert.
- Các rule C360 là tín hiệu công việc từ activity thực tế, không phải customer
  priority score hay mô hình dự báo. W08-03 chưa gửi notification.
