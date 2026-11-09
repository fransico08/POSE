# Dashboard KPI API v1

`GET /api/v1/dashboard/kpis` trả dữ liệu tổng hợp chỉ đọc, không ghi audit và không lưu cache. Quyền bắt buộc: `dashboard.read`; organization được lấy từ phiên đã xác thực.

## Phạm vi

Theo DEC-17, Admin và Data Staff xem số liệu của tổ chức; Data Staff, kể cả khi kiêm role khác, chỉ nhận dữ liệu tổng hợp. Manager và Sales xem nhóm A, B, D theo scope tương ứng; Customer Care xem nhóm B theo Task của mình. Actor nhiều role nhận hợp scope được phép. KPI ngoài phạm vi có `availability: "NOT_APPLICABLE"`, `reason: "OUTSIDE_ROLE_SCOPE"` và `value`, `numerator`, `denominator` đều null.

## Tham số

- `asOf`: thời điểm ISO-8601 có múi giờ; mặc định thời điểm hiện tại, được dùng thống nhất cho cả response.
- `start`, `end`: thời điểm ISO-8601, gửi đồng thời, `start < end`, khoảng tối đa 366 ngày; đầu khoảng bao gồm, cuối khoảng không bao gồm. Khi không gửi, dùng 30 ngày kết thúc tại `asOf`.
- `timezone`: tên IANA, mặc định `Asia/Ho_Chi_Minh`; dùng để biểu diễn khoảng và không đổi instant tính trong database.
- `ownerUserId`: UUID tùy chọn, phải thuộc scope của actor. Sau scope, lọc Lead theo owner (A01–A05), Customer theo owner (A06), Task theo owner (B01–B05), Interaction theo người ghi (D01–D02); nhóm C trả `NOT_APPLICABLE` với `OWNER_FILTER_NOT_SUPPORTED`. Quy tắc lọc nhóm E ở mục dưới. `workflowType`: mã workflow tùy chọn. `metricIds`: danh sách ID tùy chọn thuộc 16 KPI W09-01 hoặc 5 KPI DEC-21.

## Response

Response có `organizationId`, `asOf`, `period`, `filters` và `metrics`. Metric có `metricId`, `dimensions`, `value`, `unit`, `numerator`, `denominator`, `availability`, `reason`, `periodOrAsOf`, `generatedAt`, `sourceSql`. Các bucket của một KPI đứng liền nhau theo thứ tự A01–E05; số thập phân giữ raw numeric, không định dạng phần trăm trong backend.

```json
{"organizationId":"00000000-0000-0000-0000-000000000001","asOf":"2026-10-01T00:00:00Z","period":{"start":"2026-09-01T00:00:00Z","end":"2026-10-01T00:00:00Z","timezone":"Asia/Ho_Chi_Minh"},"filters":{"ownerUserId":null,"workflowType":null,"metricIds":[]},"metrics":[{"metricId":"A01_TOTAL_LEADS","dimensions":{},"value":10,"unit":"count","numerator":null,"denominator":null,"availability":"AVAILABLE_SNAPSHOT","reason":null,"periodOrAsOf":"2026-10-01T00:00:00Z","generatedAt":"2026-10-01T00:00:00Z","sourceSql":"W09-01:A01"}]}
```

Công thức và nguồn dữ liệu: [W09-01 KPI Dictionary](../implementation/w09-01-dashboard-kpi-dictionary.md) và [W09-02](../implementation/w09-02-sector-kpi-ui-contract.md). Không suy doanh thu, CLV hoặc các chỉ số ngành từ Opportunity.

Theo quyết định bổ sung DEC-17, Lead/Customer archive không được đếm; B05 khi mẫu số 0 dùng `NOT_AVAILABLE_ZERO_DENOMINATOR`; A06 chỉ tính Interaction có `occurred_at <= asOf`. C02 đếm FAILED theo workflow type × retry count, không trả nội dung `last_error` cho bất kỳ vai trò nào.

Khi không có bản ghi trong kỳ, A05 `NO_RESPONSE`, D01, D02 và C02 trả `value: null` cùng `NO_DATA_IN_PERIOD`; khác với `ZERO` là giá trị thực bằng 0. Đối soát coi kết quả 0 của SQL W09-01 trong trường hợp kỳ trống là tương đương về ngữ nghĩa và ghi nhận để Nhi cập nhật SQL.

## Lỗi

Theo envelope foundation: tham số không hợp lệ trả 400 `VALIDATION_ERROR` với `fieldErrors`; thiếu permission trả 403 `FORBIDDEN_PERMISSION`; owner ngoài scope hoặc tenant trả 403 `FORBIDDEN_SCOPE`. Không nhận `organizationId` do client chỉ định.

## Nhóm E — Giá trị và rò rỉ (DEC-21)

`metricIds` nhận thêm `E01_OVERDUE_LEAD_RESPONSE`, `E02_UNATTENDED_OPPORTUNITY_VALUE`, `E03_UNCONTACTED_AFTER_HANDOVER`, `E04_FIRST_MESSAGE_RESPONSE_TIME`, `E05_AI_SUGGESTION_ACCEPTANCE_RATE`; các KPI này đứng sau D02 và dùng `sourceSql` là `DEC-21:E01` đến `DEC-21:E05`.

- E01: Lead NEW/QUALIFIED chưa archive, tạo không muộn hơn `asOf` trừ SLA, không có Interaction gắn Lead đến `asOf`; snapshot, đơn vị count.
- E02: Opportunity NEW/QUALIFIED/PROPOSAL không có Interaction gắn Opportunity hoặc Customer/Lead của nó trong `(asOf − idle-days, asOf]`; nhóm theo `dimensions.currency`, tổng `expected_value` null coi là 0, numerator là số Opportunity.
- E03: với mỗi Customer chưa archive, lấy bàn giao SALES APPROVED mới nhất được duyệt trong `[start,end)`; đếm Customer chưa có Interaction của target từ lúc duyệt đến `asOf`. Denominator là số Customer bàn giao; kỳ rỗng trả `NO_DATA_IN_PERIOD` và value null.
- E04: `NOT_SUPPORTED`, reason `CONVERSATION_DATA_NOT_AVAILABLE`, value null cho đến khi có dữ liệu hội thoại.
- E05: số ACCEPTED + EDITED chia cho tổng ACCEPTED + EDITED + REJECTED được duyệt trong `[start,end)`, theo `dimensions.kind`; mẫu số 0 trả `NOT_AVAILABLE_ZERO_DENOMINATOR`. Không trả nội dung suggestion.

Ngưỡng hệ thống mặc định 24 giờ và 14 ngày lần lượt qua `POSE_KPI_LEAD_RESPONSE_SLA_HOURS` và `POSE_KPI_OPPORTUNITY_IDLE_DAYS`. Admin/Data Staff xem tổng hợp toàn tổ chức; Manager và Sales theo scope, E05 theo người tạo (Manager gồm bản thân và thành viên hiệu lực team quản lý). Care chỉ có role này nhận `NOT_APPLICABLE`, `OUTSIDE_ROLE_SCOPE`; nhiều role áp dụng hợp quyền theo DEC-17. `ownerUserId` lọc lần lượt owner Lead, owner Opportunity, target bàn giao và người tạo invocation. Các điều kiện Interaction của nhóm E và Việc hôm nay chỉ xét Interaction người xem được đọc; E02 của Manager có thể khác Admin. Không trả 0 giả khi không áp dụng hoặc chưa hỗ trợ.

## Việc hôm nay

`GET /api/v1/dashboard/today` yêu cầu `dashboard.read`, có `asOf` ISO-8601, `timezone` IANA mặc định `Asia/Ho_Chi_Minh`, `limit` mặc định 20 và tối đa 50. Response `applicable` và `items`; mỗi mục có `type`, `rank`, `subjectType`, `subjectId`, `title`, `ownerUserId`, `ownerName`, `reason`, `occurredAt` hoặc `dueAt`, `expectedValue`, `currency`, `href`. Kết quả theo scope ghi của hồ sơ và scope Task; Care chỉ xem Task của mình, người chỉ có Data Staff nhận `applicable: false` và danh sách rỗng. Không ghi audit; `Cache-Control: no-store`.

Thứ tự nhóm: `OVERDUE_TASK` theo hạn tăng; `LEAD_RESPONSE_OVERDUE` theo thời điểm tạo tăng; `HANDOVER_NOT_CONTACTED` theo thời điểm duyệt tăng, chỉ lần bàn giao mới nhất và target còn là owner; `IDLE_OPPORTUNITY` theo giá trị giảm, null cuối rồi ID; `TASK_DUE_TODAY` theo hạn tăng. Một Task không xuất hiện hai lần. Ngày hôm nay được xác định theo timezone.

Ví dụ dữ liệu giả: `{"applicable":true,"items":[{"type":"OVERDUE_TASK","rank":1,"subjectType":"TASK","subjectId":"00000000-0000-0000-0000-000000000001","title":"Theo dõi hồ sơ","ownerUserId":"00000000-0000-0000-0000-000000000002","ownerName":"Nhân viên mẫu","reason":"Nhiệm vụ đã quá hạn","occurredAt":null,"dueAt":"2026-11-09T01:00:00Z","expectedValue":null,"currency":null,"href":"/app/nhiem-vu/00000000-0000-0000-0000-000000000001"}]}`.

## Việc tiếp theo

Frontend cần bổ sung root `dashboard` vào allowlist BFF và triển khai UI theo W09-02; hai việc này không thuộc card backend.
