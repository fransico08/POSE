# Dashboard KPI API v1

`GET /api/v1/dashboard/kpis` trả dữ liệu tổng hợp chỉ đọc, không ghi audit và không lưu cache. Quyền bắt buộc: `dashboard.read`; organization được lấy từ phiên đã xác thực.

## Phạm vi

Theo DEC-17, Admin và Data Staff xem số liệu của tổ chức; Data Staff, kể cả khi kiêm role khác, chỉ nhận dữ liệu tổng hợp. Manager và Sales xem nhóm A, B, D theo scope tương ứng; Customer Care xem nhóm B theo Task của mình. Actor nhiều role nhận hợp scope được phép. KPI ngoài phạm vi có `availability: "NOT_APPLICABLE"`, `reason: "OUTSIDE_ROLE_SCOPE"` và `value`, `numerator`, `denominator` đều null.

## Tham số

- `asOf`: thời điểm ISO-8601 có múi giờ; mặc định thời điểm hiện tại, được dùng thống nhất cho cả response.
- `start`, `end`: thời điểm ISO-8601, gửi đồng thời, `start < end`, khoảng tối đa 366 ngày; đầu khoảng bao gồm, cuối khoảng không bao gồm. Khi không gửi, dùng 30 ngày kết thúc tại `asOf`.
- `timezone`: tên IANA, mặc định `Asia/Ho_Chi_Minh`; dùng để biểu diễn khoảng và không đổi instant tính trong database.
- `ownerUserId`: UUID tùy chọn, phải thuộc scope của actor. Sau scope, lọc Lead theo owner (A01–A05), Customer theo owner (A06), Task theo owner (B01–B05), Interaction theo người ghi (D01–D02); nhóm C trả `NOT_APPLICABLE` với `OWNER_FILTER_NOT_SUPPORTED`. `workflowType`: mã workflow tùy chọn. `metricIds`: danh sách ID tùy chọn; ID phải thuộc 16 KPI W09-01.

## Response

Response có `organizationId`, `asOf`, `period`, `filters` và `metrics`. Metric có `metricId`, `dimensions`, `value`, `unit`, `numerator`, `denominator`, `availability`, `reason`, `periodOrAsOf`, `generatedAt`, `sourceSql`. Các bucket của một KPI đứng liền nhau theo thứ tự A01–D02; số thập phân giữ raw numeric, không định dạng phần trăm trong backend.

```json
{"organizationId":"00000000-0000-0000-0000-000000000001","asOf":"2026-10-01T00:00:00Z","period":{"start":"2026-09-01T00:00:00Z","end":"2026-10-01T00:00:00Z","timezone":"Asia/Ho_Chi_Minh"},"filters":{"ownerUserId":null,"workflowType":null,"metricIds":[]},"metrics":[{"metricId":"A01_TOTAL_LEADS","dimensions":{},"value":10,"unit":"count","numerator":null,"denominator":null,"availability":"AVAILABLE_SNAPSHOT","reason":null,"periodOrAsOf":"2026-10-01T00:00:00Z","generatedAt":"2026-10-01T00:00:00Z","sourceSql":"W09-01:A01"}]}
```

Công thức và nguồn dữ liệu: [W09-01 KPI Dictionary](../implementation/w09-01-dashboard-kpi-dictionary.md) và [W09-02](../implementation/w09-02-sector-kpi-ui-contract.md). Không suy doanh thu, CLV hoặc các chỉ số ngành từ Opportunity.

Theo quyết định bổ sung DEC-17, Lead/Customer archive không được đếm; B05 khi mẫu số 0 dùng `NOT_AVAILABLE_ZERO_DENOMINATOR`; A06 chỉ tính Interaction có `occurred_at <= asOf`. C02 đếm FAILED theo workflow type × retry count, không trả nội dung `last_error` cho bất kỳ vai trò nào.

Khi không có bản ghi trong kỳ, A05 `NO_RESPONSE`, D01, D02 và C02 trả `value: null` cùng `NO_DATA_IN_PERIOD`; khác với `ZERO` là giá trị thực bằng 0. Đối soát coi kết quả 0 của SQL W09-01 trong trường hợp kỳ trống là tương đương về ngữ nghĩa và ghi nhận để Nhi cập nhật SQL.

## Lỗi

Theo envelope foundation: tham số không hợp lệ trả 400 `VALIDATION_ERROR` với `fieldErrors`; thiếu permission trả 403 `FORBIDDEN_PERMISSION`; owner ngoài scope hoặc tenant trả 403 `FORBIDDEN_SCOPE`. Không nhận `organizationId` do client chỉ định.

## Việc tiếp theo

Frontend cần bổ sung root `dashboard` vào allowlist BFF và triển khai UI theo W09-02; hai việc này không thuộc card backend.
