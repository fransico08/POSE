#!/usr/bin/env python3
"""Static validator for the W13 AI-05 TECH pilot."""
from __future__ import annotations

import csv
import hashlib
import tempfile
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

from generate_tech_pilot import ANNOTATION_TABLE, ASSIGNEE, CANONICAL, CHANNEL_ID, ORG, OUTPUT_FILES, SEMANTIC_REVIEW, SOURCE, TEAM, active_offerings, current_prices, generate, parse_approved, profiles, render_human_review

HERE = Path(__file__).resolve().parent
INTENTS = {"ASK_PRICE", "ASK_INFO", "BOOKING", "PURCHASE_READY", "COMPLAINT", "SUPPORT", "OTHER"}
SENTIMENTS = {"POSITIVE", "NEUTRAL", "NEGATIVE"}
START = datetime(2026, 11, 10, tzinfo=timezone.utc)
END = datetime(2026, 11, 17, tzinfo=timezone.utc)


def rows(path: Path):
    with path.open(encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def instant(value: str):
    return datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)


def validate(output: Path):
    failures = []
    approved = parse_approved()
    offerings = active_offerings()
    prices = current_prices()
    profiles_by_id = profiles()
    accounts = rows(output / "channel_accounts.csv")
    conversations = rows(output / "conversations.csv")
    messages = rows(output / "conversation_messages.csv")
    annotations = rows(output / "round_1_annotations.csv")
    if len(accounts) != 1: failures.append("channel account count")
    if accounts:
        expected = {"id": CHANNEL_ID, "organization_id": ORG, "channel_type": "ZALO_SIM", "team_id": TEAM, "default_assignee_user_id": ASSIGNEE, "status": "ACTIVE"}
        for key, value in expected.items():
            if accounts[0].get(key) != value: failures.append(f"channel account {key}")
    if len(offerings) != 32: failures.append("expected 32 ACTIVE TECH offerings")
    if len(ANNOTATION_TABLE) != 200: failures.append("explicit annotation table must contain 200 rows")
    if len(conversations) != 50 or len({row["id"] for row in conversations}) != 50: failures.append("conversation count/uniqueness")
    if len({row["external_thread_id"] for row in conversations}) != 50: failures.append("external thread uniqueness")
    by_id = {row["id"]: row for row in conversations}
    by_message = defaultdict(list)
    inbound = set()
    duplicates = Counter()
    split_ids = defaultdict(set)
    customer_count = lead_count = 0
    for row in conversations:
        if row["organization_id"] != ORG or row["channel_account_id"] != CHANNEL_ID or row["assigned_user_id"] != ASSIGNEE: failures.append("conversation tenant/channel/assignee")
        if bool(row["customer_id"]) == bool(row["lead_id"]): failures.append("profile cardinality")
        profile_id = row["customer_id"] or row["lead_id"]
        profile = profiles_by_id.get(profile_id, {})
        if profile.get("organization_id") != ORG: failures.append("profile tenant")
        if row["customer_id"]:
            customer_count += 1
            if profile.get("status") != "ACTIVE": failures.append("customer status")
        else:
            lead_count += 1
            if profile.get("status") not in {"NEW", "QUALIFIED"}: failures.append("lead status")
    if (customer_count, lead_count) != (35, 15): failures.append("35/15 profile split")
    for row in messages:
        by_message[row["conversation_id"]].append(row)
        duplicates[(row["direction"], row["content"])] += 1
        if row["organization_id"] != ORG or row["conversation_id"] not in by_id: failures.append("message FK/tenant")
        if not row["content"].strip() or row["content_type"] != "TEXT": failures.append("message content")
        if not START <= instant(row["occurred_at"]) < END: failures.append("timestamp window")
        if row["direction"] == "INBOUND":
            inbound.add(row["id"])
            if row["sent_by_user_id"] or row["delivery_status"] != "RECEIVED": failures.append("inbound delivery")
        elif row["direction"] == "OUTBOUND":
            if row["sent_by_user_id"] != ASSIGNEE or row["delivery_status"] != "SENT": failures.append("outbound delivery")
        else: failures.append("message direction")
    if len(messages) != 352 or len({row["id"] for row in messages}) != len(messages): failures.append("message count/uniqueness")
    if any(count > 1 and len(content) > 20 for (_, content), count in duplicates.items()): failures.append("duplicate substantive messages")
    labels = {row["message_id"]: row for row in annotations}
    if set(labels) != inbound or len(annotations) != len(inbound): failures.append("annotation coverage")
    for row in annotations:
        if row["organization_id"] != ORG or row["conversation_id"] not in by_id: failures.append("annotation FK/tenant")
        if row["intent"] not in INTENTS or row["sentiment"] not in SENTIMENTS: failures.append("annotation labels")
        split_ids[row["split"]].add(row["conversation_id"])
    if len(split_ids["dev"]) != 40 or len(split_ids["test"]) != 10 or split_ids["dev"] & split_ids["test"]: failures.append("dev/test split")
    for index, item in enumerate(approved):
        conversation = conversations[index]
        offering = offerings.get(item.offering_code, {})
        if offering.get("organization_id") != ORG or offering.get("status") != "ACTIVE" or offering.get("id") not in prices: failures.append(f"W12 offering {item.editorial_id}")
        actual = by_message[conversation["id"]]
        if [(row["direction"], row["content"]) for row in actual] != [(turn.direction, turn.content) for turn in item.turns]: failures.append(f"exact text {item.editorial_id}")
        if (conversation["customer_id"] or conversation["lead_id"]) != item.profile_id: failures.append(f"profile mapping {item.editorial_id}")
        moments = [instant(row["occurred_at"]) for row in actual]
        if moments != sorted(moments): failures.append(f"chronology {item.editorial_id}")
        if instant(conversation["created_at"]) != moments[0] or instant(conversation["last_message_at"]) != moments[-1]: failures.append(f"conversation timestamps {item.editorial_id}")
    raw = b"".join((output / filename).read_bytes() for filename in OUTPUT_FILES[:4])
    if b"\xef\xbf\xbd" in raw or not any(ord(char) > 127 for row in messages for char in row["content"]): failures.append("Unicode")
    expected = {filename: hashlib.sha256((output / filename).read_bytes()).hexdigest() for filename in [*OUTPUT_FILES, "SHA256SUMS.txt"] if filename != "SHA256SUMS.txt"}
    actual = {line.split("  ", 1)[1]: line.split("  ", 1)[0] for line in (output / "SHA256SUMS.txt").read_text(encoding="ascii").splitlines() if "  " in line}
    if actual != expected: failures.append("SHA256SUMS")
    if (output / "tech_human_review.txt").read_text(encoding="utf-8") != render_human_review(conversations, messages, annotations): failures.append("human review evidence")
    return failures, len(messages), len(annotations), Counter(row["intent"] for row in annotations), Counter(row["sentiment"] for row in annotations), sum("Human review required" in row["notes"] for row in annotations)


def semantic_regression_tests():
    failures = []
    cases = {
        ("TECH-001", 1): ("SUPPORT", "NEGATIVE"),  # contains “báo giá”, but is an outage report.
        ("TECH-002", 3): ("ASK_INFO", "NEGATIVE"),  # cost concern is not itself a complaint.
        ("TECH-013", 2): ("ASK_INFO", "NEUTRAL"),  # workflow detail, no pricing question.
        ("TECH-014", 1): ("COMPLAINT", "NEGATIVE"),  # repeated unanswered progress request.
        ("TECH-027", 1): ("SUPPORT", "NEGATIVE"),  # verification request about an unrecognized charge.
        ("TECH-004", 4): ("OTHER", "NEUTRAL"),  # acknowledgment/context only.
        ("TECH-050", 1): ("ASK_INFO", "NEUTRAL"),  # “báo cáo” is not ASK_PRICE.
    }
    for key, expected in cases.items():
        if ANNOTATION_TABLE.get(key) != expected:
            failures.append(f"semantic regression {key}: expected {expected}")
    return failures


def reproducibility():
    with tempfile.TemporaryDirectory() as directory:
        target = Path(directory)
        generate(target)
        return [filename for filename in [*OUTPUT_FILES, "SHA256SUMS.txt"] if (HERE / filename).read_bytes() != (target / filename).read_bytes()]


if __name__ == "__main__":
    failures, messages, annotations, intents, sentiments, review_count = validate(HERE)
    failures.extend(semantic_regression_tests())
    failures.extend(f"reproducibility: {filename}" for filename in reproducibility())
    print(f"conversations=50 messages={messages} inbound_annotations={annotations}")
    print(f"intent_distribution={dict(sorted(intents.items()))}")
    print(f"sentiment_distribution={dict(sorted(sentiments.items()))}")
    print(f"human_review_flags={review_count} unresolved_ids={[f'{key[0]}-{key[1]:02d}' for key in sorted(SEMANTIC_REVIEW)]}")
    print("semantic_regression_tests=PASS" if not semantic_regression_tests() else "semantic_regression_tests=FAIL")
    print("PASS" if not failures else "FAIL")
    for failure in failures: print(f"- {failure}")
    raise SystemExit(1 if failures else 0)
