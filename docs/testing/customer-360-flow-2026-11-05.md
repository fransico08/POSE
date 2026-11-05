# Evidence QA luồng dữ liệu và nghiệm thu vai trò Customer 360 — W07 (2026-11-05)

## 1. Môi trường kiểm thử cô lập

- **Checkout & nhánh:** `D:\POSE`, nhánh `Tài`.
- **Biến môi trường process:** Đã xóa toàn bộ biến `PG*`, `POSE_*`, `SPRING_DATASOURCE_*` khỏi process trước khi khởi chạy. Xác nhận các cổng `8080`, `8081`, `3010`, `3011`, `54329` đều rảnh trước khi chạy.
- **PostgreSQL test cluster:** `127.0.0.1:54329`, user `pose_auth_test`.
  - Kiểm `SHOW data_directory` trả về đúng: `D:/POSE/backend/target/auth-postgres-test`.
  - Không kết nối Supabase hoặc PostgreSQL cục bộ cổng `5432`.
- **Database bản sao:**
  - Nguồn (đã ở Flyway V11): `pose_v2_review_20261105144618591`.
  - Lệnh tạo bản sao: `createdb -h 127.0.0.1 -p 54329 -U pose_auth_test -T pose_v2_review_20261105144618591 pose_v2_c360_202611051512`.
  - Tên database bản sao giữ lại sau khi kiểm: **`pose_v2_c360_202611051512`**.
- **Runtime:**
  - Backend: chạy tại `http://127.0.0.1:8081` với `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5` nối vào `pose_v2_c360_202611051512`.
  - Frontend BFF: chạy `next start` tại `http://127.0.0.1:3011` với `BACKEND_URL=http://127.0.0.1:8081`.
  - Mọi lời gọi QA đều đi qua BFF (`POST /api/session/login`, `GET /api/backend/customers/{id}/360`, `GET /api/backend/customer-360/reconciliation`, `GET /api/backend/customers`).

## 2. Tài khoản test trong tổ chức EDU (`0b47be39-3b3c-5195-a4e2-8a29ffbadcf0`)

Mật khẩu test được sinh ngẫu nhiên trong bộ nhớ, băm bằng `BCryptPasswordEncoder(12)` (từ `spring-security-crypto` trong `~/.m2`), chỉ cập nhật trên bản sao `pose_v2_c360_202611051512` và xóa ngay thư mục biên dịch tạm sau khi nạp. Không in mật khẩu, không ghi email.

| Ký hiệu vai trò | Role code | User ID (`ACTIVE`) | Ghi chú phạm vi trên dữ liệu EDU |
|---|---|---|---|
| **Admin** | `ADMIN` | `cd6bed61-dbbc-52bc-b0e0-5e8598d89dbf` | Toàn bộ tổ chức EDU |
| **Manager** | `MANAGER` | `a6fd80d1-2f4f-57d8-8650-c8ed8870a0b9` | Toàn bộ tổ chức EDU |
| **Sales A** | `SALES_REP` | `16bf03a9-05d2-57ef-9017-be06b9552839` | Owner của Customer 1 (`be4c13e1-...`) và Customer 3 (`cfa07d5a-...`) |
| **Sales B** | `SALES_REP` | `76834503-7c65-4ca7-9e9d-284df9c1fad9` | User `SALES_REP` đơn vai trò trong bản sao, không sở hữu 3 Customer mẫu |
| **Customer Care** | `CUSTOMER_CARE` | `8a294c3d-8926-5ab0-aeec-b75765f1cccc` | Được gán `CARE` `ACTIVE` trên cả 3 Customer mẫu; không được gán trên `3d3bbf2f-...` |
| **Sales kiêm Care** | `SALES_REP` + `CUSTOMER_CARE` | `c17b7640-3f09-554c-af94-a2fa5f3d01f6` | Owner kiêm `CARE` `ACTIVE` của Customer 2 (`f59b823d-...`); ngoài phạm vi Customer 1 & 3 |
| **Data Staff** | `DATA_STAFF` | `076cb017-5e61-5a9a-b6d5-28c0f090e7be` | Đọc metadata 360 toàn tổ chức EDU và đối soát dữ liệu |

## 3. Ma trận nghiệm thu theo vai trò (Contract & DEC-15 vs Thực tế)

Ký hiệu trong bảng:
- **MĐ (Mong đợi):** Theo [`docs/api/customer-360-api-v1.md`](../api/customer-360-api-v1.md) và [`DEC-15`](../meetings/decision-log.md#dec-15--customer-360-aggregation-v1).
- **TT (Thực tế):** Kết quả gọi qua BFF `http://127.0.0.1:3011/api/backend/*`.

| Vai trò | Định danh & Contact | Lead đã chuyển | Cơ hội (Opportunity) | Tương tác (Interaction) | Nhiệm vụ (Task) | Feedback | Support đang mở | Đối soát (`/reconciliation`) |
|---|---|---|---|---|---|---|---|---|
| **Admin** | **MĐ:** Đầy đủ định danh + contact (`email`, `phone`, `address`, `notes`).<br>**TT:** `200`, `hasContact=true` (**ĐẠT**). | **MĐ:** `restricted=false`, đếm & danh sách toàn tổ chức.<br>**TT:** `restricted=false`, khớp SQL (**ĐẠT**). | **MĐ:** `restricted=false`, đếm theo stage, tổng mở/chốt, danh sách.<br>**TT:** `restricted=false`, khớp SQL (**ĐẠT**). | **MĐ:** `restricted=false`, gồm cả `CARE_VISIBLE` và `SALES_ONLY`, đủ nội dung & tiêu đề cơ hội.<br>**TT:** `restricted=false`, khớp SQL (**ĐẠT**). | **MĐ:** `restricted=false`, đếm theo status + quá hạn toàn tổ chức.<br>**TT:** `restricted=false`, khớp SQL (**ĐẠT**). | **MĐ:** `restricted=false`, đếm + điểm TB mọi visibility.<br>**TT:** `restricted=false`, khớp SQL (**ĐẠT**). | **MĐ:** `restricted=false`, đếm yêu cầu chưa đóng toàn tổ chức.<br>**TT:** `restricted=false`, khớp SQL (**ĐẠT**). | **MĐ:** `200`, trả `totals`, `matched`, `mismatched`.<br>**TT:** `200`, `matched=20`, `mismatched=0` (**ĐẠT**). |
| **Manager** | **MĐ:** Đầy đủ định danh + contact.<br>**TT:** `200`, `hasContact=true` (**ĐẠT**). | **MĐ:** `restricted=false`, đếm & danh sách toàn tổ chức.<br>**TT:** `restricted=false`, khớp SQL (**ĐẠT**). | **MĐ:** `restricted=false`, đếm, tổng tiền & danh sách toàn tổ chức.<br>**TT:** `restricted=false`, khớp SQL (**ĐẠT**). | **MĐ:** `restricted=false`, gồm cả `SALES_ONLY`, đủ nội dung.<br>**TT:** `restricted=false`, khớp SQL (**ĐẠT**). | **MĐ:** `restricted=false`, đếm + quá hạn toàn tổ chức.<br>**TT:** `restricted=false`, khớp SQL (**ĐẠT**). | **MĐ:** `restricted=false`, đếm + điểm TB mọi visibility.<br>**TT:** `restricted=false`, khớp SQL (**ĐẠT**). | **MĐ:** `restricted=false`, đếm yêu cầu chưa đóng toàn tổ chức.<br>**TT:** `restricted=false`, khớp SQL (**ĐẠT**). | **MĐ:** `403 FORBIDDEN_PERMISSION`.<br>**TT:** `403 FORBIDDEN_PERMISSION` (**ĐẠT**). |
| **Sales A** *(trong scope: C1, C3)* | **MĐ:** Đầy đủ định danh + contact khi là owner/được gán.<br>**TT:** `200` trên C1 & C3 (`hasContact=true`), `403 FORBIDDEN_SCOPE` trên C2 (**ĐẠT**). | **MĐ:** `restricted=false`, chỉ đếm Lead trong scope của Sales A.<br>**TT:** `restricted=false`, C1=`1`, C3=`0`, khớp SQL (**ĐẠT**). | **MĐ:** `restricted=false`, chỉ đếm Cơ hội do Sales A sở hữu.<br>**TT:** `restricted=false`, C1=`1` (`8.075.000 ₫`), C3=`0` (do Cơ hội của C3 thuộc `c17b7640-...`), khớp SQL (**ĐẠT**). | **MĐ:** `restricted=false`, thấy cả `SALES_ONLY` và nội dung của Customer thuộc scope.<br>**TT:** `restricted=false`, C1=`2`, C3=`2` (`hasSalesOnly=true`), khớp SQL (**ĐẠT**). | **MĐ:** `restricted=false`, chỉ đếm Task do Sales A sở hữu.<br>**TT:** `restricted=false`, C1=`1` (`overdue=1`), C3=`0`, khớp SQL (**ĐẠT**). | **MĐ:** `restricted=false`, gồm cả `SALES_ONLY`.<br>**TT:** `restricted=false`, C1=`1` (`avg=1.0`), C3=`1` (`avg=4.0`), khớp SQL (**ĐẠT**). | **MĐ:** `restricted=true`, `count=null` (không có quyền đọc Support).<br>**TT:** `restricted=true`, `count=null` (**ĐẠT**). | **MĐ:** `403 FORBIDDEN_PERMISSION`.<br>**TT:** `403 FORBIDDEN_PERMISSION` (**ĐẠT**). |
| **Sales B** *(ngoài scope C1, C2, C3)* | **MĐ:** `403 FORBIDDEN_SCOPE` khi Customer không thuộc quyền sở hữu/phân công.<br>**TT:** `403 FORBIDDEN_SCOPE` trên cả C1, C2, C3 (**ĐẠT**). | **MĐ:** Chặn ở mức hồ sơ (`403`).<br>**TT:** `403 FORBIDDEN_SCOPE` (**ĐẠT**). | **MĐ:** Chặn ở mức hồ sơ (`403`).<br>**TT:** `403 FORBIDDEN_SCOPE` (**ĐẠT**). | **MĐ:** Chặn ở mức hồ sơ (`403`).<br>**TT:** `403 FORBIDDEN_SCOPE` (**ĐẠT**). | **MĐ:** Chặn ở mức hồ sơ (`403`).<br>**TT:** `403 FORBIDDEN_SCOPE` (**ĐẠT**). | **MĐ:** Chặn ở mức hồ sơ (`403`).<br>**TT:** `403 FORBIDDEN_SCOPE` (**ĐẠT**). | **MĐ:** Chặn ở mức hồ sơ (`403`).<br>**TT:** `403 FORBIDDEN_SCOPE` (**ĐẠT**). | **MĐ:** `403 FORBIDDEN_PERMISSION`.<br>**TT:** `403 FORBIDDEN_PERMISSION` (**ĐẠT**). |
| **Customer Care** *(được gán CARE trên C1, C2, C3)* | **MĐ:** Đầy đủ định danh + contact trên Customer được gán CARE; `403` nếu không được gán.<br>**TT:** `200` trên C1, C2, C3 (`hasContact=true`); `403 FORBIDDEN_SCOPE` trên `3d3bbf2f-...` (**ĐẠT**). | **MĐ:** `restricted=true`, `count=null`, `items=[]`.<br>**TT:** `restricted=true`, `count=null` (**ĐẠT**). | **MĐ:** `restricted=true`, `stages=null`, `openExpectedValue=null`, `wonFinalValue=null`, `items=[]`.<br>**TT:** `restricted=true`, toàn bộ số = `null` (**ĐẠT**). | **MĐ:** `restricted=false`, chỉ đếm `CARE_VISIBLE`, ẩn `opportunity.title` (`null`).<br>**TT:** C1=`2`, C2=`2`, C3=`1` (lọc bỏ 1 bản ghi `SALES_ONLY`), `hasSalesOnly=false`, `hasOppTitle=false` (**ĐẠT**). | **MĐ:** `restricted=false`, chỉ đếm Task có `owner_user_id = Care`.<br>**TT:** `restricted=false`, `taskTotal=0`, `overdue=0` trên C1, C2, C3, khớp SQL (**ĐẠT**). | **MĐ:** `restricted=false`, chỉ tính Feedback `CARE_VISIBLE`.<br>**TT:** C1: `count=0, avg=null` (lọc bỏ 1 `SALES_ONLY`); C2: `count=1, avg=null`; C3: `count=1, avg=4.0` (**ĐẠT**). | **MĐ:** `restricted=false`, chỉ đếm Support đang mở gán cho Care.<br>**TT:** C1=`1`, C2=`1`, C3=`0`, khớp SQL (**ĐẠT**). | **MĐ:** `403 FORBIDDEN_PERMISSION`.<br>**TT:** `403 FORBIDDEN_PERMISSION` (**ĐẠT**). |
| **Sales kiêm Care** *(owner + CARE trên C2)* | **MĐ:** `200` trên C2 (`hasContact=true`); `403` trên C1, C3.<br>**TT:** `200` trên C2, `403 FORBIDDEN_SCOPE` trên C1, C3 (**ĐẠT**). | **MĐ:** Hợp nhất quyền: `restricted=false` (nhờ `SALES_REP`).<br>**TT:** `restricted=false`, `count=0` trên C2 (**ĐẠT**). | **MĐ:** `restricted=false` (nhờ `SALES_REP`), đếm Cơ hội của mình.<br>**TT:** `restricted=false`, `oppTotal=1`, `open=5.200.000 ₫` trên C2 (**ĐẠT**). | **MĐ:** `restricted=false`, thấy đủ `SALES_ONLY` và `opportunity.title`.<br>**TT:** `restricted=false`, `interCount=2`, `hasOppTitle=true` trên C2 (**ĐẠT**). | **MĐ:** `restricted=false`, đếm Task của mình.<br>**TT:** `restricted=false`, `taskTotal=1`, `overdue=1` trên C2 (**ĐẠT**). | **MĐ:** `restricted=false`, thấy mọi visibility.<br>**TT:** `restricted=false`, `fbCount=1` trên C2 (**ĐẠT**). | **MĐ:** Hợp nhất quyền: `restricted=false` (nhờ `CUSTOMER_CARE`), đếm Support gán cho mình.<br>**TT:** `restricted=false`, `supCount=0` trên C2 (vì ticket của C2 gán cho `8a294c3d-...`) (**ĐẠT**). | **MĐ:** `403 FORBIDDEN_PERMISSION`.<br>**TT:** `403 FORBIDDEN_PERMISSION` (**ĐẠT**). |
| **Data Staff** | **MĐ:** Đọc được mọi Customer trong tenant nhưng ẩn liên hệ/ghi chú (`email/phone/address/notes = null`).<br>**TT:** `200` trên C1, C2, C3, `hasContact=false` (**ĐẠT**). | **MĐ:** `restricted=true`, `count=null`.<br>**TT:** `restricted=true`, `count=null` (**ĐẠT**). | **MĐ theo code hiện tại (FINDING):** `restricted=true`, số = `null` (xem mục 7).<br>**TT:** `restricted=true`, `oppTotal=null` (**ĐẠT theo FINDING**). | **MĐ:** `restricted=false`, đếm mọi visibility nhưng `subject=null`, `content=null`, `opportunity.title=null`.<br>**TT:** C1=`2`, C2=`2`, C3=`2` (`hasSalesOnly=true`), `hasInterContent=false`, `hasOppTitle=false` (**ĐẠT**). | **MĐ:** `restricted=true`, `statuses=null`, `overdue=null`.<br>**TT:** `restricted=true`, `taskTotal=null`, `overdue=null` (**ĐẠT**). | **MĐ:** `restricted=false`, đếm + điểm TB mọi visibility.<br>**TT:** C1=`1 (1.0)`, C2=`1 (null)`, C3=`1 (4.0)`, khớp SQL (**ĐẠT**). | **MĐ:** `restricted=true`, `count=null`.<br>**TT:** `restricted=true`, `count=null` (**ĐẠT**). | **MĐ:** `200`, trả `totals`, `matched`, `mismatched`.<br>**TT:** `200`, `matched=20`, `mismatched=0` (**ĐẠT**). |

## 4. Bảng đối chiếu số liệu 3 Customer EDU với SQL chỉ đọc

Ba Customer EDU được chọn trên bản sao `pose_v2_c360_202611051512`:
1. **Customer 1 (`be4c13e1-85c6-5c93-a59e-d2bce69be4fa`):** Có Lead đã chuyển (`leads_all = 1`), owner là `Sales A`, `Customer Care` được gán CARE, có 1 Cơ hội đang mở (`8.075.000 ₫`), 2 Tương tác `CARE_VISIBLE`, 1 Task quá hạn (owner `Sales A`), 1 Feedback `SALES_ONLY` (`rating = 1.00`), 1 Support đang mở (gán cho `Customer Care`), 1 nguồn dữ liệu.
2. **Customer 2 (`f59b823d-a2f1-5d9c-9c10-baf6b63465fe`):** Có Task quá hạn (`task_overdue_all = 1`), owner là `Sales kiêm Care` (`c17b7640-...`), cả `Customer Care` và `Sales kiêm Care` được gán CARE, có 1 Cơ hội đang mở (`5.200.000 ₫`), 2 Tương tác `CARE_VISIBLE`, 1 Feedback `CARE_VISIBLE` (`rating = null`), 1 Support đang mở (gán cho `Customer Care`), 1 nguồn dữ liệu.
3. **Customer 3 (`cfa07d5a-446d-5e5b-b5c0-12d9ea3efa07`):** Có Tương tác `SALES_ONLY` (`2` tương tác: `1 CARE_VISIBLE` + `1 SALES_ONLY`), owner của Customer là `Sales A`, `Customer Care` được gán CARE, có 1 Cơ hội `WON` (`14.190.000 ₫`, owner của Cơ hội là `c17b7640-...`), 1 Task chưa quá hạn (owner của Task là `c17b7640-...`), 1 Feedback `CARE_VISIBLE` (`rating = 4.00`), 0 Support đang mở, 0 nguồn dữ liệu.

### 4.1. Customer 1 — `be4c13e1-85c6-5c93-a59e-d2bce69be4fa` (Có Lead đã chuyển)

| Chỉ số | SQL gốc (toàn phần / lọc theo quyền) | Admin | Manager | Sales A | Sales B | Customer Care | Sales kiêm Care | Data Staff |
|---|---|---|---|---|---|---|---|---|
| HTTP Status | — | `200` | `200` | `200` | `403` | `200` | `403` | `200` |
| `sources.count` | `1` | `1` | `1` | `1` | — | `1` | — | `1` |
| `leads.count` | `1` (owner Sales A) | `1` | `1` | `1` | — | `null` (`restricted`) | — | `null` (`restricted`) |
| `opportunities` (tổng / mở / WON) | `1` / `8075000.00` / `0` | `1` / `8075000.00` / `0` | `1` / `8075000.00` / `0` | `1` / `8075000.00` / `0` | — | `null` (`restricted`) | — | `null` (`restricted`) |
| `interactions.count` | Tổng `2` (`2 CARE_VISIBLE`, `0 SALES_ONLY`) | `2` | `2` | `2` | — | `2` | — | `2` (ẩn nội dung) |
| `tasks` (tổng / quá hạn) | Tổng `1` / `1` (owner Sales A; Care own = `0`) | `1` / `1` | `1` / `1` | `1` / `1` | — | `0` / `0` | — | `null` (`restricted`) |
| `feedback` (`count` / `averageRating`) | Tổng `1` (`1.00`, `SALES_ONLY`); Care visible = `0` (`null`) | `1` / `1.0` | `1` / `1.0` | `1` / `1.0` | — | `0` / `null` | — | `1` / `1.0` |
| `support.count` | `1` (gán cho `Customer Care`) | `1` | `1` | `null` (`restricted`) | — | `1` | — | `null` (`restricted`) |

### 4.2. Customer 2 — `f59b823d-a2f1-5d9c-9c10-baf6b63465fe` (Có Task quá hạn)

| Chỉ số | SQL gốc (toàn phần / lọc theo quyền) | Admin | Manager | Sales A | Sales B | Customer Care | Sales kiêm Care | Data Staff |
|---|---|---|---|---|---|---|---|---|
| HTTP Status | — | `200` | `200` | `403` | `403` | `200` | `200` | `200` |
| `sources.count` | `1` | `1` | `1` | — | — | `1` | `1` | `1` |
| `leads.count` | `0` | `0` | `0` | — | — | `null` (`restricted`) | `0` | `null` (`restricted`) |
| `opportunities` (tổng / mở / WON) | `1` / `5200000.00` / `0` (owner `Sales kiêm Care`) | `1` / `5200000.00` / `0` | `1` / `5200000.00` / `0` | — | — | `null` (`restricted`) | `1` / `5200000.00` / `0` | `null` (`restricted`) |
| `interactions.count` | Tổng `2` (`2 CARE_VISIBLE`, `0 SALES_ONLY`) | `2` | `2` | — | — | `2` | `2` | `2` (ẩn nội dung) |
| `tasks` (tổng / quá hạn) | Tổng `1` / `1` (owner `Sales kiêm Care`; Care own = `0`) | `1` / `1` | `1` / `1` | — | — | `0` / `0` | `1` / `1` | `null` (`restricted`) |
| `feedback` (`count` / `averageRating`) | Tổng `1` (`null`, `CARE_VISIBLE`) | `1` / `null` | `1` / `null` | — | — | `1` / `null` | `1` / `null` | `1` / `null` |
| `support.count` | Tổng `1` (gán cho `Customer Care`; `Sales kiêm Care` = `0`) | `1` | `1` | — | — | `1` | `0` | `null` (`restricted`) |

### 4.3. Customer 3 — `cfa07d5a-446d-5e5b-b5c0-12d9ea3efa07` (Có Interaction `SALES_ONLY`)

| Chỉ số | SQL gốc (toàn phần / lọc theo quyền) | Admin | Manager | Sales A | Sales B | Customer Care | Sales kiêm Care | Data Staff |
|---|---|---|---|---|---|---|---|---|
| HTTP Status | — | `200` | `200` | `200` | `403` | `200` | `403` | `200` |
| `sources.count` | `0` | `0` | `0` | `0` | — | `0` | — | `0` |
| `leads.count` | `0` | `0` | `0` | `0` | — | `null` (`restricted`) | — | `null` (`restricted`) |
| `opportunities` (tổng / mở / WON) | Tổng `1` / `0` / `14190000.00` (owner `Sales kiêm Care`; Sales A own = `0`) | `1` / `0` / `14190000.00` | `1` / `0` / `14190000.00` | `0` / `0` / `0` | — | `null` (`restricted`) | — | `null` (`restricted`) |
| `interactions.count` | Tổng `2` (`1 CARE_VISIBLE` + `1 SALES_ONLY`) | `2` | `2` | `2` | — | `1` (chỉ `CARE_VISIBLE`) | — | `2` (ẩn nội dung) |
| `tasks` (tổng / quá hạn) | Tổng `1` / `0` (owner `Sales kiêm Care`; Sales A & Care own = `0`) | `1` / `0` | `1` / `0` | `0` / `0` | — | `0` / `0` | — | `null` (`restricted`) |
| `feedback` (`count` / `averageRating`) | Tổng `1` (`4.00`, `CARE_VISIBLE`) | `1` / `4.0` | `1` / `4.0` | `1` / `4.0` | — | `1` / `4.0` | — | `1` / `4.0` |
| `support.count` | `0` | `0` | `0` | `null` (`restricted`) | — | `0` | — | `null` (`restricted`) |

> **Nhận xét đối chiếu:** 100% số đếm từ API `/api/backend/customers/{id}/360` khớp tuyệt đối với truy vấn SQL trực tiếp theo đúng quy tắc quyền và phạm vi (DEC-15 D1 & D2).

## 5. Kết quả đối soát dữ liệu (`/api/backend/customer-360/reconciliation`) và đối chiếu `expected_kpis.csv`

### 5.1. Phân quyền endpoint đối soát

| Vai trò | HTTP Status | Mã lỗi / Kết quả |
|---|---|---|
| **Admin** | `200 OK` | `matched = 20`, `mismatched = 0`, `items = []` |
| **Data Staff** | `200 OK` | `matched = 20`, `mismatched = 0`, `items = []` |
| **Manager** | `403 Forbidden` | `FORBIDDEN_PERMISSION` |
| **Sales A** | `403 Forbidden` | `FORBIDDEN_PERMISSION` |
| **Sales B** | `403 Forbidden` | `FORBIDDEN_PERMISSION` |
| **Customer Care** | `403 Forbidden` | `FORBIDDEN_PERMISSION` |
| **Sales kiêm Care** | `403 Forbidden` | `FORBIDDEN_PERMISSION` |

### 5.2. Đối chiếu `totals` của EDU với `expected_kpis.csv`

Đọc chỉ đọc `D:\POSE-data\POSE_CRM_DATA_V2_FINAL\database\data\seed\multisector_v2\expected_kpis.csv` cho tổ chức `edu` (`0b47be39-3b3c-5195-a4e2-8a29ffbadcf0`):

| Chỉ số | `reconciliation` (`v_customer_360_summary`) | `expected_kpis.csv` (toàn tổ chức EDU) | Giải thích đối chiếu |
|---|---|---|---|
| Tổng số Customer (`matched + mismatched`) | `20` (`matched = 20, mismatched = 0`) | `customers_total = 20` | **Khớp 100%** (20/20 Customer khớp giữa bảng gốc và view tổng hợp). |
| `totals.open_task_count` | `8` | `tasks_open_or_in_progress = 8` | **Khớp 100%** (cả 8 Task đang mở/đang làm trong EDU đều gắn với Customer). |
| `totals.opportunity_count` | `8` | `opportunities_total = 12` | **Khớp theo phạm vi Customer:** 8 Cơ hội gắn với Customer (`customer_id IS NOT NULL`), còn 4 Cơ hội chỉ gắn với Lead (`customer_id IS NULL`). |
| `totals.interaction_count` | `16` | `interactions_total = 26` | **Khớp theo phạm vi Customer:** 16 Tương tác gắn với Customer (`customer_id IS NOT NULL`), còn 10 Tương tác chỉ gắn với Lead (`customer_id IS NULL`). |
| `totals.feedback_count` | `5` | *(không có cột trong CSV)* | Khớp tổng `SELECT count(*) FROM feedbacks WHERE organization_id = EDU AND customer_id IS NOT NULL` (`5`). |
| `totals.open_support_count` | `4` | *(không có cột trong CSV)* | Khớp tổng `SELECT count(*) FROM support_requests WHERE organization_id = EDU AND status NOT IN ('RESOLVED','CLOSED')` (`4`). |
| `totals.source_count` | `15` | *(không có cột trong CSV)* | Khớp tổng `SELECT count(*) FROM source_record_links WHERE organization_id = EDU AND subject_type = 'CUSTOMER' AND status = 'ACTIVE'` (`15`). |

## 6. Kết quả kiểm các ca bảo mật và Audit Log

1. **Chặn ngoài phạm vi và khác tenant (`403 FORBIDDEN_SCOPE`):**
   - `Admin` (EDU) gọi Customer thuộc tenant `REA` (`c421cee7-018e-5698-a1a3-ea45760fd6a6`): `403 FORBIDDEN_SCOPE`.
   - `Sales A` (EDU) gọi Customer thuộc tenant `REA` (`c421cee7-018e-5698-a1a3-ea45760fd6a6`): `403 FORBIDDEN_SCOPE`.
   - `Sales B` (EDU) gọi Customer ngoài phạm vi sở hữu/phân công (`be4c13e1-85c6-5c93-a59e-d2bce69be4fa`): `403 FORBIDDEN_SCOPE`.
   - `Customer Care` (EDU) gọi Customer EDU không có phân công CARE (`3d3bbf2f-f4b7-5092-b1b9-7bdf39a11d67`): `403 FORBIDDEN_SCOPE`.
2. **Ẩn dữ liệu nhạy cảm với `DATA_STAFF`:**
   - Trên cả 3 Customer (`be4c13e1-...`, `f59b823d-...`, `cfa07d5a-...`), JSON trả về cho `DATA_STAFF` có `hasContact = false` (`profile.email = null`, `profile.phone = null`, `profile.address = null`, `profile.notes = null`), `hasInterContent = false` (`subject = null`, `content = null`), và `hasOppTitle = false` (`opportunity.title = null`).
3. **Ẩn dữ liệu bán hàng nội bộ với `CUSTOMER_CARE`:**
   - Trên Customer 3 (`cfa07d5a-...`, có 1 Interaction `SALES_ONLY` và 1 Opportunity), JSON trả về cho `CUSTOMER_CARE` có `hasSalesOnly = false` (loại bỏ hoàn toàn bản ghi `SALES_ONLY` khỏi `items` và khỏi `count`) và `hasOppTitle = false` (`opportunities.restricted = true`, `interaction.opportunity.title = null`).
4. **Chỉ đọc, không phát sinh `audit_logs`:**
   - Số bản ghi `audit_logs` trước và sau toàn bộ chuỗi gọi `GET /api/backend/customers/{id}/360` và `GET /api/backend/customer-360/reconciliation`: **`84 -> 84`** (0 bản ghi phát sinh).

## 7. FINDING kèm request/response rút gọn

### FINDING-W07-01 — `DATA_STAFF` nhận `opportunities.restricted = true` ở `/api/v1/customers/{id}/360`
- **Mô tả:** Theo bảng định hướng ban đầu của DEC-15 D2, `DATA_STAFF` được kỳ vọng đọc số lượng/giá trị Cơ hội ở mức metadata trong hồ sơ 360. Tuy nhiên `Customer360Service` ủy quyền kiểm tra quyền đọc phần Cơ hội cho `OpportunityService.readScope(...)`, mà `OpportunityService` từ chối `DATA_STAFF` theo DEC-12 F4. Kết quả là phần `opportunities` trong hồ sơ 360 trả `restricted: true` cho `DATA_STAFF` (trong khi tổng `opportunity_count` vẫn có trong endpoint đối soát `/api/v1/customer-360/reconciliation`).
- **Request/Response rút gọn (qua BFF với phiên `DATA_STAFF`):**
  ```http
  GET /api/backend/customers/cfa07d5a-446d-5e5b-b5c0-12d9ea3efa07/360 HTTP/1.1
  Host: 127.0.0.1:3011
  ```
  ```json
  {
    "customer": {
      "id": "cfa07d5a-446d-5e5b-b5c0-12d9ea3efa07",
      "profile": {
        "email": null,
        "phone": null,
        "address": null,
        "notes": null
      }
    },
    "opportunities": {
      "restricted": true,
      "stages": null,
      "openExpectedValue": null,
      "wonFinalValue": null,
      "items": []
    },
    "interactions": {
      "restricted": false,
      "count": 2,
      "items": [
        {
          "visibility": "SALES_ONLY",
          "subject": null,
          "content": null,
          "opportunity": {
            "id": "...",
            "title": null,
            "stage": "WON"
          }
        }
      ]
    }
  }
  ```

### FINDING-W07-02 — `DATA_STAFF` chỉ mở được hồ sơ 360 qua link từ bảng lệch trên `/app/khach-hang-360`
- **Mô tả:** `DATA_STAFF` có permission `customer_360.read` nhưng không có `customer.read`, nên khi gọi danh sách Customer để tìm kiếm sẽ nhận `403 FORBIDDEN_SCOPE`. Trên giao diện `/app/khach-hang-360`, `DATA_STAFF` chỉ thấy khối **Đối soát dữ liệu** (kèm thông báo giải thích) và chỉ mở được hồ sơ `/app/khach-hang-360/[id]` qua link từ bảng Customer lệch (hoặc truy cập URL trực tiếp).
- **Request/Response rút gọn (qua BFF với phiên `DATA_STAFF`):**
  ```http
  GET /api/backend/customers?size=20 HTTP/1.1
  Host: 127.0.0.1:3011
  ```
  ```json
  {
    "code": "FORBIDDEN_SCOPE",
    "message": "Bạn không có quyền truy cập dữ liệu ngoài phạm vi được phép."
  }
  ```

## 8. Kiểm giao diện bằng trình duyệt và dọn môi trường

- **Kiểm giao diện bằng trình duyệt:** Phiên hiện tại không có công cụ trình duyệt (Chrome DevTools MCP), nên **chưa kiểm UI bằng trình duyệt**.
- **Dọn môi trường sau QA:**
  - Đã dừng tiến trình frontend (`3011`), backend (`8081`) và dừng PostgreSQL test cluster (`pg_ctl stop` trên `D:\POSE\backend\target\auth-postgres-test`).
  - Đã xóa thư mục/file tạm chứa mật khẩu sinh ngẫu nhiên, mã nguồn `HashGen.java` và kết quả JSON trung gian.
  - Kiểm tra `Get-NetTCPConnection -LocalPort 8080,8081,3010,3011,54329 -State Listen`: xác nhận **cả 5 cổng `8080`, `8081`, `3010`, `3011`, `54329` đều không lắng nghe**.
  - Database bản sao được giữ lại trong cluster test để tra cứu khi cần: **`pose_v2_c360_202611051512`**.
