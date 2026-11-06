# POSE CRM — Traceability: current baseline → Data Dictionary → Migration/Seed

## Decision Log

| Decision | Tác động vật lý |
|---|---|
| DEC-01 — Opportunity đúng một Offering, Customer hoặc Lead | `opportunities.offering_id NOT NULL`; `customer_id XOR lead_id`; không có `opportunity_lines`. |
| DEC-02 — Data Staff validate, Manager/Admin approve một bước trước commit | `import_jobs` có `REVIEW_REQUIRED`, `approved_by_user_id`, `approved_at`, `committed_at`; bảng `import_decisions`. |
| DEC-03 — Support/Handover thuộc TLCN | `support_requests`, `support_responses`, `support_attachments`, `handover_requests`. |
| DEC-04 — Admin/RBAC, visibility, User.organization, outbox/workflow, Offering attributes | `roles`, `permissions`, `role_permissions`, `user_role_grants`; `users.organization_id`; `visibility`; `outbox_events`, `workflow_executions`; attribute definition/value. |
| DEC-05 — Handover SALES/CARE | `handover_type`, `target_user_id`; validator kiểm target role/assignment; `customer_assignments` ghi scope CARE/SALES. |

## Business Rules → database/test

| Rule | Mapping / test |
|---|---|
| Organization scope | `organization_id` trên business records + validator cross-org + backend vẫn là enforcement cuối. |
| Multi-role | `user_role_grants`; seed có ít nhất 2 user multi-role. |
| Sales scope | `sales_assignments` với ACTIVE/EXPIRED/REVOKED + effective interval. |
| Lead lifecycle | `NEW/QUALIFIED/CONVERTED/DISQUALIFIED`, conversion identity check và conversion-link final state. |
| Opportunity lifecycle | `NEW/QUALIFIED/PROPOSAL/WON/LOST`; closing reason; audit `OPPORTUNITY_REOPENED`. |
| Sensitive note | `interactions.visibility` và `feedbacks.visibility` = CARE_VISIBLE/SALES_ONLY. |
| CRM Task retry | unique `(organization_id,idempotency_key)` khi key có giá trị. |
| Customer 360 | view `customer_360_summary`; verify cardinality bằng số Customer. |
| Import traceability | original file/checksum, row payload/errors, approval decision, committed entity link. |
| n8n không ghi DB | outbox/workflow tables là persistence integration; automation phải đi qua Backend/API. |
| Support | Customer + Care assignment; response history; attachment; escalation/resolution fields. |
| Handover | một PENDING/Customer; SALES target phải có assignment Offering; CARE target phải có role CUSTOMER_CARE. |

## Domain mapping không phải 1 class = 1 table

- `CrmSubject`: interface → cặp FK nullable + XOR CHECK.
- `ContactIdentity`: value object → identity columns trong Customer/Lead.
- `Customer360Summary`: read model → view.
- `AuthorizationPolicy`, `DataValidationGateway`, `ImportApplicationService`, `WorkflowCommandService`: service/control → không có table.
- `customer_assignments`: physical support table để biểu diễn lịch sử authorization/handover theo thời gian.
