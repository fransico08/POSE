# W09 Dashboard UI Data QA — Task 2 evidence

**Run date:** 07/11/2026 (Asia/Ho_Chi_Minh)  
**Scope:** Task 2 only. Task 1 API ↔ SQL reconciliation was not rerun.

## Result: PASS for executed Dashboard coverage

| Check | Result | Evidence type |
|---|---|---|
| KPI card values and API-backed September baseline | PASS | Playwright Admin case |
| A02/A06/B03/D01 chart bars, axis and table totals | PASS | Playwright Admin case |
| Custom date range, URL persistence and invalid-date feedback | PASS | Playwright Admin case; component tests |
| Owner filter and workflow owner-filter restriction | PASS | Playwright Admin case; component tests |
| Customer Care task-only / not-applicable state | PASS | Playwright Care case |
| Data Staff aggregate Dashboard and 16-KPI response shape | PASS | Playwright Data case |
| Sales hidden owner filter and mobile no-overflow | PASS | Playwright Sales case |
| Percentage, duration, zero/null/no-data and error/loading states | PASS | Dashboard Vitest component suite |
| Workflow-type filter selection | PASS | Guarded Playwright browser assertion |
| Cross-tenant UI isolation | PASS | Guarded Playwright browser assertion with EDU and REA sessions |
| Manual interactive browser review | NOT_TESTED | No manual browser session; Playwright is automated browser coverage |

## Actual execution

- Dashboard Vitest previously passed: **1 file, 11 tests, 0 failures**.
- Applied approved test-runner-only changes: removed unused 8080 from preflight and added optional `-KeepFailureLogs`; no application source was changed.
- Repackaged the backend through Maven package lifecycle with tests skipped. Verified executable Spring Boot manifest (`JarLauncher`, `PoseBackendApplication`, `BOOT-INF`).
- Guarded run:

  ```text
  scripts/e2e-env.ps1 -JdkDirectory <OpenJDK 25.0.4.1> -MavenPath <Maven 3.10.0> -TestFile e2e/dashboard.spec.ts -KeepFailureLogs
  ```

  Final result: **Playwright 4/4 Dashboard tests passed**. `frontend/test-results/.last-run.json` recorded `status: "passed"` with no failed tests.

- The run used the guarded isolated cluster at `127.0.0.1:54329`; after cleanup, 8081, 3010, 3011 and 54329 were not listening. Supabase, 5432 and 54330 were not accessed.

## Additional guarded browser coverage

- The approved `frontend/e2e/w09-dashboard-additional.spec.ts` ran through the same isolated runner: **2/2 passed**.
- Workflow filter: selected a real UI workflow option, observed the Dashboard request, and asserted the API response echoed the selected `workflowType` while the select retained that value.
- Tenant isolation: opened authenticated EDU Admin and REA Manager browser contexts, asserted different response `organizationId` values, and asserted each visible A01 card matched its own tenant response.

No application code, migrations, seeds, KPI SQL or Git state was changed.
