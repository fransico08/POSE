# Tài liệu POSE CRM

Thư mục này chỉ chứa tài liệu đang dùng để triển khai POSE CRM. Khi có mâu thuẫn, ưu tiên tài liệu theo thứ tự dưới đây.

## Nguồn yêu cầu

1. `SRS POSE.docx`: đặc tả yêu cầu CRM đa ngành.
2. [Business Rules v1](analysis/business-rules-v1.md): luồng và quy tắc nghiệp vụ.
3. [Roles and Authorization v1](analysis/roles-authorization-v1.md): role, permission và data scope.
4. [Domain Class Diagram v1](design/domain-class-diagram-v1.md): domain class, quan hệ nghiệp vụ và ranh giới Backend.
5. [Architecture v1](api/architecture-v1.md): thành phần hệ thống, module và ranh giới tích hợp.
6. [API Contract Foundation v1](api/api-contract-foundation-v1.md): Auth, RBAC, Organization và Team.

## Ownership tài liệu tuần 1

| Artefact | Owner chính | Vai trò hỗ trợ |
|---|---|---|
| Business Rules v1 | Nguyễn Đức Thắng | Tài review backend/RBAC, Nhi review data/import |
| Roles and Authorization v1 | Huỳnh Minh Tài | Thắng/Nhi review scope nghiệp vụ và dữ liệu |
| Domain Class Diagram v1 | Huỳnh Minh Tài | Thắng review sales flow, Nhi review data mapping |
| Data Dictionary v1 | Vân Phạm Thảo Nhi | Tài review persistence/RBAC, Thắng review sales flow |
| Architecture v1 và API Contract Foundation v1 | Huỳnh Minh Tài | Thắng/Nhi review module integration |

## Trạng thái review hiện tại

- Roles and Authorization v1 và API Contract Foundation v1 đã được đồng bộ về multi-role, self-privilege escalation, scope, audit và các rule kế thừa cho CRM API; đang chờ Thắng/Nhi review.
- Domain Class Diagram v1 là đầu vào cho Backend; Data Dictionary v1 vẫn là điều kiện bắt buộc trước khi tạo migration CRM.

## Tài liệu sẽ được bổ sung

- `design/`: Domain Class Diagram, Data Dictionary và wireframe; diagram do nhóm tạo được lưu tại đây khi đưa vào workspace.
- `api/`: API Contract v1.
- `requirements/`: acceptance criteria và test case.
- `testing/`: test plan, evidence và báo cáo test.
- `meetings/`: biên bản review/decision log khi cần.

Không tạo hoặc sử dụng tài liệu cho cart, checkout, payment, voucher, inventory hay các chức năng E-Commerce đầy đủ trong TLCN.
