# W09 Dashboard KPI API <-> SQL reconciliation — Task 1 evidence

**Run date:** 07/11/2026 (Asia/Ho_Chi_Minh)
**Scope:** Task 1 only. Dashboard UI testing was not started.

## Outcome: PASS

The existing guarded PostgreSQL runner was executed with the verified local data package. Its `RestoredDashboardSmokeTest` completed successfully for all six organizations (`cli`, `edu`, `log`, `rea`, `ret`, `tech`). The JUnit report records **1 test, 0 failures, 0 errors, 0 skipped** for `apiMatchesAdjustedW0901ForAllSixOrganizations`.

This is an executed API-to-independent-SQL reconciliation, not a result copied from prior documentation. The test calls `GET /api/v1/dashboard/kpis` and reconciles each metric against SQL queries on the dedicated restored database.

| KPI metric | Status | Executed coverage |
|---|---|---|
| A01 | PASS | All six organizations, API value vs SQL. |
| A02 | PASS | All six organizations, API buckets vs SQL. |
| A03 | PASS | All six organizations, API value vs SQL. |
| A04 | PASS | All six organizations, API value vs SQL. |
| A05 | PASS | All six organizations, API response buckets/average vs SQL. |
| A06 | PASS | All six organizations, API engagement segments vs SQL. |
| B01 | PASS | All six organizations, API value vs SQL. |
| B02 | PASS | All six organizations, API value vs SQL. |
| B03 | PASS | All six organizations, API buckets vs SQL. |
| B04 | PASS | All six organizations, API buckets vs SQL. |
| B05 | PASS | All six organizations, API value/zero-denominator behavior vs SQL. |
| C01 | PASS | All six organizations, API workflow buckets vs SQL. |
| C02 | PASS | All six organizations, API failed/retry buckets vs SQL. |
| C03 | PASS | All six organizations, API workflow buckets vs SQL. |
| D01 | PASS | All six organizations, API channel buckets vs SQL. |
| D02 | PASS | All six organizations, API user buckets vs SQL. |

## Actual execution evidence

- Test runner: `backend/scripts/test-postgres-v2.ps1`.
- Data package: `E:\POSE\Lab\POSE_CRM_DATA_V2_FINAL`.
- Backup SHA-256 actually verified before execution: `37994087124A2E38707B3C29853D58B9C0A0BCED959F7DC1875FD921340E48A6`.
- Java available to the runtime: OpenJDK 21.0.8 at `C:\Program Files\Android\openjdk\jdk-21.0.8`; Maven 3.10.0 at `C:\apache-maven-3.10.0\bin\mvn.cmd`.
- Runner verification output: `TASK 5 PASS - synchronized migration/seed verification passed.`
- Runner verification output: `Foundation DB v2 assertions passed`.
- Guard confirmation while the runner-owned server was running: `SHOW data_directory` returned `E:/POSE/Lab/POSE_Tai_W09_Test/backend/target/auth-postgres-test` on `127.0.0.1:54329`.
- JUnit artifact actually generated: `backend/target/surefire-reports/TEST-com.pose.backend.auth.RestoredDashboardSmokeTest.xml`.
- JUnit summary actually read from that artifact: `tests=1`, `failures=0`, `errors=0`, `skipped=0`; test case `apiMatchesAdjustedW0901ForAllSixOrganizations` passed.
- The local `54329` listener was absent after the run. No connection to Supabase, port 5432, or the existing `54330` database was made.

## Notes

- The requested `docs/testing/W09_DATA_QA_CODEX_HANDOFF.md` is absent from this checkout. This did not prevent execution because the runner, test fixture, SQL reconciliation assertions, and verified package were present.
- A subsequent retry with VS Code's OpenJDK 25.0.4.1 was rejected by the runner because the first runner-owned isolated cluster was still detected. It did not execute another test and is not used as PASS evidence.
- No application code, migration, seed data, or shared database was modified. No Git operation was run. This report is outside the worktree.
