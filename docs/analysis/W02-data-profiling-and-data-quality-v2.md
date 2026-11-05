# W02 - Data Profiling & Data Quality v2

## 1. Scope

This report profiles the synchronized `multisector_v2` seed used by POSE CRM Dataset v2. It is aligned with the 36-table PostgreSQL schema and the final review-ready Task 5 baseline.

- Demo as-of date: **2026-10-11**
- Physical tables: **36**
- Total seed rows across the 36 tables: **2,143**
- Verification artifact: `expected_kpis.csv` (6 organization rows; not a physical table)
- Industries: Education, Real Estate, SaaS/Software, Retail/F&B Chain, Logistics/Transportation, Healthcare/Clinic

## 2. Volume profile

| Entity | Rows |
|---|---:|
| Organizations | 6 |
| Users | 36 |
| Customers | 120 |
| Leads | 60 |
| Opportunities | 72 |
| Interactions | 156 |
| Feedbacks | 48 |
| CRM Tasks | 84 |
| Offerings | 26 |
| Handover Requests | 21 |
| Support Requests | 36 |
| Audit Logs | 63 |
| Duplicate Review Cases | 12 |
| Import Rows | 51 |

## 3. Profiling findings

### Identity and duplicates

- Customers missing both normalized email and phone: **0**
- Leads missing both normalized email and phone: **0**
- Duplicate customer normalized-email groups within organization: **0**
- Duplicate customer normalized-phone groups within organization: **0**
- Duplicate lead normalized-email groups within organization: **0**
- Duplicate lead normalized-phone groups within organization: **0**

### Opportunity integrity

- Customer-subject Opportunities: **48**
- Lead-subject Opportunities: **24**
- Customer/Lead XOR violations: **0**
- All 72 Opportunities reference one Offering.

### Task chronology and KPI realism

- Task chronology violations: **0**
- OPEN/IN_PROGRESS tasks: **48**
- Overdue as of 2026-10-11: **16**
- Due today: **8**
- Future due date: **24**

The final demo therefore avoids the previous 100% overdue KPI problem.

### User and Offering state coverage

- Users: **35 ACTIVE, 1 INACTIVE**
- Multi-role users: **2**
- Offerings: **24 ACTIVE, 1 DRAFT, 1 INACTIVE**
- Sales Assignments: **37 ACTIVE, 6 EXPIRED, 6 REVOKED**

### Handover coverage

- Total handovers: **21**
- SALES handovers: **13**
- CARE handovers: **8**
- CARE status coverage: **6 APPROVED, 1 PENDING, 1 REJECTED**

### Import quality and approval audit

- Import jobs: **6 COMMITTED, 6 REVIEW_REQUIRED, 1 FAILED**
- Import decisions: **6**
- Canonical `IMPORT_APPROVED` audit events: **6**
- Import approval count mismatches: **0**
- Import row SHA256 fingerprint mismatches: **0**
- INVALID email rows that still contain a valid email: **0**

### Audit / scope / timeline checks

- Cross-organization audit actors: **0**
- Lead feedback owner/scope mismatches: **0**
- Opportunity audit-after-updated_at violations: **0**
- INACTIVE Customer metadata still marked Active: **0**

## 4. Duplicate review and import approval rules

The final W02 rule catalog explicitly includes both reviewer-requested areas:

- `DQ-15` / `DQ-16`: structural and review-state checks for `duplicate_review_cases`.
- `DQ-12` / `DQ-13`: import decision integrity and one canonical `IMPORT_APPROVED` audit per decision.

See `docs/reference/data-quality-rules-v2.csv` for the complete rule catalog.

## 5. Quality gates used for the final package

The final package is accepted only when all of the following pass:

```powershell
python database/scripts/validate_seed_files.py
python database/scripts/check_contract_static.py
python database/scripts/validate_deep_rules.py
psql "$env:POSE_DATABASE_URL" -v ON_ERROR_STOP=1 -f "verify_v2.sql"
```

Final observed status: **PASS with zero CSV/static/deep validation errors, and Task 5 database verification PASS.**

## 6. Reproducibility

Run the profiling script:

```powershell
python database/scripts/w02_profile_seed_v2.py
```

The script reports current row counts and the core W02 quality metrics directly from the canonical seed folder.

## 7. Conclusion

The synchronized v2 seed is suitable for demo/review use. The previously identified timeline, audit, import, scope and coverage defects have been corrected in the source CSVs, not only in a local database. The backup, migration, seed and validation artifacts are aligned to the same v2 release.
