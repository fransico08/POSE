# Review Fixes — Nhi / synchronized v2

## Phần 1 — 7 lỗi dữ liệu

| Mã | Trạng thái v2 | Cách sửa/kiểm |
|---|---|---|
| L01 | FIXED | Lead `CONVERTED` trỏ Customer match normalized email/phone, có `converted_at`; non-converted không có conversion link. |
| L02 | FIXED | Mỗi Import Row `VALID` trong Job `COMMITTED` có `committed_entity_type/id` tới CRM entity. |
| L03 | FIXED | Interaction theo Customer nằm trong assignment interval; Interaction theo Lead do owner hợp lệ và có Sales Assignment. |
| L04 | FIXED | Interaction/Feedback/Support không xảy ra trước ngày tạo subject; final-state converted Lead không giữ activity/source link theo lựa chọn hiện tại. |
| L05 | FIXED | `users.csv` không chứa password hash; `credentials.csv` chứa BCrypt riêng từng user. |
| L06 | FIXED | Có multi-role, REVOKED + EXPIRED assignment, failed+retry import, audit role/assignment/owner/stage/reopen/import. |
| L07 | FIXED | Validator lưu set/list role theo user, không ghi đè; kiểm L01–L04 và các baseline rule. |

## Phần 2 — D01 đến D10

| Mã | Trạng thái v2 | Mapping |
|---|---|---|
| D01 | FIXED | Role code `ADMIN`, `MANAGER`, `SALES_REP`, `CUSTOMER_CARE`, `DATA_STAFF`; mỗi org có Admin. |
| D02 | FIXED | `roles`, `permissions`, `role_permissions`, `user_role_grants`; seed 28 permission codes hiện hành. |
| D03 | FIXED | `users.organization_id`; unique `(organization_id, normalized_email)`. |
| D04 | FIXED | Team có `code` unique/org và `manager_user_id`; validator kiểm Manager active cùng org. |
| D05 | FIXED | Interaction + Feedback có `visibility`; seed có CARE_VISIBLE và SALES_ONLY. |
| D06 | FIXED | Opportunity stages theo baseline, WON/LOST closing rule, seed audit reopen. |
| D07 | FIXED | Import workflow `RECEIVED/VALIDATING/REVIEW_REQUIRED/COMMITTED/FAILED`; approve một bước bởi Manager/Admin. |
| D08 | FIXED | Outbox, Workflow Execution, Offering Attribute Definition/Value và historical Offering Price. |
| D09 | FIXED | Opportunity đúng một Offering; Customer XOR Lead; bỏ `opportunity_lines`. |
| D10 | FIXED | Handover `SALES`/`CARE`, `target_user_id`, target eligibility, seed có cả CARE và SALES. |

## Test coverage trong seed v2

- 6 Organization / 6 ngành.
- 2 User có multi-role.
- Sales Assignment: 37 ACTIVE, 6 REVOKED, 6 EXPIRED.
- Import Job: 6 COMMITTED, 6 REVIEW_REQUIRED, 1 FAILED có retry data.
- Interaction visibility: có cả CARE_VISIBLE và SALES_ONLY.
- Handover: CARE APPROVED/PENDING/REJECTED; SALES PENDING/REJECTED/APPROVED.
- Audit chứa `USER_ROLES_CHANGED`, `SALES_ASSIGNMENT_REVOKED`, `CUSTOMER_OWNER_CHANGED`, `OPPORTUNITY_REOPENED`, `IMPORT_APPROVED`, `IMPORT_RETRY`, `HANDOVER_APPROVED`.

## Shipping gate

Chỉ đánh dấu Task 5 Done sau khi chạy trên PostgreSQL local và nhận cả hai dòng:

```text
TASK 5 PASS - synchronized migration/seed verification passed.
TASK 5 COMPLETE - synchronized review-ready baseline passed.
```
