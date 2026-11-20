#!/usr/bin/env python3
"""Generate the W13 AI-05 TECH pilot from approved editorial dialogue."""
from __future__ import annotations

import argparse
import csv
import hashlib
import re
import uuid
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
CANONICAL = ROOT / "database" / "data" / "seed" / "multisector_v2"
HERE = Path(__file__).resolve().parent
SOURCE = HERE / "TECH_50_hoi_thoai_bien_tap_v4.md"
ORG = "001ba809-bda6-52d8-8645-5e4ba33533b4"
TEAM = "dce66c23-b7b0-5b39-b810-2442cce662b1"
ASSIGNEE = "6576acdd-cb6d-5f4a-9097-4e8176d9d60e"
NAMESPACE = uuid.UUID("a2e5d7c9-7c33-5bb0-8f3b-4ca0d46b9b2e")
CHANNEL_ID = str(uuid.uuid5(NAMESPACE, "channel-account:zalo-sim-tech-ai05"))
START = datetime(2026, 11, 10, 8, 30, tzinfo=timezone.utc)
ANNOTATOR = "Nhi-round-1"
VERSION = "w13-ai05-tech-pilot-v1"
OUTPUT_FILES = [
    "channel_accounts.csv",
    "conversations.csv",
    "conversation_messages.csv",
    "round_1_annotations.csv",
    "tech_human_review.txt",
    "labeling-guide.vi.md",
]


@dataclass(frozen=True)
class Turn:
    direction: str
    content: str


@dataclass(frozen=True)
class EditorialConversation:
    editorial_id: str
    split: str
    profile_type: str
    profile_id: str
    offering_code: str
    declared_price: str
    turns: tuple[Turn, ...]


def uid(kind: str, *parts: object) -> str:
    return str(uuid.uuid5(NAMESPACE, ":".join([kind, *(str(part) for part in parts)])))


def iso(value: datetime) -> str:
    return value.strftime("%Y-%m-%dT%H:%M:%SZ")


def csv_rows(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def parse_approved() -> list[EditorialConversation]:
    text = SOURCE.read_text(encoding="utf-8")
    blocks = re.findall(r"^## TECH-\d{3} .*?(?=^## TECH-\d{3} |\Z)", text, re.MULTILINE | re.DOTALL)
    result: list[EditorialConversation] = []
    for block in blocks:
        head = re.search(r"^## (TECH-\d{3}) ", block, re.MULTILINE)
        meta = re.search(
            r"- Split: \*\*(dev|test)\*\*\s*·\s*Link: `(CUSTOMER|LEAD)`\s*`([^`]+)`\s*·\s*"
            r"Offering: `([^`]+)`\s*·\s*Giá W12: \*\*(.+?)\*\*",
            block,
            re.DOTALL,
        )
        turns = tuple(
            Turn("INBOUND" if speaker == "KH" else "OUTBOUND", content.rstrip())
            for speaker, content in re.findall(r"^- \*\*(KH|NV):\*\* (.+?)\s*$", block, re.MULTILINE)
        )
        if not head or not meta or not turns or any(a.direction == b.direction for a, b in zip(turns, turns[1:])):
            raise RuntimeError(f"Invalid approved TECH block: {head.group(1) if head else 'unknown'}")
        result.append(EditorialConversation(head.group(1), *meta.groups(), turns))
    if len(result) != 50 or [item.editorial_id for item in result] != [f"TECH-{i:03d}" for i in range(1, 51)]:
        raise RuntimeError("Expected TECH-001..TECH-050 in exact order")
    if sum(len(item.turns) for item in result) != 352:
        raise RuntimeError("Expected 352 approved messages")
    return result


def active_offerings() -> dict[str, dict[str, str]]:
    return {
        row["code"]: row
        for row in csv_rows(CANONICAL / "offerings.csv")
        if row["organization_id"] == ORG and row["status"] == "ACTIVE"
    }


def current_prices() -> dict[str, dict[str, str]]:
    offerings = active_offerings()
    return {
        row["offering_id"]: row
        for row in csv_rows(CANONICAL / "offering_prices.csv")
        if row["organization_id"] == ORG
        and not row["effective_to"]
        and row["offering_id"] in {item["id"] for item in offerings.values()}
    }


def profiles() -> dict[str, dict[str, str]]:
    result: dict[str, dict[str, str]] = {}
    for filename in ("customers.csv", "leads.csv"):
        for row in csv_rows(CANONICAL / filename):
            if row["organization_id"] == ORG:
                result[row["id"]] = row
    return result


ANNOTATION_ROWS = """
TECH-001|1|SUPPORT|NEGATIVE
TECH-001|2|SUPPORT|NEUTRAL
TECH-001|3|SUPPORT|NEUTRAL
TECH-001|4|SUPPORT|NEGATIVE
TECH-002|1|ASK_INFO|NEUTRAL
TECH-002|2|ASK_INFO|NEUTRAL
TECH-002|3|ASK_INFO|NEGATIVE
TECH-002|4|SUPPORT|NEUTRAL
TECH-003|1|SUPPORT|NEGATIVE
TECH-003|2|SUPPORT|NEUTRAL
TECH-003|3|SUPPORT|NEGATIVE
TECH-003|4|OTHER|NEUTRAL
TECH-004|1|ASK_INFO|NEUTRAL
TECH-004|2|ASK_INFO|NEUTRAL
TECH-004|3|ASK_PRICE|NEUTRAL
TECH-004|4|OTHER|NEUTRAL
TECH-005|1|ASK_INFO|NEUTRAL
TECH-005|2|ASK_INFO|NEUTRAL
TECH-005|3|ASK_PRICE|NEUTRAL
TECH-005|4|SUPPORT|NEUTRAL
TECH-006|1|ASK_INFO|NEUTRAL
TECH-006|2|SUPPORT|NEUTRAL
TECH-006|3|ASK_INFO|NEUTRAL
TECH-006|4|SUPPORT|NEUTRAL
TECH-007|1|ASK_INFO|NEUTRAL
TECH-007|2|ASK_INFO|NEUTRAL
TECH-007|3|ASK_INFO|NEUTRAL
TECH-007|4|SUPPORT|NEUTRAL
TECH-008|1|ASK_INFO|NEUTRAL
TECH-008|2|ASK_INFO|NEUTRAL
TECH-008|3|ASK_PRICE|NEUTRAL
TECH-008|4|OTHER|NEUTRAL
TECH-009|1|ASK_INFO|NEUTRAL
TECH-009|2|ASK_INFO|NEUTRAL
TECH-009|3|ASK_INFO|NEUTRAL
TECH-009|4|OTHER|NEUTRAL
TECH-010|1|SUPPORT|NEGATIVE
TECH-010|2|SUPPORT|NEGATIVE
TECH-010|3|ASK_PRICE|NEUTRAL
TECH-010|4|OTHER|NEUTRAL
TECH-011|1|ASK_INFO|NEUTRAL
TECH-011|2|ASK_INFO|NEUTRAL
TECH-011|3|ASK_PRICE|NEUTRAL
TECH-011|4|OTHER|NEUTRAL
TECH-012|1|ASK_INFO|NEUTRAL
TECH-012|2|ASK_INFO|NEUTRAL
TECH-012|3|ASK_PRICE|NEUTRAL
TECH-012|4|ASK_INFO|NEUTRAL
TECH-013|1|ASK_INFO|NEUTRAL
TECH-013|2|ASK_INFO|NEUTRAL
TECH-013|3|ASK_INFO|NEUTRAL
TECH-013|4|SUPPORT|NEUTRAL
TECH-014|1|COMPLAINT|NEGATIVE
TECH-014|2|COMPLAINT|NEGATIVE
TECH-014|3|COMPLAINT|NEGATIVE
TECH-014|4|OTHER|NEUTRAL
TECH-015|1|ASK_INFO|NEUTRAL
TECH-015|2|ASK_INFO|NEUTRAL
TECH-015|3|ASK_PRICE|NEUTRAL
TECH-015|4|ASK_INFO|NEUTRAL
TECH-016|1|ASK_INFO|NEUTRAL
TECH-016|2|ASK_INFO|NEUTRAL
TECH-016|3|ASK_PRICE|NEUTRAL
TECH-016|4|OTHER|NEUTRAL
TECH-017|1|COMPLAINT|NEGATIVE
TECH-017|2|ASK_INFO|NEUTRAL
TECH-017|3|ASK_INFO|NEUTRAL
TECH-017|4|SUPPORT|NEUTRAL
TECH-018|1|SUPPORT|NEUTRAL
TECH-018|2|ASK_INFO|NEGATIVE
TECH-018|3|ASK_PRICE|NEUTRAL
TECH-018|4|SUPPORT|NEUTRAL
TECH-019|1|ASK_INFO|NEUTRAL
TECH-019|2|ASK_INFO|NEUTRAL
TECH-019|3|ASK_PRICE|NEUTRAL
TECH-019|4|ASK_INFO|NEUTRAL
TECH-020|1|ASK_INFO|NEUTRAL
TECH-020|2|ASK_INFO|NEUTRAL
TECH-020|3|ASK_PRICE|NEUTRAL
TECH-020|4|SUPPORT|NEUTRAL
TECH-021|1|ASK_INFO|NEUTRAL
TECH-021|2|SUPPORT|NEUTRAL
TECH-021|3|ASK_INFO|NEUTRAL
TECH-021|4|OTHER|NEUTRAL
TECH-022|1|ASK_INFO|NEUTRAL
TECH-022|2|ASK_INFO|NEUTRAL
TECH-022|3|ASK_INFO|NEUTRAL
TECH-022|4|SUPPORT|NEUTRAL
TECH-023|1|SUPPORT|NEGATIVE
TECH-023|2|SUPPORT|NEUTRAL
TECH-023|3|SUPPORT|NEUTRAL
TECH-023|4|SUPPORT|NEUTRAL
TECH-024|1|ASK_PRICE|NEUTRAL
TECH-024|2|ASK_INFO|NEUTRAL
TECH-024|3|ASK_INFO|NEUTRAL
TECH-024|4|OTHER|NEUTRAL
TECH-025|1|ASK_INFO|NEUTRAL
TECH-025|2|ASK_INFO|NEUTRAL
TECH-025|3|ASK_INFO|NEGATIVE
TECH-025|4|SUPPORT|NEUTRAL
TECH-026|1|ASK_INFO|NEUTRAL
TECH-026|2|ASK_INFO|NEUTRAL
TECH-026|3|ASK_INFO|NEUTRAL
TECH-026|4|OTHER|NEUTRAL
TECH-027|1|SUPPORT|NEGATIVE
TECH-027|2|SUPPORT|NEUTRAL
TECH-027|3|COMPLAINT|NEGATIVE
TECH-027|4|COMPLAINT|NEGATIVE
TECH-028|1|ASK_INFO|NEUTRAL
TECH-028|2|ASK_INFO|NEUTRAL
TECH-028|3|ASK_INFO|NEGATIVE
TECH-028|4|OTHER|NEUTRAL
TECH-029|1|ASK_INFO|NEUTRAL
TECH-029|2|ASK_INFO|NEUTRAL
TECH-029|3|ASK_PRICE|NEUTRAL
TECH-029|4|SUPPORT|NEUTRAL
TECH-030|1|ASK_INFO|NEUTRAL
TECH-030|2|ASK_INFO|NEUTRAL
TECH-030|3|ASK_INFO|NEGATIVE
TECH-030|4|OTHER|NEUTRAL
TECH-031|1|ASK_INFO|NEUTRAL
TECH-031|2|ASK_INFO|NEGATIVE
TECH-031|3|ASK_INFO|NEUTRAL
TECH-031|4|OTHER|NEUTRAL
TECH-032|1|SUPPORT|NEGATIVE
TECH-032|2|SUPPORT|NEUTRAL
TECH-032|3|SUPPORT|NEUTRAL
TECH-032|4|OTHER|NEUTRAL
TECH-033|1|SUPPORT|NEGATIVE
TECH-033|2|SUPPORT|NEUTRAL
TECH-033|3|SUPPORT|NEUTRAL
TECH-033|4|SUPPORT|NEUTRAL
TECH-034|1|ASK_INFO|NEUTRAL
TECH-034|2|ASK_INFO|NEUTRAL
TECH-034|3|ASK_PRICE|NEUTRAL
TECH-034|4|SUPPORT|NEUTRAL
TECH-035|1|SUPPORT|NEUTRAL
TECH-035|2|SUPPORT|NEUTRAL
TECH-035|3|ASK_INFO|NEUTRAL
TECH-035|4|SUPPORT|NEUTRAL
TECH-036|1|ASK_INFO|NEGATIVE
TECH-036|2|ASK_INFO|NEUTRAL
TECH-036|3|SUPPORT|NEUTRAL
TECH-036|4|OTHER|NEUTRAL
TECH-037|1|ASK_INFO|NEUTRAL
TECH-037|2|ASK_INFO|NEUTRAL
TECH-037|3|ASK_INFO|NEGATIVE
TECH-037|4|OTHER|NEUTRAL
TECH-038|1|ASK_INFO|NEUTRAL
TECH-038|2|ASK_INFO|NEGATIVE
TECH-038|3|ASK_PRICE|NEUTRAL
TECH-038|4|OTHER|NEUTRAL
TECH-039|1|ASK_INFO|NEUTRAL
TECH-039|2|ASK_INFO|NEUTRAL
TECH-039|3|ASK_PRICE|NEUTRAL
TECH-039|4|OTHER|NEUTRAL
TECH-040|1|ASK_INFO|NEGATIVE
TECH-040|2|ASK_INFO|NEUTRAL
TECH-040|3|SUPPORT|NEUTRAL
TECH-040|4|SUPPORT|NEUTRAL
TECH-041|1|ASK_INFO|NEUTRAL
TECH-041|2|ASK_INFO|NEUTRAL
TECH-041|3|ASK_PRICE|NEUTRAL
TECH-041|4|OTHER|NEUTRAL
TECH-042|1|ASK_INFO|NEUTRAL
TECH-042|2|ASK_INFO|NEUTRAL
TECH-042|3|ASK_PRICE|NEUTRAL
TECH-042|4|OTHER|NEUTRAL
TECH-043|1|ASK_INFO|NEUTRAL
TECH-043|2|ASK_INFO|NEUTRAL
TECH-043|3|ASK_INFO|NEUTRAL
TECH-043|4|SUPPORT|NEUTRAL
TECH-044|1|ASK_INFO|NEUTRAL
TECH-044|2|ASK_INFO|NEUTRAL
TECH-044|3|ASK_PRICE|NEUTRAL
TECH-044|4|OTHER|NEUTRAL
TECH-045|1|ASK_INFO|NEUTRAL
TECH-045|2|ASK_INFO|NEUTRAL
TECH-045|3|SUPPORT|NEUTRAL
TECH-045|4|OTHER|NEUTRAL
TECH-046|1|ASK_INFO|NEUTRAL
TECH-046|2|ASK_INFO|NEUTRAL
TECH-046|3|ASK_INFO|NEUTRAL
TECH-046|4|OTHER|NEUTRAL
TECH-047|1|ASK_INFO|NEUTRAL
TECH-047|2|ASK_INFO|NEUTRAL
TECH-047|3|ASK_PRICE|NEUTRAL
TECH-047|4|SUPPORT|NEUTRAL
TECH-048|1|ASK_INFO|NEUTRAL
TECH-048|2|ASK_INFO|NEUTRAL
TECH-048|3|ASK_INFO|NEUTRAL
TECH-048|4|OTHER|NEUTRAL
TECH-049|1|ASK_INFO|NEUTRAL
TECH-049|2|ASK_INFO|NEUTRAL
TECH-049|3|ASK_INFO|NEGATIVE
TECH-049|4|SUPPORT|NEUTRAL
TECH-050|1|ASK_INFO|NEUTRAL
TECH-050|2|ASK_INFO|NEUTRAL
TECH-050|3|ASK_INFO|NEUTRAL
TECH-050|4|SUPPORT|NEUTRAL
"""

ANNOTATION_TABLE = {}
for row in ANNOTATION_ROWS.strip().splitlines():
    editorial_id, ordinal, intent, sentiment = row.split("|")
    ANNOTATION_TABLE[(editorial_id, int(ordinal))] = (intent, sentiment)

SEMANTIC_REVIEW = {}

APPROVED_REVIEW_DECISIONS = {
    ("TECH-001", 1): "SUPPORT / NEGATIVE — sự cố đăng nhập và hotline không bắt máy.",
    ("TECH-002", 3): "ASK_INFO / NEGATIVE — lo ngại chi phí phát sinh, chưa phải khiếu nại dịch vụ.",
    ("TECH-014", 1): "COMPLAINT / NEGATIVE — đã hỏi tiến độ nhiều lần nhưng chưa được giải đáp cụ thể.",
    ("TECH-027", 1): "SUPPORT / NEGATIVE — yêu cầu kiểm tra khoản phí chưa nhận diện, chưa kết luận sai phạm.",
    ("TECH-040", 1): "ASK_INFO / NEGATIVE — lo ngại mất dữ liệu khi chuyển đổi, chưa có khiếu nại đã xảy ra.",
    ("TECH-048", 1): "ASK_INFO / NEUTRAL — hỏi về hướng dẫn trong giai đoạn cân nhắc triển khai.",
}


def label_inbound(editorial_id: str, inbound_index: int) -> tuple[str, str, bool, str]:
    key = (editorial_id, inbound_index)
    if key not in ANNOTATION_TABLE:
        raise RuntimeError(f"Missing explicit annotation table row: {editorial_id}/{inbound_index}")
    intent, sentiment = ANNOTATION_TABLE[key]
    ambiguity = SEMANTIC_REVIEW.get(key, "")
    return intent, sentiment, bool(ambiguity), ambiguity or "Đã rà soát theo ngữ nghĩa và ngữ cảnh hội thoại."


def message_moments(started: datetime, turns: tuple[Turn, ...], index: int) -> list[datetime]:
    moments = [started]
    for sequence, turn in enumerate(turns[1:], start=1):
        previous = turns[sequence - 1]
        delay = 1 + ((index + sequence) % 5) if previous.direction == "INBOUND" and turn.direction == "OUTBOUND" else 4 + ((index * 3 + sequence) % 17)
        moments.append(moments[-1] + timedelta(minutes=delay))
    return moments


def write_csv(path: Path, fieldnames: list[str], rows: list[dict[str, str]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def render_human_review(conversations: list[dict[str, str]], messages: list[dict[str, str]], annotations: list[dict[str, str]]) -> str:
    labels = {row["message_id"]: row for row in annotations}
    by_conversation: dict[str, list[dict[str, str]]] = {}
    for message in messages:
        by_conversation.setdefault(message["conversation_id"], []).append(message)
    lines = ["W13 AI-05 TECH pilot - human review", ""]
    for conversation in conversations:
        lines.append(f"[{conversation['external_thread_id']}] {conversation['status']}")
        for message in by_conversation[conversation["id"]]:
            label = labels.get(message["id"])
            suffix = f" [{label['intent']}/{label['sentiment']}]" if label else ""
            lines.append(f"{'KH' if message['direction'] == 'INBOUND' else 'NV'}: {message['content']}{suffix}")
        lines.append("")
    lines.append("Đã phê duyệt sáu quyết định semantic review:")
    for (editorial_id, inbound_index), decision in APPROVED_REVIEW_DECISIONS.items():
        lines.append(f"- {editorial_id} inbound-{inbound_index:02d}: {decision}")
    return "\n".join(lines)


def generate(output: Path = HERE) -> None:
    output.mkdir(parents=True, exist_ok=True)
    approved = parse_approved()
    offerings = active_offerings()
    prices = current_prices()
    profiles_by_id = profiles()
    if len(offerings) != 32:
        raise RuntimeError(f"Expected 32 ACTIVE TECH offerings, found {len(offerings)}")
    accounts = [{"id": CHANNEL_ID, "organization_id": ORG, "channel_type": "ZALO_SIM", "display_name": "POSE Tech Zalo Demo", "external_account_id": "pose-tech-zalo-sim", "status": "ACTIVE", "secret_ref": "POSE_CHANNEL_ZALO_SIM_WEBHOOK_SECRET", "team_id": TEAM, "default_assignee_user_id": ASSIGNEE, "created_at": iso(START), "updated_at": iso(START)}]
    conversations: list[dict[str, str]] = []
    messages: list[dict[str, str]] = []
    annotations: list[dict[str, str]] = []
    for index, item in enumerate(approved):
        profile = profiles_by_id.get(item.profile_id)
        offering = offerings.get(item.offering_code)
        if not profile or profile["organization_id"] != ORG or (item.profile_type == "CUSTOMER" and profile["status"] != "ACTIVE") or (item.profile_type == "LEAD" and profile["status"] not in {"NEW", "QUALIFIED"}):
            raise RuntimeError(f"Invalid W12 profile mapping: {item.editorial_id}")
        if not offering or offering["id"] not in prices:
            raise RuntimeError(f"Invalid W12 offering mapping: {item.editorial_id}")
        conversation_id = uid("conversation", ORG, item.editorial_id)
        started = START + timedelta(hours=index * 3, minutes=(index % 5) * 7)
        moments = message_moments(started, item.turns, index)
        inbound_times = [when for turn, when in zip(item.turns, moments) if turn.direction == "INBOUND"]
        final_text = item.turns[-1].content.lower()
        status = "PENDING" if any(token in final_text for token in ("gửi", "báo lại", "kiểm tra", "phản hồi")) else "CLOSED"
        conversations.append({"id": conversation_id, "organization_id": ORG, "channel_account_id": CHANNEL_ID, "external_thread_id": f"zalo-ai05-tech-{index + 1:03d}", "lead_id": item.profile_id if item.profile_type == "LEAD" else "", "customer_id": item.profile_id if item.profile_type == "CUSTOMER" else "", "assigned_user_id": ASSIGNEE, "status": status, "consent_status": "UNKNOWN", "last_inbound_at": iso(max(inbound_times)), "last_message_at": iso(moments[-1]), "created_at": iso(started), "updated_at": iso(moments[-1])})
        inbound_index = 0
        for sequence, (turn, when) in enumerate(zip(item.turns, moments), start=1):
            message_id = uid("message", conversation_id, sequence)
            messages.append({"id": message_id, "organization_id": ORG, "conversation_id": conversation_id, "direction": turn.direction, "external_message_id": f"zalo-ai05-tech-{index + 1:03d}-{sequence:02d}", "content_type": "TEXT", "content": turn.content, "sent_by_user_id": ASSIGNEE if turn.direction == "OUTBOUND" else "", "delivery_status": "SENT" if turn.direction == "OUTBOUND" else "RECEIVED", "occurred_at": iso(when), "interaction_id": "", "created_at": iso(when)})
            if turn.direction == "INBOUND":
                inbound_index += 1
                intent, sentiment, uncertain, note = label_inbound(item.editorial_id, inbound_index)
                annotations.append({"id": uid("annotation", message_id), "organization_id": ORG, "conversation_id": conversation_id, "message_id": message_id, "intent": intent, "sentiment": sentiment, "split": item.split, "annotator": ANNOTATOR, "annotation_round": "1", "created_at": iso(when + timedelta(seconds=1)), "notes": note + (" Human review required." if uncertain else "")})
    write_csv(output / "channel_accounts.csv", list(accounts[0]), accounts)
    write_csv(output / "conversations.csv", list(conversations[0]), conversations)
    write_csv(output / "conversation_messages.csv", list(messages[0]), messages)
    write_csv(output / "round_1_annotations.csv", list(annotations[0]), annotations)
    (output / "tech_human_review.txt").write_text(render_human_review(conversations, messages, annotations), encoding="utf-8")
    (output / "labeling-guide.vi.md").write_text(LABELING_GUIDE, encoding="utf-8")
    files = [*OUTPUT_FILES]
    (output / "SHA256SUMS.txt").write_text("\n".join(f"{hashlib.sha256((output / filename).read_bytes()).hexdigest()}  {filename}" for filename in files) + "\n", encoding="ascii")


LABELING_GUIDE = """# Hướng dẫn gán nhãn TECH W13 AI-05

Nguồn hội thoại được giữ nguyên từ `TECH_50_hoi_thoai_bien_tap_v4.md`. Chỉ gán nhãn cho message `INBOUND`.

- `ASK_INFO`: hỏi thông tin về gói, phạm vi, khả năng hoặc quy trình.
- `ASK_PRICE`: hỏi giá, phí, chi phí hoặc đơn vị tính.
- `BOOKING`: yêu cầu liên hệ lại, hẹn trao đổi hoặc sắp xếp bước làm việc.
- `PURCHASE_READY`: thể hiện ý định thực hiện/mua/triển khai tương đối rõ.
- `COMPLAINT`: phản ánh lỗi, sai lệch, trải nghiệm không phù hợp hoặc khiếu nại.
- `SUPPORT`: cần hướng dẫn, kiểm tra, chuẩn bị tài liệu hoặc xử lý bước tiếp theo.
- `OTHER`: xác nhận, kết thúc, hoặc nội dung không thuộc các nhóm trên.

Sentiment: `POSITIVE`, `NEUTRAL`, `NEGATIVE`. Nhãn được xác định theo bảng annotation tường minh cho từng lượt inbound và đã đối chiếu ngữ cảnh; không dùng từ khóa đơn lẻ. Các annotation có ghi `Human review required` cần người duyệt xem lại.
"""


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=HERE)
    generate(parser.parse_args().output)
