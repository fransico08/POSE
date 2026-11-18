#!/usr/bin/env python3
"""Validate the static W13 AI-05 EDU pilot without database access."""
from __future__ import annotations

import csv
import hashlib
import re
import subprocess
import sys
import tempfile
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

from generate_edu_pilot import ASSIGNEE, CANONICAL, CHANNEL_ID, ORG, TEAM, active_offerings, generate, offering_for, profiles, render_human_review, workflow_for

HERE = Path(__file__).resolve().parent
INTENTS = {"ASK_PRICE", "ASK_INFO", "BOOKING", "PURCHASE_READY", "COMPLAINT", "SUPPORT", "OTHER"}
SENTIMENTS = {"POSITIVE", "NEUTRAL", "NEGATIVE"}
FILES = ["channel_accounts.csv", "conversations.csv", "conversation_messages.csv", "round_1_annotations.csv"]
WINDOW_START = datetime(2026, 11, 10, tzinfo=timezone.utc)
WINDOW_END = datetime(2026, 11, 17, tzinfo=timezone.utc)


def rows(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def instant(value: str) -> datetime:
    return datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)


def expect(condition: bool, text: str, failures: list[str]) -> None:
    if not condition:
        failures.append(text)


def uniqueness(values: list[str], name: str, failures: list[str]) -> None:
    expect(len(values) == len(set(values)), f"duplicate {name}", failures)


def canonical_profiles() -> tuple[dict[str, dict[str, str]], dict[str, dict[str, str]]]:
    customers = {row["id"]: row for row in rows(CANONICAL / "customers.csv")}
    leads = {row["id"]: row for row in rows(CANONICAL / "leads.csv")}
    return customers, leads


def validate(output: Path) -> tuple[list[str], dict[str, Counter[str]]]:
    failures: list[str] = []
    account_rows = rows(output / "channel_accounts.csv")
    conversations = rows(output / "conversations.csv")
    messages = rows(output / "conversation_messages.csv")
    annotations = rows(output / "round_1_annotations.csv")
    customers, leads = canonical_profiles()

    expect(len(account_rows) == 1, "channel_accounts must contain exactly one row", failures)
    if account_rows:
        account = account_rows[0]
        expected = {
            "id": CHANNEL_ID,
            "organization_id": ORG,
            "channel_type": "ZALO_SIM",
            "team_id": TEAM,
            "default_assignee_user_id": ASSIGNEE,
            "status": "ACTIVE",
            "secret_ref": "POSE_CHANNEL_ZALO_SIM_WEBHOOK_SECRET",
        }
        for key, value in expected.items():
            expect(account.get(key) == value, f"channel_accounts.{key} is invalid", failures)

    expect(len(conversations) == 50, "expected 50 conversations", failures)
    uniqueness([row["id"] for row in conversations], "conversation ids", failures)
    uniqueness([row["external_thread_id"] for row in conversations], "external thread ids", failures)
    customer_count = 0
    lead_count = 0
    conversation_by_id = {row["id"]: row for row in conversations}
    for row in conversations:
        expect(row["organization_id"] == ORG, "conversation organization mismatch", failures)
        expect(row["channel_account_id"] == CHANNEL_ID, "conversation channel mismatch", failures)
        expect(row["assigned_user_id"] == ASSIGNEE, "conversation assignee mismatch", failures)
        expect(row["status"] in {"OPEN", "PENDING", "CLOSED"}, "conversation status invalid", failures)
        customer_id, lead_id = row["customer_id"], row["lead_id"]
        expect(bool(customer_id) != bool(lead_id), "conversation must reference exactly one profile", failures)
        if customer_id:
            customer_count += 1
            profile = customers.get(customer_id, {})
            expect(profile.get("organization_id") == ORG and profile.get("status") == "ACTIVE" and not profile.get("archived_at"), "invalid customer FK", failures)
        if lead_id:
            lead_count += 1
            profile = leads.get(lead_id, {})
            expect(profile.get("organization_id") == ORG and profile.get("status") in {"NEW", "QUALIFIED"} and not profile.get("archived_at"), "invalid lead FK", failures)
    expect(customer_count == 35, f"expected 35 customer conversations, got {customer_count}", failures)
    expect(lead_count == 15, f"expected 15 lead conversations, got {lead_count}", failures)

    uniqueness([row["id"] for row in messages], "message ids", failures)
    uniqueness([f"{row['conversation_id']}:{row['direction']}:{row['external_message_id']}" for row in messages], "message external keys", failures)
    by_conversation: dict[str, list[dict[str, str]]] = defaultdict(list)
    inbound_ids: set[str] = set()
    contents: list[str] = []
    for row in messages:
        by_conversation[row["conversation_id"]].append(row)
        contents.append(row["content"])
        expect(row["organization_id"] == ORG, "message organization mismatch", failures)
        expect(row["conversation_id"] in conversation_by_id, "orphan message", failures)
        expect(row["direction"] in {"INBOUND", "OUTBOUND"}, "message direction invalid", failures)
        expect(row["content_type"] == "TEXT" and bool(row["content"].strip()), "message content invalid", failures)
        expect(len(row["content"]) <= 4000, "message exceeds content limit", failures)
        expect(not row["interaction_id"], "interaction_id must remain empty in this pilot", failures)
        timestamp = instant(row["occurred_at"])
        expect(WINDOW_START <= timestamp < WINDOW_END, "message timestamp outside approved window", failures)
        if row["direction"] == "INBOUND":
            inbound_ids.add(row["id"])
            expect(not row["sent_by_user_id"] and row["delivery_status"] == "RECEIVED", "inbound sender/status invalid", failures)
        else:
            expect(row["sent_by_user_id"] == ASSIGNEE and row["delivery_status"] == "SENT", "outbound sender/status invalid", failures)
    for conversation_id, items in by_conversation.items():
        uniqueness([item["content"] for item in items], f"verbatim message content in {conversation_id}", failures)
    for conversation_id, items in by_conversation.items():
        expect(4 <= len(items) <= 12, "conversation message count outside 4-12", failures)
        timestamps = [instant(row["occurred_at"]) for row in items]
        expect(timestamps == sorted(timestamps), "message chronology invalid", failures)
        conversation = conversation_by_id[conversation_id]
        inbound_times = [instant(row["occurred_at"]) for row in items if row["direction"] == "INBOUND"]
        expect(instant(conversation["last_inbound_at"]) == max(inbound_times), "last_inbound_at mismatch", failures)
        expect(instant(conversation["last_message_at"]) == timestamps[-1], "last_message_at mismatch", failures)
        expect(instant(conversation["created_at"]) == timestamps[0], "conversation created_at mismatch", failures)

    uniqueness([row["message_id"] for row in annotations], "annotation message ids", failures)
    annotation_ids = {row["message_id"] for row in annotations}
    expect(annotation_ids == inbound_ids, "annotations must match every and only inbound message", failures)
    split_conversations: dict[str, set[str]] = defaultdict(set)
    intent_counts: Counter[str] = Counter()
    sentiment_counts: Counter[str] = Counter()
    for row in annotations:
        expect(row["organization_id"] == ORG, "annotation organization mismatch", failures)
        expect(row["conversation_id"] in conversation_by_id, "annotation conversation missing", failures)
        expect(row["intent"] in INTENTS, "annotation intent invalid", failures)
        expect(row["sentiment"] in SENTIMENTS, "annotation sentiment invalid", failures)
        expect(row["split"] in {"dev", "test"}, "annotation split invalid", failures)
        split_conversations[row["split"]].add(row["conversation_id"])
        intent_counts[row["intent"]] += 1
        sentiment_counts[row["sentiment"]] += 1
    expect(len(split_conversations["dev"]) == 40 and len(split_conversations["test"]) == 10, "split must contain 40 dev and 10 test conversations", failures)
    expect(not (split_conversations["dev"] & split_conversations["test"]), "conversation split leakage", failures)
    for split, conversation_ids in split_conversations.items():
        split_intents = {row["intent"] for row in annotations if row["split"] == split}
        split_sentiments = {row["sentiment"] for row in annotations if row["split"] == split}
        expect(len(split_intents) >= 4, f"{split} intent diversity too narrow", failures)
        expect(len(split_sentiments) >= 2, f"{split} sentiment diversity too narrow", failures)
        for conversation_id in conversation_ids:
            expected_split = "dev" if conversations.index(conversation_by_id[conversation_id]) < 40 else "test"
            expect(split == expected_split, "conversation split assignment inconsistent", failures)

    privacy_patterns = [re.compile(r"(?i)posedemo@"), re.compile(r"(?i)@example\.com"), re.compile(r"(?i)(password|mật khẩu)\s*[:=]\s*\S+"), re.compile(r"\+?\d[\d .-]{8,}\d")]
    all_text = "\n".join(contents)
    for pattern in privacy_patterns:
        expect(not pattern.search(all_text), f"privacy scan matched {pattern.pattern}", failures)
    raw = b"".join((output / filename).read_bytes() for filename in FILES)
    expect(b"\xef\xbf\xbd" not in raw, "Unicode replacement character found", failures)
    expect(any(ord(char) > 127 for char in all_text), "Vietnamese Unicode text missing", failures)

    checksum_rows = (output / "SHA256SUMS.txt").read_text(encoding="ascii").splitlines()
    expected_sums = {
        filename: hashlib.sha256((output / filename).read_bytes()).hexdigest()
        for filename in FILES
    }
    actual_sums = {line.split("  ", 1)[1]: line.split("  ", 1)[0] for line in checksum_rows if "  " in line}
    expect(actual_sums == expected_sums, "SHA256SUMS.txt mismatch", failures)
    review_path = output / "edu_human_review.txt"
    expect(review_path.exists(), "edu_human_review.txt is missing", failures)
    if review_path.exists():
        expect(
            review_path.read_text(encoding="utf-8") == render_human_review(conversations, messages, annotations),
            "edu_human_review.txt does not match CSV messages and annotations",
            failures,
        )

    business = business_quality(conversations, messages, annotations)
    failures.extend(business["failures"])
    return failures, {
        "intents": intent_counts,
        "sentiments": sentiment_counts,
        "messages": Counter(total=len(messages), inbound=len(inbound_ids)),
        "workflows": business["workflows"],
    }


def business_quality(
    conversations: list[dict[str, str]],
    messages: list[dict[str, str]],
    annotations: list[dict[str, str]],
) -> dict[str, object]:
    failures: list[str] = []
    workflows: Counter[str] = Counter()
    message_by_conversation: dict[str, list[dict[str, str]]] = defaultdict(list)
    labels = {row["message_id"]: row for row in annotations}
    for row in messages:
        message_by_conversation[row["conversation_id"]].append(row)

    unsupported_patterns = [
        re.compile(r"\b\d[\d.]*\s*(đ|đồng)\b", re.IGNORECASE),
        re.compile(r"(giảm|ưu đãi|khuyến mãi).{0,30}\d", re.IGNORECASE),
        re.compile(r"(?<!không )(cam kết|đảm bảo).{0,30}(đậu|kết quả|giỏi|thành thạo)", re.IGNORECASE),
        re.compile(r"(hoàn tiền|refund|bảo lưu).{0,40}(được|sẽ|chắc chắn)", re.IGNORECASE),
    ]
    competitor_reply = re.compile(r"(trung tâm|nơi) khác.{0,50}(tốt hơn|kém hơn|rẻ hơn|đắt hơn|hơn bên em)", re.IGNORECASE)
    repeated_substantive: dict[str, Counter[str]] = {"INBOUND": Counter(), "OUTBOUND": Counter()}
    selected_profiles = profiles()
    edu_offering_names = {row["name"] for row in active_offerings().values()}
    all_offering_rows = rows(CANONICAL / "offerings.csv")
    approved_offering_rows = [
        row
        for row in all_offering_rows
        if row["organization_id"] == ORG
        and row["status"] == "ACTIVE"
        and row["code"].startswith("EDU-")
    ]
    foreign_offering_names = {
        row["name"]
        for row in all_offering_rows
        if row["organization_id"] != ORG and row["status"] == "ACTIVE"
    }
    excluded_edu_offering_names = {
        row["name"]
        for row in all_offering_rows
        if row["organization_id"] == ORG and row["name"] not in edu_offering_names
    }
    internal_role_pattern = re.compile(r"\b(admissions|academic support|customer care|học thuật)\b", re.IGNORECASE)
    unsupported_history_pattern = re.compile(r"(từng học ở trung tâm|mới vào lớp|đang học .{0,30}|sắp hoàn thành)", re.IGNORECASE)
    for ordinal, conversation in enumerate(conversations, start=1):
        profile_type, profile = selected_profiles[ordinal - 1]
        offering = offering_for(ordinal - 1, profile_type, profile)
        workflow = workflow_for(ordinal - 1, profile_type, offering)
        workflows[workflow] += 1
        items = message_by_conversation[conversation["id"]]
        conversation_text = "\n".join(item["content"] for item in items)
        expect(offering["name"] in conversation_text, f"missing verified offering in {conversation['external_thread_id']}", failures)
        expect(not any(name in conversation_text for name in foreign_offering_names), f"cross-organization offering leakage in {conversation['external_thread_id']}", failures)
        expect(not any(name in conversation_text for name in excluded_edu_offering_names), f"excluded EDU offering leakage in {conversation['external_thread_id']}", failures)
        expect(not unsupported_history_pattern.search(conversation_text), f"unsupported learning history in {conversation['external_thread_id']}", failures)
        expect(any(item["direction"] == "OUTBOUND" for item in items), f"{workflow} has no staff response", failures)
        for item in items:
            normalized = re.sub(r"\s+", " ", item["content"]).strip()
            if len(normalized) > 20:
                repeated_substantive[item["direction"]][normalized] += 1
            if item["direction"] != "OUTBOUND":
                continue
            for pattern in unsupported_patterns:
                expect(not pattern.search(item["content"]), f"unsupported business claim in {conversation['external_thread_id']}", failures)
            expect(not competitor_reply.search(item["content"]), f"staff competitor comparison in {conversation['external_thread_id']}", failures)
            expect(not internal_role_pattern.search(item["content"]), f"internal role name in {conversation['external_thread_id']}", failures)
        inbound = [item for item in items if item["direction"] == "INBOUND"]
        for item in inbound:
            label = labels[item["id"]]
            content = item["content"].lower()
            if label["intent"] == "ASK_PRICE":
                expect(any(token in content for token in ("học phí", "chi phí", "phí")), "ASK_PRICE without price context", failures)
            elif label["intent"] == "BOOKING":
                expect(any(token in content for token in ("đặt lịch", "đổi buổi", "báo đổi lịch", "xác nhận", "giờ", "sớm", "cuối tuần", "lịch họp", "học thêm", "gửi yêu cầu", "cách trao đổi về lịch")), "BOOKING without scheduling context", failures)
            elif label["intent"] == "COMPLAINT":
                expect(any(token in content for token in ("chờ", "phản ánh", "không khớp", "chưa ai", "thất vọng", "không cần", "giải thích", "nói một kiểu", "tư vấn khác", "mâu thuẫn", "nói khác nhau", "lần trước", "hai lần", "một bạn nói", "chung chung")), "COMPLAINT without dissatisfaction context", failures)
            elif label["intent"] == "PURCHASE_READY":
                expect(any(token in content for token in ("đăng ký", "gia hạn", "học tiếp", "chốt", "vẫn cân nhắc")), "PURCHASE_READY without enrollment context", failures)
            if label["sentiment"] == "NEGATIVE":
                expect(any(token in content for token in ("lo", "khó", "chờ", "chưa", "không", "thất vọng", "bận", "sợ", "cần", "nói một kiểu", "tư vấn khác", "mâu thuẫn", "nói khác nhau", "lần trước", "hai lần")), "NEGATIVE without concern marker", failures)
        for first, second in zip(items, items[1:]):
            expect(first["direction"] != second["direction"], f"repeated sender turn in {conversation['external_thread_id']}", failures)
    expect(
        workflows["admissions_new"] + workflows["admissions_parent"] + workflows["admissions_returning"] + workflows["admissions_objection"] + workflows["admissions_abandoned"] == 30,
        "admissions workflow count is not exactly 30",
        failures,
    )
    for workflow in ("onboarding", "academic_support", "schedule_change", "complaint", "renewal_retention"):
        expect(workflows[workflow] == 4, f"{workflow} count is not exactly 4", failures)
    expect(sum(workflows.values()) == 50, "workflow mapping does not cover 50 conversations", failures)
    expect(bool(edu_offering_names), "no active EDU offerings available", failures)
    expect(len(approved_offering_rows) == 23, "expected exactly 23 approved EDU offerings", failures)
    expect(len(edu_offering_names) == 23, "generator offering catalog differs from approved EDU offerings", failures)
    for direction, contents in repeated_substantive.items():
        expect(not any(count > 1 for count in contents.values()), f"duplicate substantive {direction.lower()} message", failures)
    return {"failures": failures, "workflows": workflows}


def reproducibility() -> list[str]:
    failures: list[str] = []
    with tempfile.TemporaryDirectory() as directory:
        target = Path(directory)
        generate(target)
        for filename in FILES:
            expect((HERE / filename).read_bytes() == (target / filename).read_bytes(), f"reproducibility mismatch: {filename}", failures)
    return failures


def main() -> None:
    failures, stats = validate(HERE)
    failures.extend(reproducibility())
    print(f"conversations=50 messages={stats['messages']['total']} inbound_labeled={stats['messages']['inbound']}")
    print("intent_distribution=" + ", ".join(f"{key}:{stats['intents'][key]}" for key in sorted(INTENTS)))
    print("sentiment_distribution=" + ", ".join(f"{key}:{stats['sentiments'][key]}" for key in sorted(SENTIMENTS)))
    print("workflow_distribution=" + ", ".join(f"{key}:{stats['workflows'][key]}" for key in sorted(stats["workflows"])))
    if failures:
        print("FAIL")
        for failure in failures:
            print(f"- {failure}")
        raise SystemExit(1)
    print("PASS: schema, FK, tenant, uniqueness, chronology, labels, split, Unicode, privacy, SHA-256, reproducibility")


if __name__ == "__main__":
    main()
