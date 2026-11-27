# W15 V2 data: signal decision comparison status

## Scope

Status for comparing approved AI-07 signal decisions with approved reference annotations. No canonical W12/W13 data, frozen round-2 label or database record was changed. The W15 evaluation manifest and human-adjudicated gold artifact were updated on 27/11/2026.

## Ba nhóm dataset chính thức

| Nhóm | Phạm vi | Đường dẫn authoritative | Phân tách bắt buộc |
| --- | --- | --- | --- |
| CRM Master Dataset | W12 technical và customer business data | `database/data/seed/multisector_v2` | Seed vận hành; không sinh từ artifact đánh giá. |
| Customer Conversation Dataset | W13 channel, conversation, message và metadata tenant | `database/data/seed/w13_ai05` | Giữ UUID, tenant relation, UTF-8 và checksum canonical. |
| AI Annotation & Evaluation Dataset | AI-05 annotation/adjudication/gold, AI-07 reviewed decisions, AI-10 evaluation | `database/data/newitems/w15_ai05_evaluation` | Chỉ evaluation; không thay seed W12/W13. Reference evaluator W13: `database/data/seed/w13_ai05_evaluation`. |

`manifest.json` là mapping máy đọc được của ba nhóm trên. Các artifact frozen/adjudication/gold được giữ trong nhóm AI Annotation & Evaluation; `ai07_signal_decisions.csv` chỉ xuất hiện khi có reviewed decision runtime thật.

## Frozen round-2 handoff reviewed

- Source revision: `origin/Thắng` at `e6ec2d8eaff40ce1e49bbbeccf4c55d0d919e364`.
- Handoff: `database/data/newitems/w13_ai05_round2_thang/round_2_handoff_codex_blind_2026-11-25.json`.
- Declared artifact: 197 `test` `INBOUND` messages, 60 conversations, six industries.
- Declared SHA-256: `E71C0C1882166C90C112C39D8D4BF86A22A13935C706F353F53414FE4B260FD0` for `round_2_codex_blind_2026-11-25.csv`.
- Declared result: intent kappa 0.4327, sentiment kappa 0.5337, 104 disagreements.
- Annotator is explicitly `Codex delegated by Thang (blind pass)`. This is not human inter-annotator agreement.

## Comparison result

| Check | Status | Evidence |
| --- | --- | --- |
| Frozen handoff metadata reviewed | PASS | Handoff and agreement report were read from `origin/Thắng`. |
| Byte-preserving frozen extraction | PASS | Git blobs copied without overwrite to `database/data/newitems/w15_ai05_evaluation/`; round-2 blob `8ecc0fde61bb86cc6bc4b611cb8677885b27db07`. |
| SHA-256 recomputed from frozen CSV | PASS | Actual `E71C0C1882166C90C112C39D8D4BF86A22A13935C706F353F53414FE4B260FD0`, equal to handoff. |
| 197 IDs, taxonomy, tenant and conversation mapping revalidated | PASS | Offline verifier matched exactly 197 test `INBOUND` UUIDs, 60 conversations and six sectors against canonical `database/data/seed/w13_ai05`. |
| Kappa and 104 disagreements reproduced | PASS | Intent agreement 0.563452, kappa 0.4327; sentiment agreement 0.857868, kappa 0.5337; 104 disagreement IDs/labels match frozen disagreement CSV. |
| Blank adjudication review prepared | PASS | `adjudication_review_2026-11-25.csv` has 104 rows with original prior context/text and both rounds; all final-label, approver, time and rationale fields are empty. |
| Approved AI-07 decisions compared to reference annotations | BLOCKED | `origin/Nhi` has no approved decision-export dataset keyed by `(organization_id, message_id)`. |
| Human adjudication | PASS | Nhi approved exactly 104/104 disagreement decisions at `2026-11-27T16:32:58+07:00`; `adjudication.csv` passed exact-ID, taxonomy, reviewer, timestamp and rationale validation. |
| Human-adjudicated gold | PASS | Controlled W15 route generated exactly 197 rows: 104 `HUMAN_ADJUDICATED` and 93 `ROUND_1_AND_2_AGREE`; SHA-256 `A6465783CDC2C78790CB20426DD22F2CD0B1C5B80C84F1B3555F7068283A4D4D`. |
| Standard kappa route | BLOCKED | Intent kappa remains `0.4327` and sentiment kappa `0.5337`, both below `0.60`; the W13 evaluator and its standard gate were not changed. |
| F1 / approved AI-07 decisions compared to gold | NOT RUN | No approved AI-07 decision export exists for the same 197 canonical keys. |

## V2-05 runtime-export evidence ngày 27/11/2026

- Backend runtime source: `78e4fe48e7a3f84e56cf608ab318796025051adc`; build bằng JDK `21.0.13.1+2` và Maven `3.10.0`: **PASS** (`workflow-core`, `pose-backend`, tests bị Maven bỏ qua theo `-DskipTests`). JAR boot có `Build-Jdk-Spec: 21`, `Start-Class: com.pose.backend.PoseBackendApplication`, SHA-256 `BF6F0C94AB65C6CFCA84FA11CD58235AFE89FCCE701FFF99E07C8C45261DDF65`.
- Static runtime safety: **PASS**. Cấu hình mặc định và procedure khởi động bắt buộc đặt `POSE_AI_SIGNAL_AUTO_EXTRACT=false`, `POSE_WORKFLOW_WORKER_ENABLED=false`, `POSE_CHANNEL_TELEGRAM_SMOKE_ENABLED=false`, `SPRING_FLYWAY_ENABLED=false`; `SignalAutoExtractJob` chỉ chạy khi đồng thời `pose.ai.enabled` và `pose.ai.signal.auto-extract` là true.
- Target V15 được Nhi xác nhận thủ công: `127.0.0.1:54331`, `pose_w15_v14_v15_rehearsal`, `pose_w15_rehearsal_admin`; `customer_signals=0`, `customer_signal_extractions=0`, `ai_invocations=0`. Đây là bằng chứng xác nhận thủ công, chưa phải export độc lập của script.
- Subject-isolation preflight: **PASS**. Manual execution trên target `pose_w15_v14_v15_rehearsal` với user `pose_w15_rehearsal_admin`, `127.0.0.1:54331`, PostgreSQL `180004` trả `canonical=197`, `valid=197`, `pending=197`, `out_of_scope_pending=0`, `extractions=0`, `signals=0`, `invocations=0`. Script SHA-256 `564E746A46344D60171987E146BCA10A61CD9BC15B92F6D8A1A378FF5021BB2D`; guard `data_directory` chuẩn hóa `\\` thành `/` bằng `replace(..., chr(92), '/')`, UTF-8 BOM được giữ. Đây là preflight cô lập subject, không phải smoke/extraction/review/export.
- MockProvider smoke: **NOT RUN**. Không chạy trước khi preflight xác nhận subject cô lập; endpoint hoạt động theo subject và có thể ghi trạng thái idempotency lên message canonical. MockProvider chỉ kiểm thử chức năng, không phải đánh giá chất lượng model.
- Real-provider extraction, review độc lập, raw-prediction snapshot và `ai07_signal_decisions.csv`: **BLOCKED**. Chưa có ủy quyền provider/key an toàn, preflight subject cô lập, reviewer độc lập hoặc raw prediction. Không dùng gold để dựng prediction và không tính F1.

## Required next review input

1. Produce the approved AI-07 reviewed-decision export for the same 197 canonical keys. Its minimum evaluator contract is one JSON `case` per key with `organizationId`, `messageId`, `reference[]`, and `candidates[]`; each candidate must provide `signalType` (`INTENT` or `SENTIMENT`), taxonomy `value`, `source` (`AI` or `HUMAN`) and `status` (`PROPOSED`, `ACCEPTED`, `EDITED` or `REJECTED`). Retain only `ACCEPTED`, `EDITED` or `HUMAN` decisions for metrics.
2. Run `database/scripts/evaluate_w15_ai05_round2.ps1` only after export coverage and approval pass. The standard kappa route remains closed; the controlled human-adjudicated gold route does not change its metrics or threshold.

## Phân tích adjudication ngày 25/11/2026

- Input đã phân tích: `database/data/newitems/w15_ai05_evaluation/adjudication_review_2026-11-25.csv` (104/104 khóa `(organization_id, message_id)` duy nhất).
- Bảng review không thay nhãn gốc: `database/data/newitems/w15_ai05_evaluation/adjudication_analysis_2026-11-25.csv`. Hai nhãn vòng 1/vòng 2, message và context trước được giữ nguyên. Quyết định người được lưu riêng trong `annotations_master.csv` và `adjudication.csv`; frozen review/analysis vẫn không bị sửa.
- Phân loại disagreement: `INTENT_ONLY` 76, `SENTIMENT_ONLY` 18, `BOTH` 10. Theo ngành: CLI 20, EDU 11, LOG 19, REA 21, RET 13, TECH 20.
- Cặp intent nhiều nhất: `ASK_INFO -> OTHER` 30; `ASK_INFO -> PURCHASE_READY` 7; `SUPPORT -> OTHER` 7; `ASK_INFO -> ASK_PRICE` 6; `SUPPORT -> ASK_INFO` 6. Cặp sentiment nhiều nhất: `NEUTRAL -> NEGATIVE` 23; `NEUTRAL -> POSITIVE` 2; `POSITIVE -> NEUTRAL` 2; `NEGATIVE -> NEUTRAL` 1.
- Ranh giới chính cần người review: câu bổ sung bối cảnh so với câu hỏi ngầm (`ASK_INFO`/`OTHER`), ý định mua có điều kiện so với chỉ tìm hiểu, và lo ngại thực sự so với câu hỏi trung tính (`NEUTRAL`/`NEGATIVE`). Một số tin có nhiều ý hoặc chỉ có nghĩa sau context trước nên không được ép nhãn.
- 104 khuyến nghị final đã được Nhi phê duyệt. Controlled route xác minh 1.002 canonical annotation, 805 dev, 197 test, 93 agreement, 104 disagreement, exact `(organization_id, message_id)`, source checksum và no-leakage trước khi tạo gold.

## Human-adjudicated gold finalization ngày 27/11/2026

- Method: `Human-adjudicated gold with AI-assisted human-review provenance`, theo ủy quyền trực tiếp của Nhi với tư cách Data workstream owner; không tuyên bố đây là DEC-39 chính thức.
- Inputs: `annotations_master.csv`, `adjudication.csv`, canonical round 1 và frozen round 2. Không dùng dữ liệu dự đoán AI-07 để tạo gold.
- Regression: PASS 10 nhóm gồm valid complete adjudication; missing approval; invalid taxonomy; duplicate/unexpected key; bad checksum; dev/test leakage; unauthorized finalization; overwrite protection; standard kappa preservation; deterministic gold.
- Gold: `gold_test_annotations.csv` có đúng 197 dòng, 197 `message_id` duy nhất, 104 dòng nguồn `HUMAN_ADJUDICATED` và 93 dòng `ROUND_1_AND_2_AGREE`.
- Limitation: 93 agreement cases không được con người adjudicate độc lập; kappa frozen không đạt threshold, vì vậy standard evaluator route vẫn BLOCKED. F1 không được tính trong bước này.

## Kế hoạch export AI-07 chỉ đọc

Chưa chạy truy vấn cơ sở dữ liệu. Khi có gold được con người duyệt, export chỉ đọc phải bắt đầu từ chính 197 khóa `(organization_id, message_id)` của frozen round-2, không lấy danh sách tin từ `customer_signals`.

1. Dùng danh sách 197 khóa làm bảng điều khiển; join `conversation_messages` và `customer_signals` theo cả `organization_id` và `message_id`. Chỉ nhận tin `INBOUND` và từ chối khóa thiếu/thừa hoặc trùng.
2. Export provenance CSV gồm tối thiểu `organization_id`, `message_id`, `signal_id`, `signal_type`, `value`, `source`, `status`, `invocation_id`, `reviewed_by_user_id`, `reviewed_at`. Các cột này có cơ sở từ `backend/src/main/resources/db/migration/V15__customer_signals.sql`; không export nội dung tin ngoài artifact review đã duyệt.
3. Tạo JSON evaluator chỉ sau bước đối chiếu coverage: đúng 197 `cases`, mỗi case có `organizationId`, `messageId`, `reference` từ gold đã duyệt và `candidates` từ export. Retain metric chỉ `source=HUMAN` hoặc `status` `ACCEPTED`/`EDITED`, đúng `database/scripts/evaluate_w15_ai05_round2.ps1` và contract `docs/api/customer-signals-api-v1.md`.
4. Xác minh riêng mỗi message tối đa một candidate AI cho từng `signal_type`; record `PROPOSED`/`REJECTED` chỉ giữ cho audit, không đưa vào metric. Không chạy F1 cho đến khi gold được phê duyệt.

## Hoàn tất Data V2-05 đến V2-07 ngày 27/11/2026

| Hạng mục | Trạng thái | Bằng chứng |
| --- | --- | --- |
| Contract export AI-07 | PASS | `customer_signals`, `customer_signal_extractions`, `ai_invocations` đã được đối chiếu với V15; export giữ khóa `(organization_id, message_id)`, identity signal, provenance invocation/provider/model và thông tin review. `PROPOSED`, `REJECTED`, thiếu và conflict chỉ dùng cho coverage. |
| Eligibility / tenant / UTF-8 | PASS (contract) | Metric chỉ nhận `source=HUMAN` hoặc `status=ACCEPTED`/`EDITED`; join bắt buộc theo tenant và message; CSV/JSON UTF-8. Contract không thay thế việc kiểm dữ liệu runtime. |
| Evaluator fixture | PASS | `database/scripts/test_evaluate_w15_ai05_round2.ps1`: metric theo từng message, deterministic và reject taxonomy, HUMAN-status, duplicate-key, JSON-array không hợp lệ. Đây chỉ là fixture synthetic. |
| `evaluation_results.json` | PASS | Artifact có trạng thái `NOT_RUN_NO_ACTUAL_ELIGIBLE_REVIEWED_DECISIONS`, gold SHA-256 và điều kiện chạy; không ghi F1 giả. |
| AI-07 actual export / comparison | NOT RUN | Target isolated có `0` signal, `0` extraction, `0` invocation. Không có reviewed decision thật cho 197 keys nên không tạo `ai07_signal_decisions.csv`, không tính F1/coverage/thống kê ngành. |
| AI-10 handoff | READY (contract only) | Gold 197 rows, evaluator contract, manifest và kết quả NOT RUN đã cố định; AI-10 chỉ được chạy sau một export actual có review độc lập. |

Phụ thuộc ngoài Data: người vận hành AI-07 phải tạo export read-only cho đúng 197 khóa sau extraction và review độc lập. Export không được dùng gold làm prediction, phải bảo toàn prediction raw trước `EDITED`, và phải giữ record rejected/missing/conflict để báo coverage. MockProvider/team integration test chỉ chứng minh chức năng và RBAC, không phải chất lượng Claude. Kappa frozen giữ nguyên `0.4327`/`0.5337`; standard route vẫn BLOCKED.
