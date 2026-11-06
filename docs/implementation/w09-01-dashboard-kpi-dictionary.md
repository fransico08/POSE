# W09-01 — Dashboard KPI Dictionary (single source of truth)

## Quy ước chung

- Grain bắt buộc có `organization_id`; API chỉ trả tenant mà actor được phân quyền.
- `as_of` là `TIMESTAMPTZ` do API truyền; `start` inclusive, `end` exclusive;
  hiển thị mặc định `Asia/Ho_Chi_Minh`, còn DB tính bằng `TIMESTAMPTZ`.
- SQL đối soát capture `as_of` đúng một lần trong mỗi block; API phải truyền cùng
  một `asOf` cho các widget cần so sánh trong một dashboard refresh.
- KPI snapshot không được trình bày là historical funnel. `NULL` tỷ lệ là
  `NOT_AVAILABLE` khi mẫu số bằng 0, không phải 0% hay 100%.
- Mọi snapshot trả `period_or_as_of` là thời điểm `as_of` được SQL capture; đây
  là generated-at semantics, không phải thời điểm một bản ghi nguồn được sửa.
- Raw rate giữ độ chính xác numeric; API/UI chỉ định dạng hiển thị phần trăm tối
  đa 2 chữ số thập phân, không ghi đè giá trị raw dùng cho reconciliation.

| ID | Label VI / EN | Câu hỏi & công thức | Grain / nguồn / time filter | NULL, tenant, availability | Chart / API fields / giới hạn |
|---|---|---|---|---|---|
| A01_TOTAL_LEADS | Tổng Lead / Total Leads | `count(leads.id)` | org snapshot; `leads.organization_id` | 0 hợp lệ; scope org; **AVAILABLE** | Card; `value` |
| A02_LEAD_STATUS_DISTRIBUTION | Phân bố trạng thái Lead / Lead Status Distribution | `count(*)` và `count(*)/total` theo status stored | org × `leads.status`; snapshot | mẫu số 0→NULL; enum schema; **AVAILABLE** | Donut; `dimension,status,value,share` |
| A03_QUALIFIED_LEAD_RATE | Tỷ lệ Lead đủ điều kiện / Qualified Lead Rate | `QUALIFIED / all leads` | org; `leads.status`; snapshot | 0 lead→NULL; không là conversion; **AVAILABLE** | Card/Donut; `numerator,denominator,value` |
| A04_LEAD_TO_CUSTOMER_CONVERSION | Lead chuyển Customer / Lead-to-Customer Conversion | `converted_customer_id IS NOT NULL / all leads` | org; `leads.converted_customer_id,converted_at`; snapshot | 0 lead→NULL; không suy từ QUALIFIED; **AVAILABLE (snapshot only)** | Card; `conversion_count,denominator,value`; chưa có cohort funnel |
| A05_FIRST_RESPONSE_TIME | Thời gian phản hồi đầu / First Response Time | `min(interactions.occurred_at)-lead.created_at` cho interaction Lead cùng org | org × Lead; `leads.created_at`, `interactions.lead_id,occurred_at`; interval filter theo Lead created | no-response riêng, negative timestamp loại; **AVAILABLE** | Histogram/median card; `responded_count,no_response_count,avg_seconds` |
| A06_CUSTOMER_ENGAGEMENT_SEGMENTS | Phân khúc engagement Customer / Customer Engagement Segments | theo `last_interaction_at`: Active `<30d`, At-risk `≥30d và ≤60d`, Inactive `>60d`, Never Engaged NULL | org × segment; `customers`, `interactions.customer_id,occurred_at`; as_of | mỗi Customer một bucket; **AVAILABLE** | Stacked bar; `dimension,value`; chỉ interaction, không suy satisfaction |
| B01_OPEN_TASKS | Task đang mở / Open Tasks | status `OPEN|IN_PROGRESS` | org snapshot; `crm_tasks.status` | 0 hợp lệ; **AVAILABLE** | Card; `value` |
| B02_OVERDUE_OPEN_TASKS | Task mở quá hạn / Overdue Open Tasks | open task và `due_at < as_of` | org; `crm_tasks.status,due_at` | 0 hợp lệ; **AVAILABLE** | Card/list; `value` |
| B03_TASK_PRIORITY_DISTRIBUTION | Phân bố priority Task / Task Priority Distribution | `count(*)` theo priority × status stored | org × priority × status; `crm_tasks` | enum thực tế; **AVAILABLE** | Grouped bar; `priority,status,value` |
| B04_TASKS_PER_USER | Task theo người phụ trách / Tasks per User | `count(*)` theo owner; unassigned chỉ metadata vì owner schema NOT NULL | org × owner; `crm_tasks.owner_user_id`, `users` | user missing/mismatch là integrity failure; **AVAILABLE** | Bar/table; `owner_user_id,value`; mẫu số staffing không tính |
| B05_ON_TIME_COMPLETION_RATE | Hoàn thành đúng hạn / On-time Completion Rate | DONE có `completed_at <= due_at` / DONE có completed_at | org; `crm_tasks.status,completed_at,due_at`; completed_at period | denominator 0→NULL; **AVAILABLE** | Card; `numerator,denominator,value`; không dùng updated_at |
| C01_TERMINAL_WORKFLOW_SUCCESS_RATE | Thành công workflow terminal / Terminal Workflow Success Rate | DONE / (DONE + FAILED) | org × workflow type; `workflow_executions.status,finished_at`; terminal period | denom 0→NULL; **AVAILABLE** | Card/line; `numerator,denominator,value`; không phải runtime proof |
| C02_FAILED_EXECUTIONS_AND_RETRY | Workflow lỗi và retry / Failed Executions & Retry | FAILED count; distribution `retry_count`, `last_error` | org × workflow/status/retry; `workflow_executions`; period `finished_at` | NULL error giữ separate; **AVAILABLE** | Table/bar; `failed_count,retry_count,last_error`; không đếm duplicate blocked |
| C03_WORKFLOW_CYCLE_TIME | Chu kỳ workflow / Workflow Cycle Time | `finished_at-started_at` với thời gian hợp lệ | org × workflow; `workflow_executions`; terminal period | thiếu/negative loại và đếm riêng; **AVAILABLE** | Percentile/line; `avg_seconds,eligible_count` |
| D01_INTERACTIONS_BY_CHANNEL | Interaction theo kênh / Interactions by Channel | `count(*)` theo channel stored | org × channel × period; `interactions.channel,occurred_at` | channel NULL→`UNKNOWN`; **AVAILABLE** | Line/stacked bar; `channel,value` |
| D02_INTERACTIONS_BY_ORG_USER | Interaction theo người ghi / Interactions by Org User | `count(*)` theo recorded_by_user | org × user × period; `interactions.recorded_by_user_id`; join user + org | missing/mismatch là integrity check; **AVAILABLE** | Bar/table; `user_id,value` |

## Layout dashboard văn bản

- **KPI cards:** A01, A03, A04 (snapshot), B01, B02, C01.
- **Lead:** donut A02; response/no-response A05; Customer engagement A06.
- **Workload:** grouped bar B03, owner table B04, B05 card.
- **Workflow:** C01–C03 status/retry table; **Interactions:** D01 line/stacked bar, D02 bar.
- Cùng `organizationId`, `asOf`, `start`, `end`, timezone ở mọi widget; widget
  `ZERO`, `NO_DATA_IN_PERIOD`, `NOT_AVAILABLE` hoặc `NOT_APPLICABLE` hiển thị
  reason riêng, không silent default; rate UI format `%` với 2 decimal places.
