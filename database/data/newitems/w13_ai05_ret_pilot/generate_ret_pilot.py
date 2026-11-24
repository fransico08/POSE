#!/usr/bin/env python3
"""Generate the W13 AI-05 RET/F&B pilot from frozen V3 editorial dialogue."""
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
SOURCE = HERE / "RET_FNB_50_hoi_thoai_bien_tap_V3.md"
ORG = "83236f5d-2b8b-52bb-af64-ccde174487ab"
TEAM = "719bae44-3351-579e-9681-1b0e21f95015"
ASSIGNEE = "e5e881bc-49f7-51c8-a2d5-f8c0a0b5ef75"
NAMESPACE = uuid.UUID("59c8c69c-5d31-5e4c-9804-6072e21e0390")
CHANNEL_ID = str(uuid.uuid5(NAMESPACE, "channel-account:zalo-sim-ret-ai05"))
START = datetime(2026, 11, 10, 8, 30, tzinfo=timezone.utc)
ANNOTATOR = "Nhi-round-1"
OUTPUT_FILES = ["channel_accounts.csv", "conversations.csv", "conversation_messages.csv", "round_1_annotations.csv", "ret_human_review.txt", "labeling-guide.vi.md"]


@dataclass(frozen=True)
class Turn:
    direction: str
    content: str


@dataclass(frozen=True)
class EditorialConversation:
    editorial_id: str
    profile_type: str
    profile_id: str
    profile_status: str
    offering_code: str
    split: str
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
    blocks = re.findall(r"^### RET-\d{3}\n.*?(?=^### RET-\d{3}|^## Hướng dẫn)", text, re.MULTILINE | re.DOTALL)
    result = []
    for block in blocks:
        head = re.search(r"^### (RET-\d{3})", block, re.MULTILINE)
        meta = re.search(r"\*\*Entity:\*\* `(Customer|Lead)` · \*\*UUID:\*\* `([^`]+)` · \*\*Status:\*\* `([^`]+)`\s*\n- \*\*Offering W12:\*\* `([^`]+)` · \*\*Split:\*\* `(dev|test)`", block)
        turns = tuple(Turn("INBOUND" if who == "KH" else "OUTBOUND", content.rstrip()) for who, content in re.findall(r"^\*\*(KH|NV)\*\*: (.+?)\s*$", block, re.MULTILINE))
        if not head or not meta or not turns or any(left.direction == right.direction for left, right in zip(turns, turns[1:])):
            raise RuntimeError(f"Invalid RET V3 block: {head.group(1) if head else 'unknown'}")
        result.append(EditorialConversation(head.group(1), *meta.groups(), turns))
    if len(result) != 50 or [item.editorial_id for item in result] != [f"RET-{number:03d}" for number in range(1, 51)]:
        raise RuntimeError("Expected RET-001..RET-050")
    if sum(len(item.turns) for item in result) != 331:
        raise RuntimeError("Expected 331 approved messages")
    return result


def active_offerings() -> dict[str, dict[str, str]]:
    return {row["code"]: row for row in csv_rows(CANONICAL / "offerings.csv") if row["organization_id"] == ORG and row["status"] == "ACTIVE"}


def current_prices() -> dict[str, dict[str, str]]:
    active = active_offerings()
    return {row["offering_id"]: row for row in csv_rows(CANONICAL / "offering_prices.csv") if row["organization_id"] == ORG and not row["effective_to"] and row["offering_id"] in {item["id"] for item in active.values()}}


def profiles() -> dict[str, dict[str, str]]:
    result = {}
    for filename in ("customers.csv", "leads.csv"):
        for row in csv_rows(CANONICAL / filename):
            if row["organization_id"] == ORG:
                result[row["id"]] = row
    return result


# Explicit, context-reviewed annotations. Code pairs are intent/sentiment for inbound turns in order.
ANNOTATION_CODES = {
    1: "IN ON OP", 2: "PN IN PN ON", 3: "BN IN ON ON", 4: "PN IN ON", 5: "IN RN IN", 6: "IN IN ON OP", 7: "IN IN ON", 8: "PN RN PN ON", 9: "IN IN SN ON", 10: "IN RN ON", 11: "BN ON SN", 12: "RN IN ON", 13: "CG CG ON", 14: "CG CG CG", 15: "IN RN RN", 16: "SN IN SN", 17: "RN ON ON", 18: "IN BN SN", 19: "CG CG SN", 20: "RN RN ON", 21: "PN IN RP", 22: "RN ON ON", 23: "RN ON PN RN", 24: "IN IN ON", 25: "RN IN IN", 26: "SN ON SN", 27: "IN IN RN", 28: "IN IN IN", 29: "RN ON IN", 30: "RN IN ON", 31: "CG CG IN", 32: "IN SN ON", 33: "CG CG CG", 34: "IN IN ON", 35: "BN IN ON", 36: "PN ON OP", 37: "IN IN IN", 38: "PN IN RN", 39: "IN SN SN ON", 40: "PN IN RN ON", 41: "IN IN IN", 42: "IN RN ON", 43: "IN IN RN", 44: "PN IN SN", 45: "PN RN IN", 46: "IN RN IN", 47: "PN IN RP", 48: "BN BN ON", 49: "PN IN RN", 50: "PN IN RN OP",
}
INTENT_CODES = {"I": "ASK_INFO", "P": "ASK_PRICE", "B": "BOOKING", "R": "PURCHASE_READY", "C": "COMPLAINT", "S": "SUPPORT", "O": "OTHER"}
SENTIMENT_CODES = {"N": "NEUTRAL", "G": "NEGATIVE", "P": "POSITIVE"}


def annotation_table() -> dict[tuple[str, int], tuple[str, str]]:
    result = {}
    for ordinal, encoded in ANNOTATION_CODES.items():
        for inbound_index, token in enumerate(encoded.split(), start=1):
            result[(f"RET-{ordinal:03d}", inbound_index)] = (INTENT_CODES[token[0]], SENTIMENT_CODES[token[1]])
    if len(result) != 159:
        raise RuntimeError(f"Expected 159 explicit annotation rows, found {len(result)}")
    return result


ANNOTATION_TABLE = annotation_table()
PENDING = {3, 9, 11, 13, 14, 16, 18, 19, 24, 26, 28, 29, 31, 32, 33, 34, 35, 37, 41, 43, 44, 45, 48, 49}


def status_for(editorial_id: str) -> str:
    return "PENDING" if int(editorial_id[-3:]) in PENDING else "CLOSED"


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


def write_text_crlf(path: Path, content: str, encoding: str) -> None:
    with path.open("w", encoding=encoding, newline="\r\n") as file:
        file.write(content)


def write_text_lf(path: Path, content: str, encoding: str) -> None:
    with path.open("w", encoding=encoding, newline="\n") as file:
        file.write(content)


def render_human_review(conversations, messages, annotations) -> str:
    labels = {row["message_id"]: row for row in annotations}
    grouped = {}
    for message in messages:
        grouped.setdefault(message["conversation_id"], []).append(message)
    lines = ["W13 AI-05 RET/F&B pilot - human review", ""]
    for conversation in conversations:
        lines.append(f"[{conversation['external_thread_id']}] {conversation['status']}")
        for message in grouped[conversation["id"]]:
            annotation = labels.get(message["id"])
            suffix = f" [{annotation['intent']}/{annotation['sentiment']}]" if annotation else ""
            lines.append(f"{'KH' if message['direction'] == 'INBOUND' else 'NV'}: {message['content']}{suffix}")
        lines.append("")
    return "\n".join(lines)


LABELING_GUIDE = """# Hướng dẫn gán nhãn RET/F&B W13 AI-05

Chỉ annotation cho lượt `INBOUND`; bảng nhãn explicit trong generator được rà theo ngữ cảnh từng hội thoại, không dùng keyword heuristic.

- `ASK_INFO`: hỏi món, khả năng phục vụ, lựa chọn hoặc thông tin áp dụng.
- `ASK_PRICE`: hỏi trực tiếp giá, tổng tiền hoặc khoản chi phí.
- `BOOKING`: trao đổi đặt trước, thời điểm nhận hoặc khả năng giao/nhận.
- `PURCHASE_READY`: chọn món hoặc thể hiện ý định đặt rõ ràng.
- `COMPLAINT`: phản ánh sai món, sai giá, thiếu thông tin hoặc trải nghiệm không phù hợp.
- `SUPPORT`: cần kiểm tra, hướng dẫn hoặc xử lý một yêu cầu cụ thể.
- `OTHER`: xác nhận, bổ sung ngữ cảnh hoặc kết thúc không có yêu cầu mới.

Sentiment chỉ là `NEGATIVE` khi có lo ngại, khó chịu hoặc phản ánh thật sự; `POSITIVE` khi thể hiện hài lòng/cảm ơn rõ ràng; còn lại là `NEUTRAL`.
"""


def generate(output: Path = HERE) -> None:
    output.mkdir(parents=True, exist_ok=True)
    approved = parse_approved()
    offerings, prices, profile_rows = active_offerings(), current_prices(), profiles()
    if len(offerings) != 49:
        raise RuntimeError(f"Expected 49 ACTIVE RET offerings, found {len(offerings)}")
    accounts = [{"id": CHANNEL_ID, "organization_id": ORG, "channel_type": "ZALO_SIM", "display_name": "POSE Retail Zalo Demo", "external_account_id": "pose-ret-zalo-sim", "status": "ACTIVE", "secret_ref": "POSE_CHANNEL_ZALO_SIM_WEBHOOK_SECRET", "team_id": TEAM, "default_assignee_user_id": ASSIGNEE, "created_at": iso(START), "updated_at": iso(START)}]
    conversations, messages, annotations = [], [], []
    for index, item in enumerate(approved):
        profile, offering = profile_rows.get(item.profile_id), offerings.get(item.offering_code)
        valid_profile = profile and profile["organization_id"] == ORG and profile["status"] == item.profile_status and ((item.profile_type == "Customer" and item.profile_status == "ACTIVE") or (item.profile_type == "Lead" and item.profile_status in {"NEW", "QUALIFIED"}))
        if not valid_profile or not offering or offering["id"] not in prices:
            raise RuntimeError(f"Invalid W12 mapping: {item.editorial_id}")
        conversation_id = uid("conversation", ORG, item.editorial_id)
        started = START + timedelta(hours=index * 3, minutes=(index % 5) * 7)
        moments = message_moments(started, item.turns, index)
        inbound_times = [when for turn, when in zip(item.turns, moments) if turn.direction == "INBOUND"]
        conversations.append({"id": conversation_id, "organization_id": ORG, "channel_account_id": CHANNEL_ID, "external_thread_id": f"zalo-ai05-ret-{index + 1:03d}", "lead_id": item.profile_id if item.profile_type == "Lead" else "", "customer_id": item.profile_id if item.profile_type == "Customer" else "", "assigned_user_id": ASSIGNEE, "status": status_for(item.editorial_id), "consent_status": "UNKNOWN", "last_inbound_at": iso(max(inbound_times)), "last_message_at": iso(moments[-1]), "created_at": iso(started), "updated_at": iso(moments[-1])})
        inbound_index = 0
        for sequence, (turn, when) in enumerate(zip(item.turns, moments), start=1):
            message_id = uid("message", conversation_id, sequence)
            messages.append({"id": message_id, "organization_id": ORG, "conversation_id": conversation_id, "direction": turn.direction, "external_message_id": f"zalo-ai05-ret-{index + 1:03d}-{sequence:02d}", "content_type": "TEXT", "content": turn.content, "sent_by_user_id": ASSIGNEE if turn.direction == "OUTBOUND" else "", "delivery_status": "SENT" if turn.direction == "OUTBOUND" else "RECEIVED", "occurred_at": iso(when), "interaction_id": "", "created_at": iso(when)})
            if turn.direction == "INBOUND":
                inbound_index += 1
                intent, sentiment = ANNOTATION_TABLE[(item.editorial_id, inbound_index)]
                annotations.append({"id": uid("annotation", message_id), "organization_id": ORG, "conversation_id": conversation_id, "message_id": message_id, "intent": intent, "sentiment": sentiment, "split": item.split, "annotator": ANNOTATOR, "annotation_round": "1", "created_at": iso(when + timedelta(seconds=1)), "notes": "Explicit context-reviewed annotation from frozen RET V3 dialogue."})
    write_csv(output / "channel_accounts.csv", list(accounts[0]), accounts)
    write_csv(output / "conversations.csv", list(conversations[0]), conversations)
    write_csv(output / "conversation_messages.csv", list(messages[0]), messages)
    write_csv(output / "round_1_annotations.csv", list(annotations[0]), annotations)
    write_text_crlf(output / "ret_human_review.txt", render_human_review(conversations, messages, annotations), "utf-8")
    write_text_crlf(output / "labeling-guide.vi.md", LABELING_GUIDE, "utf-8")
    write_text_lf(output / "SHA256SUMS.txt", "\n".join(f"{hashlib.sha256((output / name).read_bytes()).hexdigest()}  {name}" for name in OUTPUT_FILES) + "\n", "ascii")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=HERE)
    generate(parser.parse_args().output)
