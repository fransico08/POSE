# W15-V1-04: Final concurrency evidence

## Implemented and published evidence

`84a6a77` on `origin/Nhi` added `W15ConcurrencyVerificationTest`. It demonstrates the current race: two concurrent extracts persist one signal/extraction but invoke MockProvider and create two AI invocations. It also checks successful retry idempotency and concurrent accept/edit gives one terminal audit.

## Status

| Criterion | Status | Evidence |
| --- | --- | --- |
| Persisted duplicate protection | PASS | Executed `W15ConcurrencyVerificationTest` on `56b7ba2`; concurrent extracts leave one signal/extraction. |
| Single provider invocation for concurrent workers | FAIL | The executed test confirms two MockProvider calls on current code. |
| Retry after success | PASS | The executed test confirms one invocation after sequential retry. |
| Crash recovery / lease ownership / tenant claim isolation | NOT RUN | Requires approved V16 claim implementation. |
| Review and audit consistency | PASS | The executed test asserts concurrent accept/edit gives 200 + 409 and one terminal audit. |

No claim/lease production patch is integrated. Do not report V1-04 fixed before Tài's patch and runtime tests PASS.
