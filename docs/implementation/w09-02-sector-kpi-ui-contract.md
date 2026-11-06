# W09-02 — Sector KPI, UI và API reconciliation contract

## Quy ước chung

W09-02 tái sử dụng nguyên 16 KPI W09-01, không tạo công thức thứ hai. Mỗi lần làm mới dashboard dùng một `asOf` chung; `start` inclusive, `end` exclusive, database dùng `TIMESTAMPTZ`, UI hiển thị `Asia/Ho_Chi_Minh`. Backend lấy `organization_id` từ tenant/role đã xác thực; owner filter chỉ thu hẹp dữ liệu trong tenant đó.

Raw ratio giữ chính xác `numeric`; UI chỉ format phần trăm tối đa hai chữ số. `ZERO` là số đo được bằng 0; `NO_DATA_IN_PERIOD` là không row trong `[start,end)`; `NOT_AVAILABLE` là thiếu nguồn/mẫu số; `NOT_APPLICABLE` là không đúng scope. Không thay các trạng thái này bằng 0.

## KPI → nguồn → biểu đồ và filter

| ID | Nguồn/grain | Thời gian, mẫu số | UI / availability |
|---|---|---|---|
| A01 | `leads`; org | snapshot | Card tổng Lead; `AVAILABLE_SNAPSHOT` |
| A02 | `leads.status`; org × status | snapshot; status/A01 | Donut/bar; mẫu số 0 → `NOT_AVAILABLE_ZERO_DENOMINATOR` |
| A03 | `leads.status='QUALIFIED'`; org | snapshot; QUALIFIED/A01 | Card; không là historical funnel |
| A04 | `leads.converted_customer_id`; org | snapshot; converted reference/A01 | Card; conversion snapshot, không cohort funnel |
| A05 | `leads` + `interactions`; org × lead | cả Lead/interaction trong `[start,end)`; `min(occurred_at)-created_at` | Average/median + no-response; không Lead → `NO_DATA_IN_PERIOD` |
| A06 | `customers` + customer `interactions`; org × segment | `asOf`; ACTIVE <30d, AT_RISK 30–60d, INACTIVE >60d, NEVER NULL | Stacked bar; snapshot |
| B01 | `crm_tasks.status`; org | snapshot; OPEN/IN_PROGRESS | Card |
| B02 | `crm_tasks.due_at,status`; org | snapshot; open và `due_at < asOf` | Card/list |
| B03 | `crm_tasks.priority,status`; org × priority × status | snapshot | Grouped/stacked bar |
| B04 | `crm_tasks.owner_user_id` + `users`; org × owner | snapshot; owner filter | Bar/table; owner cùng tenant |
| B05 | `crm_tasks`; org | DONE có `completed_at` trong `[start,end)`; on-time/DONE | Card; denominator 0 → `NOT_AVAILABLE_ZERO_DENOMINATOR` |
| C01 | `workflow_executions`; org × workflow | terminal `finished_at` trong period; DONE/(DONE+FAILED) | Reliability card/line; persisted state, không runtime proof |
| C02 | `workflow_executions`; org × type × retry/error | FAILED `finished_at` trong period | Retry/failure table; NULL error là dimension riêng |
| C03 | `workflow_executions`; org × workflow | terminal period; `finished_at >= started_at` | Cycle-time card/line; thiếu/âm loại riêng |
| D01 | `interactions.channel`; org × channel | `occurred_at` trong period | Stacked bar/line |
| D02 | `interactions.recorded_by_user_id` + `users`; org × recorder | `occurred_at` trong period; optional recorder filter | Bar/table; recorder cùng tenant |

## Áp dụng đa ngành và ranh giới dữ liệu

| Industry từ `organizations.industry` | Diễn giải CRM được phép | NOT_AVAILABLE (không suy diễn) |
|---|---|---|
| Retail / F&B Chain | Quan tâm offering, follow-up, workload, interaction | Revenue, order, repeat purchase |
| Healthcare / Clinic | Tư vấn/chăm sóc, Task nhắc việc, kênh liên hệ | Chẩn đoán, kết quả y tế, lịch hẹn/tái khám, ưu tiên thiết yếu theo CLV |
| Education | Lead tư vấn offering, Task/kênh liên hệ | Enrollment, completion, kết quả học tập |
| Logistics / Transportation | Yêu cầu báo giá/chăm sóc, Task điều phối | Shipment, SLA, tracking |
| Real Estate | Lead quan tâm offering, follow-up tư vấn | Đặt cọc, hợp đồng, giao dịch |
| SaaS / Software và ngành khác | 16 KPI CRM lõi | MRR/revenue/retention khi chưa có transaction source |

Không hardcode enum ngành ở API/UI: backend đọc `organizations.industry` thực tế. V1 không có transaction, appointment, enrollment, shipment, consent hay outcome được xác minh; RFM monetary, CLV, NBA và model evaluation là `NOT_AVAILABLE`. Không dùng `opportunities.expected_value` làm doanh thu.

## Contract cho Tài

Ví dụ minh họa, không xác nhận endpoint hiện hữu:

```json
{
  "organizationId": "server-authorized UUID",
  "asOf": "2026-09-30T00:00:00Z",
  "period": {"start": "2026-09-01T00:00:00Z", "end": "2026-10-01T00:00:00Z", "timezone": "Asia/Ho_Chi_Minh"},
  "filters": {"ownerUserId": null, "workflowType": null, "metricIds": ["A01_TOTAL_LEADS"]},
  "metrics": [{"metricId": "A01_TOTAL_LEADS", "organizationId": "server-authorized UUID", "dimensions": {"status": null}, "value": 10, "unit": "count", "numerator": null, "denominator": null, "availability": "AVAILABLE_SNAPSHOT", "reason": null, "periodOrAsOf": "2026-09-30T00:00:00Z", "generatedAt": "2026-09-30T00:00:00Z", "sourceSql": "W09-01:A01", "chart": "card"}]
}
```

Validate `start < end`; parameterize SQL; authorize organization/owner trước truy vấn; trả stable array, raw numeric và filter đã áp dụng. `periodOrAsOf` là instant sinh snapshot, không phải source update time. Detail list cần pagination và cùng filter như card. Pre-aggregate fact để tránh row multiplication; không bao giờ cộng tenant từ client request.

## Layout và đối soát

Overview có A01, A03, A04, B01, B02, C01 (5–7 cards theo role); phần chi tiết là A02/A05/A06, B03–B05, C02–C03, D01–D02. Status là snapshot; line chỉ dùng khi timestamp period hợp lệ. Click-through chỉ khi API có detail route được cấp quyền. Cùng input org/owner/`asOf`/`[start,end)`/timezone phải cho SQL/API/UI cùng denominator; tổng chart phải khớp card.

Seed demo tập trung tháng 09/2026, nên default last-30-days có thể `NO_DATA_IN_PERIOD`; dùng `[2026-09-01,2026-10-01)` để đối soát. Data Mart/RFM/CLV/NBA tương lai phải version contract khi đổi formula/provenance và bổ sung transaction, consent, action/outcome, model version được duyệt.
