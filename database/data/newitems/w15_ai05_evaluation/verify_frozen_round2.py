#!/usr/bin/env python3
"""Verify frozen W13 round-two labels and prepare a blank human adjudication table."""
from __future__ import annotations

import csv
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
SEED = ROOT / "database" / "data" / "seed" / "w13_ai05"
MESSAGES = SEED / "conversation_messages.csv"
ROUND_ONE = SEED / "round_1_annotations_consolidated.csv"
FROZEN_ROUND_TWO = HERE / "round_2_codex_blind_2026-11-25.csv"
HANDOFF = HERE / "round_2_handoff_codex_blind_2026-11-25.json"
FROZEN_DISAGREEMENTS = HERE / "disagreements_codex_blind_2026-11-25.csv"
ADJUDICATION_REVIEW = HERE / "adjudication_review_2026-11-25.csv"
REPORT = HERE / "frozen_round2_verification_2026-11-25.json"

INTENTS = {
    "ASK_PRICE",
    "ASK_INFO",
    "BOOKING",
    "PURCHASE_READY",
    "COMPLAINT",
    "SUPPORT",
    "OTHER",
}
SENTIMENTS = {"POSITIVE", "NEUTRAL", "NEGATIVE"}
EXPECTED_MESSAGE_COUNT = 197
EXPECTED_DISAGREEMENT_COUNT = 104
EXPECTED_INTENT_KAPPA = 0.4327
EXPECTED_SENTIMENT_KAPPA = 0.5337
ROUND_TWO_HEADERS = (
    "message_id",
    "organization_id",
    "conversation_id",
    "sector",
    "occurred_at",
    "prior_context",
    "message_text",
    "intent",
    "sentiment",
    "annotator",
    "locked_at",
    "notes",
)
ADJUDICATION_HEADERS = (
    "message_id",
    "organization_id",
    "conversation_id",
    "sector",
    "occurred_at",
    "prior_context",
    "message_text",
    "round_1_intent",
    "round_1_sentiment",
    "round_2_intent",
    "round_2_sentiment",
    "adjudicated_intent",
    "adjudicated_sentiment",
    "approved_by",
    "approved_at",
    "rationale",
)


class VerificationError(ValueError):
    """Raised when a frozen artifact is inconsistent with the canonical W13 source."""


def read_csv(path: Path) -> tuple[tuple[str, ...], list[dict[str, str]]]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        return tuple(reader.fieldnames or ()), list(reader)


def write_csv(path: Path, headers: tuple[str, ...], rows: list[dict[str, str]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=headers, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def cohen_kappa(left: list[str], right: list[str]) -> tuple[float, float]:
    if not left or len(left) != len(right):
        raise VerificationError("Kappa requires non-empty paired labels")

    total = len(left)
    observed = sum(one == two for one, two in zip(left, right)) / total
    left_counts = Counter(left)
    right_counts = Counter(right)
    expected = sum(
        (left_counts[label] / total) * (right_counts[label] / total)
        for label in set(left_counts) | set(right_counts)
    )
    kappa = 1.0 if observed == 1.0 and expected == 1.0 else (observed - expected) / (1 - expected)
    return observed, kappa


def prior_context(message: dict[str, str], groups: dict[str, list[dict[str, str]]]) -> str:
    rows = []
    for item in groups[message["conversation_id"]]:
        if item["id"] == message["id"]:
            break
        rows.append(f"{item['direction']}: {item['content']}")
    return "\n".join(rows)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise VerificationError(message)


def main() -> None:
    handoff = json.loads(HANDOFF.read_text(encoding="utf-8"))
    expected_hash = str(handoff["round2_sha256"]).upper()
    actual_hash = sha256(FROZEN_ROUND_TWO)
    require(actual_hash == expected_hash, "Frozen round-two SHA-256 does not match handoff")
    require(handoff["message_count"] == EXPECTED_MESSAGE_COUNT, "Handoff message_count is not 197")

    message_headers, messages = read_csv(MESSAGES)
    round_one_headers, round_one = read_csv(ROUND_ONE)
    round_two_headers, round_two = read_csv(FROZEN_ROUND_TWO)
    disagreement_headers, frozen_disagreements = read_csv(FROZEN_DISAGREEMENTS)
    require(message_headers[:5] == ("id", "organization_id", "conversation_id", "direction", "external_message_id"), "Unexpected canonical message schema")
    require("message_id" in round_one_headers and "split" in round_one_headers, "Unexpected round-one schema")
    require(round_two_headers == ROUND_TWO_HEADERS, "Frozen round-two headers differ from the handoff schema")
    require(disagreement_headers == (
        "message_id", "conversation_id", "sector", "message_text", "round_1_intent",
        "round_2_intent", "round_1_sentiment", "round_2_sentiment",
    ), "Frozen disagreement headers differ from the handoff schema")

    message_by_id = {row["id"]: row for row in messages}
    require(len(message_by_id) == len(messages), "Canonical conversation_messages has duplicate IDs")
    first_by_id = {row["message_id"]: row for row in round_one if row["split"] == "test"}
    require(len(first_by_id) == EXPECTED_MESSAGE_COUNT, "Canonical round-one test split is not 197 unique messages")

    groups: dict[str, list[dict[str, str]]] = defaultdict(list)
    for message in messages:
        groups[message["conversation_id"]].append(message)
    for group in groups.values():
        group.sort(key=lambda row: (row["occurred_at"], row["id"]))

    round_two_by_id = {row["message_id"]: row for row in round_two}
    require(len(round_two) == len(round_two_by_id) == EXPECTED_MESSAGE_COUNT, "Frozen round-two message IDs are not exactly 197 unique rows")
    require(set(round_two_by_id) == set(first_by_id), "Frozen round-two IDs do not exactly match canonical test split")

    paired: list[dict[str, str]] = []
    for message_id in sorted(round_two_by_id):
        second = round_two_by_id[message_id]
        first = first_by_id[message_id]
        message = message_by_id.get(message_id)
        require(message is not None, f"Frozen message missing from canonical source: {message_id}")
        require(message["direction"] == "INBOUND", f"Frozen message is not INBOUND: {message_id}")
        require(second["organization_id"] == message["organization_id"] == first["organization_id"], f"Tenant mismatch: {message_id}")
        require(second["conversation_id"] == message["conversation_id"] == first["conversation_id"], f"Conversation mismatch: {message_id}")
        require(second["occurred_at"] == message["occurred_at"], f"Timestamp mismatch: {message_id}")
        require(second["message_text"] == message["content"], f"Message text mismatch: {message_id}")
        require(second["prior_context"] == prior_context(message, groups), f"Prior context mismatch: {message_id}")
        require(second["sector"] == first["sector"], f"Sector mismatch: {message_id}")
        require(second["intent"] in INTENTS and second["sentiment"] in SENTIMENTS, f"Invalid round-two taxonomy: {message_id}")
        require(first["intent"] in INTENTS and first["sentiment"] in SENTIMENTS, f"Invalid round-one taxonomy: {message_id}")
        require(second["annotator"] == handoff["annotator"], f"Annotator mismatch: {message_id}")
        require(second["locked_at"] == handoff["locked_at"], f"Lock timestamp mismatch: {message_id}")
        paired.append({**second, "round_1_intent": first["intent"], "round_1_sentiment": first["sentiment"]})

    require(len({row["conversation_id"] for row in paired}) == 60, "Frozen test split does not cover 60 conversations")
    require(len({row["sector"] for row in paired}) == 6, "Frozen test split does not cover six sectors")
    intent_agreement, intent_kappa = cohen_kappa(
        [row["round_1_intent"] for row in paired],
        [row["intent"] for row in paired],
    )
    sentiment_agreement, sentiment_kappa = cohen_kappa(
        [row["round_1_sentiment"] for row in paired],
        [row["sentiment"] for row in paired],
    )
    require(round(intent_kappa, 4) == EXPECTED_INTENT_KAPPA, "Intent kappa does not reproduce 0.4327")
    require(round(sentiment_kappa, 4) == EXPECTED_SENTIMENT_KAPPA, "Sentiment kappa does not reproduce 0.5337")

    disagreements = [
        row for row in paired
        if row["round_1_intent"] != row["intent"] or row["round_1_sentiment"] != row["sentiment"]
    ]
    require(len(disagreements) == EXPECTED_DISAGREEMENT_COUNT, "Disagreement count does not reproduce 104")
    frozen_by_id = {row["message_id"]: row for row in frozen_disagreements}
    require(len(frozen_disagreements) == len(frozen_by_id) == EXPECTED_DISAGREEMENT_COUNT, "Frozen disagreement file does not contain 104 unique rows")
    require(set(frozen_by_id) == {row["message_id"] for row in disagreements}, "Frozen disagreement IDs do not match reproduced disagreements")
    for row in disagreements:
        frozen = frozen_by_id[row["message_id"]]
        require(frozen["conversation_id"] == row["conversation_id"], f"Frozen disagreement conversation mismatch: {row['message_id']}")
        require(frozen["sector"] == row["sector"], f"Frozen disagreement sector mismatch: {row['message_id']}")
        require(frozen["message_text"] == row["message_text"], f"Frozen disagreement text mismatch: {row['message_id']}")
        for field in ("round_1_intent", "round_1_sentiment"):
            require(frozen[field] == row[field], f"Frozen disagreement label mismatch: {row['message_id']}")
        require(frozen["round_2_intent"] == row["intent"], f"Frozen disagreement intent mismatch: {row['message_id']}")
        require(frozen["round_2_sentiment"] == row["sentiment"], f"Frozen disagreement sentiment mismatch: {row['message_id']}")

    review_rows = []
    for row in sorted(disagreements, key=lambda item: (item["sector"], item["conversation_id"], item["occurred_at"], item["message_id"])):
        review_rows.append({
            "message_id": row["message_id"],
            "organization_id": row["organization_id"],
            "conversation_id": row["conversation_id"],
            "sector": row["sector"],
            "occurred_at": row["occurred_at"],
            "prior_context": row["prior_context"],
            "message_text": row["message_text"],
            "round_1_intent": row["round_1_intent"],
            "round_1_sentiment": row["round_1_sentiment"],
            "round_2_intent": row["intent"],
            "round_2_sentiment": row["sentiment"],
            "adjudicated_intent": "",
            "adjudicated_sentiment": "",
            "approved_by": "",
            "approved_at": "",
            "rationale": "",
        })
    write_csv(ADJUDICATION_REVIEW, ADJUDICATION_HEADERS, review_rows)

    sector_counts = Counter(row["sector"] for row in paired)
    report = {
        "source_revision": "origin/Thắng:e6ec2d8eaff40ce1e49bbbeccf4c55d0d919e364",
        "round2_csv": FROZEN_ROUND_TWO.name,
        "round2_sha256": actual_hash,
        "handoff_sha256": sha256(HANDOFF),
        "frozen_disagreements_sha256": sha256(FROZEN_DISAGREEMENTS),
        "message_count": len(paired),
        "conversation_count": len({row["conversation_id"] for row in paired}),
        "sector_counts": dict(sorted(sector_counts.items())),
        "intent_agreement": round(intent_agreement, 6),
        "intent_kappa": round(intent_kappa, 4),
        "sentiment_agreement": round(sentiment_agreement, 6),
        "sentiment_kappa": round(sentiment_kappa, 4),
        "disagreement_count": len(disagreements),
        "adjudication_review": ADJUDICATION_REVIEW.name,
        "gold_or_f1_created": False,
    }
    with REPORT.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=True))


if __name__ == "__main__":
    try:
        main()
    except (VerificationError, FileNotFoundError, KeyError, json.JSONDecodeError) as error:
        raise SystemExit(f"FAIL: {error}") from error
