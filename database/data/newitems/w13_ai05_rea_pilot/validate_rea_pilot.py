#!/usr/bin/env python3
"""Validate the static W13 AI-05 REA pilot without database access."""
from __future__ import annotations

import csv
import hashlib
import re
import tempfile
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

from generate_rea_pilot import ASSIGNEE, CANONICAL, CHANNEL_ID, ORG, OUTPUT_FILES, TEAM, active_offerings, approved_dialogues, generate, offering_for, profiles, render_human_review, turns_for

HERE = Path(__file__).resolve().parent
INTENTS = {"ASK_PRICE", "ASK_INFO", "BOOKING", "PURCHASE_READY", "COMPLAINT", "SUPPORT", "OTHER"}
SENTIMENTS = {"POSITIVE", "NEUTRAL", "NEGATIVE"}
WINDOW_START = datetime(2026, 11, 10, tzinfo=timezone.utc)
WINDOW_END = datetime(2026, 11, 17, tzinfo=timezone.utc)


def rows(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def instant(value: str) -> datetime:
    return datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)


def expect(condition: bool, message: str, failures: list[str]) -> None:
    if not condition:
        failures.append(message)


def validate(output: Path) -> tuple[list[str], dict[str, Counter[str]]]:
    failures: list[str] = []
    accounts = rows(output / "channel_accounts.csv")
    conversations = rows(output / "conversations.csv")
    messages = rows(output / "conversation_messages.csv")
    annotations = rows(output / "round_1_annotations.csv")
    customers = {row["id"]: row for row in rows(CANONICAL / "customers.csv")}
    leads = {row["id"]: row for row in rows(CANONICAL / "leads.csv")}
    offerings = active_offerings()
    approved = approved_dialogues()
    expect(len(accounts) == 1, "expected one channel account", failures)
    if accounts:
        expected = {"id": CHANNEL_ID, "organization_id": ORG, "channel_type": "ZALO_SIM", "team_id": TEAM, "default_assignee_user_id": ASSIGNEE, "status": "ACTIVE", "secret_ref": "POSE_CHANNEL_ZALO_SIM_WEBHOOK_SECRET"}
        for key, value in expected.items():
            expect(accounts[0].get(key) == value, f"invalid channel account {key}", failures)
    expect(len(conversations) == 50 and len({row['id'] for row in conversations}) == 50, "invalid conversation count or IDs", failures)
    expect(len({row["external_thread_id"] for row in conversations}) == 50, "duplicate external thread ids", failures)
    by_conversation = {row["id"]: row for row in conversations}
    customer_count = lead_count = 0
    for row in conversations:
        expect(row["organization_id"] == ORG and row["channel_account_id"] == CHANNEL_ID and row["assigned_user_id"] == ASSIGNEE, "invalid conversation tenant/channel/assignee", failures)
        expect(bool(row["customer_id"]) != bool(row["lead_id"]), "conversation must have exactly one profile", failures)
        if row["customer_id"]:
            customer_count += 1
            profile = customers.get(row["customer_id"], {})
            expect(profile.get("organization_id") == ORG and profile.get("status") == "ACTIVE" and not profile.get("archived_at"), "invalid customer FK", failures)
        if row["lead_id"]:
            lead_count += 1
            profile = leads.get(row["lead_id"], {})
            expect(profile.get("organization_id") == ORG and profile.get("status") in {"NEW", "QUALIFIED"} and not profile.get("archived_at"), "invalid lead FK", failures)
    expect((customer_count, lead_count) == (35, 15), "expected 35 customers and 15 leads", failures)

    by_messages: dict[str, list[dict[str, str]]] = defaultdict(list)
    inbound_ids: set[str] = set()
    duplicates: dict[str, Counter[str]] = {"INBOUND": Counter(), "OUTBOUND": Counter()}
    for row in messages:
        by_messages[row["conversation_id"]].append(row)
        expect(row["organization_id"] == ORG and row["conversation_id"] in by_conversation, "orphan or foreign message", failures)
        expect(row["direction"] in {"INBOUND", "OUTBOUND"} and row["content_type"] == "TEXT" and bool(row["content"].strip()), "invalid message", failures)
        expect(WINDOW_START <= instant(row["occurred_at"]) < WINDOW_END, "message outside time window", failures)
        duplicates[row["direction"]][re.sub(r"\s+", " ", row["content"]).strip()] += 1
        if row["direction"] == "INBOUND":
            inbound_ids.add(row["id"])
            expect(not row["sent_by_user_id"] and row["delivery_status"] == "RECEIVED", "invalid inbound delivery", failures)
        else:
            expect(row["sent_by_user_id"] == ASSIGNEE and row["delivery_status"] == "SENT", "invalid outbound delivery", failures)
    expect(len({row["id"] for row in messages}) == len(messages), "duplicate message IDs", failures)
    for direction, values in duplicates.items():
        expect(not any(count > 1 for content, count in values.items() if len(content) > 20), f"duplicate substantive {direction.lower()} message", failures)
    for conversation_id, items in by_messages.items():
        expect(4 <= len(items) <= 12, "conversation length outside 4-12", failures)
        moments = [instant(item["occurred_at"]) for item in items]
        expect(moments == sorted(moments), "message chronology invalid", failures)
        expect(all(left["direction"] != right["direction"] for left, right in zip(items, items[1:])), "consecutive same-sender turns", failures)
        conversation = by_conversation[conversation_id]
        expect(instant(conversation["created_at"]) == moments[0] and instant(conversation["last_message_at"]) == moments[-1], "conversation timestamps invalid", failures)
        expect(instant(conversation["last_inbound_at"]) == max(instant(item["occurred_at"]) for item in items if item["direction"] == "INBOUND"), "last inbound timestamp invalid", failures)

    labels = {row["message_id"]: row for row in annotations}
    expect(set(labels) == inbound_ids and len(labels) == len(annotations), "annotation coverage invalid", failures)
    splits: dict[str, set[str]] = defaultdict(set)
    intents: Counter[str] = Counter()
    sentiments: Counter[str] = Counter()
    for row in annotations:
        expect(row["organization_id"] == ORG and row["conversation_id"] in by_conversation, "foreign annotation", failures)
        expect(row["intent"] in INTENTS and row["sentiment"] in SENTIMENTS, "invalid label vocabulary", failures)
        splits[row["split"]].add(row["conversation_id"])
        intents[row["intent"]] += 1
        sentiments[row["sentiment"]] += 1
    expect(len(splits["dev"]) == 40 and len(splits["test"]) == 10 and not (splits["dev"] & splits["test"]), "invalid dev/test split", failures)
    prohibited_claim = re.compile(r"(còn căn|đã giữ chỗ|đã duyệt vay|đã hoàn tất giao dịch|đã có lịch xem)", re.IGNORECASE)
    for index, conversation in enumerate(conversations):
        profile_type, profile = profiles()[index]
        offering = offering_for(index, profile_type, profile)
        items = by_messages[conversation["id"]]
        text = "\n".join(item["content"] for item in items)
        expect(offering["id"] in offerings and offering["status"] == "ACTIVE", f"inactive or foreign offering in {conversation['external_thread_id']}", failures)
        expected_turns = approved.get(conversation["id"], [])
        expect([(item["direction"], item["content"]) for item in items] == [(turn.direction, turn.content) for turn in expected_turns], f"approved text mismatch in {conversation['external_thread_id']}", failures)
        expect(not prohibited_claim.search(text), f"unsupported real-estate claim in {conversation['external_thread_id']}", failures)
        for item in items:
            if item["direction"] != "INBOUND":
                continue
            label = labels[item["id"]]
            content = item["content"].lower()
            if label["intent"] == "ASK_PRICE":
                expect(any(token in content for token in ("phí", "giá", "mức", "bao nhiêu", "tiền")), "ASK_PRICE without price context", failures)
            elif label["intent"] == "BOOKING":
                expect(any(token in content for token in ("đặt lịch", "khung giờ", "lịch tư vấn", "sắp xếp", "hẹn", "thời gian")), "BOOKING without scheduling context", failures)
            elif label["intent"] == "COMPLAINT":
                expect(any(token in content for token in ("phản ánh", "khó chịu", "chưa", "không", "khác nhau", "sao", "lẫn", "lệch")), "COMPLAINT without dissatisfaction context", failures)
    all_content = "\n".join(row["content"] for row in messages)
    raw = b"".join((output / filename).read_bytes() for filename in OUTPUT_FILES[:4])
    expect(b"\xef\xbf\xbd" not in raw and any(ord(char) > 127 for char in all_content), "Unicode validation failed", failures)
    expect(not re.search(r"(?i)(password|mật khẩu)\s*[:=]|@example\.com|(?:\+84|0)\d[\d .-]{8,}\d", all_content), "privacy scan failed", failures)
    expect(not re.search(r"(?i)catalog|riêng trường hợp của mình|vnd//", all_content), "known robotic wording or price format found", failures)
    expected_hashes = {filename: hashlib.sha256((output / filename).read_bytes()).hexdigest() for filename in OUTPUT_FILES}
    actual_hashes = {line.split("  ", 1)[1]: line.split("  ", 1)[0] for line in (output / "SHA256SUMS.txt").read_text(encoding="ascii").splitlines() if "  " in line}
    expect(actual_hashes == expected_hashes, "SHA256SUMS mismatch", failures)
    expect((output / "rea_human_review.txt").read_text(encoding="utf-8") == render_human_review(conversations, messages, annotations), "human review TXT mismatch", failures)
    workflows = Counter(turns_for(index, offering_for(index, profile_type, profile))[0] for index, (profile_type, profile) in enumerate(profiles()))
    expect(len(workflows) >= 6 and workflows["complaint"] >= 4, "workflow diversity too narrow", failures)
    return failures, {"messages": Counter(total=len(messages), inbound=len(inbound_ids)), "intents": intents, "sentiments": sentiments, "workflows": workflows}


def reproducibility() -> list[str]:
    failures: list[str] = []
    with tempfile.TemporaryDirectory() as directory:
        target = Path(directory)
        generate(target)
        for filename in [*OUTPUT_FILES, "SHA256SUMS.txt"]:
            if (HERE / filename).read_bytes() != (target / filename).read_bytes():
                failures.append(f"reproducibility mismatch: {filename}")
    return failures


if __name__ == "__main__":
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
    print("PASS: schema, FK, tenant, offerings, chronology, labels, split, Unicode, privacy, SHA-256, reproducibility")
