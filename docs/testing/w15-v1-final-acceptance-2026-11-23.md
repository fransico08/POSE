# W15 V1 final acceptance checklist

## Revision evidence

- Nhi baseline reviewed: `a924e8da372d2ecedb007ea8de7864b34b40e669`.
- DEC-37 implementation reviewed on Tài: `78e4fe48e7a3f84e56cf608ab318796025051adc`; it contains `9bb0f1e449d3739cca54778a7cbd37b3deac0ac4`.
- Previously executed Nhi evidence: `56b7ba2e273e116e98ffb9a49f4f311ef08992e5` (focused backend suite, 30 tests) and `d25c499` (historical disposable V14-to-V15 rehearsal).
- Runtime source archive: `E:\POSE\Lab\POSE-W15-RUNTIME-78e4fe4`, extracted non-Git directly from `78e4fe48e7a3f84e56cf608ab318796025051adc`. The archive marker and V15 SHA-256 `AA286913DE16F393E9590598A03417FE24DFAAD4BD5FCD8B000C86939F8BB7FE` were verified before execution.
- Manual PostgreSQL runtime evidence: executed on 27/11/2026 with the pinned runtime archive, source `127.0.0.1:54329`, and disposable PostgreSQL 18 target `127.0.0.1:54331`. The execution report recorded every listed V15 rehearsal assertion as PASS.

| Criterion | Status | Evidence / limitation |
| --- | --- | --- |
| DEC-37 single-JVM concurrency implementation | PASS (code review) | Tài revision `78e4fe4` includes the reviewed striped JVM lock and post-lock `SUCCEEDED` recheck from `9bb0f1e`. |
| DEC-37 MockProvider concurrency runtime | PASS (teammate-reported) | Tài reports the updated `W15ConcurrencyVerificationTest` passes with one provider call, one invocation, one extraction and one signal. |
| DEC-37 MockProvider concurrency runtime | NOT RUN (independent) | Nhi has not executed the test on `78e4fe4`; no independent runtime PASS is claimed. |
| Manual versus auto extraction race | PARTIAL | The reviewed test covers two concurrent manual requests. The shared processor lock is intended to cover manual plus `SignalAutoExtractJob`, but that exact mixed-trigger runtime case was not independently executed by Nhi. |
| Claim/lease across multiple backend instances | DEFERRED TO KLTN | DEC-37 approves JVM locking for the TLCN single-JVM deployment. No database claim/lease or migration is required for W15 V1. |
| Target V15 evidence preservation | PASS (manual execution) | `backend/target/w15-v15-rehearsal-backups/pose_w15_v15_pre_restore_20261125.dump` was created from the disposable target before reset. `pg_restore --list` passed again on 27/11/2026; size `9,100,549` bytes; SHA-256 `B9E10D0B6F1546E1E7767E452AD74ED1D7F905A1219D4B3BCF0B59A0E9F6E4D5`. |
| Target V15 recovery verification | PASS (manual runtime) | `TARGET_V15_RESTORE`, `TARGET_V15_RECOVERY_VERIFY`, and `RECOVERY_CLEANUP` passed on 27/11/2026. The verified V15 archive was restored into the approved temporary database, checked, then removed. |
| Canonical V14-to-V15 rehearsal | PASS (manual runtime) | On pinned revision `78e4fe48e7a3f84e56cf608ab318796025051adc`, `STATIC_GUARDS`, `SOURCE_BEFORE`, `TARGET_CLUSTER`, `RESTORE_V14`, `RESTORED_V14_VERIFICATION`, `FLYWAY_V15`, `V15_SCHEMA_AND_DATA`, `SOURCE_AFTER`, and `W15_V1_03_REHEARSAL` all passed. Flyway V15 duration: `9.215` seconds. |
| V15 tenant constraints | PASS (manual runtime) | `RESTORED_V14_VERIFICATION` checked six-tenant distribution, UUID and FK/tenant integrity; `V15_SCHEMA_AND_DATA` checked V15 constraints/indexes and unchanged protected W12/W13 fingerprints. |
| Source V14 unchanged | PASS (manual runtime) | `SOURCE_BEFORE` and `SOURCE_AFTER` passed against `127.0.0.1:54329`; no source migration, seed loader, or write operation was used. |
| V15 migration boot on isolated H2 | PASS (previous execution) | On `56b7ba2`, focused Maven tests applied Flyway V1-V15. This is not a populated PostgreSQL rehearsal. |
| Backend AI-07 MockProvider suite | PASS (previous execution) | On `56b7ba2`, 30 tests passed: `CustomerSignalIntegrationTest`, `CustomerHumanSignalIntegrationTest`, `W15ConcurrencyVerificationTest`. |
| Frontend Vitest suite | PASS (independent) | On the exact non-Git archive of `78e4fe4`, `npm ci --ignore-scripts` followed by `npm test` completed: 35 files and 402 tests passed with Vitest 5.0.4. This is frontend evidence only. |
| Review/audit concurrent consistency | PASS (previous execution) | `W15ConcurrencyVerificationTest` on `56b7ba2` asserted 200 + 409 and one terminal audit. |
| Approved signal filter in workflow/KPI | NOT VERIFIED | AI-07 v0 has no workflow/KPI consumer for `customer_signals`. |
| AI-05 frozen round-2 delivery | PASS (artifact review) | Thắng handoff declares 197 locked test-INBOUND labels and SHA-256 `E71C0C1882166C90C112C39D8D4BF86A22A13935C706F353F53414FE4B260FD0`. |
| AI-05 gold finalization | PASS (controlled Data route) | Gold gồm 197 khóa test đã được hoàn tất bằng 104 quyết định người duyệt và 93 trường hợp hai vòng đồng thuận; SHA-256 `D6E0A82F177651D8A6825517ADE8BF0A7A6767FF9AD8244148169CCE21FBE51A`. Reviewer metadata dùng `DATA_REVIEWER`; đây không thay thế standard kappa route. |
| AI-05 kappa / AI-07 decision comparison / F1 | BLOCKED / NOT RUN | Round 2 là Codex delegated blind pass, không phải inter-human agreement; intent kappa 0.4327 và sentiment kappa 0.5337 đều dưới 0.60. Chưa có export reviewed AI-07 thực tế cho 197 khóa nên không có F1. |
| Canonical taxonomy | PASS (contract review) | `INTENT`: seven values; `SENTIMENT`: three values. |

## Closeout reconciliation 28/11/2026

- Publish HEAD `2d03ce056aaee1782f8b4f5a1ca096bd11be231b` carries Data artifacts and reports. Its V15 SHA-256 is `A06ECDB0993E36EF8DAFCF98F5833D1485482C04CCA95022D7C5F2BC49807B10`; it differs from the pinned Tài runtime V15 SHA-256 `AA286913DE16F393E9590598A03417FE24DFAAD4BD5FCD8B000C86939F8BB7FE`. Therefore the 27/11 rehearsal is valid only for `78e4fe4`, not a runtime acceptance of publish HEAD.
- W12 static seed contract now validates against the backend baseline and V2-V15 migrations; it recognizes approved W12 staging copies and V10 deterministic `opportunity_lines` derivation. This is Data contract evidence, not a replacement for a PostgreSQL seed execution.
- Current actual reviewed-label comparison remains unavailable. The required dependency is a read-only AI-07 export for the same 197 `(organization_id, message_id)` keys, retaining raw provenance and applying only `HUMAN`, `ACCEPTED`, or `EDITED` candidates to metrics.

Overall W15 V1 is **PARTIAL — NOT CLOSED**. The populated PostgreSQL V14-to-V15 rehearsal and frontend suite have runtime evidence on `78e4fe4`; independent DEC-37 execution remains NOT RUN and mixed manual-versus-auto coverage remains PARTIAL. The human-adjudicated gold is PASS, but F1 and the AI-07 reviewed-decision comparison remain NOT RUN/BLOCKED without actual eligible runtime records.

## Đánh giá prediction thô AI-07 ngày 28/11/2026

- Export runtime thô `database/data/newitems/w15_ai05_evaluation/ai07_signal_predictions_raw.csv` được đối chiếu với Gold 197 dòng đã human-adjudicated: 386 signal AI duy nhất, tất cả `PROPOSED`, phủ đủ 197 khóa tenant/message canonical. Có 191 signal INTENT và 195 signal SENTIMENT; thiếu 6 cặp INTENT và 2 cặp SENTIMENT trong 394 cặp message-label kỳ vọng.
- Kết quả là đa model: `gemini-3.1-flash-lite` có 367 signal và `gemini-3.6-flash` có 19 signal. Không được diễn giải aggregate như kết quả của một model đơn.
- Accuracy full-set thô là 0.779188 (307/394), coverage 0.979695 (386/394), micro-precision 0.795337, micro-recall 0.779188 và macro-F1 0.639887. INTENT: accuracy 0.695431, macro-F1 0.631410. SENTIMENT: accuracy 0.862944, macro-F1 0.659669. Prediction thiếu được tính sai cho accuracy full-set và false negative cho recall.
- Bằng chứng extraction runtime ghi nhận đã khôi phục `RATE_LIMIT` và `TIMEOUT`; lỗi `FORBIDDEN_PERMISSION` trên luồng ủy quyền review vẫn chưa được xử lý. Export không có quyết định `ACCEPTED`, `EDITED`, `HUMAN` hoặc `REJECTED`: so sánh reviewed-decision và nghiệm thu AI-10 chính thức vẫn **PENDING / NOT RUN**.
- Artifact dẫn xuất: `ai07_gold_comparison.csv` (394 dòng, SHA-256 `BFB6730DEDFBB7F570282BBEC57AFEA0008F7BF6C822A272996CD5E04857B102`) và `ai07_raw_evaluation_results.json` (SHA-256 `CA640FB88C89ABB91BF69A001E14B851A163DE1E1EC8250B07D95A44A96371D9`). Gold, frozen annotation và các giá trị kappa gốc không đổi.
