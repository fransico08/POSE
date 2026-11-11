# W12 Additive Integration

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
