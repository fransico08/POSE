# Evidence W17 — Hiệu năng (Demo & Quy mô), đối soát KPI, mức log 4xx và rà SQL H2 (19–20/11/2026)

Thực hiện trên nhánh `Tài` (`D:\POSE`), chỉ sử dụng cụm PostgreSQL cô lập `127.0.0.1:54329` (`SHOW data_directory = D:/POSE/backend/target/auth-postgres-test`). Không chạm cổng `5432` hay Supabase, không gọi AI/Telegram thật, không sửa migration `V1–V17` (không tạo `V18`), không sửa dữ liệu của Nhi. Không thực hiện mục DEC-12 F4 (quyền Opportunity của Customer Care) và không gộp evidence từ `origin/Nhi` theo yêu cầu chờ quyết định của Tài.

## 1. Cấu hình máy và lệnh chạy lại

- **CPU:** AMD Ryzen 7 6800H with Radeon Graphics (16 logical processors)
- **RAM:** 23,2 GB
- **Hệ điều hành:** Microsoft Windows 11 Pro `10.0.26200`
- **Runtime:** Oracle JDK `25.0.2` (target Java 21), Maven `3.9.15`, PostgreSQL `18.3`, Node.js `v24.13.0`
- **Script đo lặp lại:** `scripts/w17-perf-bench.ps1` (PowerShell + Node `fetch`/`performance.now()`, không thêm thư viện mới)

```powershell
# Chế độ thường (dữ liệu khôi phục v2 + demo CRM.AI):
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\w17-perf-bench.ps1

# Chế độ quy mô lớn (-Scale, tự sinh dữ liệu tổng hợp EDU bằng generate_series + chạy EXPLAIN):
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\w17-perf-bench.ps1 -Scale
```

Script tự kiểm tra cổng `8081` và `54329` đang trống, khởi động cụm `backend/target/auth-postgres-test`, xác nhận `SHOW data_directory`, nhân bản snapshot `pose_v2_review_*` mới nhất (`pose_v2_review_20261120000918810`, đã áp đủ `V1–V17`) sang DB tạm `pose_w17_perf_*`, sinh mật khẩu/JWT/webhook secret ngẫu nhiên trong process (không in/lưu), chạy warm-up **5 lượt** rồi đo **35 lượt/endpoint** (và 1 lượt `-Scale` 30 lượt kèm đủ 7 kế hoạch `EXPLAIN`), sau đó xóa DB tạm và dừng dịch vụ kể cả khi có lỗi.

## 2. Kết quả đo hiệu năng — Bảng A: Dữ liệu demo (chế độ thường)

- **Cỡ dữ liệu EDU trong DB đo:** `10` Lead, `20` Customer, `12` Opportunity (`12` dòng), `26` Interaction, `14` Task, `3` hội thoại (`8` tin nhắn, gồm 1 hội thoại gắn Customer 360 mục tiêu giao cho `edu.s01`), `1` `customer_signals`.
- **Đối chiếu ngưỡng SRS 2.0:** API `< 3000 ms`, webhook ACK `< 2000 ms`. Chạy **2 lượt độc lập** (mỗi lượt N=35 sau 5 warm-up) để kiểm tra độ ổn định:

| Endpoint | Vai trò | Lượt 1 p50 / p95 (ms) | Lượt 2 p50 / p95 (ms) | Ngưỡng SRS 2.0 | Kết quả |
|---|---|---:|---:|---:|---|
| `POST /api/v1/auth/login` | Manager | 360,7 / 384,3 | 355,2 / 365,9 | `< 3000 ms` | **PASS** |
| `GET /api/v1/auth/me` | Manager | 10,2 / 12,4 | 9,4 / 10,5 | `< 3000 ms` | **PASS** |
| `GET /api/v1/leads` | Manager | 9,6 / 12,9 | 9,4 / 12,3 | `< 3000 ms` | **PASS** |
| `GET /api/v1/leads` | Sales | 9,3 / 14,8 | 8,7 / 11,5 | `< 3000 ms` | **PASS** |
| `GET /api/v1/customers` | Manager | 9,9 / 14,8 | 9,9 / 16,9 | `< 3000 ms` | **PASS** |
| `GET /api/v1/customers` | Sales | 9,0 / 11,2 | 8,9 / 10,8 | `< 3000 ms` | **PASS** |
| `GET /api/v1/customers/{id}/360` | Manager | 19,7 / 48,4 | 20,0 / 50,2 | `< 3000 ms` | **PASS** |
| `GET /api/v1/customers/{id}/360` | Sales | 15,7 / 39,4 | 16,4 / 41,3 | `< 3000 ms` | **PASS** |
| `GET /api/v1/opportunities` | Manager | 9,0 / 12,5 | 9,3 / 14,8 | `< 3000 ms` | **PASS** |
| `GET /api/v1/opportunities` | Sales | 8,4 / 14,3 | 8,4 / 13,2 | `< 3000 ms` | **PASS** |
| `GET /api/v1/tasks` | Manager | 9,8 / 20,2 | 9,7 / 21,3 | `< 3000 ms` | **PASS** |
| `GET /api/v1/tasks` | Sales | 8,4 / 16,6 | 7,7 / 15,0 | `< 3000 ms` | **PASS** |
| `GET /api/v1/conversations (page 0)` | Manager | 8,4 / 11,2 | 8,4 / 10,8 | `< 3000 ms` | **PASS** |
| `GET /api/v1/conversations (page 0)` | Sales | 7,6 / 10,1 | 7,9 / 11,0 | `< 3000 ms` | **PASS** |
| `GET /api/v1/conversations/{id}/messages` | Manager | 8,0 / 9,3 | 8,2 / 10,3 | `< 3000 ms` | **PASS** |
| `GET /api/v1/conversations/{id}/messages` | Sales | 7,5 / 9,1 | 7,8 / 9,5 | `< 3000 ms` | **PASS** |
| `GET /api/v1/dashboard/kpis` | Manager | 18,3 / 51,1 | 18,1 / 54,3 | `< 3000 ms` | **PASS** |
| `GET /api/v1/dashboard/kpis` | Sales | 15,1 / 43,3 | 15,4 / 41,4 | `< 3000 ms` | **PASS** |
| `GET /api/v1/dashboard/today` | Manager | 12,5 / 35,5 | 11,9 / 31,8 | `< 3000 ms` | **PASS** |
| `GET /api/v1/dashboard/today` | Sales | 11,2 / 29,3 | 11,1 / 29,3 | `< 3000 ms` | **PASS** |
| `POST /api/v1/channels/zalo-sim/webhook` | Webhook | 7,8 / 12,4 | 7,8 / 9,8 | `< 2000 ms` | **PASS** |

## 3. Kết quả đo hiệu năng — Bảng B: Dữ liệu quy mô (`-Scale`)

- **Cỡ dữ liệu tổng hợp EDU trong DB tạm `pose_w17_perf_*`** (sinh bằng `generate_series`, dữ liệu giả `example.com`/`09xxxxxxxx`, đã chạy `ANALYZE`):
  - **`5.010` Lead**, **`5.020` Customer** (kèm `customer_assignments` ACTIVE), **`2.012` Opportunity** (`4.013` dòng `opportunity_lines`, 1–3 dòng/Cơ hội), **`20.026` Interaction**, **`3.014` Task**, **`2.000` hội thoại** (chia đều giữa `edu.s01` và `edu.s02`), **`40.905` tin nhắn** (phân bố lệch: **5 hội thoại nặng × 600 tin = 3.000 tin**, gồm hội thoại mục tiêu của Customer 360 giao cho `edu.s01`; 1.995 hội thoại còn lại × 19 tin = 37.905 tin), **`2.000` `customer_signals`**.
- **Chạy 2 lượt độc lập** (mỗi lượt N=35 sau 5 warm-up; thêm 1 lượt N=30 để thu 7 kế hoạch `EXPLAIN` sau khi sửa bí danh cột `u.id` trong `explain.sql`):

| Endpoint | Vai trò | Lượt 1 p50 / p95 (ms) | Lượt 2 p50 / p95 (ms) | Ngưỡng SRS 2.0 | Kết quả |
|---|---|---:|---:|---:|---|
| `POST /api/v1/auth/login` | Manager | 358,4 / 370,6 | 356,4 / 371,1 | `< 3000 ms` | **PASS** |
| `GET /api/v1/auth/me` | Manager | 9,4 / 11,2 | 9,7 / 10,9 | `< 3000 ms` | **PASS** |
| `GET /api/v1/leads` | Manager | 30,7 / 38,2 | 32,6 / 44,3 | `< 3000 ms` | **PASS** |
| `GET /api/v1/leads` | Sales | 14,2 / 17,5 | 14,1 / 16,3 | `< 3000 ms` | **PASS** |
| `GET /api/v1/customers` | Manager | 33,2 / 39,0 | 32,1 / 37,9 | `< 3000 ms` | **PASS** |
| `GET /api/v1/customers` | Sales | 21,1 / 25,1 | 20,4 / 23,2 | `< 3000 ms` | **PASS** |
| `GET /api/v1/customers/{id}/360` | Manager | 21,0 / 49,8 | 20,9 / 50,8 | `< 3000 ms` | **PASS** |
| `GET /api/v1/customers/{id}/360` | Sales | 26,2 / 52,3 | 26,3 / 53,0 | `< 3000 ms` | **PASS** |
| `GET /api/v1/opportunities` | Manager | 66,2 / 74,8 | 62,9 / 76,5 | `< 3000 ms` | **PASS** |
| `GET /api/v1/opportunities` | Sales | 22,1 / 26,0 | 23,2 / 31,2 | `< 3000 ms` | **PASS** |
| `GET /api/v1/tasks` | Manager | 138,9 / 152,6 | 130,2 / 149,0 | `< 3000 ms` | **PASS** |
| `GET /api/v1/tasks` | Sales | 30,2 / 40,5 | 29,9 / 34,9 | `< 3000 ms` | **PASS** |
| `GET /api/v1/conversations (page 0)` | Manager | 11,3 / 14,3 | 11,3 / 14,7 | `< 3000 ms` | **PASS** |
| `GET /api/v1/conversations (page 0)` | Sales | 9,6 / 11,7 | 9,8 / 11,3 | `< 3000 ms` | **PASS** |
| `GET /api/v1/conversations (page 25)` | Manager | 15,7 / 17,2 | 16,3 / 18,6 | `< 3000 ms` | **PASS** |
| `GET /api/v1/conversations (page 25)` | Sales | 15,0 / 16,1 | 15,2 / 17,9 | `< 3000 ms` | **PASS** |
| `GET /api/v1/conversations/{id}/messages` (hội thoại 600 tin) | Manager | 8,8 / 10,0 | 8,8 / 9,9 | `< 3000 ms` | **PASS** |
| `GET /api/v1/conversations/{id}/messages` (hội thoại 600 tin) | Sales | 8,4 / 10,0 | 8,9 / 10,0 | `< 3000 ms` | **PASS** |
| `GET /api/v1/dashboard/kpis` | Manager | 727,0 / 800,3 | 722,8 / 825,0 | `< 3000 ms` | **PASS** |
| `GET /api/v1/dashboard/kpis` | Sales | 302,7 / 360,2 | 301,5 / 375,0 | `< 3000 ms` | **PASS** |
| `GET /api/v1/dashboard/today` | Manager | 109,0 / 164,2 | 108,7 / 184,3 | `< 3000 ms` | **PASS** |
| `GET /api/v1/dashboard/today` | Sales | 42,6 / 63,2 | 44,8 / 67,0 | `< 3000 ms` | **PASS** |
| `POST /api/v1/channels/zalo-sim/webhook` | Webhook | 7,3 / 9,4 | 7,8 / 9,6 | `< 2000 ms` | **PASS** |

- **Top 3 endpoint có p95 cao nhất (ngoài `login` BCrypt) ở chế độ `-Scale`:**
  1. `GET /api/v1/dashboard/kpis` (Manager): p50 `722,8–727,0 ms`, p95 **`800,3–825,0 ms`**, max `848,4 ms` (~27% ngưỡng 3.000 ms).
  2. `GET /api/v1/dashboard/kpis` (Sales): p50 `301,5–302,7 ms`, p95 **`360,2–375,0 ms`**, max `384,2 ms`.
  3. `GET /api/v1/dashboard/today` (Manager): p50 `108,7–109,0 ms`, p95 **`164,2–184,3 ms`**, max `199,9 ms` (kế tiếp là `GET /api/v1/tasks` Manager p95 `149,0–152,6 ms`).

## 4. Phân tích `EXPLAIN (ANALYZE, BUFFERS)` ở chế độ `-Scale` và đề xuất cho KLTN

| Truy vấn chính | Execution time | Buffers | Kế hoạch chính & Phát hiện |
|---|---:|---:|---|
| `GET /api/v1/conversations` (Manager, **page 0**, W17-08) | `1,468 ms` | `217 shared hit` | `Seq Scan on conversations` (2.000 dòng, `0,359 ms`, `45 hit`) do sort theo `COALESCE(last_message_at, created_at) DESC, id`. Hai correlated subquery `SubPlan 1` (`last_content`) và `SubPlan 2` (`last_direction`) đều dùng `Index Scan Backward using ix_conversation_messages_org_conversation_time` (`loops=20` mỗi nhánh = **40 index searches**, `160 shared hits`, `~0,26 ms`). |
| `GET /api/v1/conversations` (Manager, **page 25 / OFFSET 500**, W17-08) | `5,412 ms` | `4.211 shared hit` | **Phát hiện quan trọng:** PostgreSQL chạy cả `SubPlan 1` và `SubPlan 2` **trước `OFFSET 500`** trên toàn bộ `520` dòng (`loops=520` × 2 = **1.040 index searches**, `4.160 shared hits`), tăng ~19× lượng buffer hits so với page 0. Ở 2.000 hội thoại vẫn chỉ mất `5,4 ms` (HTTP p95 `17–19 ms`), nhưng sẽ tăng tuyến tính theo `OFFSET` khi phân trang sâu. |
| `GET /api/v1/dashboard/kpis` — E02 (2.012 Opp / 20.026 Interaction) | `8,742 ms` | `7.503 shared hit` | `Nested Loop Anti Join`: `Bitmap Index Scan on ix_opportunities_org_owner_stage` (1.207 dòng) × `BitmapOr` trên `ix_interactions_org_opportunity_time` + `ix_interactions_customer_time` + `ix_interactions_lead_time`. **Không có Seq Scan trên bảng lớn.** |
| `GET /api/v1/dashboard/kpis` — A06 (5.020 Customer / 20.026 Interaction) | `9,347 ms` | `207 hit, 188 read` | `Merge Left Join`: `Bitmap Index Scan on ix_customers_org_related_offering` + `Index Only Scan using ix_interactions_org_customer_time`. **Không có Seq Scan trên bảng lớn.** |
| `GET /api/v1/dashboard/today` — E01 (5.010 Lead / 20.026 Interaction) | `4,586 ms` | `193 hit, 42 read` | `Merge Anti Join`: `Bitmap Index Scan on ix_leads_org_owner_status` + `Index Only Scan using ix_interactions_org_lead_time`. **Không có Seq Scan trên bảng lớn.** |
| `GET /api/v1/tasks` (Manager, page 0, 3.014 Task) | `1,188 ms` | `79 shared hit` | `Bitmap Heap Scan on crm_tasks` (3.014 dòng) + `top-N heapsort` theo `due_at, id`. Không có Seq Scan. |
| `GET /api/v1/customers/{id}/360` — interactions list | `0,063 ms` | `11 shared hit` | Dùng index theo `(organization_id, customer_id, occurred_at)`, tức thời. |

### Kết luận theo số đo (W17-07 & W17-08) và đề xuất không tạo `V18`

- **Đánh giá ngưỡng SRS 2.0:** Toàn bộ **23/23 tổ hợp endpoint × vai trò** đều **ĐẠT** ngưỡng SRS 2.0 (`API < 3000 ms`, `webhook ACK < 2000 ms`) ổn định qua cả 2 lượt ở **quy mô demo** lẫn **quy mô tổng hợp `-Scale`** (5.010 Lead, 5.020 Customer, 2.012 Opportunity, 20.026 Interaction, 3.014 Task, 2.000 hội thoại / 40.905 tin nhắn lệch tải, 2.000 tín hiệu). Do đó **chưa cần tạo migration `V18` trong phạm vi TLCN**.
- **Giới hạn & đề xuất tối ưu cho KLTN** (chỉ ghi báo cáo, chưa áp dụng):
  1. **W17-08 (`ConversationService.list`):** Khi phân trang sâu (`OFFSET` lớn), tách `ORDER BY ... LIMIT ? OFFSET ?` vào subquery/CTE trước rồi mới `LEFT JOIN LATERAL (SELECT content, direction ... LIMIT 1)` để chỉ probe 1 lần cho đúng 20 dòng của trang (giảm từ `1.040` xuống `20` lần index search ở page 25); đồng thời cân nhắc index biểu thức `(organization_id, COALESCE(last_message_at, created_at) DESC, id)` trên `conversations` khi vượt >10.000 hội thoại/tổ chức để tránh `Seq Scan`.
  2. **W17-07 (`DashboardService`):** `GET /api/v1/dashboard/kpis` Manager đang ở mức `~800 ms` ở quy mô `-Scale` (do gộp ~29 câu truy vấn tuần tự trong một request dù từng câu chỉ `5–10 ms`). Nếu dữ liệu tăng 5–10× ở KLTN, cần gom các aggregate cùng bảng (`leads`, `crm_tasks`, `interactions`) để giảm số lượt truy vấn trên mỗi request.

## 5. Đối soát KPI W09 sau các migration V12–V17

Chạy trên DB khôi phục `pose_v2_review_20261119093749618` (đã áp đủ 17 migration `V1–V17`) tại `127.0.0.1:54329`:

1. **SQL độc lập chỉ đọc (`docs/testing/w12-kpi-reconcile-2026-11-12.sql`):** xác nhận dữ liệu nền 6 tổ chức (`cli, edu, log, rea, ret, tech`) gồm mỗi tổ chức 20 Customer (19 ACTIVE), 10 Lead (4 NEW, 3 QUALIFIED, 1 CONVERTED), 12 Opportunity (3 NEW, 2 QUALIFIED, 2 PROPOSAL, 3 WON, 2 LOST), 26 Interaction, 8 Task mở; E01 = `1`/tổ chức; E02 = `7` cơ hội/tổ chức (`cli`: 2.973.500, `edu`: 59.185.000, `log`: 7.347.500, `rea`: 38.540.000.000, `ret`: 321.050, `tech`: 24.575.000 VND); E03 = `1/1` ở `edu`.
2. **Đối chiếu API `GET /api/v1/dashboard/kpis` với SQL độc lập (`RestoredDashboardSmokeTest`):**
   - **6/6 tổ chức khớp hoàn toàn `16/16` chỉ số W09 + `5/5` chỉ số nhóm E**, **0 sai lệch**, không phát sinh dòng `audit_logs` khi đọc, `current_a06_changed=0`.

## 6. Hạ mức log lỗi 4xx từ framework xuống `WARN`

- Sửa `ApiExceptionHandler.unexpected` (`2743924`):
  - Khi ngoại lệ là `ErrorResponse` có mã HTTP `4xx` (`400–499`), ghi mức **`WARN`** (`Framework 4xx API error status=... traceId=... exception=... rootException=... sqlState=... constraint=...`).
  - Giữ mức **`ERROR`** cho các ngoại lệ `5xx`.
  - Vẫn tuân thủ W17-05: không truyền `Throwable`, không ghi message/cause chứa dữ liệu nhạy cảm, giữ nguyên response contract.
- Kiểm chứng qua `W17SafeErrorLoggingIntegrationTest#framework4xxErrorResponseLogsAtWarnNotErrorWithoutSensitiveData` và `#unexpectedExceptionDoesNotLogSensitiveMessageOrThrowableStackTrace`.

## 7. Rà nhánh SQL riêng cho H2 trong `JdbcWorkflowProcessor`

Rà soát `automation/workflow-core/src/main/java/com/pose/workflow/jdbc/JdbcWorkflowProcessor.java`:

| Vị trí | Nhánh H2 | Nhánh PostgreSQL | Đánh giá & Phủ test PostgreSQL |
|---|---|---|---|
| `startOrLoadExecution` (dòng 244–252) | `ON CONFLICT DO NOTHING` | `ON CONFLICT (organization_id, idempotency_key) DO NOTHING` | H2 không hỗ trợ liệt kê target column trong `ON CONFLICT`; PostgreSQL chỉ định rõ target để không nuốt lỗi ràng buộc khác. Ngay sau đó cả hai cùng khóa bằng `SELECT ... FOR UPDATE`. **Không có lỗi.** |
| `createTaskIfAbsent` (dòng 314–351) | `ON CONFLICT DO NOTHING` + `executeUpdate() == 1` | `ON CONFLICT (organization_id, idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING RETURNING id` + `executeQuery()` | H2 không hỗ trợ partial unique index predicate và `RETURNING id`. Cả hai nhánh chỉ ghi `audit.taskCreated` khi thực sự chèn mới và fallback sang `SELECT id FROM crm_tasks WHERE organization_id=? AND idempotency_key=?` khi trùng key. **Không có lỗi.** |

*(Ghi chú thêm: `JdbcConversationMessageProcessor` và `JdbcTransactionalOutboxPublisher` cũng áp dụng cùng mẫu `ON CONFLICT` cho H2 vs PostgreSQL và không phát hiện lỗi.)*

- **Phủ test đường PostgreSQL:** chạy trên cụm 54329 schema production (`V1–V17`) qua `WorkflowIntegrationTest` (10 ca, gồm replay idempotent, lỗi audit tạm thời, kill JVM worker giữa transaction, cô lập 2 tenant cùng key), `WorkflowSchedulerIntegrationTest` (1 ca), `RestoredWorkflowSmokeTest` (1 ca trên DB restore thật) và E2E `-WorkflowWorker` (`demo-workflow.spec.ts` 1/1). **Kết luận: không có lỗi thật, không sửa code.**

## 8. Kiểm chứng bản sạch (từ lượt sửa Java `2743924`; lượt bổ sung `-Scale` không đổi code Java)

| Bộ kiểm thử | Lệnh chạy | Kết quả |
|---|---|---|
| **Backend H2 bản sạch** | `mvn -pl backend -am -Dlogging.level.root=ERROR verify` (sau khi xóa `classes`, `test-classes`, `surefire-reports`, `workflow-core/target`) | `workflow-core` **1/1**, `pose-backend` **482 tổng, 448 đạt, 34 bỏ qua, 0 lỗi** |
| **Backend PostgreSQL 54329 bản sạch** | `backend/scripts/test-postgres-v2.ps1 -DataPackagePath D:\POSE-data\POSE_CRM_DATA_V2_FINAL` | **`Verified.`** — `workflow-core` **1/1**, `pose-backend` **496/496** (0 lỗi, 0 bỏ qua; DB `pose_foundation_test_20261119102225843`, `pose_v2_review_20261119102225843`) |
| **Playwright E2E mặc định** | `scripts/e2e-env.ps1 -Build` | **38/38 đạt (1,4 phút)**; 5 cổng `8080, 8081, 3010, 3011, 54329` đã dừng sạch |
