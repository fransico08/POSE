#!/usr/bin/env python3
"""Generate the W13 AI-05 Clinic pilot from approved editorial dialogue."""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import uuid
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
CANONICAL = ROOT / "database" / "data" / "seed" / "multisector_v2"
DEFAULT_OUTPUT = Path(__file__).resolve().parent
SOURCE = DEFAULT_OUTPUT / "CLI_50_hoi_thoai_bien_tap_001_050.md"
ORG = "68809a13-d597-5c34-a5d8-0d18d9c6cc7a"
TEAM = "baeffd09-165e-5311-bb75-6846b7c2e58c"
ASSIGNEE = "e928c188-ecab-5599-a2f8-5c36bad8c29f"
NAMESPACE = uuid.UUID("c3ab2f45-f8a3-5d72-8b1c-3d68f5e0b8d1")
CHANNEL_ID = str(uuid.uuid5(NAMESPACE, "channel-account:zalo-sim-cli-ai05"))
START = datetime(2026, 11, 10, 8, 30, tzinfo=timezone.utc)
ANNOTATOR = "Nhi-round-1"
VERSION = "w13-ai05-cli-pilot-v1"
OUTPUT_FILES = ["channel_accounts.csv", "conversations.csv", "conversation_messages.csv", "round_1_annotations.csv", "cli_human_review.txt", "cli_semantic_audit.json"]
BASELINE = DEFAULT_OUTPUT / "cli_semantic_baseline.json"


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
    blocks = re.findall(r"^## CLI-\d{3} .*?(?=^## CLI-\d{3} |\Z)", text, flags=re.MULTILINE | re.DOTALL)
    result: list[EditorialConversation] = []
    for block in blocks:
        head = re.search(r"^## (CLI-\d{3}) ", block, re.MULTILINE)
        meta = re.search(r"- Split: \*\*(dev|test)\*\* · Link: `(CUSTOMER|LEAD)` `([^`]+)` · Offering: `([^`]+)`", block)
        turns = tuple(Turn("INBOUND" if speaker == "KH" else "OUTBOUND", content.rstrip()) for speaker, content in re.findall(r"^- \*\*(KH|NV):\*\* (.+?)\s*$", block, re.MULTILINE))
        if not head or not meta or not turns or any(a.direction == b.direction for a, b in zip(turns, turns[1:])):
            raise RuntimeError(f"Invalid approved Clinic block: {head.group(1) if head else 'unknown'}")
        result.append(EditorialConversation(head.group(1), meta.group(1), meta.group(2), meta.group(3), meta.group(4), turns))
    if len(result) != 50 or [item.editorial_id for item in result] != [f"CLI-{i:03d}" for i in range(1, 51)]:
        raise RuntimeError(f"Expected CLI-001..CLI-050, found {len(result)} editorial conversations")
    if sum(len(item.turns) for item in result) != 346:
        raise RuntimeError("Expected 346 approved messages")
    return result


def active_offerings() -> dict[str, dict[str, str]]:
    return {row["code"]: row for row in csv_rows(CANONICAL / "offerings.csv") if row["organization_id"] == ORG and row["status"] == "ACTIVE"}


def current_prices() -> dict[str, dict[str, str]]:
    offerings = active_offerings()
    return {row["offering_id"]: row for row in csv_rows(CANONICAL / "offering_prices.csv") if row["organization_id"] == ORG and not row["effective_to"] and row["offering_id"] in {item["id"] for item in offerings.values()}}


def profiles() -> dict[str, dict[str, str]]:
    result = {}
    for filename in ("customers.csv", "leads.csv"):
        for row in csv_rows(CANONICAL / filename):
            if row["organization_id"] == ORG:
                result[row["id"]] = row
    return result


MANUAL_LABEL_OVERRIDES = {
    ("CLI-002", 1): ("BOOKING", "NEUTRAL"), ("CLI-005", 4): ("OTHER", "NEUTRAL"),
    ("CLI-006", 1): ("BOOKING", "NEUTRAL"), ("CLI-007", 3): ("ASK_PRICE", "NEUTRAL"),
    ("CLI-008", 1): ("BOOKING", "NEUTRAL"), ("CLI-008", 2): ("BOOKING", "NEUTRAL"), ("CLI-009", 2): ("COMPLAINT", "NEGATIVE"),
    ("CLI-010", 1): ("ASK_PRICE", "NEUTRAL"), ("CLI-010", 2): ("ASK_PRICE", "NEUTRAL"), ("CLI-011", 1): ("OTHER", "NEGATIVE"), ("CLI-011", 2): ("OTHER", "NEGATIVE"),
    ("CLI-012", 1): ("ASK_INFO", "NEUTRAL"), ("CLI-014", 3): ("BOOKING", "NEUTRAL"), ("CLI-015", 1): ("COMPLAINT", "NEGATIVE"),
    ("CLI-017", 1): ("ASK_PRICE", "NEUTRAL"), ("CLI-017", 2): ("COMPLAINT", "NEGATIVE"), ("CLI-018", 1): ("ASK_INFO", "NEUTRAL"),
    ("CLI-019", 2): ("OTHER", "NEUTRAL"), ("CLI-020", 4): ("OTHER", "NEUTRAL"), ("CLI-022", 2): ("SUPPORT", "NEUTRAL"),
    ("CLI-023", 3): ("SUPPORT", "NEUTRAL"), ("CLI-024", 1): ("BOOKING", "NEUTRAL"), ("CLI-024", 3): ("BOOKING", "NEUTRAL"),
    ("CLI-025", 2): ("COMPLAINT", "NEGATIVE"), ("CLI-025", 4): ("COMPLAINT", "NEGATIVE"), ("CLI-026", 3): ("BOOKING", "NEUTRAL"),
    ("CLI-027", 2): ("ASK_PRICE", "NEGATIVE"), ("CLI-028", 1): ("ASK_PRICE", "NEUTRAL"), ("CLI-028", 2): ("ASK_PRICE", "NEUTRAL"),
    ("CLI-029", 1): ("ASK_INFO", "NEUTRAL"), ("CLI-030", 1): ("SUPPORT", "NEUTRAL"), ("CLI-030", 3): ("ASK_PRICE", "NEUTRAL"),
    ("CLI-031", 1): ("ASK_PRICE", "NEUTRAL"), ("CLI-031", 3): ("ASK_INFO", "NEUTRAL"), ("CLI-032", 2): ("COMPLAINT", "NEGATIVE"),
    ("CLI-033", 3): ("ASK_PRICE", "NEUTRAL"), ("CLI-035", 2): ("OTHER", "NEGATIVE"), ("CLI-036", 1): ("ASK_PRICE", "NEUTRAL"),
    ("CLI-036", 3): ("SUPPORT", "NEUTRAL"), ("CLI-037", 1): ("BOOKING", "NEUTRAL"), ("CLI-037", 3): ("BOOKING", "NEUTRAL"),
    ("CLI-038", 1): ("BOOKING", "NEUTRAL"), ("CLI-038", 3): ("ASK_INFO", "NEUTRAL"), ("CLI-039", 3): ("ASK_PRICE", "NEUTRAL"),
    ("CLI-040", 1): ("BOOKING", "NEUTRAL"), ("CLI-040", 2): ("SUPPORT", "NEUTRAL"), ("CLI-040", 4): ("BOOKING", "NEUTRAL"),
    ("CLI-041", 2): ("ASK_PRICE", "NEUTRAL"), ("CLI-042", 1): ("COMPLAINT", "NEGATIVE"), ("CLI-042", 3): ("COMPLAINT", "NEGATIVE"),
    ("CLI-044", 3): ("COMPLAINT", "NEGATIVE"), ("CLI-045", 2): ("COMPLAINT", "NEGATIVE"), ("CLI-046", 1): ("COMPLAINT", "NEGATIVE"),
    ("CLI-046", 2): ("ASK_PRICE", "NEUTRAL"), ("CLI-046", 3): ("COMPLAINT", "NEGATIVE"), ("CLI-047", 4): ("BOOKING", "NEUTRAL"),
    ("CLI-048", 1): ("BOOKING", "NEGATIVE"), ("CLI-048", 2): ("OTHER", "NEGATIVE"), ("CLI-049", 2): ("ASK_PRICE", "NEGATIVE"), ("CLI-050", 2): ("COMPLAINT", "NEGATIVE"),
}


INTENT_CODES = {"I": "ASK_INFO", "P": "ASK_PRICE", "B": "BOOKING", "C": "COMPLAINT", "S": "SUPPORT", "R": "PURCHASE_READY", "O": "OTHER"}
SENTIMENT_CODES = {"N": "NEUTRAL", "G": "NEGATIVE", "P": "POSITIVE"}
SEMANTIC_CODES = {
1:"IN IN PN ON",2:"BN IN PN ON",3:"OG IN ON",4:"IN IN ON ON",5:"IN IN ON ON",6:"BN ON IN ON",7:"IN IN PN ON",8:"BN BN PN ON",9:"IN CG PN ON",10:"PN PN IN ON",11:"OG OG ON",12:"IN CG PN BN",13:"IN IN IN ON",14:"BN PN BN ON",15:"IN PN IN ON",16:"IN OG PN ON",17:"PN CG PN ON",18:"IN IN IN ON",19:"IN ON PN ON",20:"IN ON IN ON",21:"SN IN PN CG",22:"IN SN PN ON",23:"IN IN SN ON",24:"BN PN BN ON",25:"CG CG IN CG",26:"BN OG BN ON",27:"OG PG ON",28:"PN PN IN ON",29:"IN SN PN ON",30:"SN ON PN BN",31:"PN CG IN ON",32:"IN CG PN ON",33:"IN IN PN ON",34:"IN RP IN ON",35:"OG OG SN",36:"PN SN SN ON",37:"BN IN BN ON",38:"BN ON IN ON",39:"IN IN PN ON",40:"BN SN PN BN",41:"IN PN PN ON",42:"CG PN CG ON",43:"SN IN PN ON",44:"IN IN CG ON",45:"IN CG PN ON",46:"CG PN CG ON",47:"IN IN IN BN",48:"OG OG ON",49:"OG PG ON",50:"IN CG PN ON"
}
STATUS_BY_EDITORIAL = {1:"PENDING",2:"CLOSED",3:"CLOSED",4:"CLOSED",5:"PENDING",6:"CLOSED",7:"PENDING",8:"PENDING",9:"OPEN",10:"CLOSED",11:"CLOSED",12:"OPEN",13:"OPEN",14:"PENDING",15:"CLOSED",16:"PENDING",17:"CLOSED",18:"OPEN",19:"CLOSED",20:"OPEN",21:"CLOSED",22:"CLOSED",23:"CLOSED",24:"PENDING",25:"OPEN",26:"CLOSED",27:"CLOSED",28:"OPEN",29:"PENDING",30:"CLOSED",31:"CLOSED",32:"CLOSED",33:"CLOSED",34:"PENDING",35:"CLOSED",36:"PENDING",37:"PENDING",38:"PENDING",39:"CLOSED",40:"PENDING",41:"CLOSED",42:"CLOSED",43:"PENDING",44:"PENDING",45:"CLOSED",46:"OPEN",47:"CLOSED",48:"CLOSED",49:"CLOSED",50:"PENDING"}

def semantic_label(editorial_id: str, inbound_index: int) -> tuple[str, str]:
    ordinal = int(editorial_id[-3:])
    tokens = SEMANTIC_CODES[ordinal].split()
    intent, sentiment = tokens[inbound_index - 1][0], tokens[inbound_index - 1][1]
    return INTENT_CODES[intent], SENTIMENT_CODES[sentiment]
def label_inbound(content: str, editorial_id: str | None = None, inbound_index: int | None = None) -> tuple[str, str]:
    if editorial_id is not None and inbound_index is not None:
        return semantic_label(editorial_id, inbound_index)
    text = content.lower()
    if any(token in text for token in ("khó thở", "lơ mơ", "đau ngực", "đột quỵ", "co giật")):
        return "OTHER", "NEGATIVE"
    if any(token in text for token in ("phản ánh", "không hài lòng", "sai", "nhầm", "khác nhau", "khiếu nại", "bực")):
        return "COMPLAINT", "NEGATIVE"
    if any(token in text for token in ("bao nhiêu", "phí", "giá", "chi phí", "mức", "k", "triệu")):
        return "ASK_PRICE", "NEUTRAL"
    if any(token in text for token in ("đặt lịch", "hẹn", "khung giờ", "sắp xếp", "đăng ký")):
        return "BOOKING", "NEUTRAL"
    if any(token in text for token in ("cần hỗ trợ", "chuẩn bị", "hướng dẫn", "gửi", "tra cứu")):
        return "SUPPORT", "NEUTRAL"
    if any(token in text for token in ("sẽ đăng ký", "muốn làm", "đưa qua", "đặt")):
        return "PURCHASE_READY", "POSITIVE"
    return "ASK_INFO", "NEUTRAL"


def status_for(editorial_id: str) -> str:
    return STATUS_BY_EDITORIAL[int(editorial_id[-3:])]
    if any(token in text for token in ("gọi 115", "cảm ơn", "hiểu rồi", "yên tâm hơn", "để tôi", "để chị", "tôi sẽ", "mình sẽ", "được,", "ok", "rồi.")):
        return "CLOSED"
    if any(token in text for token in ("chờ", "xác nhận", "kiểm tra", "liên hệ", "hướng dẫn", "báo lại")):
        return "PENDING"
    return "OPEN"


AMBIGUITIES = {
    ("CLI-006", 2): "Mô tả diễn tiến triệu chứng, không phải yêu cầu nghiệp vụ rõ ràng.",
    ("CLI-021", 1): "Yêu cầu đăng ký nhưng chưa có lịch cụ thể; SUPPORT và BOOKING đều có thể đọc.",
    ("CLI-038", 1): "Hỏi thời điểm nhận mẫu; cần phân biệt yêu cầu lịch với thông tin vận hành.",
    ("CLI-041", 1): "So sánh hai giá; chọn ASK_INFO vì trọng tâm là sự khác biệt giữa mã dịch vụ.",
    ("CLI-048", 1): "Có hình thức hỏi đặt lịch nhưng dấu hiệu đột quỵ khiến ưu tiên cấp cứu; chọn OTHER.",
    ("CLI-050", 2): "Lo ngại không biết nguyên nhân sau khi trả phí; ranh giới giữa COMPLAINT và ASK_INFO.",
}

def justification(intent: str, sentiment: str, content: str) -> str:
    if intent == "ASK_PRICE": return "Hỏi trực tiếp về giá, phí hoặc khoản chi phí áp dụng."
    if intent == "BOOKING": return "Trao đổi về đăng ký, đặt/đổi lịch hoặc thời điểm khám."
    if intent == "COMPLAINT": return "Phản ánh bất nhất, trải nghiệm không phù hợp hoặc lo ngại cần xử lý."
    if intent == "SUPPORT": return "Cần hướng dẫn cách tiếp nhận, chuẩn bị, gửi thông tin hoặc bước tiếp theo."
    if intent == "PURCHASE_READY": return "Thể hiện ý định thực hiện dịch vụ sau khi đã được giải thích."
    if intent == "OTHER": return "Lượt xác nhận, kết thúc, hành động đã quyết định hoặc nội dung ngoài nhóm yêu cầu chính."
    if sentiment == "NEGATIVE": return "Thể hiện lo lắng/căng thẳng trong bối cảnh sức khỏe hoặc dịch vụ."
    return "Trao đổi thông tin hoặc làm rõ nhu cầu, chưa yêu cầu giá hay lịch cụ thể."

def message_moments(started: datetime, turns: tuple[Turn, ...], index: int) -> list[datetime]:
    moments = [started]
    for sequence, turn in enumerate(turns[1:], start=1):
        previous = turns[sequence - 1]
        delay = 1 + ((index + sequence) % 5) if turn.direction == "OUTBOUND" and previous.direction == "INBOUND" else 4 + ((index * 3 + sequence) % 17)
        moments.append(moments[-1] + timedelta(minutes=delay))
    return moments


def render_human_review(conversations: list[dict[str, str]], messages: list[dict[str, str]], annotations: list[dict[str, str]]) -> str:
    labels = {row["message_id"]: row for row in annotations}
    by_conversation: dict[str, list[dict[str, str]]] = {}
    for message in messages:
        by_conversation.setdefault(message["conversation_id"], []).append(message)
    lines = ["W13 AI-05 Clinic pilot - human review", ""]
    for conversation in conversations:
        lines.append(f"[{conversation['external_thread_id']}] {conversation['status']}")
        for message in by_conversation[conversation["id"]]:
            label = labels.get(message["id"])
            suffix = f" [{label['intent']}/{label['sentiment']}]" if label else ""
            lines.append(f"{'KH' if message['direction'] == 'INBOUND' else 'NV'}: {message['content']}{suffix}")
        lines.append("")
    return "\n".join(lines)


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


def generate(output: Path = DEFAULT_OUTPUT) -> None:
    output.mkdir(parents=True, exist_ok=True)
    approved = parse_approved()
    baseline = json.loads(BASELINE.read_text(encoding="utf-8"))
    offerings = active_offerings()
    profiles_by_id = profiles()
    prices = current_prices()
    if len(offerings) != 34:
        raise RuntimeError(f"Expected 34 ACTIVE Clinic offerings, found {len(offerings)}")
    accounts = [{"id": CHANNEL_ID, "organization_id": ORG, "channel_type": "ZALO_SIM", "display_name": "POSE Clinic Zalo Demo", "external_account_id": "pose-clinic-zalo-sim", "status": "ACTIVE", "secret_ref": "POSE_CHANNEL_ZALO_SIM_WEBHOOK_SECRET", "team_id": TEAM, "default_assignee_user_id": ASSIGNEE, "created_at": iso(START), "updated_at": iso(START)}]
    conversations: list[dict[str, str]] = []
    messages: list[dict[str, str]] = []
    annotations: list[dict[str, str]] = []
    mapping: list[dict[str, str]] = []
    for index, item in enumerate(approved):
        profile = profiles_by_id.get(item.profile_id)
        offering = offerings.get(item.offering_code)
        if not profile or profile["organization_id"] != ORG or (item.profile_type == "CUSTOMER" and profile["status"] != "ACTIVE") or (item.profile_type == "LEAD" and profile["status"] not in {"NEW", "QUALIFIED"}):
            raise RuntimeError(f"Invalid W12 profile mapping: {item.editorial_id}")
        if not offering or offering["status"] != "ACTIVE" or offering["organization_id"] != ORG or offering["id"] not in prices:
            raise RuntimeError(f"Invalid W12 offering mapping: {item.editorial_id} {item.offering_code}")
        conversation_id = uid("conversation", ORG, item.editorial_id)
        mapping.append({"editorial_id": item.editorial_id, "conversation_id": conversation_id, "profile_type": item.profile_type, "profile_id": item.profile_id, "offering_code": item.offering_code, "split": item.split})
        started = START + timedelta(hours=index * 3, minutes=(index % 5) * 7)
        moments = message_moments(started, item.turns, index)
        inbound = [when for turn, when in zip(item.turns, moments) if turn.direction == "INBOUND"]
        conversations.append({"id": conversation_id, "organization_id": ORG, "channel_account_id": CHANNEL_ID, "external_thread_id": f"zalo-ai05-cli-{index + 1:03d}", "lead_id": item.profile_id if item.profile_type == "LEAD" else "", "customer_id": item.profile_id if item.profile_type == "CUSTOMER" else "", "assigned_user_id": ASSIGNEE, "status": status_for(item.editorial_id), "consent_status": "UNKNOWN", "last_inbound_at": iso(max(inbound)), "last_message_at": iso(moments[-1]), "created_at": iso(started), "updated_at": iso(moments[-1])})
        inbound_index = 0
        for sequence, (turn, when) in enumerate(zip(item.turns, moments), start=1):
            message_id = uid("message", conversation_id, sequence)
            messages.append({"id": message_id, "organization_id": ORG, "conversation_id": conversation_id, "direction": turn.direction, "external_message_id": f"zalo-ai05-cli-{index + 1:03d}-{sequence:02d}", "content_type": "TEXT", "content": turn.content, "sent_by_user_id": ASSIGNEE if turn.direction == "OUTBOUND" else "", "delivery_status": "SENT" if turn.direction == "OUTBOUND" else "RECEIVED", "occurred_at": iso(when), "interaction_id": "", "created_at": iso(when)})
            if turn.direction == "INBOUND":
                inbound_index += 1
                intent, sentiment = label_inbound(turn.content, item.editorial_id, inbound_index)
                annotations.append({"id": uid("annotation", message_id), "organization_id": ORG, "conversation_id": conversation_id, "message_id": message_id, "intent": intent, "sentiment": sentiment, "split": item.split, "annotator": ANNOTATOR, "annotation_round": "1", "created_at": iso(when + timedelta(seconds=1)), "notes": "Generated from approved Clinic editorial dialogue."})
    write_csv(output / "channel_accounts.csv", list(accounts[0]), accounts)
    write_csv(output / "conversations.csv", list(conversations[0]), conversations)
    write_csv(output / "conversation_messages.csv", list(messages[0]), messages)
    write_csv(output / "round_1_annotations.csv", list(annotations[0]), annotations)
    write_text_crlf(output / "cli_human_review.txt", render_human_review(conversations, messages, annotations), "utf-8")
    annotation_lookup = {row["message_id"]: row for row in annotations}
    message_to_editorial = {}
    audit_annotations = []
    for item in approved:
        conversation_id = uid("conversation", ORG, item.editorial_id)
        inbound_index = 0
        for sequence, turn in enumerate(item.turns, start=1):
            if turn.direction != "INBOUND":
                continue
            inbound_index += 1
            message_id = uid("message", conversation_id, sequence)
            current = annotation_lookup[message_id]
            old = baseline["annotations"].get(message_id, {})
            intent, sentiment = current["intent"], current["sentiment"]
            audit_annotations.append({"editorial_id": item.editorial_id, "conversation_id": conversation_id, "message_id": message_id, "inbound_index": inbound_index, "content": turn.content, "old_intent": old.get("intent", "UNAVAILABLE"), "new_intent": intent, "old_sentiment": old.get("sentiment", "UNAVAILABLE"), "new_sentiment": sentiment, "changed": old.get("intent") != intent or old.get("sentiment") != sentiment, "justification": justification(intent, sentiment, turn.content), "human_review": (item.editorial_id, inbound_index) in AMBIGUITIES, "ambiguity": AMBIGUITIES.get((item.editorial_id, inbound_index), "")})
    audit_statuses = []
    for index, item in enumerate(approved):
        conversation_id = uid("conversation", ORG, item.editorial_id)
        new_status = conversations[index]["status"]
        old_status = baseline["statuses"].get(conversation_id, "UNAVAILABLE")
        reason = {"CLOSED": "Đã có kết luận hoặc hành động rõ ràng trong toàn bộ hội thoại.", "PENDING": "Còn chờ xác nhận, liên hệ hoặc bước tiếp theo.", "OPEN": "Nhu cầu/câu hỏi hoặc phản ánh vẫn chưa được kết luận."}[new_status]
        audit_statuses.append({"editorial_id": item.editorial_id, "conversation_id": conversation_id, "old_status": old_status, "new_status": new_status, "changed": old_status != new_status, "reason": reason})
    audit = {"version": VERSION, "annotation_count": len(audit_annotations), "status_count": len(audit_statuses), "annotations": audit_annotations, "statuses": audit_statuses}
    write_text_crlf(output / "cli_semantic_audit.json", json.dumps(audit, ensure_ascii=False, indent=2) + "\n", "utf-8")
    manifest = {"version": VERSION, "namespace": str(NAMESPACE), "namespace_purpose": "UUIDv5 for Clinic pilot artifacts", "organization_id": ORG, "mapping": mapping}
    write_text_crlf(output / "cli_id_mapping.json", json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", "utf-8")
    files = [*OUTPUT_FILES, "cli_id_mapping.json"]
    write_text_lf(output / "SHA256SUMS.txt", "\n".join(f"{hashlib.sha256((output / filename).read_bytes()).hexdigest()}  {filename}" for filename in files) + "\n", "ascii")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    generate(parser.parse_args().output)
