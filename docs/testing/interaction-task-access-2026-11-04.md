# QA quyền Interaction và CRM Task — 04/11/2026

Thực hiện trên `D:\POSE`, nhánh `Tài`, HEAD `2c4c6df`. Không sửa backend, contract hay migration. Các request chạy qua BFF `127.0.0.1:3011`, backend `127.0.0.1:8081`, PostgreSQL `127.0.0.1:54329`; đã kiểm `SHOW data_directory = D:/POSE/backend/target/auth-postgres-test` trước thao tác. Không kết nối Supabase hay 5432.

Bản QA `pose_v2_w06_202611042327` sao chép từ review mới nhất `pose_v2_review_20261104230302977`, vốn đã có V11; backend báo up to date. Để kiểm migration thực sự, bản bổ sung `pose_v2_w06_v10_202611042338` sao chép từ review V10 `pose_v2_review_20261104030055798`: Flyway báo `Migrating schema public to version 11`, `Successfully applied 1 migration`, và history `11|t`. Hai database được giữ lại.

Mật khẩu ngẫu nhiên chỉ tồn tại trong process; BCrypt 12 và credentials của sáu user được áp dụng riêng trên bản QA. Backend dùng pool tối đa 5. Có ba lượt chạy trên cùng bản sao; bộ chạy đầu tiên bị sửa vì chờ sai thông báo khởi động, hai lượt sau gửi request. Bảng dưới ghi lượt cuối.

| Vai trò | User ID |
|---|---|
| Sales A | `16bf03a9-05d2-57ef-9017-be06b9552839` |
| Sales B kiêm Care | `c17b7640-3f09-554c-af94-a2fa5f3d01f6` |
| Manager | `a6fd80d1-2f4f-57d8-8650-c8ed8870a0b9` |
| Customer Care | `8a294c3d-8926-5ab0-aeec-b75765f1cccc` |
| Admin | `cd6bed61-dbbc-52bc-b0e0-5e8598d89dbf` |
| Data Staff | `076cb017-5e61-5a9a-b6d5-28c0f090e7be` |

**Giới hạn mẫu:** Sales B trong EDU đồng thời là Customer Care; không có một tài khoản Sales B đơn vai trò khác trong mẫu. Không suy diễn kết quả của Sales B kiêm Care cho một user Sales B đơn vai trò.

## Ma trận quyền theo thao tác

Căn cứ kỳ vọng: [contract W06](../api/interaction-task-audit-api-v1.md) và [ma trận quyền](../analysis/roles-authorization-v1.md). Các ô ghi điều kiện phạm vi; dấu “chưa kiểm” là không có request tương ứng, không phải đạt. Chi tiết từng request ở bảng sau.

| Vai trò | Đọc Interaction / Task | Tạo | Sửa | SALES_ONLY | Start / complete / cancel | Assign | Audit |
|---|---|---|---|---|---|---|
| Sales A | 200 trong scope; ngoài scope 403 → 200, 200 | 201 trong scope; ngoài scope 403 → 201, 201 | 200 trong scope; ngoài scope 403 → 200, 200 | 200 trong scope; ngoài scope 403 → 200 | 200 cho owner/manager trong scope; ngoài scope 403 → 200, chưa kiểm, chưa kiểm | 403 → chưa kiểm | 403 → 403 FORBIDDEN_PERMISSION, 403 FORBIDDEN_PERMISSION |
| Sales B đơn vai trò | Chưa có user mẫu | Chưa kiểm | Chưa kiểm | Chưa kiểm | Chưa kiểm | Chưa kiểm | Chưa kiểm |
| Manager | 200 trong scope; ngoài scope 403 → 200, 200 | 201 trong scope; ngoài scope 403 → 201, 201 | 200 trong scope; ngoài scope 403 → 200, chưa kiểm | 200 trong scope; ngoài scope 403 → 200 | 200 cho owner/manager trong scope; ngoài scope 403 → chưa kiểm, 200, chưa kiểm | 200 trong scope → chưa kiểm | 200 trong scope → 200, 200 |
| Customer Care | 200 trong scope; ngoài scope 403 → 200, 403 FORBIDDEN_SCOPE | 201 trong scope; ngoài scope 403 → 201, 201 | 200 trong scope; ngoài scope 403 → 200, 403 FORBIDDEN_SCOPE | 403 → 403 FORBIDDEN_SCOPE | 200 cho owner/manager trong scope; ngoài scope 403 → 403 FORBIDDEN_SCOPE, 403 FORBIDDEN_SCOPE, 200 | 403 → 403 FORBIDDEN_PERMISSION | 403 → 403 FORBIDDEN_PERMISSION, 403 FORBIDDEN_PERMISSION |
| Sales B kiêm Care | 200 trong scope; ngoài scope 403 → 403 FORBIDDEN_SCOPE, 403 FORBIDDEN_SCOPE | 201 trong scope; ngoài scope 403 → 403 FORBIDDEN_SCOPE, 403 FORBIDDEN_SCOPE | 200 trong scope; ngoài scope 403 → 403 FORBIDDEN_SCOPE, 403 FORBIDDEN_SCOPE | 200 trong scope; ngoài scope 403 → 403 FORBIDDEN_SCOPE | 200 cho owner/manager trong scope; ngoài scope 403 → 403 FORBIDDEN_SCOPE, 403 FORBIDDEN_SCOPE, 403 FORBIDDEN_SCOPE | 403 → 403 FORBIDDEN_PERMISSION | 403 → 403 FORBIDDEN_PERMISSION, 403 FORBIDDEN_PERMISSION |
| Admin | 200 trong scope; ngoài scope 403 → 200, 200 | 403 → 403 FORBIDDEN_PERMISSION, 403 FORBIDDEN_PERMISSION | 200 trong scope; ngoài scope 403 → 200, chưa kiểm | 200 trong scope; ngoài scope 403 → 200 | 200 cho owner/manager trong scope; ngoài scope 403 → chưa kiểm, chưa kiểm, chưa kiểm | 200 trong scope → 200 | 200 trong scope → 200, 200 |
| Data Staff | metadata Interaction 200; Task 403 → 200, 403 FORBIDDEN_PERMISSION | 403 → 403 FORBIDDEN_PERMISSION, 403 FORBIDDEN_PERMISSION | 403 → 403 FORBIDDEN_PERMISSION, 403 FORBIDDEN_PERMISSION | 200 metadata → 200 | 403 → 403 FORBIDDEN_PERMISSION, 403 FORBIDDEN_PERMISSION, 403 FORBIDDEN_PERMISSION | 403 → 403 FORBIDDEN_PERMISSION | 403 → 403 FORBIDDEN_PERMISSION, 403 FORBIDDEN_PERMISSION |

## Request đã chạy

Các path API trong bảng dùng tiền tố BFF `/api/backend`, trừ `/api/session/login`. Không ghi body hoặc nội dung Interaction; mã lỗi và field là từ response. Các ca DEC-14 chỉ ghi nhận, không phân loại đúng/sai.

| Vai trò | Ca / request | Kỳ vọng | Thực tế | Kết quả / ghi chú |
|---|---|---|---|---|
| môi trường | Flyway: `START backend` | V11 up to date | — | Đạt — Bản sao mới nhất đã có V11; không áp lại migration. |
| Admin | Đăng nhập: `POST /api/session/login` | 200 | 200 | Đạt |
| Manager | Đăng nhập: `POST /api/session/login` | 200 | 200 | Đạt |
| Sales A | Đăng nhập: `POST /api/session/login` | 200 | 200 | Đạt |
| Sales B kiêm Care | Đăng nhập: `POST /api/session/login` | 200 | 200 | Đạt |
| Customer Care | Đăng nhập: `POST /api/session/login` | 200 | 200 | Đạt |
| Data Staff | Đăng nhập: `POST /api/session/login` | 200 | 200 | Đạt |
| Sales A | Tạo tương tác CARE_VISIBLE: `POST /interactions` | 201 | 201 | Đạt |
| Sales A | Tạo tương tác SALES_ONLY: `POST /interactions` | 201 | 201 | Đạt |
| Sales A | Tạo nhiệm vụ: `POST /tasks` | 201 | 201 | Đạt |
| Admin | Danh sách tương tác: `GET /interactions?size=100` | 200 | 200 | Đạt |
| Admin | Chi tiết CARE_VISIBLE: `GET /interactions/95bbf698-c247-4d52-99f1-99ccc3cc3967` | 200 | 200 | Đạt |
| Admin | Chi tiết SALES_ONLY: `GET /interactions/62085dc4-5434-47b8-a898-be46d4548b3a` | 200 | 200 | Đạt |
| Admin | Danh sách nhiệm vụ: `GET /tasks?size=100` | 200 | 200 | Đạt |
| Admin | Chi tiết nhiệm vụ Sales A: `GET /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6` | 200 | 200 | Đạt |
| Admin | Audit danh sách: `GET /audit?size=20` | 200 | 200 | Đạt |
| Manager | Danh sách tương tác: `GET /interactions?size=100` | 200 | 200 | Đạt |
| Manager | Chi tiết CARE_VISIBLE: `GET /interactions/95bbf698-c247-4d52-99f1-99ccc3cc3967` | 200 | 200 | Đạt |
| Manager | Chi tiết SALES_ONLY: `GET /interactions/62085dc4-5434-47b8-a898-be46d4548b3a` | 200 | 200 | Đạt |
| Manager | Danh sách nhiệm vụ: `GET /tasks?size=100` | 200 | 200 | Đạt |
| Manager | Chi tiết nhiệm vụ Sales A: `GET /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6` | 200 | 200 | Đạt |
| Manager | Audit danh sách: `GET /audit?size=20` | 200 | 200 | Đạt |
| Sales A | Danh sách tương tác: `GET /interactions?size=100` | 200 | 200 | Đạt |
| Sales A | Chi tiết CARE_VISIBLE: `GET /interactions/95bbf698-c247-4d52-99f1-99ccc3cc3967` | 200 | 200 | Đạt |
| Sales A | Chi tiết SALES_ONLY: `GET /interactions/62085dc4-5434-47b8-a898-be46d4548b3a` | 200 | 200 | Đạt |
| Sales A | Danh sách nhiệm vụ: `GET /tasks?size=100` | 200 | 200 | Đạt |
| Sales A | Chi tiết nhiệm vụ Sales A: `GET /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6` | 200 | 200 | Đạt |
| Sales A | Audit danh sách: `GET /audit?size=20` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| Sales B kiêm Care | Danh sách tương tác: `GET /interactions?size=100` | 200 | 200 | Đạt |
| Sales B kiêm Care | Chi tiết CARE_VISIBLE: `GET /interactions/95bbf698-c247-4d52-99f1-99ccc3cc3967` | 403 FORBIDDEN_SCOPE | 403 `FORBIDDEN_SCOPE` | Đạt |
| Sales B kiêm Care | Chi tiết SALES_ONLY: `GET /interactions/62085dc4-5434-47b8-a898-be46d4548b3a` | 403 FORBIDDEN_SCOPE | 403 `FORBIDDEN_SCOPE` | Đạt |
| Sales B kiêm Care | Danh sách nhiệm vụ: `GET /tasks?size=100` | 200 | 200 | Đạt |
| Sales B kiêm Care | Chi tiết nhiệm vụ Sales A: `GET /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6` | 403 FORBIDDEN_SCOPE | 403 `FORBIDDEN_SCOPE` | Đạt |
| Sales B kiêm Care | Audit danh sách: `GET /audit?size=20` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| Customer Care | Danh sách tương tác: `GET /interactions?size=100` | 200 | 200 | Đạt |
| Customer Care | Chi tiết CARE_VISIBLE: `GET /interactions/95bbf698-c247-4d52-99f1-99ccc3cc3967` | 200 | 200 | Đạt |
| Customer Care | Chi tiết SALES_ONLY: `GET /interactions/62085dc4-5434-47b8-a898-be46d4548b3a` | 403 FORBIDDEN_SCOPE | 403 `FORBIDDEN_SCOPE` | Đạt |
| Customer Care | Danh sách nhiệm vụ: `GET /tasks?size=100` | 200 | 200 | Đạt |
| Customer Care | Chi tiết nhiệm vụ Sales A: `GET /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6` | 403 FORBIDDEN_SCOPE | 403 `FORBIDDEN_SCOPE` | Đạt |
| Customer Care | Audit danh sách: `GET /audit?size=20` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| Data Staff | Danh sách tương tác: `GET /interactions?size=100` | 200 | 200 | Đạt |
| Data Staff | Chi tiết CARE_VISIBLE: `GET /interactions/95bbf698-c247-4d52-99f1-99ccc3cc3967` | 200 | 200 | Đạt |
| Data Staff | Chi tiết SALES_ONLY: `GET /interactions/62085dc4-5434-47b8-a898-be46d4548b3a` | 200 | 200 | Đạt |
| Data Staff | Danh sách nhiệm vụ: `GET /tasks?size=100` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| Data Staff | Chi tiết nhiệm vụ Sales A: `GET /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| Data Staff | Audit danh sách: `GET /audit?size=20` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| Customer Care | Danh sách không có SALES_ONLY: `GET /interactions?visibility=SALES_ONLY&size=100` | 200 | 200 | Đạt |
| Customer Care | Không tạo SALES_ONLY: `POST /interactions` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| Customer Care | Tạo CARE_VISIBLE: `POST /interactions` | 201 | 201 | Đạt |
| Admin | Admin không tạo tương tác: `POST /interactions` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| Admin | Admin không tạo nhiệm vụ: `POST /tasks` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| Data Staff | Data Staff không tạo tương tác: `POST /interactions` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| Data Staff | Data Staff không tạo nhiệm vụ: `POST /tasks` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| Manager | Manager đọc users: `GET /users?size=100` | 200 | 200 | Đạt |
| Manager | Manager tạo tương tác: `POST /interactions` | 201 | 201 | Đạt |
| Sales B kiêm Care | Sales B tạo tương tác ngoài scope: `POST /interactions` | 403 FORBIDDEN_SCOPE | 403 `FORBIDDEN_SCOPE` | Đạt |
| Manager | Manager tạo nhiệm vụ: `POST /tasks` | 201 | 201 | Đạt |
| Customer Care | Care tạo nhiệm vụ của mình: `POST /tasks` | 201 | 201 | Đạt |
| Sales B kiêm Care | Sales B tạo nhiệm vụ ngoài scope: `POST /tasks` | 403 FORBIDDEN_SCOPE | 403 `FORBIDDEN_SCOPE` | Đạt |
| Sales B kiêm Care | Không start nhiệm vụ ngoài quyền: `POST /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6/start` | 403 FORBIDDEN_SCOPE | 403 `FORBIDDEN_SCOPE` | Đạt |
| Sales B kiêm Care | Không complete nhiệm vụ ngoài quyền: `POST /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6/complete` | 403 FORBIDDEN_SCOPE | 403 `FORBIDDEN_SCOPE` | Đạt |
| Sales B kiêm Care | Không cancel nhiệm vụ ngoài quyền: `POST /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6/cancel` | 403 FORBIDDEN_SCOPE | 403 `FORBIDDEN_SCOPE` | Đạt |
| Sales B kiêm Care | Không assign nhiệm vụ ngoài quyền: `POST /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6/assign` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| Customer Care | Không start nhiệm vụ ngoài quyền: `POST /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6/start` | 403 FORBIDDEN_SCOPE | 403 `FORBIDDEN_SCOPE` | Đạt |
| Customer Care | Không complete nhiệm vụ ngoài quyền: `POST /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6/complete` | 403 FORBIDDEN_SCOPE | 403 `FORBIDDEN_SCOPE` | Đạt |
| Customer Care | Không cancel nhiệm vụ ngoài quyền: `POST /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6/cancel` | 403 FORBIDDEN_SCOPE | 403 `FORBIDDEN_SCOPE` | Đạt |
| Customer Care | Không assign nhiệm vụ ngoài quyền: `POST /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6/assign` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| Data Staff | Không start nhiệm vụ ngoài quyền: `POST /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6/start` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| Data Staff | Không complete nhiệm vụ ngoài quyền: `POST /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6/complete` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| Data Staff | Không cancel nhiệm vụ ngoài quyền: `POST /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6/cancel` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| Data Staff | Không assign nhiệm vụ ngoài quyền: `POST /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6/assign` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| Sales A | Sửa tương tác: `PATCH /interactions/95bbf698-c247-4d52-99f1-99ccc3cc3967` | 200 | 200 | Đạt |
| Sales A | Phiên bản cũ tương tác: `PATCH /interactions/95bbf698-c247-4d52-99f1-99ccc3cc3967` | 409 INTERACTION_CHANGED | 409 `INTERACTION_CHANGED` | Đạt |
| Manager | Sửa tương tác cùng scope: `PATCH /interactions/95bbf698-c247-4d52-99f1-99ccc3cc3967` | 200 | 200 | Đạt |
| Admin | Sửa tương tác cùng scope: `PATCH /interactions/95bbf698-c247-4d52-99f1-99ccc3cc3967` | 200 | 200 | Đạt |
| Customer Care | Sửa tương tác cùng scope: `PATCH /interactions/95bbf698-c247-4d52-99f1-99ccc3cc3967` | 200 | 200 | Đạt |
| Sales B kiêm Care | Không sửa tương tác ngoài quyền: `PATCH /interactions/95bbf698-c247-4d52-99f1-99ccc3cc3967` | 403 FORBIDDEN_SCOPE | 403 `FORBIDDEN_SCOPE` | Đạt |
| Data Staff | Không sửa tương tác ngoài quyền: `PATCH /interactions/95bbf698-c247-4d52-99f1-99ccc3cc3967` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| Sales A | Sửa nhiệm vụ của mình: `PATCH /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6` | 200 | 200 | Đạt |
| Sales B kiêm Care | Sửa nhiệm vụ ngoài owner: `PATCH /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6` | 403 FORBIDDEN_SCOPE | 403 `FORBIDDEN_SCOPE` | Đạt |
| Customer Care | Sửa nhiệm vụ ngoài owner: `PATCH /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6` | 403 FORBIDDEN_SCOPE | 403 `FORBIDDEN_SCOPE` | Đạt |
| Data Staff | Sửa nhiệm vụ ngoài owner: `PATCH /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| Sales A | Bắt đầu nhiệm vụ: `POST /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6/start` | 200 | 200 | Đạt |
| Manager | Hoàn tất nhiệm vụ: `POST /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6/complete` | 200 | 200 | Đạt |
| Sales A | Không sửa nhiệm vụ DONE: `PATCH /tasks/5208268c-af5c-4303-a0bd-a23280dc3fb6` | 409 TASK_STATE_CONFLICT | 409 `TASK_STATE_CONFLICT` | Đạt |
| Sales A | Tạo nhiệm vụ để giao: `POST /tasks` | 201 | 201 | Đạt |
| Admin | Giao nhiệm vụ cho Care: `POST /tasks/903f00b6-8d18-4394-ac4c-17339ee4fc72/assign` | 200 | 200 | Đạt |
| Customer Care | Care hủy nhiệm vụ được giao: `POST /tasks/903f00b6-8d18-4394-ac4c-17339ee4fc72/cancel` | 200 | 200 | Đạt |
| Sales A | Tương tác không tồn tại: `GET /interactions/00000000-0000-4000-8000-000000000000` | 403 FORBIDDEN_SCOPE | 403 `FORBIDDEN_SCOPE` | Đạt |
| Sales A | Tương tác tenant khác: `GET /interactions/bfdc408e-33fe-551c-a76f-d03858a0b8e1` | 403 FORBIDDEN_SCOPE | 403 `FORBIDDEN_SCOPE` | Đạt |
| Sales A | Nhiệm vụ không tồn tại: `GET /tasks/00000000-0000-4000-8000-000000000000` | 403 FORBIDDEN_SCOPE | 403 `FORBIDDEN_SCOPE` | Đạt |
| Sales A | Nhiệm vụ tenant khác: `GET /tasks/f774f380-8880-56db-bbee-2b2bffc20d20` | 403 FORBIDDEN_SCOPE | 403 `FORBIDDEN_SCOPE` | Đạt |
| Admin | Audit theo tương tác thử: `GET /audit?entityType=INTERACTION&entityId=95bbf698-c247-4d52-99f1-99ccc3cc3967` | 200 | 200 | Đạt |
| Admin | Chi tiết audit: `GET /audit/5629a0c4-49ec-4bb3-a88f-ba21926a06a6` | 200 | 200 | Đạt |
| Manager | Chi tiết audit: `GET /audit/5629a0c4-49ec-4bb3-a88f-ba21926a06a6` | 200 | 200 | Đạt |
| Sales A | Chi tiết audit: `GET /audit/5629a0c4-49ec-4bb3-a88f-ba21926a06a6` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| Sales B kiêm Care | Chi tiết audit: `GET /audit/5629a0c4-49ec-4bb3-a88f-ba21926a06a6` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| Customer Care | Chi tiết audit: `GET /audit/5629a0c4-49ec-4bb3-a88f-ba21926a06a6` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| Data Staff | Chi tiết audit: `GET /audit/5629a0c4-49ec-4bb3-a88f-ba21926a06a6` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| Sales A | Tạo Lead kiểm convert: `POST /leads` | 201 | 201 | Đạt |
| Sales A | FINDING lọc Opportunity theo Lead: `GET /opportunities?leadId={id}` | Danh sách chỉ chứa Lead được chọn | 200 | Không đạt — Số bản ghi khác Lead được chọn: 6; response không chứa nội dung tương tác |
| Sales A | Tạo tương tác trên Lead mở: `POST /interactions` | 201 | 201 | Đạt |
| Sales A | Tạo nhiệm vụ trên Lead mở: `POST /tasks` | 201 | 201 | Đạt |
| Sales A | Qualify Lead: `POST /leads/8fe74108-b83b-45af-87f0-c242c91a4c87/qualify` | 200 | 200 | Đạt |
| Sales A | Convert Lead: `POST /leads/8fe74108-b83b-45af-87f0-c242c91a4c87/convert` | 200 | 200 | Đạt |
| Sales A | Tạo interactions trên Lead convert: `POST /interactions` | Chờ quyết định | 201 | Chờ quyết định — Chờ quyết định DEC-14; ghi nhận HTTP thực tế |
| Sales A | Tạo tasks trên Lead convert: `POST /tasks` | Chờ quyết định | 201 | Chờ quyết định — Chờ quyết định DEC-14; ghi nhận HTTP thực tế |
| Sales A | interactions sau chuyển Lead: `GET /interactions/{id}` | Chờ quyết định | 200 | Chờ quyết định — Gắn Lead=True; gắn Customer=False |
| Sales A | tasks sau chuyển Lead: `GET /tasks/{id}` | Chờ quyết định | 200 | Chờ quyết định — Gắn Lead=True; gắn Customer=False |
| Sales A | Tạo Lead kiểm disqualify: `POST /leads` | 201 | 201 | Đạt |
| Sales A | Tạo tương tác trên Lead mở: `POST /interactions` | 201 | 201 | Đạt |
| Sales A | Tạo nhiệm vụ trên Lead mở: `POST /tasks` | 201 | 201 | Đạt |
| Sales A | Disqualify Lead: `POST /leads/f17d32fe-05bb-48a6-b657-cda20f58f044/disqualify` | 200 | 200 | Đạt |
| Sales A | Tạo interactions trên Lead disqualify: `POST /interactions` | Chờ quyết định | 201 | Chờ quyết định — Chờ quyết định DEC-14; ghi nhận HTTP thực tế |
| Sales A | Tạo tasks trên Lead disqualify: `POST /tasks` | Chờ quyết định | 201 | Chờ quyết định — Chờ quyết định DEC-14; ghi nhận HTTP thực tế |
| db | Audit không lưu nội dung: `SELECT audit_logs` | 0 | — | Đạt — Số snapshot có dấu hiệu nội dung: 0 |

## FINDING và DEC-14

- **FINDING:** `GET /api/backend/opportunities?leadId={id}&size=100` trả HTTP 200 nhưng có 6 cơ hội không thuộc Lead được chọn. Contract Opportunity không hỗ trợ `leadId`. Bộ chọn Opportunity gắn Lead trong giao diện bị vô hiệu hóa; không sửa backend hay contract.
- DEC-14: `POST /interactions` và `POST /tasks` trên Lead CONVERTED hoặc DISQUALIFIED đều trả 201 trong ca thử. Interaction và Task đã tạo trước khi chuyển vẫn gắn Lead, không chuyển sang Customer. Các kết quả này **chờ Tài quyết định**, không phân loại là lỗi.
- Data Staff nhận `subject`, `content`, `outcome` null ở hai mức visibility đã kiểm; Care không đọc được SALES_ONLY. ID Interaction/Task tenant khác và không tồn tại đều trả 403 `FORBIDDEN_SCOPE` trong ca đã chạy.
- Đối chiếu DB thấy các action `INTERACTION_CREATED`, `INTERACTION_UPDATED`, `TASK_CREATED`, `TASK_UPDATED`, `TASK_ASSIGNED`, `TASK_STARTED`, `TASK_COMPLETED`, `TASK_CANCELLED`; không thấy giá trị thử hoặc key nội dung bị cấm trong snapshot được kiểm. Việc này không bao quát mọi audit của hệ thống.
- Một số ô trong ma trận chưa có request riêng; không suy diễn đạt. Chưa kiểm UI bằng trình duyệt vì phiên không có công cụ trình duyệt. Không kiểm thiết bị chạm hay screen reader.
- Sau dọn, 8080, 8081, 3010, 3011 và 54329 không lắng nghe. Hai database test nêu trên được giữ lại.
