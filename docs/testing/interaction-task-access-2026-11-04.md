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

**Giới hạn mẫu của lượt 04/11:** Sales B có sẵn trong EDU đồng thời là Customer Care; khi đó chưa có một tài khoản Sales B đơn vai trò trong mẫu. Ngày 05/11 đã bổ sung tài khoản thử và kiểm riêng ở phần cuối tài liệu; không suy diễn kết quả của Sales B kiêm Care cho một user Sales B đơn vai trò.

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

## Bổ sung ma trận — 05/11/2026

Tiếp tục trên database cô lập `pose_v2_w06_202611042327`: trước thao tác đã xác nhận `SHOW data_directory = D:/POSE/backend/target/auth-postgres-test`; BFF `127.0.0.1:3011`, backend `127.0.0.1:8081` và PostgreSQL `127.0.0.1:54329`. Không kết nối Supabase hay 5432. Đã tạo thêm một user thử chỉ có role SALES_REP, membership và phân công Offering trong bản sao; Customer thử thuộc user này cũng được tạo qua BFF. Tài khoản, Customer, Interaction và Task thử có tiền tố `TEST-W06`. Mật khẩu sinh ngẫu nhiên chỉ ở process và hash BCrypt 12 chỉ ghi trong bản sao. User và database thử được giữ lại.

User Sales B đơn vai trò của lượt kiểm cuối: `76834503-7c65-4ca7-9e9d-284df9c1fad9`. Các user vai trò khác giữ như bảng phía trên. Có hai lượt bổ sung trên cùng bản sao; bảng dưới ghi lượt cuối. Đây là kết quả bổ sung, không xóa lịch sử hoặc các giới hạn của lượt 04/11.

Căn cứ kỳ vọng: [contract W06](../api/interaction-task-audit-api-v1.md) và [ma trận quyền](../analysis/roles-authorization-v1.md). Mỗi ô ghi **kỳ vọng → thực tế** theo thứ tự thao tác ở tiêu đề; kiểm thành công là trong scope, trừ ca cấm được ghi 403.

| Vai trò | Danh sách/chi tiết Interaction, Task | Tạo Interaction/Task | Sửa Interaction/Task | Đọc SALES_ONLY | Start/complete/cancel/assign | Audit danh sách/chi tiết |
|---|---|---|---|---|---|---|
| Sales A | 200 → 200<br>200 → 200<br>200 → 200<br>200 → 200 | 201 → 201<br>201 → 201 | 200 → 200<br>200 → 200 | 200 → 200 | 200 → 200<br>200 → 200<br>200 → 200<br>403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION | 403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION<br>403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION |
| Sales B đơn vai trò | 200 → 200<br>200 → 200<br>200 → 200<br>200 → 200 | 201 → 201<br>201 → 201 | 200 → 200<br>200 → 200 | 200 → 200 | 200 → 200<br>200 → 200<br>200 → 200<br>403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION | 403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION<br>403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION |
| Manager | 200 → 200<br>200 → 200<br>200 → 200<br>200 → 200 | 201 → 201<br>201 → 201 | 200 → 200<br>200 → 200 | 200 → 200 | 200 → 200<br>200 → 200<br>200 → 200<br>200 → 200 | 200 → 200<br>200 → 200 |
| Customer Care | 200 → 200<br>200 → 200<br>200 → 200<br>200 → 200 | 201 → 201<br>201 → 201 | 200 → 200<br>200 → 200 | 403 FORBIDDEN_SCOPE → 403 FORBIDDEN_SCOPE | 200 → 200<br>200 → 200<br>200 → 200<br>403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION | 403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION<br>403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION |
| Sales kiêm Care | 200 → 200<br>200 → 200<br>200 → 200<br>200 → 200 | 201 → 201<br>201 → 201 | 200 → 200<br>200 → 200 | 200 → 200 | 200 → 200<br>200 → 200<br>200 → 200<br>403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION | 403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION<br>403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION |
| Admin | 200 → 200<br>200 → 200<br>200 → 200<br>200 → 200 | 403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION<br>403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION | 200 → 200<br>200 → 200 | 200 → 200 | 200 → 200<br>200 → 200<br>200 → 200<br>200 → 200 | 200 → 200<br>200 → 200 |
| Data Staff | 200 → 200<br>200 → 200<br>403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION<br>403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION | 403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION<br>403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION | 403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION<br>403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION | 200 → 200 | 403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION<br>403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION<br>403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION<br>403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION | 403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION<br>403 FORBIDDEN_PERMISSION → 403 FORBIDDEN_PERMISSION |

Các request kiểm Sales A và Sales B đọc Interaction ngoài scope đều trả `403 FORBIDDEN_SCOPE`; dữ liệu của Data Staff ở Interaction trả các trường nội dung null. Kiểm tra audit bổ sung tìm thấy `INTERACTION_CREATED` 40, `INTERACTION_UPDATED` 16, `TASK_CREATED` 86, `TASK_UPDATED` 11, `TASK_ASSIGNED` 7, `TASK_STARTED` 15, `TASK_COMPLETED` 15, `TASK_CANCELLED` 15 trên các bản ghi `TEST-W06` của bản sao. Test snapshot lượt cuối không thấy key hoặc giá trị thử bị kiểm.

### Request đã chạy bổ sung

Không ghi nội dung Interaction, thông tin liên hệ, token hay mật khẩu. Tất cả path trừ login dùng tiền tố BFF `/api/backend`; mã và field là response đã rút gọn.

| Vai trò | Ca / request | Kỳ vọng | Thực tế | Kết quả / ghi chú |
|---|---|---|---|---|
| admin | Đăng nhập: `POST /api/session/login` | 200 | 200 | Đạt |
| manager | Đăng nhập: `POST /api/session/login` | 200 | 200 | Đạt |
| sales_a | Đăng nhập: `POST /api/session/login` | 200 | 200 | Đạt |
| dual | Đăng nhập: `POST /api/session/login` | 200 | 200 | Đạt |
| care | Đăng nhập: `POST /api/session/login` | 200 | 200 | Đạt |
| data | Đăng nhập: `POST /api/session/login` | 200 | 200 | Đạt |
| sales_b | Đăng nhập: `POST /api/session/login` | 200 | 200 | Đạt |
| sales_b | Tạo khách hàng thử: `POST /customers` | 201 | 201 | Đạt |
| sales_a | Tạo Interaction: `POST /interactions` | 201 | 201 | Đạt |
| sales_a | Tạo Task: `POST /tasks` | 201 | 201 | Đạt |
| sales_b | Tạo Interaction: `POST /interactions` | 201 | 201 | Đạt |
| sales_b | Tạo Task: `POST /tasks` | 201 | 201 | Đạt |
| dual | Tạo Interaction: `POST /interactions` | 201 | 201 | Đạt |
| dual | Tạo Task: `POST /tasks` | 201 | 201 | Đạt |
| care | Tạo Interaction: `POST /interactions` | 201 | 201 | Đạt |
| care | Tạo Task: `POST /tasks` | 201 | 201 | Đạt |
| manager | Tạo Interaction: `POST /interactions` | 201 | 201 | Đạt |
| manager | Tạo Task: `POST /tasks` | 201 | 201 | Đạt |
| admin | Tạo Interaction: `POST /interactions` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| admin | Tạo Task: `POST /tasks` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| data | Tạo Interaction: `POST /interactions` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| data | Tạo Task: `POST /tasks` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| admin | Danh sách Interaction: `GET /interactions?size=100` | 200 | 200 | Đạt |
| admin | Đọc Interaction trong scope: `GET /interactions/795ce650-06c2-4309-ba33-b59941e7fd59` | 200 | 200 | Đạt |
| admin | Danh sách Task: `GET /tasks?size=100` | 200 | 200 | Đạt |
| admin | Đọc Task trong scope: `GET /tasks/c3ba8553-832d-4e16-8407-8db8c3639c6f` | 200 | 200 | Đạt |
| admin | Sửa Interaction: `PATCH /interactions/795ce650-06c2-4309-ba33-b59941e7fd59` | 200 | 200 | Đạt |
| admin | Sửa Task: `PATCH /tasks/c3ba8553-832d-4e16-8407-8db8c3639c6f` | 200 | 200 | Đạt |
| manager | Danh sách Interaction: `GET /interactions?size=100` | 200 | 200 | Đạt |
| manager | Đọc Interaction trong scope: `GET /interactions/9a139b4d-e708-4715-99f5-62a1ef1d6a2a` | 200 | 200 | Đạt |
| manager | Danh sách Task: `GET /tasks?size=100` | 200 | 200 | Đạt |
| manager | Đọc Task trong scope: `GET /tasks/18d8c3e6-1e17-4754-b806-4c4cbe2ecc41` | 200 | 200 | Đạt |
| manager | Sửa Interaction: `PATCH /interactions/9a139b4d-e708-4715-99f5-62a1ef1d6a2a` | 200 | 200 | Đạt |
| manager | Sửa Task: `PATCH /tasks/18d8c3e6-1e17-4754-b806-4c4cbe2ecc41` | 200 | 200 | Đạt |
| sales_a | Danh sách Interaction: `GET /interactions?size=100` | 200 | 200 | Đạt |
| sales_a | Đọc Interaction trong scope: `GET /interactions/795ce650-06c2-4309-ba33-b59941e7fd59` | 200 | 200 | Đạt |
| sales_a | Danh sách Task: `GET /tasks?size=100` | 200 | 200 | Đạt |
| sales_a | Đọc Task trong scope: `GET /tasks/c3ba8553-832d-4e16-8407-8db8c3639c6f` | 200 | 200 | Đạt |
| sales_a | Sửa Interaction: `PATCH /interactions/795ce650-06c2-4309-ba33-b59941e7fd59` | 200 | 200 | Đạt |
| sales_a | Sửa Task: `PATCH /tasks/c3ba8553-832d-4e16-8407-8db8c3639c6f` | 200 | 200 | Đạt |
| dual | Danh sách Interaction: `GET /interactions?size=100` | 200 | 200 | Đạt |
| dual | Đọc Interaction trong scope: `GET /interactions/d84e7979-d421-42a2-a17b-070a41c60409` | 200 | 200 | Đạt |
| dual | Danh sách Task: `GET /tasks?size=100` | 200 | 200 | Đạt |
| dual | Đọc Task trong scope: `GET /tasks/04c300fb-142e-4a78-a6ee-56ce5798eef1` | 200 | 200 | Đạt |
| dual | Sửa Interaction: `PATCH /interactions/d84e7979-d421-42a2-a17b-070a41c60409` | 200 | 200 | Đạt |
| dual | Sửa Task: `PATCH /tasks/04c300fb-142e-4a78-a6ee-56ce5798eef1` | 200 | 200 | Đạt |
| care | Danh sách Interaction: `GET /interactions?size=100` | 200 | 200 | Đạt |
| care | Đọc Interaction trong scope: `GET /interactions/bb2bbfa1-9b38-4d58-b9e6-c30ea58a2317` | 200 | 200 | Đạt |
| care | Danh sách Task: `GET /tasks?size=100` | 200 | 200 | Đạt |
| care | Đọc Task trong scope: `GET /tasks/2ad4890f-9bb5-472c-bc11-4a089f1d9cf5` | 200 | 200 | Đạt |
| care | Sửa Interaction: `PATCH /interactions/bb2bbfa1-9b38-4d58-b9e6-c30ea58a2317` | 200 | 200 | Đạt |
| care | Sửa Task: `PATCH /tasks/2ad4890f-9bb5-472c-bc11-4a089f1d9cf5` | 200 | 200 | Đạt |
| data | Danh sách Interaction: `GET /interactions?size=100` | 200 | 200 | Đạt |
| data | Đọc Interaction trong scope: `GET /interactions/795ce650-06c2-4309-ba33-b59941e7fd59` | 200 | 200 | Đạt |
| data | Danh sách Task: `GET /tasks?size=100` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| data | Đọc Task trong scope: `GET /tasks/c3ba8553-832d-4e16-8407-8db8c3639c6f` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| data | Sửa Interaction: `PATCH /interactions/795ce650-06c2-4309-ba33-b59941e7fd59` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| data | Sửa Task: `PATCH /tasks/c3ba8553-832d-4e16-8407-8db8c3639c6f` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| sales_b | Danh sách Interaction: `GET /interactions?size=100` | 200 | 200 | Đạt |
| sales_b | Đọc Interaction trong scope: `GET /interactions/ad0ac888-0dfc-46ba-8708-feb234eafb37` | 200 | 200 | Đạt |
| sales_b | Danh sách Task: `GET /tasks?size=100` | 200 | 200 | Đạt |
| sales_b | Đọc Task trong scope: `GET /tasks/9c1b8185-a75d-47f3-b9be-030b9c7148bd` | 200 | 200 | Đạt |
| sales_b | Sửa Interaction: `PATCH /interactions/ad0ac888-0dfc-46ba-8708-feb234eafb37` | 200 | 200 | Đạt |
| sales_b | Sửa Task: `PATCH /tasks/9c1b8185-a75d-47f3-b9be-030b9c7148bd` | 200 | 200 | Đạt |
| sales_a | Tạo SALES_ONLY: `POST /interactions` | 201 | 201 | Đạt |
| sales_b | Tạo SALES_ONLY: `POST /interactions` | 201 | 201 | Đạt |
| dual | Tạo SALES_ONLY: `POST /interactions` | 201 | 201 | Đạt |
| manager | Tạo SALES_ONLY: `POST /interactions` | 201 | 201 | Đạt |
| sales_b | Đọc SALES_ONLY trong scope: `GET /interactions/5c9e1320-0973-4767-81bf-7d2dc6929f42` | 200 | 200 | Đạt |
| dual | Đọc SALES_ONLY trong scope: `GET /interactions/4ade5c6d-756e-4efc-8272-1bf0216c943a` | 200 | 200 | Đạt |
| care | Tạo SALES_ONLY: `POST /interactions` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| admin | Đọc SALES_ONLY: `GET /interactions/979a5b7d-d2ed-4770-973d-585c159e644e` | 200 | 200 | Đạt |
| manager | Đọc SALES_ONLY: `GET /interactions/979a5b7d-d2ed-4770-973d-585c159e644e` | 200 | 200 | Đạt |
| sales_a | Đọc SALES_ONLY: `GET /interactions/979a5b7d-d2ed-4770-973d-585c159e644e` | 200 | 200 | Đạt |
| care | Đọc SALES_ONLY: `GET /interactions/979a5b7d-d2ed-4770-973d-585c159e644e` | 403 FORBIDDEN_SCOPE | 403 `FORBIDDEN_SCOPE` | Đạt |
| data | Đọc SALES_ONLY: `GET /interactions/979a5b7d-d2ed-4770-973d-585c159e644e` | 200 | 200 | Đạt |
| admin | Task start: `POST /tasks/480f6369-a63f-4cc3-b06c-fd9f1b905d45/start` | 200 | 200 | Đạt |
| admin | Task complete: `POST /tasks/ada145c4-ab5c-4220-a7b3-6fb5230122bc/complete` | 200 | 200 | Đạt |
| admin | Task cancel: `POST /tasks/889463e7-ced4-4d20-a152-c77dbeb19577/cancel` | 200 | 200 | Đạt |
| admin | Task assign: `POST /tasks/ea2b2f2c-6396-469e-985a-8d479b0f808d/assign` | 200 | 200 | Đạt |
| manager | Task start: `POST /tasks/a598c7db-cd4d-4995-aa6e-ecb59df5aa85/start` | 200 | 200 | Đạt |
| manager | Task complete: `POST /tasks/a9340487-95ac-4db8-ae43-c087f9976908/complete` | 200 | 200 | Đạt |
| manager | Task cancel: `POST /tasks/028dafc6-1e99-4021-811f-9583adfd1580/cancel` | 200 | 200 | Đạt |
| manager | Task assign: `POST /tasks/206e8062-642d-4429-8644-8f8501b55eda/assign` | 200 | 200 | Đạt |
| sales_a | Task start: `POST /tasks/2d2dd766-cbeb-4e8e-8906-03a5354fd633/start` | 200 | 200 | Đạt |
| sales_a | Task complete: `POST /tasks/7f2c6532-f782-41c0-a491-62c2bd304588/complete` | 200 | 200 | Đạt |
| sales_a | Task cancel: `POST /tasks/e7b1af1f-1645-4011-a33f-f0b48b26003f/cancel` | 200 | 200 | Đạt |
| sales_a | Task assign: `POST /tasks/a3d86010-8e33-40d5-ab1d-897efdcfb17f/assign` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| dual | Task start: `POST /tasks/99dfb44d-eff5-4897-89f9-ca74a65c6273/start` | 200 | 200 | Đạt |
| dual | Task complete: `POST /tasks/71ec260a-e22b-4472-a7b8-a319cd6e1e59/complete` | 200 | 200 | Đạt |
| dual | Task cancel: `POST /tasks/07ab447e-5d7d-402b-af07-01c2c8d04971/cancel` | 200 | 200 | Đạt |
| dual | Task assign: `POST /tasks/b816b2dc-b46e-4e07-9d81-0f3f7415a807/assign` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| care | Task start: `POST /tasks/16869145-ce37-4409-ac76-7c323c3151d7/start` | 200 | 200 | Đạt |
| care | Task complete: `POST /tasks/10fd8bfa-42ca-478e-a550-6abd5667a001/complete` | 200 | 200 | Đạt |
| care | Task cancel: `POST /tasks/8f2dfba3-163b-40fe-8fa3-94ec7c883c46/cancel` | 200 | 200 | Đạt |
| care | Task assign: `POST /tasks/49ed39dd-cc48-4901-ad29-197f887836e5/assign` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| data | Task start: `POST /tasks/1a95bcab-7ac6-42d6-b8f0-51f31c4dbf1d/start` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| data | Task complete: `POST /tasks/7bb1acf4-f45f-4d71-8ec7-f19b521bea45/complete` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| data | Task cancel: `POST /tasks/07248183-c70b-4e57-9aaa-bc87b39a873e/cancel` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| data | Task assign: `POST /tasks/f1dbb3c7-2aa2-47f2-9957-0bbf101fb1b0/assign` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| sales_b | Task start: `POST /tasks/ada753e3-c93f-4c96-8259-fc438fa2ae6f/start` | 200 | 200 | Đạt |
| sales_b | Task complete: `POST /tasks/bffe6b33-20f5-46c8-9d20-5556aaa30a36/complete` | 200 | 200 | Đạt |
| sales_b | Task cancel: `POST /tasks/f177f2c0-364b-4e5c-9b79-cafd44f147c1/cancel` | 200 | 200 | Đạt |
| sales_b | Task assign: `POST /tasks/96779682-eef3-479e-8ff1-d062ae3e8b77/assign` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| admin | Tìm audit thử: `GET /audit?entityType=INTERACTION&entityId=795ce650-06c2-4309-ba33-b59941e7fd59` | 200 | 200 | Đạt |
| admin | Danh sách audit: `GET /audit?size=20` | 200 | 200 | Đạt |
| admin | Chi tiết audit: `GET /audit/f745d807-2427-4a13-bbfe-fdae5b201043` | 200 | 200 | Đạt |
| manager | Danh sách audit: `GET /audit?size=20` | 200 | 200 | Đạt |
| manager | Chi tiết audit: `GET /audit/f745d807-2427-4a13-bbfe-fdae5b201043` | 200 | 200 | Đạt |
| sales_a | Danh sách audit: `GET /audit?size=20` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| sales_a | Chi tiết audit: `GET /audit/f745d807-2427-4a13-bbfe-fdae5b201043` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| dual | Danh sách audit: `GET /audit?size=20` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| dual | Chi tiết audit: `GET /audit/f745d807-2427-4a13-bbfe-fdae5b201043` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| care | Danh sách audit: `GET /audit?size=20` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| care | Chi tiết audit: `GET /audit/f745d807-2427-4a13-bbfe-fdae5b201043` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| data | Danh sách audit: `GET /audit?size=20` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| data | Chi tiết audit: `GET /audit/f745d807-2427-4a13-bbfe-fdae5b201043` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| sales_b | Danh sách audit: `GET /audit?size=20` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| sales_b | Chi tiết audit: `GET /audit/f745d807-2427-4a13-bbfe-fdae5b201043` | 403 FORBIDDEN_PERMISSION | 403 `FORBIDDEN_PERMISSION` | Đạt |
| sales_b | Sales B đọc Interaction ngoài scope: `GET /interactions/795ce650-06c2-4309-ba33-b59941e7fd59` | 403 FORBIDDEN_SCOPE | 403 `FORBIDDEN_SCOPE` | Đạt |
| sales_a | Sales A đọc Interaction ngoài scope: `GET /interactions/ad0ac888-0dfc-46ba-8708-feb234eafb37` | 403 FORBIDDEN_SCOPE | 403 `FORBIDDEN_SCOPE` | Đạt |
| data | Danh sách đối tượng cho bộ lọc: `GET /customers?size=20` | 200 để chọn đối tượng | 403 `FORBIDDEN_SCOPE` | Không đạt — Data Staff có interaction.read nhưng backend CRM có thể từ chối scope |
| data | Danh sách đối tượng cho bộ lọc: `GET /leads?size=20` | 200 để chọn đối tượng | 403 `FORBIDDEN_SCOPE` | Không đạt — Data Staff có interaction.read nhưng backend CRM có thể từ chối scope |
| db | Audit không chứa nội dung: `SELECT audit_logs` | 0 | — | Đạt — Số snapshot có dấu hiệu nội dung: 0 |

### FINDING bổ sung

Data Staff có `interaction.read` và xem được metadata Interaction, nhưng `GET /api/backend/customers?size=20` và `GET /api/backend/leads?size=20` đều trả `403 FORBIDDEN_SCOPE`. Đây phù hợp phạm vi CRM hiện tại, song chưa có API danh sách đối tượng để bộ chọn/lọc của Data Staff hoạt động. Phần chọn đối tượng bị chặn và giao diện hiển thị lý do; không thay backend hay nới scope. FINDING Opportunity lọc theo Lead và các ca DEC-14 ở phần trên vẫn giữ nguyên.

Chưa kiểm UI bằng trình duyệt, screen reader hoặc thiết bị thật. Sau lượt bổ sung, backend/frontend và cluster test được dừng; 8080, 8081, 3010, 3011 và 54329 không còn lắng nghe. Không có thao tác trên Supabase hoặc PostgreSQL 5432.
