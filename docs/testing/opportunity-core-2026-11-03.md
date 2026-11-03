# W05 — Bằng chứng Opportunity core

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

## Bổ sung API và xử lý review

Tài đã chốt F1–F4 tại DEC-12: mọi dòng khi tạo/PATCH phải ACTIVE và thuộc scope của cùng role; reopen audit giữ các trường chốt được phép, không ghi lý do; thu hồi Sales Assignment kiểm Offering ở mọi dòng; Customer Care theo permission trong data.

| Nhóm test | Bằng chứng |
|---|---|
| Tạo và validation | Hai dòng, số tiền, audit, field lỗi, trùng, giới hạn, ngày VN và field backend tính |
| Scope và role | Offering/Customer ngoài scope, dòng INACTIVE, Lead cũ, assignment Customer, Manager, Admin, Care, Data Staff, tenant khác và không mượn scope giữa role |
| PATCH, chốt, reopen | Thay/thêm/xóa dòng, version/stage, locked, win/lose/cancel, reopen và audit không ghi lý do |
| Transaction | Audit lỗi rollback create/PATCH/win; hai PATCH đồng thời chỉ một bộ dòng thắng |
| F3 | Offering dòng thứ hai chặn thu hồi; WON/LOST/CANCELLED không chặn; open-work không lặp |

Lượt hoàn thiện xuất phát từ `0666f69`, cùng JDK/Maven và cluster cô lập như trên. Sau khi xóa biến môi trường như yêu cầu, chạy lại `mvn.cmd '-Dlogging.level.root=ERROR' verify` và `powershell.exe -NoProfile -File .\scripts\test-postgres-v2.ps1 -DataPackagePath 'D:\POSE-data\POSE_CRM_DATA_V2_FINAL' -JdkDirectory 'C:\Program Files\Java\jdk-25.0.2' -MavenPath 'C:\Users\Dell\.maven\maven-3.9.15\bin\mvn.cmd'`. Số đọc từ Surefire XML:

| Bộ kiểm thử | Tổng | Đạt | Skip | Lỗi | So với mốc |
|---|---:|---:|---:|---:|---:|
| H2 | 154 | 138 | 16 | 0 | +16 đạt so với 122 |
| PostgreSQL | 154 | 141 | 13 | 0 | +16 đạt so với 125; `Verified.` |
| `OpportunityIntegrationTest` | 15 | 15 | 0 | 0 | Chạy trên cả hai DB |
| `SalesAssignmentIntegrationTest` | 35 | 35 | 0 | 0 | Chạy trên cả hai DB |

Cổng `54329` đã được xác nhận không lắng nghe sau lần PostgreSQL cuối. Các ca demo acceptance vẫn skip vì không dùng credentials.

## Khắc phục review F5 — 04/11/2026

Sau khi kiểm scope người sửa, PATCH có `lines` kiểm assignment của owner đối với từng Offering mới thêm và trả `422 VALIDATION_ERROR` tại `lines[i].offeringId` nếu không đủ điều kiện. Không kiểm lại assignment owner cho Offering cũ hoặc khi request không có `lines`; kiểm tra ACTIVE và scope người sửa vẫn áp dụng như F1.

| Kiểm thử | Kết quả H2 và PostgreSQL |
|---|---|
| Manager thêm Offering chưa giao cho owner | 422 đúng field/thông báo; dòng, giá trị và audit không đổi |
| Manager thêm Offering đã giao; Admin thêm Offering chưa giao | Lần lượt 200 và 422 đúng field |
| Assignment của Offering cũ bị thu hồi | Manager sửa title rồi đổi số lượng và vị trí dòng cũ: 200 vì Offering vẫn ACTIVE trong scope Manager |
| Offering ngoài scope người sửa cùng Offering chưa giao owner | 400 tại dòng ngoài scope, ưu tiên trước 422 |
| Giá trị legacy null | PATCH đầu tiên tính lại tổng từ các dòng; trường hợp kiểm thử trả `0.00` |

Chạy lại H2 `mvn.cmd '-Dlogging.level.root=ERROR' verify` sau khi xóa biến `POSE_TEST_*`, `POSE_DATABASE_*`, `POSE_V2_SMOKE_DATABASE_URL`, `POSE_DEMO_PASSWORD` và đặt JDK 25.0.2. PostgreSQL dùng lại lệnh `test-postgres-v2.ps1` nêu ở trên, kiểm cổng `54329` không lắng nghe trước khi chạy. Số liệu đọc từ Surefire XML:

| Bộ kiểm thử | Tổng | Đạt | Skip | Lỗi | So với mốc |
|---|---:|---:|---:|---:|---:|
| H2 | 160 | 144 | 16 | 0 | +6 đạt so với 138 |
| PostgreSQL | 160 | 147 | 13 | 0 | +6 đạt so với 141; `Verified.` |
| `OpportunityIntegrationTest` | 21 | 21 | 0 | 0 | Chạy trên cả hai DB |

Cổng `54329` đã dừng sau kiểm thử. Không chạy tài khoản demo, không kết nối Supabase hoặc PostgreSQL cổng 5432, không sửa frontend hay migration.

**FINDING F4:** ma trận `roles-authorization-v1.md` cho Customer Care đọc Opportunity, nhưng data không cấp `opportunity.read`; API trả `403 FORBIDDEN_PERMISSION` theo quyết định của Tài. `business-rules-v1.md` §5 còn ghi một Offering; §11.2 đã nêu UC-12 nhiều dòng thay DEC-01. Hai tài liệu lịch sử không bị sửa.

Không chạy frontend, không chạy acceptance dùng tài khoản demo, không áp V10 lên Supabase và không chạy lại rehearsal vì không thêm migration. Không triển khai chuyển Lead thành Customer, Interaction/Task hay dashboard. Trước khi áp V10 lên Supabase, Tài sao lưu bằng `pg_dump` và kiểm dữ liệu WON có `expected_value` khác null; không xác nhận được dữ liệu Supabase từ phiên này.

Sau khi Tài áp V10 trong tương lai, kiểm quyền Data API bằng:

```sql
select has_table_privilege('anon','public.opportunity_lines','select'), has_table_privilege('authenticated','public.opportunity_lines','select');
```

Kết quả mong đợi: cả hai `false`. Câu lệnh chưa chạy trên Supabase.
