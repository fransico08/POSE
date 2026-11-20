#!/usr/bin/env python3
"""Static and semantic validation for the W13 AI-05 RET/F&B pilot."""
from __future__ import annotations

import csv
import hashlib
import tempfile
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

from generate_ret_pilot import ANNOTATION_TABLE, ASSIGNEE, CHANNEL_ID, HERE, ORG, OUTPUT_FILES, START, TEAM, active_offerings, current_prices, generate, parse_approved, profiles, render_human_review

INTENTS = {"ASK_PRICE", "ASK_INFO", "BOOKING", "PURCHASE_READY", "COMPLAINT", "SUPPORT", "OTHER"}
SENTIMENTS = {"POSITIVE", "NEUTRAL", "NEGATIVE"}
END = datetime(2026, 11, 17, tzinfo=timezone.utc)
VALID_CONVERSATION_STATUSES = {"OPEN", "PENDING", "CLOSED"}

# These approved totals are checked against current W12 price rows.
ARITHMETIC_CASES = {
    "RET-002": (("RET-CF-COLDBREWMILK", 1), ("RET-FOOD-TOAST", 1), 68000),
    "RET-007": (("RET-MTE-GAO-RANG", 1), ("RET-FRT-DAU-TAY", 1), 52000),
    "RET-008": (("MILKTEA_S", 3), 117000),
    "RET-010": (("RET-MTE-GAO-RANG", 1), ("RET-FOOD-TOAST", 1), 49000),
    "RET-012": (("RET-FOOD-SANDWICH", 1), ("RET-CF-SUA-DA-SG", 1), 80000),
    "RET-015": (("RET-FOOD-CROISSANT", 1), ("RET-CF-PHIN-SUA", 1), 54000),
    "RET-017": (("MILKTEA_S", 2), ("RET-FOOD-CROISSANT", 1), 113000),
    "RET-018": (("RET-FOOD-SANDWICH", 8), 440000),
    "RET-020": (("RET-CF-COLDBREWMILK", 1), ("RET-FOOD-TOAST", 1), 68000),
    "RET-021": (("RET-CF-PHIN-DEN", 1), ("RET-FOOD-CROISSANT", 1), 52000),
    "RET-022": (("MILKTEA_S", 4), 156000),
    "RET-023": (("RET-CF-PHIN-SUA", 1), ("RET-FOOD-TOAST", 1), 48000),
    "RET-024": (("RET-FOOD-PAIN-FRAISE", 2), 78000),
    "RET-026": (("MILKTEA_S", 1), ("RET-FRT-DAO-CAM-SA", 1), 69000),
    "RET-027": (("RET-MTE-GAO-RANG", 1), ("RET-FOOD-TOAST", 1), 49000),
    "RET-030": (("RET-CF-COLDBREWMILK", 1), ("RET-FOOD-TOAST", 1), 68000),
    "RET-035": (("RET-CF-COLDBREWMILK", 5), 195000),
    "RET-038": (("RET-MTE-GAO-RANG", 1), ("RET-FOOD-TOAST", 1), 49000),
    "RET-039": (("RET-CF-SUA-DA-SG", 2), 50000),
    "RET-040": (("RET-FRT-MANG-CAU", 3), ("RET-FRT-DAU-TAY", 1), 122000),
    "RET-043": (("RET-CF-COLDBREWMILK", 3), 117000),
    "RET-045": (("RET-CMB-STUDENT", 2), 90000),
    "RET-047": (("RET-CF-PHIN-SUA", 1), ("RET-FOOD-CROISSANT", 1), 54000),
    "RET-048": (("RET-FOOD-SANDWICH", 2), 110000),
    "RET-050": (("RET-CF-KEM-MUOI", 1), ("RET-FOOD-CROISSANT", 1), 62000),
}


def rows(path):
    with path.open(encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def instant(value):
    return datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)


def validate(output):
    failures = []
    approved, offerings, prices, profile_rows = parse_approved(), active_offerings(), current_prices(), profiles()
    accounts = rows(output / "channel_accounts.csv"); conversations = rows(output / "conversations.csv"); messages = rows(output / "conversation_messages.csv"); annotations = rows(output / "round_1_annotations.csv")
    if len(ANNOTATION_TABLE) != 159: failures.append("explicit annotation table count")
    if len(offerings) != 49: failures.append("ACTIVE RET offering count")
    if len(accounts) != 1: failures.append("channel account count")
    if accounts and any(accounts[0].get(key) != value for key, value in {"id": CHANNEL_ID, "organization_id": ORG, "channel_type": "ZALO_SIM", "team_id": TEAM, "default_assignee_user_id": ASSIGNEE, "status": "ACTIVE"}.items()): failures.append("channel account mapping")
    if len(conversations) != 50 or len({row["id"] for row in conversations}) != 50: failures.append("conversation count/UUID uniqueness")
    by_id = {row["id"]: row for row in conversations}
    by_message = defaultdict(list)
    message_by_id = {row["id"]: row for row in messages}
    inbound = set()
    duplicate = Counter()
    split_ids = defaultdict(set)
    customers = leads = 0
    for row in conversations:
        if row["organization_id"] != ORG or row["channel_account_id"] != CHANNEL_ID or row["assigned_user_id"] != ASSIGNEE: failures.append("conversation FK/tenant")
        if row["status"] not in VALID_CONVERSATION_STATUSES: failures.append("conversation status")
        if bool(row["customer_id"]) == bool(row["lead_id"]): failures.append("profile cardinality")
        profile = profile_rows.get(row["customer_id"] or row["lead_id"], {})
        if profile.get("organization_id") != ORG: failures.append("profile tenant")
        if row["customer_id"]:
            customers += 1
            if profile.get("status") != "ACTIVE": failures.append("customer status")
        else:
            leads += 1
            if profile.get("status") not in {"NEW", "QUALIFIED"}: failures.append("lead status")
    if (customers, leads) != (35, 15): failures.append("35 Customer / 15 Lead")
    for row in messages:
        by_message[row["conversation_id"]].append(row); duplicate[(row["direction"], row["content"])] += 1
        if row["organization_id"] != ORG or row["conversation_id"] not in by_id: failures.append("message FK/tenant")
        if row["direction"] not in {"INBOUND", "OUTBOUND"} or row["content_type"] != "TEXT" or not row["content"].strip(): failures.append("message schema")
        if row["direction"] == "INBOUND" and (row["sent_by_user_id"] or row["delivery_status"] != "RECEIVED"): failures.append("inbound sender/delivery")
        if row["direction"] == "OUTBOUND" and (row["sent_by_user_id"] != ASSIGNEE or row["delivery_status"] != "SENT"): failures.append("outbound sender/delivery")
        if not START <= instant(row["occurred_at"]) < END: failures.append("timestamp window")
        if row["direction"] == "INBOUND": inbound.add(row["id"])
    if len(messages) != 331 or len({row["id"] for row in messages}) != 331: failures.append("message count/UUID uniqueness")
    repeated_substantive = [
        content
        for (_, content), count in duplicate.items()
        if count > 1
        and len(content) > 20
        and "dùng gì" not in content.lower()
        and "muốn uống gì" not in content.lower()
    ]
    if repeated_substantive:
        failures.append("duplicate substantive messages")
    label_by_message = {row["message_id"]: row for row in annotations}
    if set(label_by_message) != inbound or len(annotations) != 159: failures.append("annotation coverage")
    for row in annotations:
        message = message_by_id.get(row["message_id"])
        if row["intent"] not in INTENTS or row["sentiment"] not in SENTIMENTS or row["organization_id"] != ORG: failures.append("annotation schema")
        if not message or message["conversation_id"] != row["conversation_id"] or message["direction"] != "INBOUND": failures.append("annotation FK/direction")
        split_ids[row["split"]].add(row["conversation_id"])
    if len(split_ids["dev"]) != 40 or len(split_ids["test"]) != 10 or split_ids["dev"] & split_ids["test"]: failures.append("split")
    for index, item in enumerate(approved):
        conversation = conversations[index]; offering = offerings.get(item.offering_code, {}); actual = by_message[conversation["id"]]
        if offering.get("organization_id") != ORG or offering.get("status") != "ACTIVE" or offering.get("id") not in prices: failures.append(f"W12 offering {item.editorial_id}")
        if (conversation["customer_id"] or conversation["lead_id"]) != item.profile_id: failures.append(f"profile mapping {item.editorial_id}")
        if [(row["direction"], row["content"]) for row in actual] != [(turn.direction, turn.content) for turn in item.turns]: failures.append(f"exact text {item.editorial_id}")
        moments = [instant(row["occurred_at"]) for row in actual]
        if moments != sorted(moments) or instant(conversation["created_at"]) != moments[0] or instant(conversation["last_message_at"]) != moments[-1]: failures.append(f"chronology {item.editorial_id}")
        inbound_moments = [instant(row["occurred_at"]) for row in actual if row["direction"] == "INBOUND"]
        if instant(conversation["last_inbound_at"]) != inbound_moments[-1]: failures.append(f"last inbound {item.editorial_id}")
    raw = b"".join((output / name).read_bytes() for name in OUTPUT_FILES[:4])
    if b"\xef\xbf\xbd" in raw or not any(ord(char) > 127 for row in messages for char in row["content"]): failures.append("Unicode")
    expected = {name: hashlib.sha256((output / name).read_bytes()).hexdigest() for name in OUTPUT_FILES}; actual = {line.split("  ", 1)[1]: line.split("  ", 1)[0] for line in (output / "SHA256SUMS.txt").read_text(encoding="ascii").splitlines() if "  " in line}
    if actual != expected: failures.append("SHA256SUMS")
    if (output / "ret_human_review.txt").read_text(encoding="utf-8") != render_human_review(conversations, messages, annotations): failures.append("human review evidence")
    return failures, Counter(row["intent"] for row in annotations), Counter(row["sentiment"] for row in annotations)


def semantic_regression_tests():
    cases = {("RET-002", 1): ("ASK_PRICE", "NEUTRAL"), ("RET-013", 1): ("COMPLAINT", "NEGATIVE"), ("RET-018", 2): ("BOOKING", "NEUTRAL"), ("RET-024", 2): ("ASK_INFO", "NEUTRAL"), ("RET-033", 1): ("COMPLAINT", "NEGATIVE"), ("RET-048", 1): ("BOOKING", "NEUTRAL"), ("RET-050", 3): ("PURCHASE_READY", "NEUTRAL")}
    return [f"semantic regression {key}" for key, expected in cases.items() if ANNOTATION_TABLE.get(key) != expected]


def arithmetic_regression_tests():
    prices = current_prices()
    offering_by_code = active_offerings()
    failures = []
    for editorial_id, values in ARITHMETIC_CASES.items():
        *components, expected_total = values
        actual_total = 0
        for offering_code, quantity in components:
            offering_id = offering_by_code[offering_code]["id"]
            actual_total += int(prices[offering_id]["amount"]) * quantity
        if actual_total != expected_total:
            failures.append(f"arithmetic {editorial_id}")
    return failures


def reproducibility():
    with tempfile.TemporaryDirectory() as directory:
        target = Path(directory); generate(target)
        return [name for name in [*OUTPUT_FILES, "SHA256SUMS.txt"] if (HERE / name).read_bytes() != (target / name).read_bytes()]


if __name__ == "__main__":
    failures, intents, sentiments = validate(HERE)
    failures.extend(semantic_regression_tests())
    failures.extend(arithmetic_regression_tests())
    failures.extend(f"reproducibility {name}" for name in reproducibility())
    print("conversations=50 messages=331 inbound_annotations=159")
    print(f"intent_distribution={dict(sorted(intents.items()))}")
    print(f"sentiment_distribution={dict(sorted(sentiments.items()))}")
    print("semantic_regression_tests=PASS" if not semantic_regression_tests() else "semantic_regression_tests=FAIL")
    print("arithmetic_regression_tests=PASS" if not arithmetic_regression_tests() else "arithmetic_regression_tests=FAIL")
    print("PASS" if not failures else "FAIL")
    for failure in failures: print(f"- {failure}")
    raise SystemExit(1 if failures else 0)
