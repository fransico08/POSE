# W05 — Bằng chứng Opportunity core (chưa hoàn thành)

Ngày 03/11/2026; checkout `D:\POSE`, nhánh `Tài`, xuất phát từ `6a27856`. Backend Java target 21, JDK 25.0.2, Maven 3.9.15; H2 MODE PostgreSQL và PostgreSQL 18 trên cluster cô lập `127.0.0.1:54329`. Không chạy frontend, không kết nối Supabase hay PostgreSQL cổng 5432.

## Đã làm và kiểm chứng

| Phạm vi | Kiểm thử | Kết quả |
|---|---|---|
| V10 | `OpportunityLinesMigrationTest`: backfill, bảo toàn tiền, ngày VN, constraint trạng thái, FK tenant, unique Offering | Đạt trên H2 |
| V10 và dữ liệu restore | `RestoredV2SmokeTest`: 72 Opportunity/72 dòng, tổng theo tổ chức và WON | Đạt trên PostgreSQL |
| Sales Assignment | `cancelledOpportunityDoesNotPreventRevocation`: công việc CANCELLED không chặn thu hồi | Đạt H2 và PostgreSQL |
| H2 | Maven `verify` | 138 test, 122 đạt, 16 skip, 0 lỗi; mốc 120 đạt |
| PostgreSQL | `scripts/test-postgres-v2.ps1`, verifier gói và smoke | 138 test, 125 đạt, 13 skip, 0 lỗi; `Verified.`; mốc 123 đạt |
| Rehearsal | `scripts/setup-supabase-v2.ps1 -Rehearsal` trên database mới của cluster 54329, xác nhận `SHOW data_directory` | `Verified`: restore và Flyway 1–10, Hibernate validate, API role không có quyền bảng, RLS; cluster đã dừng |

Lệnh Maven H2: đặt `JAVA_HOME` về JDK 25.0.2, xóa các biến `POSE_TEST_*`, `POSE_DATABASE_*`, `POSE_V2_SMOKE_DATABASE_URL`, `POSE_DEMO_PASSWORD` khỏi process, rồi chạy `mvn.cmd '-Dlogging.level.root=ERROR' verify`. PostgreSQL dùng chính lệnh `scripts/test-postgres-v2.ps1` và đường dẫn gói/JDK/Maven trong yêu cầu W05. Rehearsal dùng `scripts/setup-supabase-v2.ps1 -Rehearsal` với cùng gói/JDK/Maven; kiểm cổng và data directory trước khi chạy. Số test đọc từ Surefire XML. Không chạy acceptance cần tài khoản demo.

V10 dùng chính UUID của Opportunity làm UUID của dòng backfill trong **bảng riêng**, mỗi Opportunity sinh một dòng tất định; không gọi hàm UUID riêng của cơ sở dữ liệu. `expected_value` gốc không thay đổi, bao gồm giá trị null; WON cũ cần `expected_value` khác null để thỏa constraint mới. Dữ liệu restore đã qua smoke; chưa xác minh dữ liệu Supabase hiện tại.

## FINDING và phần dừng

1. D4 yêu cầu mọi Offering sau lưu nằm trong `OfferingRepository.SALES_READ_SCOPE` (`backend/src/main/java/com/pose/backend/offering/OfferingRepository.java`), fragment bắt buộc `o.status='ACTIVE'`. D5 cho phép giữ Offering cũ `INACTIVE`. Không thể đáp ứng cả hai với fragment nguyên trạng; chưa tự nới scope hoặc tạo API ghi.
2. D3 yêu cầu giữ giá trị chốt cũ trong audit `before` khi reopen; phần mutation lại nói audit chỉ ghi stage/trạng thái, không ghi notes hay lý do. Cần xác định chính xác những trường được phép ghi trước khi làm reopen.
3. `SalesAssignmentRepository.countOpenWork/openWork` hiện chỉ đối chiếu `opportunities.offering_id`. Với nhiều dòng, việc thu hồi assignment của Offering không phải dòng đầu có thể bỏ sót Opportunity mở. Phần đã sửa chỉ loại `CANCELLED` như yêu cầu; chưa tự mở rộng điều kiện này.
4. Ma trận `roles-authorization-v1.md` cho Customer Care đọc Opportunity, nhưng data không cấp `opportunity.read`; yêu cầu W05 chọn theo data. `business-rules-v1.md` §5 còn ghi một Offering; §11.2 đã nêu UC-12 nhiều dòng thay DEC-01. Hai tài liệu lịch sử không bị sửa.

Chưa triển khai các route Opportunity, xử lý quyền/scope, CRUD, các trạng thái, audit, test integration API hay kiểm thử đồng thời/rollback. Dự thảo [contract](../api/opportunity-api-v1.md) chưa phải contract của API đã có. Không áp V10 lên Supabase; cần Tài quyết định các điểm trên, sao lưu bằng `pg_dump`, hoàn thiện và kiểm chứng lại trước khi triển khai.

Sau khi Tài áp V10 trong tương lai, kiểm quyền Data API bằng:

```sql
select has_table_privilege('anon','public.opportunity_lines','select'), has_table_privilege('authenticated','public.opportunity_lines','select');
```

Kết quả mong đợi: cả hai `false`. Câu lệnh chưa chạy trên Supabase.
