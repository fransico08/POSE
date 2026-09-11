# Tài liệu POSE CRM

Thư mục này chỉ chứa tài liệu đang dùng để triển khai POSE CRM. Khi có mâu thuẫn, ưu tiên tài liệu theo thứ tự dưới đây.

## Nguồn yêu cầu

1. `SRS POSE.docx`: đặc tả yêu cầu CRM đa ngành.
2. [Business Rules v1](analysis/business-rules-v1.md): luồng và quy tắc nghiệp vụ.
3. [RBAC Matrix v1](analysis/rbac-matrix-v1.md): role, permission và data scope.
4. [ERD v1](design/erd-v1.md): entity, quan hệ và ràng buộc dữ liệu lõi.
5. [Architecture v1](api/architecture-v1.md): thành phần hệ thống, module và ranh giới tích hợp.

## Ownership tài liệu tuần 1

| Artefact | Owner chính | Vai trò hỗ trợ |
|---|---|---|
| Business Rules v1 | Nguyễn Đức Thắng | Tài review backend/RBAC, Nhi review data/import |
| RBAC Matrix v1 | Huỳnh Minh Tài | Thắng/Nhi review scope nghiệp vụ và dữ liệu |
| ERD v1 và Data Dictionary | Vân Phạm Thảo Nhi | Tài review technical/RBAC, Thắng review sales flow |
| Architecture v1 và API Contract v1 | Huỳnh Minh Tài | Thắng/Nhi review module integration |

## Tài liệu sẽ được bổ sung

- `design/`: Data Dictionary và wireframe; diagram do nhóm tạo được lưu tại đây khi đưa vào workspace.
- `api/`: API Contract v1.
- `requirements/`: acceptance criteria và test case.
- `testing/`: test plan, evidence và báo cáo test.
- `meetings/`: biên bản review/decision log khi cần.

Không tạo hoặc sử dụng tài liệu cho cart, checkout, payment, voucher, inventory hay các chức năng E-Commerce đầy đủ trong TLCN.
