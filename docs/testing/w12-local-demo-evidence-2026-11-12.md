# Bằng chứng Local Demo POSE CRM W12 - 2026-11-12

## Phạm vi ghi nhận ngày 2026-11-12

Database local: `pose_w12_v12_clean_test`.

Lần ghi nhận này không chạy lại `seed_v2.psql`, không re-import dữ liệu và
không thay đổi database. Đây là hồ sơ bằng chứng runtime đã có, không phải
xác nhận cho một lần bootstrap mới.

## Kết quả runtime đã được xác nhận

| Hạng mục | Kết quả |
| --- | --- |
| Kết nối PostgreSQL local | PASS |
| Public tables | 41 |
| Organizations | 6 |
| Canonical seed verifier | TASK 5 PASS |
| Exit code verifier | 0 |
| Demo credential verifier | 5/5 PASS |
| Exit code credential verifier | 0 |
| Re-import canonical seed | Không thực hiện |
| Database mutation | Không thực hiện |

Kết quả `TASK 5 PASS` là bằng chứng cho
`database/scripts/verify_seed_v2.psql`, bao gồm row count canonical, V12
migration replay, tenant/lifecycle checks, Customer 360 và KPI của sáu tổ
chức. Đây là kết quả runtime đã được xác nhận trước đó; tài liệu này không
chạy lại verifier.

## Xác minh demo credentials

Credential verifier đã PASS cho 5/5 demo accounts. Tất cả account đều
`ACTIVE` và BCrypt hash đã được xác minh. Password không được in hoặc lưu trong
evidence.

Lệnh tái kiểm tra an toàn, chỉ chạy khi `POSE_DEMO_PASSWORD` đã được cấp qua
môi trường:

```powershell
if ([string]::IsNullOrWhiteSpace($env:POSE_DEMO_PASSWORD)) {
    throw 'POSE_DEMO_PASSWORD is required and must not be empty.'
}

python database/scripts/verify_demo_credentials.py
```

Lệnh không in password; kết quả runtime được ghi nhận tại đây đã kết thúc với
exit code `0`.

## Ngoại lệ và cảnh báo đã biết tại thời điểm 2026-11-12

- Canonical verifier giữ đúng hai ngoại lệ legacy của `handover_requests` có
  reviewer cross-tenant; đây không phải ngoại lệ cho dữ liệu additive mới.
- Canonical validator giữ ngoại lệ historical cho 12 Task `CANCELLED` từ
  baseline `933f3c3f`: chỉ các UUID, organization, status `CANCELLED` và
  origin `MANUAL` đã được đối chiếu mới được chấp nhận. Mọi Task khác thiếu
  `cancellation_reason` vẫn fail.
- Kết quả `TASK 5 PASS` không thay thế kiểm thử API/integration cho luồng hủy
  Task; API V12 vẫn yêu cầu reason khi hủy.

## Cập nhật trạng thái ngày 2026-11-13

Phần này là cập nhật sau evidence runtime ngày 2026-11-12; không thay đổi hay
diễn giải lại các kết quả test lịch sử ở trên.

- Follow-up về `w12_additive_manifest.json`, loader và verifier additive đã
  được xử lý bằng phân loại **LEGACY — KHÔNG SỬ DỤNG CHO CANONICAL V12** trong
  `database/data/newitems/W12_ADDITIVE_INTEGRATION.md`. Không cập nhật hash
  hoặc số lượng cũ để mô phỏng metadata canonical.
- Canonical seed là `database/data/seed/multisector_v2/`; dùng
  `database/scripts/seed_v2.psql` để nạp và
  `database/scripts/validate_seed_files.py` để kiểm tra CSV canonical.
  Validator canonical đã PASS; additive workflow không phải đường bootstrap
  canonical.
- Theo cập nhật từ team, Tài đã báo cáo merge commit `2c04aab` và đóng
  follow-up này vào ngày 2026-11-13. Thông tin merge chỉ được ghi nhận theo
  báo cáo của team, không được xác minh độc lập trong tài liệu này.

## Follow-up Data còn mở

Hai follow-up dưới đây chưa được đánh dấu resolved:

1. `E03 = 100%` trên sáu tổ chức; cần quyết định nghiệp vụ/dữ liệu trước khi
   thay đổi dữ liệu hoặc diễn giải lại KPI.
2. `related_offering_id` còn thiếu cho `4,074/10,000` Customers; cần xác nhận
   hoặc hiệu chỉnh theo `DEC-20 D1`.

Không có bằng chứng trong hồ sơ này để đóng hai follow-up trên hoặc thay đổi
định nghĩa KPI.

## Kết luận

Local demo verification ngày 2026-11-12: canonical seed verifier và credential
verifier đều PASS. Canonical W12 tiếp tục dùng seed/validator canonical; trạng
thái legacy của additive workflow không phải là một lần chạy seed mới. Hai
follow-up Data về `E03` và `related_offering_id` vẫn cần quyết định hoặc xác
nhận riêng.
