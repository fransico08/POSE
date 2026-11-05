# POSE CRM — Data Dictionary v2 (Synchronized / Review-ready)

> **Trạng thái:** review-ready cho Task 3 và làm đầu vào trực tiếp cho Migration/Seed Task 5. Bản này đồng bộ với snapshot `main` tải ngày 27/09/2026 và Decision Log DEC-01 → DEC-05. Không gọi là “final khóa schema” vì Domain Class Diagram vẫn ghi một số lựa chọn cần đóng review chính thức.

## 1. Nguồn chuẩn và thứ tự ưu tiên

1. **Hard baseline:** `docs/meetings/decision-log.md` — DEC-01 → DEC-05.
2. **Hard baseline:** `docs/analysis/roles-authorization-v1.md`.
3. **Hard baseline:** `docs/api/api-contract-foundation-v1.md` và `docs/api/architecture-v1.md`.
4. `docs/reviews/POSE_Gop_y_Data_Dictionary_va_Demo_v1.1.docx` — danh sách lỗi L01–L07/D01–D10 và phần Nhi phải giữ chờ nhóm chốt.
5. **Review context, chưa phải căn cứ khóa schema:** `docs/analysis/business-rules-v1.md` và `docs/design/domain-class-diagram-v1.md`. Các giá trị/lựa chọn lấy từ đây trong bản v2 được đánh dấu provisional.

## 2. Quy ước mapping Domain → PostgreSQL

- `CrmSubject` là **interface**, không tạo table riêng. Physical schema dùng cặp `customer_id` / `lead_id` và CHECK XOR ở các entity cần đúng một subject.
- `ContactIdentity` là **value object**, được lưu trực tiếp bằng `email`, `normalized_email`, `phone`, `normalized_phone` trên `customers` và `leads`.
- `Customer360Summary` là **read model**, triển khai bằng view `customer_360_summary`, không cho sửa trực tiếp.
- `AuthorizationPolicy`, `ImportApplicationService`, `DataValidationGateway`, `WorkflowCommandService` là control/interface, không tạo table.
- `customer_assignments` là persistence bổ sung để lưu lịch sử scope SALES/CARE theo thời gian và hỗ trợ Handover/authorization; không phải suy luận “mỗi class = một table”.
- Các bảng polymorphic (`source_record_links`, `duplicate_review_cases`) dùng `subject_type` + UUID; backend và validator kiểm tra target theo type.
- Dữ liệu nghiệp vụ dùng inactive/archive, không hard-delete trong MVP.

## 3. Các lựa chọn hiện đang áp dụng (còn chờ đóng review chính thức)

1. Offering Attribute Definition gắn ở **Category** để tái sử dụng.
2. Offering Price lưu **lịch sử khoảng hiệu lực**.
3. Lead hiện **bắt buộc một Offering** (`offering_id NOT NULL`).
4. `SALES_ONLY` áp dụng cho **Interaction và Feedback**.
5. Khi Lead chuyển Customer, implementation hiện chọn **rebind activity/task/opportunity/source link sang Customer**; final-state seed không để các liên kết này trỏ vào Lead đã `CONVERTED`.

## 4. Enum / controlled values

| Nhóm | Giá trị hiện dùng |
|---|---|
| Role code | `ADMIN`, `MANAGER`, `SALES_REP`, `CUSTOMER_CARE`, `DATA_STAFF` |
| Offering status | `DRAFT`, `ACTIVE`, `INACTIVE` |
| Sales Assignment status | `ACTIVE`, `EXPIRED`, `REVOKED` |
| Lead status | `NEW`, `QUALIFIED`, `CONVERTED`, `DISQUALIFIED` |
| Opportunity stage | `NEW`, `QUALIFIED`, `PROPOSAL`, `WON`, `LOST` |
| Task status | `OPEN`, `IN_PROGRESS`, `DONE`, `CANCELLED` |
| Note visibility | `CARE_VISIBLE`, `SALES_ONLY` |
| Import Job status | `RECEIVED`, `VALIDATING`, `REVIEW_REQUIRED`, `COMMITTED`, `FAILED` |
| Support status | `OPEN`, `IN_PROGRESS`, `ESCALATED`, `RESOLVED`, `CLOSED` |
| Support priority | `LOW`, `MEDIUM`, `HIGH`, `URGENT` |
| Handover type | `SALES`, `CARE` |
| Handover status | `PENDING`, `APPROVED`, `REJECTED` |

## 5. Physical tables

### 5.1. Access & Organization

#### `organizations`

Ranh giới tenant dữ liệu của CRM đa ngành.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `name` | `VARCHAR(160)` | No | NOT NULL | Tên hiển thị. |
| `industry` | `VARCHAR(120)` | Yes | — | Ngành demo/hoạt động chính của Organization. |
| `status` | `VARCHAR(20)` | No | NOT NULL DEFAULT 'ACTIVE' | Trạng thái vòng đời của bản ghi. |
| `metadata` | `JSONB` | No | NOT NULL DEFAULT '{}'::jsonb | Metadata mở rộng dạng JSONB, không dùng để thay thế các field nghiệp vụ lõi. |
| `created_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo bản ghi. |
| `updated_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm cập nhật gần nhất. |

**Table-level rules:**
- `CONSTRAINT ck_organizations_status CHECK (status IN ('ACTIVE','INACTIVE'))`

#### `departments`

Đơn vị tổ chức chứa Team trong một Organization.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `code` | `VARCHAR(60)` | No | NOT NULL | Mã nghiệp vụ ổn định trong phạm vi quy định. |
| `name` | `VARCHAR(160)` | No | NOT NULL | Tên hiển thị. |
| `status` | `VARCHAR(20)` | No | NOT NULL DEFAULT 'ACTIVE' | ACTIVE hoặc INACTIVE. |
| `created_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo bản ghi. |

**Table-level rules:**
- `CONSTRAINT uq_departments_org_code UNIQUE (organization_id, code)`
- `CONSTRAINT ck_departments_status CHECK (status IN ('ACTIVE','INACTIVE'))`

#### `teams`

Nhóm làm việc; có code nội bộ và Manager đang phụ trách.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `code` | `VARCHAR(60)` | No | NOT NULL | Mã Team unique trong Organization. |
| `name` | `VARCHAR(160)` | No | NOT NULL | Tên hiển thị. |
| `description` | `TEXT` | Yes | — | Mô tả nghiệp vụ. |
| `manager_user_id` | `UUID` | No | NOT NULL REFERENCES users(id) ON DELETE RESTRICT | User Manager đang phụ trách Team. |
| `status` | `VARCHAR(20)` | No | NOT NULL DEFAULT 'ACTIVE' | ACTIVE hoặc INACTIVE. |
| `created_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo bản ghi. |
| `updated_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm cập nhật gần nhất. |
| `department_id` | `UUID` | No | NOT NULL REFERENCES departments(id) ON DELETE RESTRICT | Department chứa Team. |

**Table-level rules:**
- `CONSTRAINT uq_teams_org_code UNIQUE (organization_id, code)`
- `CONSTRAINT ck_teams_status CHECK (status IN ('ACTIVE','INACTIVE'))`

#### `users`

Hồ sơ nhân viên thuộc đúng một Organization; email unique trong Organization.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `full_name` | `VARCHAR(160)` | No | NOT NULL | Họ tên User. |
| `status` | `VARCHAR(20)` | No | NOT NULL DEFAULT 'ACTIVE' | Trạng thái vòng đời của bản ghi. |
| `metadata` | `JSONB` | No | NOT NULL DEFAULT '{}'::jsonb | Metadata mở rộng dạng JSONB, không dùng để thay thế các field nghiệp vụ lõi. |
| `created_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo bản ghi. |
| `updated_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm cập nhật gần nhất. |
| `email` | `VARCHAR(254)` | No | NOT NULL | Email hiển thị/gốc. |
| `normalized_email` | `VARCHAR(254)` | No | NOT NULL | Email đã trim + lowercase để đối chiếu/unique. |
| `archived_at` | `TIMESTAMPTZ` | Yes | — | Thời điểm archive; null khi còn hiệu lực. |

**Table-level rules:**
- `CONSTRAINT uq_users_org_email UNIQUE (organization_id, normalized_email)`
- `CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE','INACTIVE','ARCHIVED'))`

#### `credentials`

Thông tin xác thực tách khỏi hồ sơ User; chỉ lưu password hash, không lưu mật khẩu thuần.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `user_id` | `UUID` | No | PRIMARY KEY REFERENCES users(id) ON DELETE RESTRICT | User liên quan. |
| `password_hash` | `VARCHAR(255)` | No | NOT NULL | BCrypt password hash; không lưu mật khẩu thuần. |
| `password_changed_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm mật khẩu gần nhất được thay đổi. |
| `failed_login_count` | `INTEGER` | No | NOT NULL DEFAULT 0 | Số lần đăng nhập sai phục vụ kiểm soát xác thực. |
| `updated_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm cập nhật gần nhất. |

**Table-level rules:**
- `CONSTRAINT ck_credentials_failures CHECK (failed_login_count >= 0)`

#### `team_memberships`

Lịch sử User tham gia Team với khoảng thời gian hiệu lực.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `team_id` | `UUID` | No | NOT NULL REFERENCES teams(id) ON DELETE RESTRICT | Team liên quan. |
| `user_id` | `UUID` | No | NOT NULL REFERENCES users(id) ON DELETE RESTRICT | User liên quan. |
| `joined_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm bắt đầu membership. |
| `left_at` | `TIMESTAMPTZ` | Yes | — | Thời điểm kết thúc membership; null nếu còn hiệu lực. |
| `status` | `VARCHAR(20)` | No | NOT NULL DEFAULT 'ACTIVE' | Trạng thái vòng đời của bản ghi. |

**Table-level rules:**
- `CONSTRAINT uq_team_membership UNIQUE (team_id, user_id, joined_at)`
- `CONSTRAINT ck_team_membership_status CHECK (status IN ('ACTIVE','ENDED'))`
- `CONSTRAINT ck_team_membership_dates CHECK (left_at IS NULL OR left_at >= joined_at)`

#### `permissions`

Danh mục permission code toàn hệ thống.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `code` | `VARCHAR(120)` | No | NOT NULL UNIQUE | Permission code ổn định dùng trong backend/API. |
| `description` | `TEXT` | No | NOT NULL | Mô tả nghiệp vụ. |
| `created_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo bản ghi. |

#### `roles`

Role theo Organization với bộ code chốt của RBAC.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `code` | `VARCHAR(40)` | No | NOT NULL | Role code: ADMIN, MANAGER, SALES_REP, CUSTOMER_CARE, DATA_STAFF. |
| `display_name` | `VARCHAR(120)` | No | NOT NULL | Tên hiển thị. |
| `description` | `TEXT` | Yes | — | Mô tả nghiệp vụ. |
| `status` | `VARCHAR(20)` | No | NOT NULL DEFAULT 'ACTIVE' | Trạng thái vòng đời của bản ghi. |
| `created_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo bản ghi. |

**Table-level rules:**
- `CONSTRAINT uq_roles_org_code UNIQUE (organization_id, code)`
- `CONSTRAINT ck_roles_code CHECK (code IN ('ADMIN','MANAGER','SALES_REP','CUSTOMER_CARE','DATA_STAFF'))`
- `CONSTRAINT ck_roles_status CHECK (status IN ('ACTIVE','INACTIVE'))`

#### `user_role_grants`

Lịch sử cấp/thu hồi Role cho User; hỗ trợ một User nhiều Role.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `user_id` | `UUID` | No | NOT NULL REFERENCES users(id) ON DELETE RESTRICT | User liên quan. |
| `role_id` | `UUID` | No | NOT NULL REFERENCES roles(id) ON DELETE RESTRICT | Role được tham chiếu. |
| `granted_by_user_id` | `UUID` | Yes | REFERENCES users(id) ON DELETE RESTRICT | User đã thực hiện cấp role. |
| `granted_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm cấp. |
| `revoked_at` | `TIMESTAMPTZ` | Yes | — | Thời điểm thu hồi. |

**Table-level rules:**
- `CONSTRAINT uq_user_role_grant UNIQUE (user_id, role_id, granted_at)`
- `CONSTRAINT ck_user_role_grant_dates CHECK (revoked_at IS NULL OR revoked_at >= granted_at)`

#### `role_permissions`

Quan hệ Role–Permission dùng để tính permission hiệu lực.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `role_id` | `UUID` | No | NOT NULL REFERENCES roles(id) ON DELETE RESTRICT | Role được tham chiếu. |
| `permission_id` | `UUID` | No | NOT NULL REFERENCES permissions(id) ON DELETE RESTRICT | Permission được tham chiếu. |
| `granted_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm cấp. |

**Table-level rules:**
- `PRIMARY KEY (role_id, permission_id)`

### 5.2. Offering & Sales Scope

#### `offering_categories`

Nhóm sản phẩm/dịch vụ trong từng Organization.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `code` | `VARCHAR(80)` | No | NOT NULL | Mã nghiệp vụ ổn định trong phạm vi quy định. |
| `name` | `VARCHAR(160)` | No | NOT NULL | Tên hiển thị. |
| `description` | `TEXT` | Yes | — | Mô tả nghiệp vụ. |
| `status` | `VARCHAR(20)` | No | NOT NULL DEFAULT 'ACTIVE' | Trạng thái vòng đời của bản ghi. |
| `created_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo bản ghi. |
| `updated_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm cập nhật gần nhất. |

**Table-level rules:**
- `CONSTRAINT uq_offering_categories_org_code UNIQUE (organization_id, code)`
- `CONSTRAINT ck_offering_categories_status CHECK (status IN ('ACTIVE','INACTIVE'))`

#### `offerings`

Mô hình chung cho Product hoặc Service.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `category_id` | `UUID` | No | NOT NULL REFERENCES offering_categories(id) ON DELETE RESTRICT | Offering Category liên quan. |
| `code` | `VARCHAR(80)` | No | NOT NULL | Mã Offering unique trong Organization. |
| `name` | `VARCHAR(200)` | No | NOT NULL | Tên hiển thị. |
| `description` | `TEXT` | Yes | — | Mô tả nghiệp vụ. |
| `kind` | `VARCHAR(20)` | No | NOT NULL | Loại Offering: PRODUCT hoặc SERVICE. |
| `unit` | `VARCHAR(80)` | Yes | — | Đơn vị tính/đơn vị cung cấp. |
| `status` | `VARCHAR(20)` | No | NOT NULL | DRAFT, ACTIVE hoặc INACTIVE. |
| `metadata` | `JSONB` | No | NOT NULL DEFAULT '{}'::jsonb | Metadata mở rộng dạng JSONB, không dùng để thay thế các field nghiệp vụ lõi. |
| `created_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo bản ghi. |
| `updated_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm cập nhật gần nhất. |

**Table-level rules:**
- `CONSTRAINT uq_offerings_org_code UNIQUE (organization_id, code)`
- `CONSTRAINT ck_offerings_kind CHECK (kind IN ('PRODUCT','SERVICE'))`
- `CONSTRAINT ck_offerings_status CHECK (status IN ('DRAFT','ACTIVE','INACTIVE'))`

#### `offering_attribute_definitions`

Định nghĩa thuộc tính mở rộng ở cấp Category theo lựa chọn hiện tại của Domain Class Diagram.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `category_id` | `UUID` | No | NOT NULL REFERENCES offering_categories(id) ON DELETE RESTRICT | Offering Category liên quan. |
| `key` | `VARCHAR(100)` | No | NOT NULL | Khóa thuộc tính mở rộng. |
| `label` | `VARCHAR(160)` | No | NOT NULL | Nhãn hiển thị thuộc tính. |
| `value_type` | `VARCHAR(30)` | No | NOT NULL | Kiểu dữ liệu logic của thuộc tính. |
| `required` | `BOOLEAN` | No | NOT NULL DEFAULT FALSE | Cho biết thuộc tính có bắt buộc hay không. |
| `validation_rule` | `TEXT` | Yes | — | Mô tả/rule validate bổ sung. |
| `options` | `JSONB` | Yes | — | Danh sách option/config dạng JSONB. |
| `sort_order` | `INTEGER` | No | NOT NULL DEFAULT 0 | Thứ tự hiển thị. |
| `status` | `VARCHAR(20)` | No | NOT NULL DEFAULT 'ACTIVE' | Trạng thái vòng đời của bản ghi. |

**Table-level rules:**
- `CONSTRAINT uq_attribute_def_category_key UNIQUE (category_id, key)`
- `CONSTRAINT ck_attribute_def_type CHECK (value_type IN ('TEXT','NUMBER','BOOLEAN','OPTION','DATE'))`
- `CONSTRAINT ck_attribute_def_status CHECK (status IN ('ACTIVE','INACTIVE'))`

#### `offering_attribute_values`

Giá trị thuộc tính mở rộng của từng Offering.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `offering_id` | `UUID` | No | NOT NULL REFERENCES offerings(id) ON DELETE RESTRICT | Offering liên quan. |
| `attribute_definition_id` | `UUID` | No | NOT NULL REFERENCES offering_attribute_definitions(id) ON DELETE RESTRICT | Định nghĩa thuộc tính được gán giá trị. |
| `value_json` | `JSONB` | No | NOT NULL | Giá trị thuộc tính dạng JSONB. |
| `created_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo bản ghi. |
| `updated_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm cập nhật gần nhất. |

**Table-level rules:**
- `CONSTRAINT uq_offering_attribute_value UNIQUE (offering_id, attribute_definition_id)`

#### `offering_prices`

Lịch sử giá Offering theo khoảng hiệu lực; không phải payment module.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `offering_id` | `UUID` | No | NOT NULL REFERENCES offerings(id) ON DELETE RESTRICT | Offering liên quan. |
| `amount` | `NUMERIC(18,2)` | No | NOT NULL | Giá trị tiền. |
| `currency_code` | `CHAR(3)` | No | NOT NULL | Mã tiền tệ ISO dạng 3 ký tự. |
| `pricing_unit` | `VARCHAR(80)` | Yes | — | Đơn vị áp dụng giá. |
| `effective_from` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm bắt đầu hiệu lực. |
| `effective_to` | `TIMESTAMPTZ` | Yes | — | Thời điểm hết hiệu lực; null nếu chưa kết thúc. |
| `created_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo bản ghi. |

**Table-level rules:**
- `CONSTRAINT ck_offering_prices_amount CHECK (amount >= 0)`
- `CONSTRAINT ck_offering_prices_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)`

#### `sales_assignments`

Phạm vi Sale được phép làm việc với Offering theo khoảng hiệu lực.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `offering_id` | `UUID` | No | NOT NULL REFERENCES offerings(id) ON DELETE RESTRICT | Offering liên quan. |
| `sales_user_id` | `UUID` | No | NOT NULL REFERENCES users(id) ON DELETE RESTRICT | User có role SALES_REP được phân công Offering. |
| `assigned_by_user_id` | `UUID` | No | NOT NULL REFERENCES users(id) ON DELETE RESTRICT | User thực hiện phân công. |
| `status` | `VARCHAR(20)` | No | NOT NULL | ACTIVE, EXPIRED hoặc REVOKED. |
| `effective_from` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm bắt đầu hiệu lực. |
| `effective_to` | `TIMESTAMPTZ` | Yes | — | Thời điểm hết hiệu lực; null nếu chưa kết thúc. |
| `revoked_at` | `TIMESTAMPTZ` | Yes | — | Thời điểm thu hồi. |
| `revoke_reason` | `TEXT` | Yes | — | Lý do thu hồi assignment. |
| `created_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo bản ghi. |
| `updated_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm cập nhật gần nhất. |

**Table-level rules:**
- `CONSTRAINT ck_sales_assignment_status CHECK (status IN ('ACTIVE','EXPIRED','REVOKED'))`
- `CONSTRAINT ck_sales_assignment_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)`
- `CONSTRAINT ck_sales_assignment_revoked CHECK (status <> 'REVOKED' OR revoked_at IS NOT NULL)`

### 5.3. CRM Subject & Sales

#### `customers`

Customer CRM với identity đã chuẩn hóa và primary Sales owner.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `owner_user_id` | `UUID` | No | NOT NULL REFERENCES users(id) ON DELETE RESTRICT | User đang là owner nghiệp vụ của bản ghi. |
| `display_name` | `VARCHAR(180)` | No | NOT NULL | Tên hiển thị. |
| `email` | `VARCHAR(254)` | Yes | — | Email hiển thị/gốc. |
| `normalized_email` | `VARCHAR(254)` | Yes | — | Email đã trim + lowercase để đối chiếu/unique. |
| `phone` | `VARCHAR(40)` | Yes | — | Số điện thoại hiển thị/gốc. |
| `normalized_phone` | `VARCHAR(40)` | Yes | — | Số điện thoại đã chuẩn hóa để đối chiếu/unique. |
| `status` | `VARCHAR(30)` | No | NOT NULL | Trạng thái vòng đời của bản ghi. |
| `metadata` | `JSONB` | No | NOT NULL DEFAULT '{}'::jsonb | Metadata mở rộng dạng JSONB, không dùng để thay thế các field nghiệp vụ lõi. |
| `created_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo bản ghi. |
| `archived_at` | `TIMESTAMPTZ` | Yes | — | Thời điểm archive; null khi còn hiệu lực. |
| `updated_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm cập nhật gần nhất. |

**Table-level rules:**
- `CONSTRAINT ck_customers_identity CHECK (normalized_email IS NOT NULL OR normalized_phone IS NOT NULL)`

#### `leads`

Lead CRM với identity chuẩn hóa, Offering quan tâm, owner và thông tin conversion.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `owner_user_id` | `UUID` | No | NOT NULL REFERENCES users(id) ON DELETE RESTRICT | User đang là owner nghiệp vụ của bản ghi. |
| `offering_id` | `UUID` | No | NOT NULL REFERENCES offerings(id) ON DELETE RESTRICT | Offering liên quan. |
| `display_name` | `VARCHAR(180)` | No | NOT NULL | Tên hiển thị. |
| `email` | `VARCHAR(254)` | Yes | — | Email hiển thị/gốc. |
| `normalized_email` | `VARCHAR(254)` | Yes | — | Email đã trim + lowercase để đối chiếu/unique. |
| `phone` | `VARCHAR(40)` | Yes | — | Số điện thoại hiển thị/gốc. |
| `normalized_phone` | `VARCHAR(40)` | Yes | — | Số điện thoại đã chuẩn hóa để đối chiếu/unique. |
| `status` | `VARCHAR(30)` | No | NOT NULL | NEW, QUALIFIED, CONVERTED hoặc DISQUALIFIED. |
| `qualified_at` | `TIMESTAMPTZ` | Yes | — | Thời điểm Lead được qualify. |
| `converted_customer_id` | `UUID` | Yes | REFERENCES customers(id) ON DELETE RESTRICT | Customer nhận từ Lead conversion. |
| `converted_at` | `TIMESTAMPTZ` | Yes | — | Thời điểm Lead được chuyển thành Customer. |
| `disqualification_reason` | `TEXT` | Yes | — | Lý do Lead bị disqualify. |
| `created_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo bản ghi. |
| `archived_at` | `TIMESTAMPTZ` | Yes | — | Thời điểm archive; null khi còn hiệu lực. |
| `updated_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm cập nhật gần nhất. |

**Table-level rules:**
- `CONSTRAINT ck_leads_identity CHECK (normalized_email IS NOT NULL OR normalized_phone IS NOT NULL)`
- `CONSTRAINT ck_leads_status CHECK (status IN ('NEW','QUALIFIED','CONVERTED','DISQUALIFIED'))`
- `CONSTRAINT ck_leads_conversion CHECK ( (status='CONVERTED' AND converted_customer_id IS NOT NULL AND converted_at IS NOT NULL) OR (status<>'CONVERTED' AND converted_customer_id IS NULL AND converted_at IS NULL) )`
- `CONSTRAINT ck_leads_disqualified CHECK (status <> 'DISQUALIFIED' OR disqualification_reason IS NOT NULL)`

#### `source_record_links`

Ánh xạ hồ sơ CRM tới khóa định danh ở hệ thống nguồn; polymorphic Customer/Lead.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `source_system_id` | `UUID` | No | NOT NULL REFERENCES source_systems(id) ON DELETE RESTRICT | Hệ thống nguồn liên quan. |
| `subject_type` | `VARCHAR(20)` | No | NOT NULL | Loại subject vật lý CUSTOMER/LEAD. |
| `subject_id` | `UUID` | No | NOT NULL | UUID subject; backend/validator kiểm tra theo subject_type. |
| `external_id` | `VARCHAR(180)` | No | NOT NULL | ID của bản ghi tại hệ thống nguồn. |
| `source_entity_type` | `VARCHAR(60)` | No | NOT NULL | Loại entity tại nguồn, giữ provenance. |
| `status` | `VARCHAR(20)` | No | NOT NULL DEFAULT 'ACTIVE' | Trạng thái vòng đời của bản ghi. |
| `linked_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo source link. |

**Table-level rules:**
- `CONSTRAINT uq_source_record_external UNIQUE (organization_id, source_system_id, source_entity_type, external_id)`
- `CONSTRAINT ck_source_record_subject CHECK (subject_type IN ('CUSTOMER','LEAD'))`
- `CONSTRAINT ck_source_record_status CHECK (status IN ('ACTIVE','ARCHIVED'))`

#### `duplicate_review_cases`

Hàng đợi review hồ sơ nghi trùng, không tự merge âm thầm.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `subject_type` | `VARCHAR(20)` | No | NOT NULL | Loại subject vật lý CUSTOMER/LEAD. |
| `subject_id` | `UUID` | No | NOT NULL | UUID subject; backend/validator kiểm tra theo subject_type. |
| `candidate_type` | `VARCHAR(20)` | No | NOT NULL | Loại hồ sơ nghi trùng. |
| `candidate_id` | `UUID` | No | NOT NULL | ID hồ sơ nghi trùng. |
| `match_reason` | `TEXT` | No | NOT NULL | Lý do/rule phát hiện nghi trùng. |
| `status` | `VARCHAR(30)` | No | NOT NULL | Trạng thái vòng đời của bản ghi. |
| `reviewed_by_user_id` | `UUID` | Yes | REFERENCES users(id) ON DELETE RESTRICT | User đã review/duyệt. |
| `reviewed_at` | `TIMESTAMPTZ` | Yes | — | Thời điểm review/duyệt. |
| `created_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo bản ghi. |

**Table-level rules:**
- `CONSTRAINT ck_duplicate_subject CHECK (subject_type IN ('CUSTOMER','LEAD') AND candidate_type IN ('CUSTOMER','LEAD'))`
- `CONSTRAINT ck_duplicate_status CHECK (status IN ('OPEN','CONFIRMED_MATCH','REJECTED'))`

#### `opportunities`

Cơ hội bán hàng; đúng một Offering và đúng một subject Customer XOR Lead (DEC-01).

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `customer_id` | `UUID` | Yes | REFERENCES customers(id) ON DELETE RESTRICT | Customer liên quan. |
| `lead_id` | `UUID` | Yes | REFERENCES leads(id) ON DELETE RESTRICT | Lead liên quan. |
| `offering_id` | `UUID` | No | NOT NULL REFERENCES offerings(id) ON DELETE RESTRICT | Offering liên quan. |
| `owner_user_id` | `UUID` | No | NOT NULL REFERENCES users(id) ON DELETE RESTRICT | User đang là owner nghiệp vụ của bản ghi. |
| `title` | `VARCHAR(220)` | No | NOT NULL | Tiêu đề nghiệp vụ. |
| `stage` | `VARCHAR(30)` | No | NOT NULL | NEW, QUALIFIED, PROPOSAL, WON hoặc LOST. |
| `expected_value` | `NUMERIC(18,2)` | Yes | — | Giá trị dự kiến của Opportunity. |
| `currency_code` | `CHAR(3)` | No | NOT NULL DEFAULT 'VND' | Mã tiền tệ ISO dạng 3 ký tự. |
| `expected_close_date` | `DATE` | Yes | — | Ngày dự kiến chốt. |
| `closing_reason` | `TEXT` | Yes | — | Lý do khi Opportunity WON/LOST. |
| `closed_at` | `TIMESTAMPTZ` | Yes | — | Thời điểm đóng. |
| `created_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo bản ghi. |
| `updated_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm cập nhật gần nhất. |

**Table-level rules:**
- `CONSTRAINT ck_opportunity_subject_xor CHECK ((customer_id IS NOT NULL) <> (lead_id IS NOT NULL))`
- `CONSTRAINT ck_opportunity_stage CHECK (stage IN ('NEW','QUALIFIED','PROPOSAL','WON','LOST'))`
- `CONSTRAINT ck_opportunity_value CHECK (expected_value IS NULL OR expected_value >= 0)`
- `CONSTRAINT ck_opportunity_closed CHECK ( (stage IN ('WON','LOST') AND closing_reason IS NOT NULL AND closed_at IS NOT NULL) OR (stage NOT IN ('WON','LOST') AND closed_at IS NULL) )`

### 5.4. Care & Work Scope

#### `customer_assignments`

Persistence vật lý cho lịch sử phân công Customer loại SALES/CARE và scope theo thời gian.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `customer_id` | `UUID` | No | NOT NULL REFERENCES customers(id) ON DELETE RESTRICT | Customer liên quan. |
| `user_id` | `UUID` | No | NOT NULL REFERENCES users(id) ON DELETE RESTRICT | User liên quan. |
| `assignment_type` | `VARCHAR(20)` | No | NOT NULL | Loại phân công Customer: SALES hoặc CARE. |
| `effective_from` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm bắt đầu hiệu lực. |
| `effective_to` | `TIMESTAMPTZ` | Yes | — | Thời điểm hết hiệu lực; null nếu chưa kết thúc. |
| `status` | `VARCHAR(20)` | No | NOT NULL | ACTIVE hoặc ENDED. |
| `assigned_by_user_id` | `UUID` | No | NOT NULL REFERENCES users(id) ON DELETE RESTRICT | User thực hiện phân công. |
| `source` | `VARCHAR(30)` | No | NOT NULL | Nguồn tạo phân công. |
| `handover_request_id` | `UUID` | Yes | REFERENCES handover_requests(id) ON DELETE RESTRICT | Handover đã tạo phân công này nếu có. |
| `created_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo bản ghi. |

**Table-level rules:**
- `CONSTRAINT ck_customer_assignment_type CHECK (assignment_type IN ('SALES','CARE'))`
- `CONSTRAINT ck_customer_assignment_status CHECK (status IN ('ACTIVE','ENDED'))`
- `CONSTRAINT ck_customer_assignment_source CHECK (source IN ('INITIAL','MANUAL','HANDOVER','IMPORT'))`
- `CONSTRAINT ck_customer_assignment_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)`

#### `interactions`

Dòng thời gian tương tác của Customer/Lead, có visibility cho nội dung nhạy cảm.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `customer_id` | `UUID` | Yes | REFERENCES customers(id) ON DELETE RESTRICT | Customer liên quan. |
| `lead_id` | `UUID` | Yes | REFERENCES leads(id) ON DELETE RESTRICT | Lead liên quan. |
| `opportunity_id` | `UUID` | Yes | REFERENCES opportunities(id) ON DELETE RESTRICT | Opportunity làm ngữ cảnh tùy chọn. |
| `recorded_by_user_id` | `UUID` | No | NOT NULL REFERENCES users(id) ON DELETE RESTRICT | User ghi nhận activity. |
| `type` | `VARCHAR(50)` | No | NOT NULL | Loại activity. |
| `channel` | `VARCHAR(50)` | No | NOT NULL | Kênh tương tác. |
| `subject` | `VARCHAR(220)` | No | NOT NULL | Chủ đề ngắn. |
| `content` | `TEXT` | No | NOT NULL | Nội dung nghiệp vụ. |
| `outcome` | `TEXT` | Yes | — | Kết quả tương tác. |
| `visibility` | `VARCHAR(20)` | No | NOT NULL | CARE_VISIBLE hoặc SALES_ONLY. |
| `occurred_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm sự kiện xảy ra. |
| `created_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo bản ghi. |
| `updated_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm cập nhật gần nhất. |

**Table-level rules:**
- `CONSTRAINT ck_interaction_subject_xor CHECK ((customer_id IS NOT NULL) <> (lead_id IS NOT NULL))`
- `CONSTRAINT ck_interaction_visibility CHECK (visibility IN ('CARE_VISIBLE','SALES_ONLY'))`

#### `feedbacks`

Phản hồi gắn Customer/Lead, có rating và visibility.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `customer_id` | `UUID` | Yes | REFERENCES customers(id) ON DELETE RESTRICT | Customer liên quan. |
| `lead_id` | `UUID` | Yes | REFERENCES leads(id) ON DELETE RESTRICT | Lead liên quan. |
| `recorded_by_user_id` | `UUID` | No | NOT NULL REFERENCES users(id) ON DELETE RESTRICT | User ghi nhận activity. |
| `category` | `VARCHAR(60)` | No | NOT NULL | Nhóm/loại nghiệp vụ. |
| `content` | `TEXT` | No | NOT NULL | Nội dung nghiệp vụ. |
| `rating` | `INTEGER` | Yes | — | Điểm đánh giá 1–5 nếu có. |
| `visibility` | `VARCHAR(20)` | No | NOT NULL | CARE_VISIBLE hoặc SALES_ONLY. |
| `received_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm nhận feedback. |
| `created_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo bản ghi. |

**Table-level rules:**
- `CONSTRAINT ck_feedback_subject_xor CHECK ((customer_id IS NOT NULL) <> (lead_id IS NOT NULL))`
- `CONSTRAINT ck_feedback_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5)`
- `CONSTRAINT ck_feedback_visibility CHECK (visibility IN ('CARE_VISIBLE','SALES_ONLY'))`

#### `crm_tasks`

Công việc CRM được giao cho User; hỗ trợ idempotency khi workflow tạo task.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `customer_id` | `UUID` | Yes | REFERENCES customers(id) ON DELETE RESTRICT | Customer liên quan. |
| `lead_id` | `UUID` | Yes | REFERENCES leads(id) ON DELETE RESTRICT | Lead liên quan. |
| `opportunity_id` | `UUID` | Yes | REFERENCES opportunities(id) ON DELETE RESTRICT | Opportunity làm ngữ cảnh tùy chọn. |
| `owner_user_id` | `UUID` | No | NOT NULL REFERENCES users(id) ON DELETE RESTRICT | User đang là owner nghiệp vụ của bản ghi. |
| `created_by_user_id` | `UUID` | No | NOT NULL REFERENCES users(id) ON DELETE RESTRICT | User tạo bản ghi. |
| `title` | `VARCHAR(220)` | No | NOT NULL | Tiêu đề nghiệp vụ. |
| `description` | `TEXT` | Yes | — | Mô tả nghiệp vụ. |
| `priority` | `VARCHAR(20)` | No | NOT NULL | Mức ưu tiên. |
| `status` | `VARCHAR(20)` | No | NOT NULL | OPEN, IN_PROGRESS, DONE hoặc CANCELLED. |
| `due_at` | `TIMESTAMPTZ` | No | NOT NULL | Hạn xử lý. |
| `result` | `TEXT` | Yes | — | Kết quả thực hiện. |
| `origin` | `VARCHAR(30)` | No | NOT NULL | Nguồn tạo task. |
| `idempotency_key` | `VARCHAR(160)` | Yes | — | Khóa chống tạo/xử lý trùng khi retry. |
| `completed_at` | `TIMESTAMPTZ` | Yes | — | Thời điểm task hoàn thành. |
| `created_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo bản ghi. |
| `updated_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm cập nhật gần nhất. |

**Table-level rules:**
- `CONSTRAINT ck_task_subject_xor CHECK ((customer_id IS NOT NULL) <> (lead_id IS NOT NULL))`
- `CONSTRAINT ck_task_status CHECK (status IN ('OPEN','IN_PROGRESS','DONE','CANCELLED'))`
- `CONSTRAINT ck_task_origin CHECK (origin IN ('MANUAL','WORKFLOW','IMPORT'))`
- `CONSTRAINT ck_task_complete CHECK ((status='DONE' AND completed_at IS NOT NULL) OR status<>'DONE')`

### 5.5. Import, Audit & Automation

#### `source_systems`

Danh mục nguồn dữ liệu ngoài theo Organization.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `code` | `VARCHAR(80)` | No | NOT NULL | Mã nghiệp vụ ổn định trong phạm vi quy định. |
| `name` | `VARCHAR(160)` | No | NOT NULL | Tên hiển thị. |
| `source_type` | `VARCHAR(30)` | No | NOT NULL | Loại hệ thống nguồn. |
| `status` | `VARCHAR(20)` | No | NOT NULL DEFAULT 'ACTIVE' | Trạng thái vòng đời của bản ghi. |
| `metadata` | `JSONB` | No | NOT NULL DEFAULT '{}'::jsonb | Metadata mở rộng dạng JSONB, không dùng để thay thế các field nghiệp vụ lõi. |
| `created_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo bản ghi. |
| `updated_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm cập nhật gần nhất. |

**Table-level rules:**
- `CONSTRAINT uq_source_systems_org_code UNIQUE (organization_id, code)`
- `CONSTRAINT ck_source_systems_status CHECK (status IN ('ACTIVE','INACTIVE'))`

#### `import_jobs`

Phiên import và trạng thái validate/approval/commit một bước (DEC-02).

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `source_system_id` | `UUID` | No | NOT NULL REFERENCES source_systems(id) ON DELETE RESTRICT | Hệ thống nguồn liên quan. |
| `submitted_by_user_id` | `UUID` | No | NOT NULL REFERENCES users(id) ON DELETE RESTRICT | Data Staff/User gửi import. |
| `approved_by_user_id` | `UUID` | Yes | REFERENCES users(id) ON DELETE RESTRICT | Manager/Admin duyệt commit import. |
| `status` | `VARCHAR(30)` | No | NOT NULL | RECEIVED, VALIDATING, REVIEW_REQUIRED, COMMITTED hoặc FAILED. |
| `original_file_ref` | `TEXT` | No | NOT NULL | Tham chiếu tới file import gốc. |
| `original_filename` | `VARCHAR(255)` | No | NOT NULL | Tên file gốc. |
| `file_checksum` | `VARCHAR(128)` | No | NOT NULL | Checksum dùng truy vết/chống nhầm file. |
| `idempotency_key` | `VARCHAR(180)` | No | NOT NULL | Khóa chống tạo/xử lý trùng khi retry. |
| `retry_count` | `INTEGER` | No | NOT NULL DEFAULT 0 | Số lần retry. |
| `total_rows` | `INTEGER` | No | NOT NULL DEFAULT 0 | Tổng số dòng import. |
| `valid_rows` | `INTEGER` | No | NOT NULL DEFAULT 0 | Số dòng hợp lệ sau validate. |
| `error_rows` | `INTEGER` | No | NOT NULL DEFAULT 0 | Số dòng lỗi sau validate. |
| `submitted_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm submit. |
| `validated_at` | `TIMESTAMPTZ` | Yes | — | Thời điểm hoàn tất validate. |
| `approved_at` | `TIMESTAMPTZ` | Yes | — | Thời điểm Manager/Admin duyệt. |
| `committed_at` | `TIMESTAMPTZ` | Yes | — | Thời điểm commit dữ liệu nghiệp vụ. |
| `failure_reason` | `TEXT` | Yes | — | Lý do thất bại. |

**Table-level rules:**
- `CONSTRAINT uq_import_job_key UNIQUE (organization_id, idempotency_key)`
- `CONSTRAINT ck_import_job_status CHECK (status IN ('RECEIVED','VALIDATING','REVIEW_REQUIRED','COMMITTED','FAILED'))`
- `CONSTRAINT ck_import_job_counts CHECK (retry_count>=0 AND total_rows>=0 AND valid_rows>=0 AND error_rows>=0)`
- `CONSTRAINT ck_import_job_approval CHECK ( (status='COMMITTED' AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL AND committed_at IS NOT NULL) OR status<>'COMMITTED' )`

#### `import_rows`

Từng dòng của Import Job, payload gốc/chuẩn hóa, lỗi và entity được commit.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `import_job_id` | `UUID` | No | NOT NULL REFERENCES import_jobs(id) ON DELETE RESTRICT | Import Job cha. |
| `row_number` | `INTEGER` | No | NOT NULL | Số dòng trong file. |
| `raw_payload` | `JSONB` | No | NOT NULL | Payload gốc của dòng import. |
| `normalized_payload` | `JSONB` | Yes | — | Payload đã chuẩn hóa. |
| `status` | `VARCHAR(20)` | No | NOT NULL | VALID, INVALID, DUPLICATE hoặc SKIPPED. |
| `error_codes` | `JSONB` | No | NOT NULL DEFAULT '[]'::jsonb | Danh sách lỗi validate dạng JSONB. |
| `duplicate_candidate` | `BOOLEAN` | No | NOT NULL DEFAULT FALSE | Đánh dấu nghi trùng. |
| `committed_entity_type` | `VARCHAR(20)` | Yes | — | Loại entity CRM đã commit. |
| `committed_entity_id` | `UUID` | Yes | — | ID entity CRM đã commit. |
| `row_fingerprint` | `VARCHAR(128)` | No | NOT NULL | Fingerprint phục vụ đối soát/retry. |
| `created_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo bản ghi. |

**Table-level rules:**
- `CONSTRAINT uq_import_row_number UNIQUE (import_job_id, row_number)`
- `CONSTRAINT ck_import_row_status CHECK (status IN ('VALID','INVALID','DUPLICATE','SKIPPED'))`
- `CONSTRAINT ck_import_row_committed_pair CHECK ((committed_entity_type IS NULL) = (committed_entity_id IS NULL))`
- `CONSTRAINT ck_import_row_entity_type CHECK (committed_entity_type IS NULL OR committed_entity_type IN ('CUSTOMER','LEAD'))`

#### `import_decisions`

Quyết định duyệt/từ chối Import Job bởi Manager/Admin.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `import_job_id` | `UUID` | No | NOT NULL UNIQUE REFERENCES import_jobs(id) ON DELETE RESTRICT | Import Job cha. |
| `decision` | `VARCHAR(20)` | No | NOT NULL | APPROVED hoặc REJECTED. |
| `decided_by_user_id` | `UUID` | No | NOT NULL REFERENCES users(id) ON DELETE RESTRICT | User ra quyết định import. |
| `decided_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm ra quyết định. |
| `reason` | `TEXT` | Yes | — | Ghi chú/lý do quyết định phê duyệt import. |

**Table-level rules:**
- `CONSTRAINT ck_import_decision CHECK (decision IN ('APPROVED','REJECTED'))`

#### `audit_logs`

Audit append-only ở mức nghiệp vụ; không ghi password/token/bí mật.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `actor_user_id` | `UUID` | No | NOT NULL REFERENCES users(id) ON DELETE RESTRICT | User thực hiện hành động audit. |
| `action` | `VARCHAR(120)` | No | NOT NULL | Mã hành động audit. |
| `entity_type` | `VARCHAR(100)` | No | NOT NULL | Loại entity bị tác động. |
| `entity_id` | `UUID` | No | NOT NULL | ID entity bị tác động. |
| `before_data` | `JSONB` | No | NOT NULL DEFAULT '{}'::jsonb | Snapshot an toàn trước thay đổi. |
| `after_data` | `JSONB` | No | NOT NULL DEFAULT '{}'::jsonb | Snapshot an toàn sau thay đổi. |
| `safe_metadata` | `JSONB` | No | NOT NULL DEFAULT '{}'::jsonb | Metadata audit đã loại bí mật. |
| `result` | `VARCHAR(30)` | No | NOT NULL | Kết quả thực hiện. |
| `occurred_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm sự kiện xảy ra. |

#### `outbox_events`

Sự kiện Outbox để automation tích hợp mà không cho n8n ghi trực tiếp DB.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `aggregate_type` | `VARCHAR(100)` | No | NOT NULL | Loại aggregate phát sự kiện. |
| `aggregate_id` | `UUID` | No | NOT NULL | ID aggregate. |
| `event_type` | `VARCHAR(120)` | No | NOT NULL | Loại sự kiện Outbox. |
| `payload` | `JSONB` | No | NOT NULL | Payload sự kiện JSONB. |
| `idempotency_key` | `VARCHAR(180)` | No | NOT NULL | Khóa chống tạo/xử lý trùng khi retry. |
| `delivery_status` | `VARCHAR(20)` | No | NOT NULL | Trạng thái phát Outbox. |
| `retry_count` | `INTEGER` | No | NOT NULL DEFAULT 0 | Số lần retry. |
| `last_error` | `TEXT` | Yes | — | Lỗi gần nhất khi publish/workflow. |
| `occurred_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm sự kiện xảy ra. |
| `published_at` | `TIMESTAMPTZ` | Yes | — | Thời điểm publish thành công. |

**Table-level rules:**
- `CONSTRAINT uq_outbox_idempotency UNIQUE (organization_id, idempotency_key)`
- `CONSTRAINT ck_outbox_status CHECK (delivery_status IN ('PENDING','PUBLISHED','FAILED'))`
- `CONSTRAINT ck_outbox_retry CHECK (retry_count >= 0)`

#### `workflow_executions`

Trạng thái chạy workflow/retry/idempotency và task được tạo.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `outbox_event_id` | `UUID` | Yes | REFERENCES outbox_events(id) ON DELETE RESTRICT | Outbox event kích hoạt workflow. |
| `workflow_type` | `VARCHAR(120)` | No | NOT NULL | Loại workflow. |
| `idempotency_key` | `VARCHAR(180)` | No | NOT NULL | Khóa chống tạo/xử lý trùng khi retry. |
| `status` | `VARCHAR(20)` | No | NOT NULL | Trạng thái vòng đời của bản ghi. |
| `retry_count` | `INTEGER` | No | NOT NULL DEFAULT 0 | Số lần retry. |
| `last_error` | `TEXT` | Yes | — | Lỗi gần nhất khi publish/workflow. |
| `started_at` | `TIMESTAMPTZ` | Yes | — | Thời điểm workflow bắt đầu. |
| `finished_at` | `TIMESTAMPTZ` | Yes | — | Thời điểm workflow kết thúc. |
| `created_task_id` | `UUID` | Yes | REFERENCES crm_tasks(id) ON DELETE RESTRICT | Task do workflow tạo nếu có. |

**Table-level rules:**
- `CONSTRAINT uq_workflow_idempotency UNIQUE (organization_id, idempotency_key)`
- `CONSTRAINT ck_workflow_status CHECK (status IN ('PENDING','RUNNING','DONE','FAILED'))`
- `CONSTRAINT ck_workflow_retry CHECK (retry_count >= 0)`

### 5.6. Support & Handover

#### `support_requests`

Yêu cầu hỗ trợ thuộc Customer và Customer Care phụ trách.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `customer_id` | `UUID` | No | NOT NULL REFERENCES customers(id) ON DELETE RESTRICT | Customer liên quan. |
| `assigned_to_user_id` | `UUID` | No | NOT NULL REFERENCES users(id) ON DELETE RESTRICT | Customer Care đang phụ trách support. |
| `title` | `VARCHAR(220)` | No | NOT NULL | Tiêu đề nghiệp vụ. |
| `category` | `VARCHAR(80)` | No | NOT NULL | Nhóm/loại nghiệp vụ. |
| `description` | `TEXT` | No | NOT NULL | Mô tả nghiệp vụ. |
| `priority` | `VARCHAR(20)` | No | NOT NULL | Mức ưu tiên. |
| `status` | `VARCHAR(30)` | No | NOT NULL | OPEN, IN_PROGRESS, ESCALATED, RESOLVED hoặc CLOSED. |
| `escalation_reason` | `TEXT` | Yes | — | Lý do chuyển cấp cho Manager. |
| `resolution` | `TEXT` | Yes | — | Kết quả xử lý support. |
| `created_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo bản ghi. |
| `updated_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm cập nhật gần nhất. |
| `resolved_at` | `TIMESTAMPTZ` | Yes | — | Thời điểm resolve. |
| `closed_at` | `TIMESTAMPTZ` | Yes | — | Thời điểm đóng. |

**Table-level rules:**
- `CONSTRAINT ck_support_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT'))`
- `CONSTRAINT ck_support_status CHECK (status IN ('OPEN','IN_PROGRESS','ESCALATED','RESOLVED','CLOSED'))`
- `CONSTRAINT ck_support_escalated CHECK (status <> 'ESCALATED' OR escalation_reason IS NOT NULL)`
- `CONSTRAINT ck_support_resolution CHECK (status NOT IN ('RESOLVED','CLOSED') OR resolution IS NOT NULL)`

#### `support_responses`

Lịch sử phản hồi support, được xem như nội dung đã gửi không sửa.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `support_request_id` | `UUID` | No | NOT NULL REFERENCES support_requests(id) ON DELETE RESTRICT | Support Request cha. |
| `content` | `TEXT` | No | NOT NULL | Nội dung nghiệp vụ. |
| `status_after` | `VARCHAR(30)` | Yes | — | Trạng thái support sau phản hồi. |
| `created_by_user_id` | `UUID` | No | NOT NULL REFERENCES users(id) ON DELETE RESTRICT | User tạo bản ghi. |
| `created_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo bản ghi. |

**Table-level rules:**
- `CONSTRAINT ck_support_response_status CHECK (status_after IS NULL OR status_after IN ('OPEN','IN_PROGRESS','ESCALATED','RESOLVED','CLOSED'))`

#### `support_attachments`

Metadata tệp đính kèm của support response/request.

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `support_request_id` | `UUID` | No | NOT NULL REFERENCES support_requests(id) ON DELETE RESTRICT | Support Request cha. |
| `support_response_id` | `UUID` | Yes | REFERENCES support_responses(id) ON DELETE RESTRICT | Support Response liên quan nếu attachment thuộc một phản hồi. |
| `file_name` | `VARCHAR(255)` | No | NOT NULL | Tên tệp đính kèm. |
| `file_type` | `VARCHAR(120)` | No | NOT NULL | MIME/type tệp. |
| `file_size` | `BIGINT` | No | NOT NULL | Kích thước tệp theo byte. |
| `storage_ref` | `TEXT` | No | NOT NULL | Tham chiếu vị trí lưu file. |
| `uploaded_by_user_id` | `UUID` | No | NOT NULL REFERENCES users(id) ON DELETE RESTRICT | User tải file. |
| `uploaded_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tải file. |

**Table-level rules:**
- `CONSTRAINT ck_support_attachment_size CHECK (file_size > 0)`

#### `handover_requests`

Yêu cầu bàn giao SALES hoặc CARE; chỉ đổi phân công sau khi Manager duyệt (DEC-05).

| Column | PostgreSQL type | Nullable | Constraint / reference | Ý nghĩa |
|---|---|:---:|---|---|
| `id` | `UUID` | No | PRIMARY KEY | Khóa định danh UUID của bản ghi. |
| `organization_id` | `UUID` | No | NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT | Organization sở hữu bản ghi; dùng làm tenant/data scope. |
| `customer_id` | `UUID` | No | NOT NULL REFERENCES customers(id) ON DELETE RESTRICT | Customer liên quan. |
| `handover_type` | `VARCHAR(20)` | No | NOT NULL | SALES hoặc CARE. |
| `requested_by_user_id` | `UUID` | No | NOT NULL REFERENCES users(id) ON DELETE RESTRICT | Sale đang phụ trách gửi yêu cầu. |
| `target_user_id` | `UUID` | Yes | REFERENCES users(id) ON DELETE RESTRICT | User nhận bàn giao đề xuất/chốt. |
| `reason` | `TEXT` | No | NOT NULL | Lý do Sale yêu cầu bàn giao Customer. |
| `status` | `VARCHAR(20)` | No | NOT NULL | PENDING, APPROVED hoặc REJECTED. |
| `reviewed_by_user_id` | `UUID` | Yes | REFERENCES users(id) ON DELETE RESTRICT | User đã review/duyệt. |
| `reviewed_at` | `TIMESTAMPTZ` | Yes | — | Thời điểm review/duyệt. |
| `rejection_reason` | `TEXT` | Yes | — | Lý do từ chối. |
| `created_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm tạo bản ghi. |
| `updated_at` | `TIMESTAMPTZ` | No | NOT NULL | Thời điểm cập nhật gần nhất. |

**Table-level rules:**
- `CONSTRAINT ck_handover_type CHECK (handover_type IN ('SALES','CARE'))`
- `CONSTRAINT ck_handover_status CHECK (status IN ('PENDING','APPROVED','REJECTED'))`
- `CONSTRAINT ck_handover_review CHECK ( (status='PENDING' AND reviewed_by_user_id IS NULL AND reviewed_at IS NULL) OR (status='APPROVED' AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL AND target_user_id IS NOT NULL) OR (status='REJECTED' AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL AND rejection_reason IS NOT NULL) )`

## 6. Read model

### `customer_360_summary`

View tổng hợp một dòng trên mỗi Customer. Nguồn đọc gồm Customer, active CARE assignment, SourceRecordLink, Opportunity, Interaction, Feedback, CRM Task và Support Request. View không phải nguồn dữ liệu chỉnh sửa.

| Column | Ý nghĩa |
|---|---|
| `customer_id` | Customer khóa của read model. |
| `organization_id` | Organization scope. |
| `owner_user_id` | Primary Sales owner. |
| `care_user_id` | Customer Care đang có assignment ACTIVE nếu có. |
| `display_name`, `normalized_email`, `normalized_phone` | Identity hiển thị/chuẩn hóa. |
| `source_count` | Số source link ACTIVE. |
| `opportunity_count` | Số Opportunity của Customer. |
| `interaction_count` | Số Interaction. |
| `feedback_count` | Số Feedback. |
| `open_task_count` | Task OPEN/IN_PROGRESS. |
| `open_support_count` | Support chưa RESOLVED/CLOSED. |
| `last_interaction_at` | Thời điểm interaction gần nhất. |
| `refreshed_at` | Thời điểm query view. |

## 7. Business constraints không thể biểu diễn đầy đủ chỉ bằng FK/CHECK

- User/Team/Role/record phải cùng Organization; backend vẫn phải enforce organization → permission → team → record scope.
- Team `manager_user_id` phải là User ACTIVE có role `MANAGER` trong cùng Organization.
- `SALES_REP` chỉ thao tác Offering/record khi Sales Assignment ACTIVE và nằm trong khoảng hiệu lực.
- Customer/Lead identity phải normalize trước khi ghi; exact duplicate không tự tạo hồ sơ mới.
- Khi Lead `CONVERTED`, Customer phải match ít nhất một normalized identity; final state phải rebind các link nghiệp vụ theo lựa chọn hiện tại.
- Opportunity owner phải là Sales Rep hợp lệ và có Sales Assignment với Offering tại thời điểm tạo.
- `WON`/`LOST` bị khóa; chỉ Manager có luồng reopen về `QUALIFIED` và phải audit.
- Customer Care không được đọc `SALES_ONLY`.
- `COMMITTED` Import Job chỉ sau một lần approve bởi Manager/Admin; Data Staff không tự commit.
- Handover `SALES` yêu cầu target Sales Rep có assignment ACTIVE với Offering liên quan; `CARE` yêu cầu target Customer Care ACTIVE.
- n8n/automation chỉ đi qua API/outbox/workflow; không ghi trực tiếp PostgreSQL.

## 8. Đồng bộ với Task 2 và Task 5

- CSV seed v2 dùng đúng cột/tên table của Data Dictionary này.
- `V1__pose_crm_schema.sql` tạo 36 physical tables + view `customer_360_summary`.
- `validate_seed_files.py` kiểm các lỗi review L01–L07 và baseline D01–D10 trước khi nạp DB.
- `verify_seed_v2.psql` kiểm lại trên PostgreSQL sau migration + seed, bao gồm Customer 360 và KPI reconciliation.
