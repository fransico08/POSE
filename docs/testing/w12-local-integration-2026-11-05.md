# Báo cáo kiểm chứng W12 — Hướng dẫn chạy local và tích hợp đa tổ chức (2026-11-05)

- **Card:** W12 — Hoàn thiện seed demo, hướng dẫn chạy local và integration fixes (phần `[Tài] FE`)
- **Nhánh:** `Tài` (`D:\POSE`)
- **Phạm vi thực hiện:**
  - Phần A: Viết hướng dẫn chạy local (`docs/huong-dan-chay-local.md`), cập nhật `backend/README.md`, `readme.md`, `frontend/README.md`, bổ sung script hỗ trợ an toàn `scripts/local-db.ps1` và tự kiểm chứng thực tế theo Cách 2 trên cluster tạm ở cổng `54330`.
  - Phần B: Mở rộng `scripts/e2e-env.ps1` cho Manager của cả 6 tổ chức (`EDU`, `REA`, `TECH`, `RET`, `LOG`, `CLI`), viết bộ kiểm thử smoke E2E đa tổ chức `frontend/e2e/tenants-smoke.spec.ts` (96 lượt kiểm trang trên 6 tổ chức × 16 trang), ghi nhận FINDING và sửa lỗi tràn ngang thẻ mobile (`375px`) khi hiển thị mã sản phẩm dài theo ngành kèm bài test Vitest.
  - Không sửa code backend, không thêm migration, không sửa gói dữ liệu của Nhi hay CSV/Data Dictionary, không cài thêm package.

---

## 1. Phần A — Kiểm chứng hướng dẫn chạy local (Cách 2 trên cluster tạm `127.0.0.1:54330`)

### 1.1. Kiểm chứng các chốt chặn an toàn của `scripts/local-db.ps1`

| Tình huống kiểm thử | Lệnh gọi | Kết quả thực tế |
|---|---|---|
| Từ chối cổng `5432` (bảo vệ PostgreSQL server thật) | `.\scripts\local-db.ps1 -Port 5432` | Dừng ngay (`ExitCode=1`): `Tu choi cong 5432 de bao ve PostgreSQL server chinh tren may.` |
| Từ chối cổng `54329` (bảo vệ cluster test E2E) | `.\scripts\local-db.ps1 -Port 54329` | Dừng ngay (`ExitCode=1`): `Tu choi cong 54329 vi day la cluster test co lap danh rieng cho E2E/integration test.` |
| Từ chối host khác `127.0.0.1` | `.\scripts\local-db.ps1 -HostAddress 0.0.0.0` | Dừng ngay (`ExitCode=1`): `Chi cho phep HostAddress=127.0.0.1 cho database local.` |
| Từ chối đường dẫn trùng cluster E2E | `.\scripts\local-db.ps1 -DataDir D:\POSE\backend\target\auth-postgres-test` | Dừng ngay (`ExitCode=1`): `Tu choi thao tac tren cluster test E2E` |

### 1.2. Các bước đã chạy thật theo Cách 2 trên cluster tạm `127.0.0.1:54330`

| Bước | Nội dung thực hiện | Bằng chứng / Kết quả thực tế | Trạng thái |
|---|---|---|---|
| **A1** | Khởi tạo cluster tạm tại `D:\POSE\backend\target\local-guide-verify-54330` (cổng `54330`), xác nhận `SHOW data_directory`, restore `POSE_CRM_DB_v2_FINAL.backup`, chạy `verify_v2.sql` và `database/verify-foundation-v2.sql` qua `scripts/local-db.ps1` | `SHOW data_directory = D:\POSE\backend\target\local-guide-verify-54330`; `TASK 5 PASS - synchronized migration/seed verification passed.`; `Foundation DB v2 assertions passed` | **ĐẠT** |
| **A2** | Khởi động Backend ở cổng `8080` trỏ tới `jdbc:postgresql://127.0.0.1:54330/pose_crm_local` với `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` và `SPRING_FLYWAY_BASELINE_VERSION=1` | Backend lắng nghe trên `127.0.0.1:8080`; Flyway tự động baseline `V1` và áp tiếp `V2…V11` thành công | **ĐẠT** |
| **A3** | Kiểm tra `flyway_schema_history`, số lượng tổ chức/người dùng và danh sách tài khoản `ADMIN`/`MANAGER` của 6 tổ chức bằng `psql` | `flyway_schema_history`: đủ 11 dòng (`1` `BASELINE` + `2..11` `SQL`, tất cả `success = t`); `orgs = 6`, `users = 36`; 6 mã `code` (`cli`, `edu`, `log`, `rea`, `ret`, `tech`) đều `ACTIVE`; `cli.admin@example.com` là `INACTIVE`, 6 tài khoản `*.mgr@example.com` đều `ACTIVE` | **ĐẠT** |
| **A4** | Đặt mật khẩu thử ngẫu nhiên (BCrypt cost 12) **chỉ trong bản sao tạm `54330`**, khởi động Frontend dev (`npm run dev` ở cổng `3000` với `BACKEND_URL=http://127.0.0.1:8080`), đăng nhập `EDU` / `edu.mgr@example.com` | `POST http://127.0.0.1:3000/api/session/login` trả `200`; `GET http://127.0.0.1:3000/api/session/me` trả `200` với `org=edu (POSE Academy)`, `user=edu.mgr@example.com`, `roles=MANAGER` | **ĐẠT** |
| **A5** | Dừng Frontend dev (`3000`), dừng Backend (`8080`), dừng cluster tạm `54330` qua `scripts/local-db.ps1 -Action Stop` và xóa hoàn toàn thư mục `D:\POSE\backend\target\local-guide-verify-54330` | `waiting for server to shut down..... done / server stopped`; thư mục tạm đã xóa sạch; các cổng `3000`, `8080`, `54330` đều đã dừng | **ĐẠT** |

### 1.3. Phần không kiểm thực tế trong Phần A

- **Cách 1 — Kết nối Supabase dùng chung (DEC-08):** Không chạy thực tế trong phiên này vì theo quy tắc dự án (`AGENTS.md`), agent không giữ/nhập credential Supabase và không kết nối tới Supabase. Các biến môi trường (`POSE_DATABASE_URL`, `POSE_DATABASE_USERNAME`, `POSE_DATABASE_PASSWORD`, `POSE_JWT_SECRET_BASE64`, `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5`) được đối chiếu trực tiếp từ lịch sử vận hành trong `backend/README.md` và `backend/src/main/resources/application.yml`.

---

## 2. Phần B — Ma trận kiểm thử tích hợp đa tổ chức (6 tổ chức × 16 trang)

Bộ kiểm thử `frontend/e2e/tenants-smoke.spec.ts` đăng nhập vai trò `MANAGER` của từng tổ chức (`edu.mgr@example.com`, `rea.mgr@example.com`, `tech.mgr@example.com`, `ret.mgr@example.com`, `log.mgr@example.com`, `cli.mgr@example.com`) và kiểm tra 16 trang (9 trang menu + 7 trang chi tiết cho từng danh sách có dữ liệu) trên cả 6 tổ chức (tổng cộng **96 lượt kiểm trang**):

- Không có response HTTP `5xx`.
- Không có `console.error` hoặc `pageerror`.
- Hiển thị đúng tên tổ chức (`POSE Academy`, `POSE Realty`, `POSE Tech`, `POSE Retail`, `POSE Logistics`, `POSE Clinic`).
- Danh sách có dữ liệu hiển thị dòng/thẻ hợp lệ, không rơi vào trạng thái lỗi.
- Không hiển thị raw enum code ở các trường đã có nhãn tiếng Việt (`Trạng thái`, `Loại`, `Loại khách hàng`, `Nguồn`, `Giai đoạn`, `Ưu tiên`, `Vai trò hiện tại`).
- Ngày/giờ hiển thị đúng định dạng `dd/MM/yyyy` hoặc `dd/MM/yyyy HH:mm` (không lộ chuỗi ISO `yyyy-MM-ddTHH:mm`).
- Ở viewport mobile `375px` (`375×812`), `document.documentElement.scrollWidth <= document.documentElement.clientWidth` (không cuộn ngang).

| Trang / Route | `EDU` (POSE Academy) | `REA` (POSE Realty) | `TECH` (POSE Tech) | `RET` (POSE Retail) | `LOG` (POSE Logistics) | `CLI` (POSE Clinic) |
|---|---|---|---|---|---|---|
| 1. `/app` (Tổng quan) | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT |
| 2. `/app/san-pham` (Danh sách Sản phẩm & dịch vụ) | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT *(sau khi sửa FINDING-W12-FE-01)* | ĐẠT |
| 3. `/app/san-pham/[id]` (Chi tiết Sản phẩm & dịch vụ) | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT |
| 4. `/app/phan-cong` (Phân công sản phẩm) | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT |
| 5. `/app/lead` (Danh sách Lead) | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT |
| 6. `/app/lead/[id]` (Chi tiết Lead) | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT |
| 7. `/app/khach-hang` (Danh sách Khách hàng) | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT |
| 8. `/app/khach-hang/[id]` (Chi tiết Khách hàng) | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT |
| 9. `/app/co-hoi` (Pipeline Cơ hội bán hàng) | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT |
| 10. `/app/co-hoi/[id]` (Chi tiết Cơ hội bán hàng) | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT |
| 11. `/app/tuong-tac` (Danh sách Tương tác) | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT |
| 12. `/app/tuong-tac/[id]` (Chi tiết Tương tác) | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT |
| 13. `/app/nhiem-vu` (Danh sách Nhiệm vụ) | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT |
| 14. `/app/nhiem-vu/[id]` (Chi tiết Nhiệm vụ) | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT |
| 15. `/app/khach-hang-360` (Hub Hồ sơ khách hàng 360) | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT |
| 16. `/app/khach-hang-360/[id]` (Chi tiết Hồ sơ 360) | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT | ĐẠT |

---

## 3. Danh sách FINDING (Tách theo FE đã sửa / Văn — Dữ liệu / Thắng — Backend)

### 3.1. FINDING phía Frontend (Đã sửa và thêm Vitest + E2E)

#### `FINDING-W12-FE-01` — Tràn ngang layout ở viewport `375px` trên trang `/app/san-pham` của tổ chức `LOG` (`POSE Logistics`) do mã sản phẩm dài không khoảng trắng
- **Tổ chức & Trang:** `LOG` (`POSE Logistics`) — `/app/san-pham` (và tiềm ẩn trên các thẻ `.offering-mobile-card`, `.sales-assignment-mobile-card` khi gặp mã dài theo ngành).
- **Bằng chứng trước khi sửa:**
  - Khi chạy `e2e/tenants-smoke.spec.ts` ở viewport `375×812`, trang `/app/san-pham` của `LOG` bị tràn ngang: `document.documentElement.scrollWidth = 384 > clientWidth = 375` (tràn `9px`).
  - Nguyên nhân: Dịch vụ `"Vận chuyển tiêu chuẩn toàn quốc"` của `LOG` có mã `STANDARD_NATIONWIDE` (19 ký tự in hoa liền nhau không khoảng trắng) nằm cạnh `<Badge>Đang hoạt động</Badge>` (`white-space: nowrap`) trong phần `<header>` của `.offering-mobile-card`. Các lớp `.offering-mobile-card`, `.offering-mobile-card > header > :first-child` và `.sales-assignment-mobile-card` thiếu `min-width: 0`, đồng thời `.offering-code` và `.offering-name` thiếu `overflow-wrap: anywhere`.
- **Cách khắc phục:**
  - Bổ sung `overflow-wrap: anywhere` cho `.offering-name`, `.offering-code`, `.offering-muted` và `min-width: 0` cho `.offering-mobile-cards`, `.offering-mobile-card`, `.offering-mobile-card > header`, `.offering-mobile-card > header > :first-child`, `.offering-mobile-card dl`, `.offering-mobile-card dl > div` trong `frontend/src/styles/offerings.css`.
  - Đồng bộ `min-width: 0` và `overflow-wrap: anywhere` cho `.sales-assignment-mobile-cards` và `.sales-assignment-mobile-card` trong `frontend/src/styles/sales-assignments.css`.
  - Thêm bài kiểm thử Vitest trong `frontend/tests/offering-sales-readonly.test.tsx` (`wraps long industry-specific offering codes and names inside mobile cards without overflowing`) và xác nhận lại bằng `e2e/tenants-smoke.spec.ts` (`scrollWidth = 375 <= clientWidth = 375` trên cả 6 tổ chức).

#### `FINDING-W12-DOC-01` — Lệnh chạy `verify_v2.sql` cần đứng tại thư mục gốc của gói dữ liệu V2
- **Bằng chứng:** Khi chạy `psql -f D:\POSE-data\POSE_CRM_DATA_V2_FINAL\verify_v2.sql` từ thư mục `D:\POSE`, `verify_seed_v2.psql` dòng 251 báo lỗi `database/data/seed/multisector_v2/expected_kpis.csv: No such file or directory` vì đường dẫn tương đối trong `\copy`.
- **Cách khắc phục:** Cập nhật `scripts/local-db.ps1` (`Push-Location $PackageRoot` trước khi gọi `verify_v2.sql`) và ghi chú rõ lệnh `Push-Location $pkgRoot` trong `docs/huong-dan-chay-local.md`.

---

### 3.2. FINDING dữ liệu ghi nhận cho Văn (Nhi) — Không tự sửa dữ liệu gốc

#### `FINDING-W12-DATA-01` — Trường `customer_type` và `related_offering_id` của toàn bộ 120 Customer và `source` của toàn bộ 60 Lead trong gói dữ liệu gốc đang là `NULL`
- **Tổ chức & Trang:** Cả 6 tổ chức (`EDU`, `REA`, `TECH`, `RET`, `LOG`, `CLI`) trên `/app/khach-hang`, `/app/khach-hang/[id]`, `/app/khach-hang-360`, `/app/khach-hang-360/[id]`, `/app/lead`, `/app/lead/[id]`.
- **Bằng chứng:**
  - Các cột `customers.customer_type`, `customers.address`, `customers.notes`, `customers.related_offering_id` và `leads.source`, `leads.notes` được thêm ở migration `V9__customer_lead_profile_fields.sql` sau khi gói backup V1 (`POSE_CRM_DB_v2_FINAL.backup`) được đóng gói.
  - Do đó, toàn bộ 120 bản ghi Customer gốc và 60 bản ghi Lead gốc trong 6 tổ chức đều có `customer_type = NULL`, `related_offering_id = NULL`, `source = NULL`.
  - Giao diện Frontend hiện hiển thị nhất quán giá trị `"Chưa cập nhật"` (giữ nguyên cách hiển thị hiện tại theo đúng yêu cầu, không gây lỗi render hay lộ chữ `null`/`undefined`).
- **Đề xuất cho Văn:** Nếu cập nhật seed demo ở đợt tiếp theo, có thể bổ sung dữ liệu mẫu cho `customer_type` (`INDIVIDUAL` / `BUSINESS`), `related_offering_id` và `leads.source` để danh sách/chi tiết Customer và Lead hiển thị phong phú hơn ngay từ dữ liệu khởi tạo.

#### `FINDING-W12-DATA-02` — Tiêu đề mẫu của Opportunity trong seed chứa mã giai đoạn tiếng Anh để phục vụ đối chiếu demo
- **Tổ chức & Trang:** Cả 6 tổ chức (`EDU`, `REA`, `TECH`, `RET`, `LOG`, `CLI`) trên `/app/co-hoi`, `/app/co-hoi/[id]`, `/app/khach-hang-360/[id]`.
- **Bằng chứng:** Trong `opportunities.csv`, tiêu đề (`title`) của các cơ hội ở giai đoạn `QUALIFIED`, `PROPOSAL`, `WON`, `LOST` có dạng `"<Tên Offering> - QUALIFIED demo 2"`, `"<Tên Offering> - PROPOSAL demo 3"`, `"<Tên Offering> - WON demo 4"`, `"<Tên Offering> - LOST demo 5"`.
- **Nhận xét:** Đây là chuỗi văn bản trong trường `title` của dữ liệu mẫu (không phải enum giai đoạn của UI; cột/nhãn giai đoạn của UI đã hiển thị đúng tiếng Việt: `Mới`, `Đủ điều kiện`, `Báo giá`, `Thành công`, `Thất bại`). Ghi nhận để Văn lưu ý nếu muốn đặt tên cơ hội tự nhiên hơn cho buổi trình diễn.

---

### 3.3. FINDING phía Backend / API ghi nhận cho Thắng

- Không phát hiện lỗi `5xx`, lỗi schema hay sai lệch contract API mới trên các luồng đọc danh sách/chi tiết của cả 6 tổ chức trong đợt kiểm thử smoke W12. (Các giới hạn contract từ những tuần trước như bộ lọc Opportunity theo `leadId` ở W06 hoặc thông điệp `409` trên một số thao tác đã được ghi nhận trong tài liệu các tuần tương ứng).

---

## 4. Kết quả chạy kiểm thử tổng thể

1. **Frontend Unit & Integration Tests (`npx vitest run`):**
   - Kết quả: **25/25 test files passed, 289/289 tests passed**.
2. **Kiểm tra kiểu tĩnh, Lint và Production Build:**
   - `npm run typecheck`: **ĐẠT** (`0` lỗi).
   - `npm run lint`: **ĐẠT** (`0` lỗi, `0` cảnh báo).
   - `npm run build`: **ĐẠT**.
3. **Full-stack Playwright E2E (`scripts/e2e-env.ps1`):**
   - Lượt 1 (`powershell.exe -NoProfile -File .\scripts\e2e-env.ps1 -Build`): **24/24 tests passed** (`52.6s`).
   - Lượt 2 (`powershell.exe -NoProfile -File .\scripts\e2e-env.ps1` — không có `-Build`): **24/24 tests passed** (`55.4s`).
   - Trạng thái cổng sau khi chạy: Tất cả các cổng `8080`, `8081`, `3010`, `3011`, `54329`, `54330` đều đã dừng sạch.

