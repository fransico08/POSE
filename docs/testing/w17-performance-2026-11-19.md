# Evidence W17 — Hiệu năng, đối soát KPI, mức log 4xx và rà SQL H2 (19/11/2026)

Thực hiện trên nhánh `Tài` (`D:\POSE`), chỉ sử dụng cụm PostgreSQL cô lập `127.0.0.1:54329` (`SHOW data_directory = D:/POSE/backend/target/auth-postgres-test`). Không chạm cổng `5432` hay Supabase, không gọi AI/Telegram thật, không sửa migration `V1–V17` (không tạo `V18`), không sửa dữ liệu của Nhi. Không thực hiện mục DEC-12 F4 (quyền Opportunity của Customer Care) và không gộp evidence từ `origin/Nhi` theo yêu cầu chờ quyết định của Tài.

## 1. Cấu hình máy và lệnh chạy lại

- **CPU:** AMD Ryzen 7 6800H with Radeon Graphics (16 logical processors)
- **RAM:** 23,2 GB
- **Hệ điều hành:** Microsoft Windows 11 Pro `10.0.26200`
- **Runtime:** Oracle JDK `25.0.2` (target Java 21), Maven `3.9.15`, PostgreSQL `18.3`, Node.js `v24.13.0`
- **Script đo lặp lại:** `scripts/w17-perf-bench.ps1` (PowerShell + Node `fetch`/`performance.now()`, không thêm thư viện mới)

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\w17-perf-bench.ps1 -Build
```

Script tự kiểm tra cổng `8081` và `54329` đang trống, khởi động cụm `backend/target/auth-postgres-test`, xác nhận `SHOW data_directory`, nhân bản snapshot `pose_v2_review_*` mới nhất (`pose_v2_review_20261119093749618`, đã áp đủ `V1–V17`) sang DB tạm `pose_w17_perf_*`, sinh mật khẩu/JWT/webhook secret ngẫu nhiên trong process (không in/lưu), tạo tài khoản kênh `ZALO_SIM` và 3 hội thoại với 10 tin nhắn demo, chạy warm-up **5 lượt** rồi đo **35 lượt/endpoint**, sau đó xóa DB tạm và dừng dịch vụ kể cả khi có lỗi.

## 2. Kết quả đo hiệu năng (đóng W17-07 và W17-08)

Đối chiếu ngưỡng **SRS 2.0**: API `< 3000 ms`, webhook ACK `< 2000 ms`. Mọi webhook call dùng `msg_id` duy nhất (chèn tin mới thực sự + nối Interaction session + ghi outbox).

| Endpoint | N | min (ms) | p50 (ms) | p95 (ms) | max (ms) | Ngưỡng SRS 2.0 | Kết quả |
|---|---:|---:|---:|---:|---:|---:|---|
| `POST /api/v1/auth/login` | 35 | 347,3 | 359,8 | 376,7 | 377,3 | `< 3000 ms` | **PASS** |
| `GET /api/v1/auth/me` | 35 | 8,7 | 10,3 | 11,7 | 11,8 | `< 3000 ms` | **PASS** |
| `GET /api/v1/leads?page=0&size=20` | 35 | 8,8 | 9,9 | 13,2 | 14,2 | `< 3000 ms` | **PASS** |
| `GET /api/v1/customers?page=0&size=20` | 35 | 9,1 | 10,4 | 13,2 | 16,0 | `< 3000 ms` | **PASS** |
| `GET /api/v1/opportunities?page=0&size=20` | 35 | 8,1 | 9,5 | 14,7 | 17,4 | `< 3000 ms` | **PASS** |
| `GET /api/v1/tasks?page=0&size=20` | 35 | 10,6 | 11,9 | 24,4 | 26,7 | `< 3000 ms` | **PASS** |
| `GET /api/v1/conversations?page=0&size=20` | 35 | 9,1 | 10,1 | 11,6 | 11,8 | `< 3000 ms` | **PASS** |
| `GET /api/v1/conversations/{id}/messages?page=0&size=50` | 35 | 8,5 | 10,3 | 11,7 | 12,0 | `< 3000 ms` | **PASS** |
| `GET /api/v1/dashboard/kpis` | 35 | 19,4 | 22,0 | 59,0 | 64,8 | `< 3000 ms` | **PASS** |
| `GET /api/v1/dashboard/today` | 35 | 12,5 | 14,2 | 45,4 | 45,7 | `< 3000 ms` | **PASS** |
| `POST /api/v1/channels/zalo-sim/webhook` | 35 | 7,3 | 8,2 | 9,9 | 10,7 | `< 2000 ms` | **PASS** |

- **Nhận xét:** Cả 11/11 endpoint đều đạt xa ngưỡng SRS 2.0 (`login` mất ~360 ms chủ yếu do BCrypt cost 12; các API nghiệp vụ khác có p95 từ `11,6` đến `59,0 ms`; webhook ACK p95 `9,9 ms`).
- **Đóng W17-07 và W17-08 trên tập dữ liệu khôi phục + demo CRM.AI:** Không có endpoint nào vượt ngưỡng hay có p95 bất thường nên chưa cần thêm index hay migration `V18`. Khi dữ liệu thực tế lớn hơn (giai đoạn KLTN), cần chạy lại `scripts/w17-perf-bench.ps1` trên tập tải lớn trước khi đề xuất index bổ sung.

## 3. Đối soát KPI W09 sau các migration V12–V17

Chạy trên DB khôi phục `pose_v2_review_20261119093749618` (đã áp đủ 17 migration `V1–V17`) tại `127.0.0.1:54329`:

1. **SQL độc lập chỉ đọc (`docs/testing/w12-kpi-reconcile-2026-11-12.sql`):** xác nhận dữ liệu nền 6 tổ chức (`cli, edu, log, rea, ret, tech`) gồm mỗi tổ chức 20 Customer (19 ACTIVE), 10 Lead (4 NEW, 3 QUALIFIED, 1 CONVERTED), 12 Opportunity (3 NEW, 2 QUALIFIED, 2 PROPOSAL, 3 WON, 2 LOST), 26 Interaction, 8 Task mở; E01 = `1`/tổ chức; E02 = `7` cơ hội/tổ chức (`cli`: 2.973.500, `edu`: 59.185.000, `log`: 7.347.500, `rea`: 38.540.000.000, `ret`: 321.050, `tech`: 24.575.000 VND); E03 = `1/1` ở `edu`.
2. **Đối chiếu API `GET /api/v1/dashboard/kpis` với SQL độc lập (`RestoredDashboardSmokeTest`):**
   - **6/6 tổ chức khớp hoàn toàn `16/16` chỉ số W09 + `5/5` chỉ số nhóm E**, **0 sai lệch**, không phát sinh dòng `audit_logs` khi đọc, `current_a06_changed=0`.

## 4. Hạ mức log lỗi 4xx từ framework xuống `WARN`

- Sửa `ApiExceptionHandler.unexpected` (`2743924`):
  - Khi ngoại lệ là `ErrorResponse` có mã HTTP `4xx` (`400–499`), ghi mức **`WARN`** (`Framework 4xx API error status=... traceId=... exception=... rootException=... sqlState=... constraint=...`).
  - Giữ mức **`ERROR`** cho các ngoại lệ `5xx`.
  - Vẫn tuân thủ W17-05: không truyền `Throwable`, không ghi message/cause chứa dữ liệu nhạy cảm, giữ nguyên response contract.
- Kiểm chứng qua `W17SafeErrorLoggingIntegrationTest#framework4xxErrorResponseLogsAtWarnNotErrorWithoutSensitiveData` và `#unexpectedExceptionDoesNotLogSensitiveMessageOrThrowableStackTrace`.

## 5. Rà nhánh SQL riêng cho H2 trong `JdbcWorkflowProcessor`

Rà soát `automation/workflow-core/src/main/java/com/pose/workflow/jdbc/JdbcWorkflowProcessor.java`:

| Vị trí | Nhánh H2 | Nhánh PostgreSQL | Đánh giá & Phủ test PostgreSQL |
|---|---|---|---|
| `startOrLoadExecution` (dòng 244–252) | `ON CONFLICT DO NOTHING` | `ON CONFLICT (organization_id, idempotency_key) DO NOTHING` | H2 không hỗ trợ liệt kê target column trong `ON CONFLICT`; PostgreSQL chỉ định rõ target để không nuốt lỗi ràng buộc khác. Ngay sau đó cả hai cùng khóa bằng `SELECT ... FOR UPDATE`. **Không có lỗi.** |
| `createTaskIfAbsent` (dòng 314–351) | `ON CONFLICT DO NOTHING` + `executeUpdate() == 1` | `ON CONFLICT (organization_id, idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING RETURNING id` + `executeQuery()` | H2 không hỗ trợ partial unique index predicate và `RETURNING id`. Cả hai nhánh chỉ ghi `audit.taskCreated` khi thực sự chèn mới và fallback sang `SELECT id FROM crm_tasks WHERE organization_id=? AND idempotency_key=?` khi trùng key. **Không có lỗi.** |

*(Ghi chú thêm: `JdbcConversationMessageProcessor` và `JdbcTransactionalOutboxPublisher` cũng áp dụng cùng mẫu `ON CONFLICT` cho H2 vs PostgreSQL và không phát hiện lỗi.)*

- **Phủ test đường PostgreSQL:** chạy trên cụm 54329 schema production (`V1–V17`) qua `WorkflowIntegrationTest` (10 ca, gồm replay idempotent, lỗi audit tạm thời, kill JVM worker giữa transaction, cô lập 2 tenant cùng key), `WorkflowSchedulerIntegrationTest` (1 ca), `RestoredWorkflowSmokeTest` (1 ca trên DB restore thật) và E2E `-WorkflowWorker` (`demo-workflow.spec.ts` 1/1). **Kết luận: không có lỗi thật, không sửa code.**

## 6. Kiểm chứng bản sạch sau khi sửa

| Bộ kiểm thử | Lệnh chạy | Kết quả |
|---|---|---|
| **Backend H2 bản sạch** | `mvn -pl backend -am -Dlogging.level.root=ERROR verify` (sau khi xóa `classes`, `test-classes`, `surefire-reports`, `workflow-core/target`) | `workflow-core` **1/1**, `pose-backend` **482 tổng, 448 đạt, 34 bỏ qua, 0 lỗi** |
| **Backend PostgreSQL 54329 bản sạch** | `backend/scripts/test-postgres-v2.ps1 -DataPackagePath D:\POSE-data\POSE_CRM_DATA_V2_FINAL` | **`Verified.`** — `workflow-core` **1/1**, `pose-backend` **496/496** (0 lỗi, 0 bỏ qua; DB `pose_foundation_test_20261119102225843`, `pose_v2_review_20261119102225843`) |
| **Playwright E2E mặc định** | `scripts/e2e-env.ps1 -Build` | **38/38 đạt (1,4 phút)**; 5 cổng `8080, 8081, 3010, 3011, 54329` đã dừng sạch |
