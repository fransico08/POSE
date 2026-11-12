# LEGACY — KHÔNG SỬ DỤNG CHO CANONICAL V12

Workflow additive này đã lỗi thời sau khi dữ liệu W12 được gộp và promote vào
canonical seed `database/data/seed/multisector_v2/`. Manifest
`w12_additive_manifest.json`, các loader additive và readonly verifier của nó
giả định một tập CSV gốc trước khi gộp; các giả định count, checksum, UUID và
một phần header/schema đó không còn khớp với canonical V12 hiện tại.

Không cập nhật count hoặc hash của manifest additive để mô phỏng metadata
canonical. Canonical CSV đã bao gồm dữ liệu gốc và dữ liệu W12 đã gộp; việc
làm như vậy không tái tạo được ranh giới original/additive và có thể tạo va
chạm UUID, sai số lượng hoặc nạp trùng. Không chạy loader additive đối với dữ
liệu canonical hoặc database đã được canonical seed.

Đường chính thức của team là canonical seed
`database/data/seed/multisector_v2/`, nạp bằng
`database/scripts/seed_v2.psql` trên database V12 cô lập, rỗng và đã áp dụng
migration. Kiểm tra CSV canonical bằng
`database/scripts/validate_seed_files.py`; validator này đã PASS và không cần
thay đổi trong follow-up này.

# W12 Additive Integration (Historical)

Use `w12_additive_manifest.json` as the immutable checksum and row-count contract.
The original V2 manifest and seed CSVs are not inputs to a W12 additive loader.

## Prerequisites

- Target is isolated `pose_w12_v12_test` with canonical V1-V12 applied.
- Original V2 rows and prerequisite organizations, users, and offerings exist.
- Every additive checksum and header matches the manifest.
- Each additive UUID is absent; every original UUID exists.
- Validate all direct FKs and organization-scoped relationships before staging.

## Load Order

1. `customers`
2. `leads`
3. `opportunities`
4. `handover_requests`
5. `customer_assignments`
6. `feedbacks`
7. `interactions`
8. `crm_tasks`
9. `support_requests`
10. `support_responses`

## Transaction Rules

- Stage one UTF-8 CSV with its exact V12 column list.
- Reject checksum mismatch, duplicate or existing additive UUID, absent original UUID,
  FK/tenant mismatch, or unexpected post-load count.
- Never use `ON CONFLICT`, upsert, or broad update.
- Verify original rows and additive counts before `COMMIT`; otherwise `ROLLBACK`.

## Legacy V2 Exception

Two original `handover_requests` rows do not meet the current review-lifecycle
predicate. They remain immutable and cannot permit an additive-row exception.

## Step 7C and Step 8 Evidence

- Step 7C runtime evidence: `pose_w12_v12_clean_test` completed `seed_v2.psql`
  and `verify_seed_v2.psql` with exit code `0`; verifier ended with `TASK 5 PASS`.
- Verified final counts include `opportunity_lines=8347` and
  `sales_assignments=526`.
- Step 8 promoted the verified staging package to the canonical seed directory.
  Static checks passed for 37 manifest CSV hashes, all manifest row counts,
  UUID uniqueness of the 10 W12 CRM tables, CSV headers against every canonical
  `\copy` list, and byte identity of the 13 promoted artifacts with the
  runtime-verified source.
- Final canonical totals: `w12_crm_rows=123769`, `seed_rows_total=126002`,
  `post_migration_expected.totalRows=134073`, `opportunity_lines=8347`, and
  `sales_assignments=526`.
- A new canonical runtime test remains required before commit.
