# W15 V1 final acceptance checklist

| Criterion | Status | Required evidence |
| --- | --- | --- |
| Canonical V14-to-V15 rehearsal | NOT RUN | Run verifier on disposable DB from `56b7ba2` or its approved successor. |
| V15 tenant constraints | NOT RUN | Runtime schema verifier on the same revision. |
| V15 migration boot on isolated H2 | PASS | On `56b7ba2`, the focused Maven suite applied Flyway V1-V15 successfully in H2 test databases. This is not a populated PostgreSQL rehearsal. |
| Backend AI-07 MockProvider suite | PASS | On `56b7ba2`, `CustomerSignalIntegrationTest`, `CustomerHumanSignalIntegrationTest`, and `W15ConcurrencyVerificationTest`: 30 tests, 0 failures, 0 errors, 0 skipped. |
| Frontend customer signals suite | NOT RUN | Existing dependencies required; do not install without approval. |
| Concurrent extract provider idempotency | FAIL | Current committed test demonstrates two calls. |
| Claim lease patch | BLOCKED | Tài + schema owner approval for post-V15 migration and implementation. |
| Review/audit race | PASS | The focused suite executed `W15ConcurrencyVerificationTest`; concurrent accept/edit produced the asserted 200 + 409 outcome and one terminal audit. |
| Approved signal filter in workflow/KPI | NOT VERIFIED | No v0 consumer exists. |
| AI-05 round-2 evaluation | BLOCKED | Waiting for labels from Thắng. Evaluator is prepared but has no real input/result. |
| Canonical taxonomy | PASS by contract review | `INTENT` seven values; `SENTIMENT` three values. |

## Rerun when labels arrive

1. Copy the approved final artifacts into the publish worktree without overwrite.
2. Supply JSON with `schemaVersion=1`, `organizationId`, `messageId`, `reference[]`, `candidates[]` per fixture.
3. Run evaluator and retain its JSON report outside tracked source.
4. After Tài's claim patch is integrated, run its MockProvider concurrency suite, then backend/frontend and V15 rehearsal on the same verified HEAD.
5. Mark overall PASS only when every mandatory row is runtime PASS.
