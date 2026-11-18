#!/usr/bin/env python3
"""Validate the additive W13 AI-05 Task 1 delivery package without a database."""
from __future__ import annotations

import csv
import hashlib
import json
import subprocess
import sys
import tempfile
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

from build_task1_delivery import (
    ACCOUNT_HEADERS, ANNOTATION_HEADERS, CANONICAL, CONVERSATION_HEADERS, CORE_FILES,
    HERE, MESSAGE_HEADERS, PILOTS, ROOT, build, read_csv, sha256,
)

INTENTS = {"ASK_PRICE", "ASK_INFO", "BOOKING", "PURCHASE_READY", "COMPLAINT", "SUPPORT", "OTHER"}
SENTIMENTS = {"POSITIVE", "NEUTRAL", "NEGATIVE"}
VALID_STATUSES = {"OPEN", "PENDING", "CLOSED"}
START = datetime(2026, 11, 10, tzinfo=timezone.utc)
END = datetime(2026, 11, 17, tzinfo=timezone.utc)


def instant(value: str) -> datetime:
    return datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)


def expect(condition: bool, text: str, failures: list[str]) -> None:
    if not condition:
        failures.append(text)


def run_sector_validators() -> tuple[list[str], dict[str, str]]:
    failures: list[str] = []
    results: dict[str, str] = {}
    for code, _, directory, _ in PILOTS:
        validator = ROOT / "database" / "data" / "newitems" / directory / f"validate_{directory.removeprefix('w13_ai05_').removesuffix('_pilot')}_pilot.py"
        completed = subprocess.run([sys.executable, str(validator)], cwd=validator.parent, capture_output=True, text=True, encoding="utf-8")
        output = (completed.stdout + completed.stderr).strip()
        results[code] = "PASS" if completed.returncode == 0 and "PASS" in output else "FAIL"
        if results[code] != "PASS":
            failures.append(f"{code}: source validator failed: {output[-500:]}")
    return failures, results


def validate(output: Path = HERE) -> tuple[list[str], dict[str, object]]:
    failures, sector_results = run_sector_validators()
    accounts = read_csv(output / "channel_accounts.csv")
    conversations = read_csv(output / "conversations.csv")
    messages = read_csv(output / "conversation_messages.csv")
    annotations = read_csv(output / "round_1_annotations_consolidated.csv")
    expect(list(accounts[0]) == ACCOUNT_HEADERS if accounts else False, "channel_accounts header", failures)
    expect(list(conversations[0]) == CONVERSATION_HEADERS if conversations else False, "conversations header", failures)
    expect(list(messages[0]) == MESSAGE_HEADERS if messages else False, "conversation_messages header", failures)
    expect(list(annotations[0]) == ANNOTATION_HEADERS if annotations else False, "annotations header", failures)
    expect((len(accounts), len(conversations), len(messages), len(annotations)) == (6, 300, 1927, 1002), "delivery counts", failures)

    organizations = {row["id"]: row for row in read_csv(CANONICAL / "organizations.csv")}
    customers = {row["id"]: row for row in read_csv(CANONICAL / "customers.csv")}
    leads = {row["id"]: row for row in read_csv(CANONICAL / "leads.csv")}
    teams = {row["id"]: row for row in read_csv(CANONICAL / "teams.csv")}
    users = {row["id"]: row for row in read_csv(CANONICAL / "users.csv")}
    memberships = read_csv(CANONICAL / "team_memberships.csv")
    account_by_id = {row["id"]: row for row in accounts}
    conversation_by_id = {row["id"]: row for row in conversations}
    expect(len(account_by_id) == len(accounts), "duplicate channel account ids", failures)
    expect(len(conversation_by_id) == len(conversations), "duplicate conversation ids", failures)
    expect(len({row["id"] for row in messages}) == len(messages), "duplicate message ids", failures)
    expect(len({(row["organization_id"], row["channel_type"], row["external_account_id"]) for row in accounts}) == len(accounts), "duplicate channel account keys", failures)

    for row in accounts:
        org = row["organization_id"]
        expect(organizations.get(org, {}).get("status") == "ACTIVE", f"account inactive/missing organization {row['id']}", failures)
        expect(row["channel_type"] == "ZALO_SIM" and row["status"] == "ACTIVE", f"invalid channel account {row['id']}", failures)
        expect(row["secret_ref"] == "POSE_CHANNEL_ZALO_SIM_WEBHOOK_SECRET", f"invalid secret ref {row['id']}", failures)
        team, assignee = teams.get(row["team_id"], {}), users.get(row["default_assignee_user_id"], {})
        expect(team.get("organization_id") == org and team.get("status") == "ACTIVE", f"invalid team FK {row['id']}", failures)
        expect(assignee.get("organization_id") == org and assignee.get("status") == "ACTIVE", f"invalid assignee FK {row['id']}", failures)
        expect(any(m["organization_id"] == org and m["team_id"] == row["team_id"] and m["user_id"] == row["default_assignee_user_id"] and m["status"] == "ACTIVE" and not m["left_at"] for m in memberships), f"inactive team membership {row['id']}", failures)

    messages_by_conversation: dict[str, list[dict[str, str]]] = defaultdict(list)
    inbound_ids: set[str] = set()
    for row in conversations:
        account = account_by_id.get(row["channel_account_id"], {})
        org = row["organization_id"]
        expect(account.get("organization_id") == org, f"conversation channel tenant {row['id']}", failures)
        expect(row["assigned_user_id"] == account.get("default_assignee_user_id"), f"conversation assignee {row['id']}", failures)
        expect(row["status"] in VALID_STATUSES, f"conversation status {row['id']}", failures)
        expect(bool(row["customer_id"]) != bool(row["lead_id"]), f"profile XOR {row['id']}", failures)
        profile = customers.get(row["customer_id"]) if row["customer_id"] else leads.get(row["lead_id"])
        expect(bool(profile) and profile.get("organization_id") == org and not profile.get("archived_at"), f"profile FK/tenant {row['id']}", failures)
        if row["customer_id"]:
            expect(profile.get("status") == "ACTIVE", f"customer status {row['id']}", failures)
        else:
            expect(profile.get("status") in {"NEW", "QUALIFIED"}, f"lead status {row['id']}", failures)

    for row in messages:
        messages_by_conversation[row["conversation_id"]].append(row)
        conversation = conversation_by_id.get(row["conversation_id"], {})
        expect(conversation.get("organization_id") == row["organization_id"], f"message FK/tenant {row['id']}", failures)
        expect(row["content_type"] == "TEXT" and bool(row["content"].strip()), f"message content {row['id']}", failures)
        expect(START <= instant(row["occurred_at"]) < END, f"message time window {row['id']}", failures)
        if row["direction"] == "INBOUND":
            inbound_ids.add(row["id"])
            expect(not row["sent_by_user_id"] and row["delivery_status"] == "RECEIVED", f"inbound delivery {row['id']}", failures)
        elif row["direction"] == "OUTBOUND":
            expect(row["sent_by_user_id"] == conversation.get("assigned_user_id") and row["delivery_status"] == "SENT", f"outbound delivery {row['id']}", failures)
        else:
            failures.append(f"message direction {row['id']}")

    for conversation_id, rows in messages_by_conversation.items():
        times = [instant(row["occurred_at"]) for row in rows]
        conversation = conversation_by_id[conversation_id]
        expect(times == sorted(times), f"message chronology {conversation_id}", failures)
        expect(instant(conversation["created_at"]) == times[0] and instant(conversation["last_message_at"]) == times[-1], f"conversation timestamps {conversation_id}", failures)

    annotations_by_message = {row["message_id"]: row for row in annotations}
    expect(set(annotations_by_message) == inbound_ids and len(annotations_by_message) == len(annotations), "annotation coverage/uniqueness", failures)
    splits: dict[str, set[str]] = defaultdict(set)
    for row in annotations:
        conversation = conversation_by_id.get(row["conversation_id"], {})
        message = next((item for item in messages_by_conversation.get(row["conversation_id"], []) if item["id"] == row["message_id"]), {})
        expect(conversation.get("organization_id") == row["organization_id"] == message.get("organization_id"), f"annotation FK/tenant {row['message_id']}", failures)
        expect(row["intent"] in INTENTS and row["sentiment"] in SENTIMENTS, f"annotation labels {row['message_id']}", failures)
        expect(row["split"] in {"dev", "test"}, f"annotation split {row['message_id']}", failures)
        splits[row["split"]].add(row["conversation_id"])
    expect(len(splits["dev"]) == 240 and len(splits["test"]) == 60 and not splits["dev"] & splits["test"], "dev/test isolation", failures)
    expect(b"\xef\xbf\xbd" not in (output / "conversation_messages.csv").read_bytes(), "Unicode replacement character", failures)

    manifest = json.loads((output / "source_manifest.json").read_text(encoding="utf-8"))
    for entry in manifest["sectors"]:
        base = ROOT / entry["source_directory"]
        for filename, expected_hash in entry["source_hashes"].items():
            expect(sha256(base / filename) == expected_hash, f"source drift {entry['sector']} {filename}", failures)
    for line in (output / "SHA256SUMS.txt").read_text(encoding="ascii").splitlines():
        expected_hash, filename = line.split("  ", 1)
        expect(filename in CORE_FILES and sha256(output / filename) == expected_hash, f"package hash {filename}", failures)

    evidence: dict[str, object] = {
        "overall": "PASS" if not failures else "FAIL", "counts": {"channel_accounts": len(accounts), "conversations": len(conversations), "messages": len(messages), "annotations": len(annotations)},
        "sector_source_validators": sector_results,
        "label_distribution": dict(sorted(Counter(row["intent"] for row in annotations).items())),
        "sentiment_distribution": dict(sorted(Counter(row["sentiment"] for row in annotations).items())),
        "checks": {"schema": not any("header" in item for item in failures), "fk_tenant_team": not any(any(key in item for key in ("FK", "tenant", "team", "profile", "assignee")) for item in failures), "chronology": not any("chronology" in item or "timestamps" in item for item in failures), "dev_test": not any("dev/test" in item for item in failures), "unicode": not any("Unicode" in item for item in failures), "source_hashes": not any("hash" in item or "drift" in item for item in failures)},
        "failures": failures,
    }
    (output / "validation_evidence.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return failures, evidence


def reproducibility() -> list[str]:
    with tempfile.TemporaryDirectory() as temporary:
        generated = Path(temporary)
        build(generated)
        return [name for name in CORE_FILES if (generated / name).read_bytes() != (HERE / name).read_bytes()]


if __name__ == "__main__":
    failures, evidence = validate()
    drift = reproducibility() if not failures else []
    if drift:
        failures.extend(f"non-reproducible {name}" for name in drift)
        evidence["overall"] = "FAIL"
        evidence["failures"] = failures
        (HERE / "validation_evidence.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"counts conversations={evidence['counts']['conversations']} messages={evidence['counts']['messages']} annotations={evidence['counts']['annotations']}")
    print("PASS" if not failures else "FAIL: " + "; ".join(failures))
    raise SystemExit(0 if not failures else 1)
