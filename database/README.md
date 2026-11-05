# POSE CRM Database — synchronized baseline v2

PostgreSQL is the system of record for POSE CRM. This repository contains the
review-ready v2 migration, canonical multisector seed, validators, and Data
Dictionary.

## Contents

- `migrations/V1__pose_crm_schema.sql`: PostgreSQL 16+/17 schema with 36
  physical tables and the `customer_360_summary` read-model view.
- `data/seed/multisector_v2/`: canonical CSV seed. `expected_kpis.csv` is a
  verification artifact, not a database table.
- `scripts/`: non-destructive offline validators plus the seed and database
  verification scripts.

The seed includes only synthetic local-demo credentials stored as BCrypt
hashes. Do not use them for production access or add real credentials to this
repository.

## Safe offline validation

Run from the repository root:

```powershell
python database/scripts/validate_seed_files.py
python database/scripts/check_contract_static.py
python database/scripts/validate_deep_rules.py
python database/scripts/w02_profile_seed_v2.py
```

All four commands are read-only against the repository files.

## Database execution

`seed_v2.psql` intentionally refuses to seed a non-empty `organizations`
table. Database migration, seeding, and `verify_seed_v2.psql` must be reviewed
and run manually only against an approved disposable/local database. This
repository does not include reset, truncate, or drop scripts from the source
package.

## Review status

The v2 data package keeps several enum and domain-design choices open for team
review. See `docs/reference/OPEN_REVIEW_ITEMS.md` before treating them as a
locked schema contract.
