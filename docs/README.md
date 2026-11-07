# Tài liệu POSE CRM

Thư mục này chỉ chứa tài liệu đang dùng để triển khai POSE CRM. Khi có mâu thuẫn, ưu tiên tài liệu theo thứ tự dưới đây.

## Nguồn yêu cầu

1. [SRS 2.0 — POSE CRM.AI](SRS-POSE-v2-CRM-AI.md): bài toán rò rỉ giá trị khách hàng, phạm vi MoSCoW, yêu cầu và kế hoạch từ 07/11/2026 ([DEC-18](meetings/decision-log.md#dec-18--chuyển-hướng-phạm-vi-và-tầm-nhìn-crmai)). `SRS POSE.docx` (1.0, 11/09/2026) chỉ còn giá trị lịch sử.
2. [Kiến trúc CRM.AI v1](api/architecture-crm-ai-v1.md): tầng kênh, AI Gateway, Copilot, Signal, quyền, dữ liệu và KPI nhóm E.
3. [Business Rules v1](analysis/business-rules-v1.md): luồng và quy tắc nghiệp vụ.
4. [Roles and Authorization v1](analysis/roles-authorization-v1.md): role, permission và data scope.
5. [Domain Class Diagram v1](design/domain-class-diagram-v1.md) ([bản draw.io](design/domain-class-diagram-v1.drawio)): lớp đối tượng, quan hệ nghiệp vụ và ranh giới Backend.
6. [Architecture v1](api/architecture-v1.md): thành phần hệ thống, module và ranh giới tích hợp.
7. [API Contract Foundation v1](api/api-contract-foundation-v1.md): Auth, RBAC, Organization và Team.
8. [Offering API v1](api/offering-api-v1.md): CRUD, trạng thái, giá, team scope và lỗi UC-03.
9. [Sales Assignment API v1](api/sales-assignment-api-v1.md): contract UC-04, team scope, hiệu lực và audit.
10. [Opportunity Pipeline API v1](api/opportunity-api-v1.md): contract W05 cho pipeline, nhiều dòng Offering, trạng thái, scope và audit.
11. [Wireframe Sales/Manager v1](design/ui-ux/sales-manager-wireframes-v1.md): navigation, wireframe UC-03 chi tiết và flow UC-01–14/21.
12. [Acceptance Criteria và Test Case: Manager phân công Offering cho Sales](testing/acceptance-criteria-sales-offering-first-flow-v1.md): tiêu chí, ca kiểm thử và phụ thuộc của luồng đầu tiên.

## Ownership tài liệu tuần 1

| Artefact | Owner chính | Vai trò hỗ trợ |
|---|---|---|
| Business Rules v1 | Nguyễn Đức Thắng | Tài review backend/RBAC, Nhi review data/import |
| Roles and Authorization v1 | Huỳnh Minh Tài | Thắng/Nhi review scope nghiệp vụ và dữ liệu |
| Domain Class Diagram v1 | Huỳnh Minh Tài | Thắng review sales flow (gồm trang 06 Support/Handover), Nhi review data mapping |
| Data Dictionary v1 | Văn Phạm Thảo Nhi | Tài review persistence/RBAC, Thắng review sales flow |
| Architecture v1 và API Contract Foundation v1 | Huỳnh Minh Tài | Thắng/Nhi review module integration |

## Trạng thái review hiện tại

- Roles and Authorization v1 và API Contract Foundation v1 đã được đồng bộ về multi-role, self-privilege escalation, scope, audit và các rule kế thừa cho CRM API; đang chờ Thắng/Nhi review.
- Domain Class Diagram v1 là bản thiết kế hiện có; người dùng đang cập nhật diagram theo UC-03 mới. UC-03 yêu cầu Category bắt buộc; UC-12 Word mới có nhiều OpportunityLine. Xem [business rules addendum](analysis/business-rules-v1.md#11-addendum-theo-word-cập-nhật-ngày-01112026) và [DEC-09](meetings/decision-log.md#dec-09--cập-nhật-uc-03-offering-và-uc-12-opportunity).
- Quyết định phạm vi ngày 26/09/2026 (Opportunity một Offering, duyệt import một bước, Support request và Handover thuộc TLCN) ghi tại [Decision Log](meetings/decision-log.md). DEC-09 và UC-12 cập nhật sau đó supersede giới hạn một Offering; backlog W05 hiện dùng nhiều OpportunityLine. Domain Class Diagram sẽ được đồng bộ riêng.
- Ngày 05/10/2026, nhánh `Tài` có đủ API foundation và mapping DB v2 được kiểm thử trên schema production/restored demo. [Backend README](../backend/README.md) ghi cách migrate/test; [API Contract Foundation](api/api-contract-foundation-v1.md) ghi payload, quyền và lỗi thực tế. Kiểm thử foundation không thay thế nghiệm thu CRM/Import/Dashboard của những card sau.

## Tài liệu sẽ được bổ sung

- `design/`: Domain Class Diagram, Data Dictionary và wireframe; diagram do nhóm tạo được lưu tại đây khi đưa vào workspace.
- `api/`: API Contract foundation và module contracts.
- `requirements/`: acceptance criteria và test case.
- `testing/`: test plan, evidence và báo cáo test.
- `meetings/`: biên bản review/decision log khi cần.

Không tạo hoặc sử dụng tài liệu cho cart, checkout, payment, voucher, inventory hay các chức năng E-Commerce đầy đủ trong TLCN.
