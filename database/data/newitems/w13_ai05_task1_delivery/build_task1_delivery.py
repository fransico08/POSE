#!/usr/bin/env python3
"""Build the additive W13 AI-05 Task 1 delivery package from sector pilots."""
from __future__ import annotations

import csv
import hashlib
import json
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
CANONICAL = ROOT / "database" / "data" / "seed" / "multisector_v2"
PILOT_ROOT = ROOT / "database" / "data" / "newitems"

PILOTS = (
    ("EDU", "edu", "w13_ai05_edu_pilot", "POSE Academy"),
    ("REA", "rea", "w13_ai05_rea_pilot", "POSE Realty"),
    ("CLI", "cli", "w13_ai05_cli_pilot", "POSE Clinic"),
    ("TECH", "tech", "w13_ai05_tech_pilot", "POSE Technology"),
    ("RET", "ret", "w13_ai05_ret_pilot", "POSE Retail & F&B"),
    ("LOG", "log", "w13_ai05_log_pilot", "POSE Logistics"),
)

ACCOUNT_HEADERS = [
    "id", "organization_id", "channel_type", "display_name", "external_account_id",
    "status", "secret_ref", "team_id", "default_assignee_user_id", "created_at", "updated_at",
]
CONVERSATION_HEADERS = [
    "id", "organization_id", "channel_account_id", "external_thread_id", "lead_id", "customer_id",
    "assigned_user_id", "status", "consent_status", "last_inbound_at", "last_message_at", "created_at", "updated_at",
]
MESSAGE_HEADERS = [
    "id", "organization_id", "conversation_id", "direction", "external_message_id", "content_type",
    "content", "sent_by_user_id", "delivery_status", "occurred_at", "interaction_id", "created_at",
]
ANNOTATION_HEADERS = [
    "sector", "organization_id", "conversation_id", "message_id", "intent", "sentiment", "split",
    "annotator", "annotation_round", "annotation_version", "source_annotation_id", "source_created_at", "notes",
]
CORE_FILES = (
    "channel_accounts.csv", "conversations.csv", "conversation_messages.csv",
    "round_1_annotations_consolidated.csv", "labeling-guide.vi.md", "sector_statistics.csv",
    "message-data-quality-rules.vi.md", "source_manifest.json", "README.md",
)


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def write_csv(path: Path, headers: list[str], rows: list[dict[str, str]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=headers, lineterminator="\n", extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def write_text(path: Path, value: str, encoding: str = "utf-8") -> None:
    with path.open("w", encoding=encoding, newline="\n") as handle:
        handle.write(value)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def normalized(row: dict[str, str], headers: list[str]) -> dict[str, str]:
    return {name: row.get(name, "") for name in headers}


def guide() -> str:
    return """# W13 AI-05 - Hướng dẫn gán nhãn vòng 1\n\n## Phạm vi\n\n- Chỉ gán nhãn tin nhắn `INBOUND`; mỗi bản ghi annotation phải thuộc đúng một tin nhắn inbound.\n- Nhãn được xác định theo ý nghĩa và ngữ cảnh hội thoại, không dựa vào từ khóa đơn lẻ.\n- Đây là artifact đánh giá dữ liệu, không phải `customer_signals`, không tạo Interaction, Outbox, Task hay nhãn vòng 2.\n\n## Intent\n\n| Intent | Sử dụng khi |\n|---|---|\n| `ASK_PRICE` | Hỏi giá, phí, thanh toán hoặc chi phí bao gồm. |\n| `ASK_INFO` | Hỏi thông tin về sản phẩm/dịch vụ, đặc tính hoặc cách hoạt động. |\n| `BOOKING` | Đề nghị đặt lịch, hẹn, lấy hàng, giao nhận hoặc đổi lịch. |\n| `PURCHASE_READY` | Thể hiện ý định mua/đăng ký/đặt dịch vụ có điều kiện rõ ràng. |\n| `COMPLAINT` | Phản ánh không hài lòng về dịch vụ/trải nghiệm. |\n| `SUPPORT` | Cần hỗ trợ xử lý sự cố, vấn đề sau mua/đang sử dụng. |\n| `OTHER` | Chào hỏi, cảm ơn, bổ sung ngữ cảnh hoặc nội dung không có intent rõ hơn. |\n\n## Sentiment\n\n- `POSITIVE`: có biểu hiện hài lòng, cảm ơn hoặc đồng tình rõ ràng.\n- `NEGATIVE`: có thật sự bức xúc, lo ngại, không hài lòng hay phàn nàn.\n- `NEUTRAL`: hỏi thông tin, xác nhận hoặc nội dung không biểu lộ cảm xúc rõ ràng.\n\nKhông ép phân bố nhãn. Trường hợp mơ hồ được ghi trong review của pilot nguồn và cần người review quyết định trước khi dùng cho đánh giá chất lượng.\n"""


def data_quality_rules() -> str:
    return """# W13 AI-05 - Quy tắc chất lượng dữ liệu tin nhắn

## Phạm vi

Các quy tắc này áp dụng cho sáu pilot W13 AI-05 và artifact hợp nhất. Chúng kiểm tra dữ liệu tĩnh; không xác nhận webhook, PostgreSQL, Interaction, Outbox, Task hay tín hiệu V15.

## Quy tắc bắt buộc

1. **Định danh và tenant:** UUID channel account, conversation và message là duy nhất; mọi FK giữ cùng `organization_id`. Conversation gắn đúng một Customer hoặc Lead hợp lệ của tenant.
2. **Kênh và người gửi:** account là `ZALO_SIM`, `secret_ref` chỉ là tên biến môi trường. Tin `INBOUND` không có nhân viên gửi và có `RECEIVED`; tin `OUTBOUND` do assignee gửi và có `SENT`.
3. **Thời gian:** message được sắp theo `occurred_at`; `created_at`, `last_inbound_at`, `last_message_at` của conversation phản ánh chuỗi tin. Timestamp trong cửa sổ dữ liệu W13 đã chốt.
4. **Nội dung:** dùng UTF-8 tiếng Việt; không có replacement character, credential, mật khẩu, token, email/điện thoại thật hoặc lời hứa nghiệp vụ chưa được W12/contract xác minh. Lỗi chính tả/không dấu có chủ đích được giữ nếu phản ánh cách nói tự nhiên.
5. **Đa dạng và lặp:** không có message substantive lặp nguyên văn trong cùng pilot; hội thoại phải khớp tình huống/ngành và không dùng template thay tên sản phẩm.
6. **Offering và quan hệ W12:** chỉ dùng Customer/Lead, team, assignee và ACTIVE offering đã được validator sector đối chiếu W12; không thay đổi W12.
7. **Nhãn vòng 1:** chỉ gán cho `INBOUND`, đúng một intent và một sentiment trên mỗi message; chỉ dùng 7 intent và 3 sentiment được phê duyệt; xét ngữ cảnh, không ép phân bố hoặc suy diễn bằng từ khóa.
8. **Chống leakage:** split gán theo conversation, không để một conversation ở cả `dev` và `test`; kiểm tra hash, generation xác định và source manifest trước bàn giao.

## Bằng chứng

Các validator sector kiểm tra quy tắc đặc thù ngành; `validate_task1_delivery.py` kiểm tra tổng hợp schema, FK/tenant/team, chronology, annotation coverage, split, Unicode, source hash và reproducibility.
"""


def read_source(sector_dir: Path) -> dict[str, list[dict[str, str]]]:
    return {
        "accounts": read_csv(sector_dir / "channel_accounts.csv"),
        "conversations": read_csv(sector_dir / "conversations.csv"),
        "messages": read_csv(sector_dir / "conversation_messages.csv"),
        "annotations": read_csv(sector_dir / "round_1_annotations.csv"),
    }


def source_hashes(sector_dir: Path) -> dict[str, str]:
    return {name: sha256(sector_dir / name) for name in (
        "channel_accounts.csv", "conversations.csv", "conversation_messages.csv", "round_1_annotations.csv",
    )}


def build(output: Path = HERE) -> dict[str, int]:
    accounts: list[dict[str, str]] = []
    conversations: list[dict[str, str]] = []
    messages: list[dict[str, str]] = []
    annotations: list[dict[str, str]] = []
    statistics: list[dict[str, str]] = []
    manifest_sectors: list[dict[str, object]] = []

    for code, org_code, directory, name in PILOTS:
        sector_dir = PILOT_ROOT / directory
        source = read_source(sector_dir)
        account_rows = [normalized(row, ACCOUNT_HEADERS) for row in source["accounts"]]
        conversation_rows = [normalized(row, CONVERSATION_HEADERS) for row in source["conversations"]]
        message_rows = [normalized(row, MESSAGE_HEADERS) for row in source["messages"]]
        accounts.extend(account_rows)
        conversations.extend(conversation_rows)
        messages.extend(message_rows)
        annotation_splits = {
            row.get("conversation_id", ""): row.get("split", "")
            for row in source["annotations"] if row.get("split", "") in {"dev", "test"}
        }
        conversation_splits = {
            row["id"]: annotation_splits.get(row["id"], "test" if row["external_thread_id"].endswith("-050") else "dev")
            for row in conversation_rows
        }
        labels = Counter(row.get("intent", "") for row in source["annotations"])
        sentiments = Counter(row.get("sentiment", "") for row in source["annotations"])
        for row in source["annotations"]:
            annotations.append({
                "sector": code,
                "organization_id": row.get("organization_id", ""),
                "conversation_id": row.get("conversation_id", ""),
                "message_id": row.get("message_id", ""),
                "intent": row.get("intent", ""),
                "sentiment": row.get("sentiment", ""),
                "split": row.get("split", conversation_splits.get(row.get("conversation_id", ""), "")),
                "annotator": row.get("annotator", ""),
                "annotation_round": row.get("annotation_round", "1"),
                "annotation_version": row.get("annotation_version", ""),
                "source_annotation_id": row.get("id", ""),
                "source_created_at": row.get("created_at", ""),
                "notes": row.get("notes", ""),
            })
        stat = {
            "sector": code, "organization_code": org_code, "organization_name": name,
            "organization_id": account_rows[0]["organization_id"], "channel_accounts": str(len(account_rows)),
            "conversations": str(len(conversation_rows)), "messages": str(len(message_rows)),
            "inbound_annotations": str(len(source["annotations"])),
            "dev_conversations": str(sum(value == "dev" for value in conversation_splits.values())),
            "test_conversations": str(sum(value == "test" for value in conversation_splits.values())),
        }
        for label in ("ASK_INFO", "ASK_PRICE", "BOOKING", "PURCHASE_READY", "COMPLAINT", "SUPPORT", "OTHER"):
            stat[label] = str(labels[label])
        for label in ("POSITIVE", "NEUTRAL", "NEGATIVE"):
            stat[label] = str(sentiments[label])
        statistics.append(stat)
        manifest_sectors.append({
            "sector": code, "organization_code": org_code, "source_directory": str(sector_dir.relative_to(ROOT)).replace("\\", "/"),
            "source_hashes": source_hashes(sector_dir),
            "counts": {"channel_accounts": len(account_rows), "conversations": len(conversation_rows), "messages": len(message_rows), "annotations": len(source["annotations"])},
        })

    write_csv(output / "channel_accounts.csv", ACCOUNT_HEADERS, accounts)
    write_csv(output / "conversations.csv", CONVERSATION_HEADERS, conversations)
    write_csv(output / "conversation_messages.csv", MESSAGE_HEADERS, messages)
    write_csv(output / "round_1_annotations_consolidated.csv", ANNOTATION_HEADERS, annotations)
    stats_headers = list(statistics[0])
    write_csv(output / "sector_statistics.csv", stats_headers, statistics)
    write_text(output / "labeling-guide.vi.md", guide())
    write_text(output / "message-data-quality-rules.vi.md", data_quality_rules())
    manifest = {
        "package": "W13 AI-05 Task 1 additive delivery", "version": "1.0", "canonical_seed": {
            "path": "database/data/seed/multisector_v2", "manifest_sha256": sha256(CANONICAL / "manifest.json"),
        }, "sectors": manifest_sectors,
    }
    write_text(output / "source_manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    summary = {
        "channel_accounts": len(accounts), "conversations": len(conversations),
        "messages": len(messages), "annotations": len(annotations),
    }
    readme = f"""# W13 AI-05 Task 1 - Gói bàn giao tích hợp\n\n## Trạng thái\n\n- `PASS` static build: 6 pilot được ghép bất biến theo thứ tự EDU, REA, CLI, TECH, RET, LOG.\n- `WARN` cần human review: chất lượng hội thoại là đánh giá nghiệp vụ; validation tự động không thay thế phê duyệt thủ công.\n- `WARN` chưa import PostgreSQL, chưa sửa canonical manifest/loader, chưa tạo Interaction, Outbox, Task, `customer_signals` hay second-round labels.\n\n## Deliverables\n\n| Artifact | Số lượng |\n|---|---:|\n| ZALO_SIM channel accounts | {summary['channel_accounts']} |\n| Conversations | {summary['conversations']} (240 dev / 60 test) |\n| Messages | {summary['messages']} |\n| Inbound round-1 annotations | {summary['annotations']} |\n\n`round_1_annotations_consolidated.csv` là artifact để review/tích hợp, có cột `sector` và trường provenance để chuẩn hóa khác biệt schema annotation của EDU với năm pilot còn lại. Không phải bảng canonical mới và không thay đổi contract V13-V15.\n\n## Import order đề xuất khi được phê duyệt\n\n1. Nạp W12 canonical và xác minh organization, users, teams, team_memberships, roles/permissions, Customers, Leads, Offerings/Prices.\n2. Nạp `channel_accounts.csv`.\n3. Nạp `conversations.csv`.\n4. Nạp `conversation_messages.csv`.\n5. Nạp artifact annotation vòng 1 qua pipeline AI được phê duyệt; hiện tại không seed V15 signals.\n\n## Data Dictionary audit\n\nTài liệu hiện hành tìm thấy là `docs/design/POSE-data-dictionary-v2.md`; nó tự mô tả là bản đồng bộ qua migrations V2-V12, không có tệp riêng tên `Data Dictionary V12`. Đối chiếu migration V12 xác nhận tài liệu đã có đủ `ai_invocations`, `ai_suggestions`, enum, FK và permission AI của V12. Tài liệu có `organizations`, `teams`, `users`, `team_memberships` và quy tắc tenant/FK nên phù hợp với các dependency W12 được kiểm tra. Các bảng V13 `channel_accounts`, `conversations`, `conversation_messages` và artifact annotation không thuộc phạm vi V12; contract cần đối chiếu là `docs/api/conversation-api-v1.md` và `docs/api/architecture-crm-ai-v1.md`. Đây là phân định tài liệu, không phải lý do để tự tạo cột/schema.\n\nXem `validation_evidence.json` sau khi chạy validator; `SHA256SUMS.txt` bảo vệ các artifact delivery bất biến.\n"""
    readme = readme.replace(
        "## Import order đề xuất khi được phê duyệt",
        "`message-data-quality-rules.vi.md` hợp nhất các quy tắc kiểm soát message, tenant/FK, nhãn, duplicate, split và reproducibility áp dụng cho cả sáu sector.\n\n## Import order đề xuất khi được phê duyệt",
    )
    write_text(output / "README.md", readme)
    checksums = "".join(f"{sha256(output / name)}  {name}\n" for name in CORE_FILES)
    write_text(output / "SHA256SUMS.txt", checksums, encoding="ascii")
    return summary


if __name__ == "__main__":
    result = build()
    print("built " + " ".join(f"{key}={value}" for key, value in result.items()))
