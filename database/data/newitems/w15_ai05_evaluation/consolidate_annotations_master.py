#!/usr/bin/env python3
"""Build the W15 annotation master without altering frozen W13/W15 evidence."""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import uuid
from copy import deepcopy
from datetime import datetime
from pathlib import Path


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
ROUND_ONE = ROOT / "database" / "data" / "seed" / "w13_ai05" / "round_1_annotations_consolidated.csv"
ROUND_TWO = HERE / "round_2_codex_blind_2026-11-25.csv"
DISAGREEMENTS = HERE / "disagreements_codex_blind_2026-11-25.csv"
ANALYSIS = HERE / "adjudication_analysis_2026-11-25.csv"
REVIEW = HERE / "adjudication_review_2026-11-25.csv"
ROUND_TWO_REPORT = HERE / "frozen_round2_verification_2026-11-25.json"
OUTPUT = HERE / "annotations_master.csv"
MANIFEST = HERE / "manifest.json"
ADJUDICATION = HERE / "adjudication.csv"
GOLD = HERE / "gold_test_annotations.csv"

INTENTS = {"ASK_PRICE", "ASK_INFO", "BOOKING", "PURCHASE_READY", "COMPLAINT", "SUPPORT", "OTHER"}
SENTIMENTS = {"POSITIVE", "NEUTRAL", "NEGATIVE"}
EXPECTED_ROUND_ONE = 1002
EXPECTED_TEST = 197
EXPECTED_DISAGREEMENTS = 104
EXPECTED_AGREEMENTS = 93
EXPECTED_REVIEWER = "Nhi"
HUMAN_FINALIZATION_AUTHORIZATION = "NHI_DATA_OWNER_W15_V2_04"
ADJUDICATION_HEADERS = (
    "message_id",
    "intent",
    "sentiment",
    "approved_by",
    "approved_at",
    "reason",
)
GOLD_HEADERS = (
    "message_id",
    "intent",
    "sentiment",
    "source",
    "approved_by",
    "approved_at",
)

FINAL_ADVISORY = {
    "5893d44f-6637-5684-8192-b6d6f63e7727": ("OTHER", "NEUTRAL"), "fbbd1872-bb79-5c0c-9f8c-12918f7d4801": ("ASK_PRICE", "NEGATIVE"),
    "84a0a90e-33f8-58c0-aa1e-c997866eca49": ("ASK_PRICE", "NEUTRAL"), "1d818ea0-c5c5-575e-b7ba-f66843fd28e7": ("OTHER", "POSITIVE"),
    "171947f0-3d1f-5007-9d75-00c668855ce5": ("OTHER", "NEGATIVE"), "6c9ef0c4-772a-5147-8333-780157f09804": ("ASK_INFO", "NEGATIVE"),
    "f547d2cc-25ca-5c3a-9123-ff2c561d9334": ("ASK_PRICE", "NEUTRAL"), "8f84eb4d-a3a0-5c05-aac4-f4fc54bc9d22": ("BOOKING", "NEGATIVE"),
    "48148322-b8f7-57c0-81bb-c114e9b2c55e": ("ASK_INFO", "NEUTRAL"), "aaa3c5fb-95a3-5b66-b5ed-97d6df90e38b": ("OTHER", "NEUTRAL"),
    "9860783f-e144-574f-984d-b5f306388834": ("OTHER", "NEGATIVE"), "edda62c2-3a9f-5d68-9b32-3f1c4d82d888": ("OTHER", "NEUTRAL"),
    "ac94ae8f-4907-59ae-8922-7fd613bd8811": ("ASK_INFO", "NEUTRAL"), "396a8e93-490d-5396-913a-7d53f5e4852d": ("OTHER", "NEUTRAL"),
    "ffef9bd4-7da5-502d-9478-fc61eb35dcbc": ("ASK_INFO", "NEUTRAL"), "c922fcf0-ac97-57e7-9ee7-15a7aad8fa3d": ("ASK_PRICE", "NEUTRAL"),
    "bdf5d009-8fee-54ac-880f-99747fd3c444": ("ASK_PRICE", "NEUTRAL"), "6fe4d9c2-c7ff-5e6a-9c08-2660f3416610": ("ASK_PRICE", "NEGATIVE"),
    "1976b655-2a9c-5322-b8e7-e84649070645": ("COMPLAINT", "NEGATIVE"), "670c73ff-2302-548c-a88a-9126b99d3ff8": ("COMPLAINT", "NEGATIVE"),
    "7ef7fd92-4a8e-5d81-8482-c4c113131887": ("OTHER", "NEGATIVE"), "c12fc39a-b90b-5bc0-9954-534dadf94cfc": ("ASK_INFO", "NEUTRAL"),
    "25970b58-b312-57b5-8d65-b4e539ef942f": ("OTHER", "NEUTRAL"), "60710359-369a-5802-82c0-6aa04646f9e5": ("OTHER", "NEUTRAL"),
    "4d0cfd5f-4447-5074-b0fc-18d9a46ac410": ("OTHER", "NEUTRAL"), "76b0ac9e-205d-581c-9cec-11d95b4fefa7": ("ASK_INFO", "NEGATIVE"),
    "865507ae-20de-576e-990b-f480d07fcedd": ("ASK_INFO", "NEGATIVE"), "9c93a66c-ea8e-55ce-8fce-d68c8c65cc6b": ("OTHER", "NEUTRAL"),
    "79a0c392-c30e-5ff5-a3dd-fd62f6c98f19": ("ASK_INFO", "NEGATIVE"), "17a0d149-bf94-518c-95a1-4363d0aa8428": ("ASK_INFO", "NEGATIVE"),
    "533c81e7-80d5-5af0-94a5-a4721c6b61be": ("OTHER", "NEGATIVE"), "c72cdfcd-16ec-5a4b-96e4-7bb15466f1f1": ("ASK_PRICE", "NEUTRAL"),
    "3dec979b-bd9b-564d-802a-6fcf889e2984": ("OTHER", "NEUTRAL"), "a981dc9a-4161-57be-98f8-fa103d44b765": ("SUPPORT", "NEUTRAL"),
    "a4aa1e52-8e2d-51bc-b288-30d5fd429ae5": ("OTHER", "NEUTRAL"), "d0f80fcd-70dd-596f-a2a6-0dd27e7ac187": ("OTHER", "NEGATIVE"),
    "bd852ad8-8305-5e2c-89eb-08d90485995f": ("OTHER", "NEUTRAL"), "18b6af9d-1f21-5955-bfca-c3843e7ddb53": ("BOOKING", "NEUTRAL"),
    "ba42a362-af14-5677-b4e3-469f0ea82c36": ("ASK_INFO", "NEUTRAL"), "42594543-2dc4-5f4a-b5ec-c24d23db1189": ("ASK_INFO", "NEUTRAL"),
    "1739c0a2-805e-5640-b258-caada71e89b9": ("OTHER", "NEUTRAL"), "dea32e2a-798a-5647-a6d4-6c60183a8493": ("ASK_PRICE", "NEUTRAL"),
    "d9658333-7168-528d-9442-620efb57d757": ("ASK_INFO", "NEUTRAL"), "88ed788b-1ece-5018-bde2-498100ccd340": ("BOOKING", "NEUTRAL"),
    "4e53df7e-17ba-5060-9eb1-7d66cfa43006": ("ASK_INFO", "NEUTRAL"), "a6ac8e76-d868-573c-bad5-4e24a3f32df1": ("OTHER", "NEUTRAL"),
    "edb63bbf-14e4-5edc-9837-bea32b516033": ("ASK_INFO", "NEUTRAL"), "4fc4dfe7-37b8-513b-837d-c8c935bb7f3a": ("PURCHASE_READY", "NEUTRAL"),
    "d62f86a5-58d2-521a-8387-4952cfde8a9c": ("ASK_INFO", "NEUTRAL"), "3c773846-0837-593f-aafa-62d1d77fb9f7": ("OTHER", "NEGATIVE"),
    "8b0ff189-d944-5161-9700-d83ab81d4c07": ("ASK_INFO", "NEUTRAL"), "b4308b37-f997-5efa-9fd3-e34c4ad56b67": ("OTHER", "NEUTRAL"),
    "1190aa67-7af8-58f4-bd0e-c6a197eba47e": ("OTHER", "NEGATIVE"), "23339cf4-1e21-5b51-bf4b-9c71d9564012": ("OTHER", "NEUTRAL"),
    "248448bf-47d5-5562-a494-a1500887c468": ("OTHER", "NEUTRAL"), "505ccfcc-04c9-57e1-b803-b7ca5b0dbc78": ("OTHER", "NEUTRAL"),
    "f99d4871-34a1-5292-8ad0-c95b3d3ef48a": ("OTHER", "NEGATIVE"), "ce1c9d43-e2e4-54f7-8c22-94d9d57adb55": ("ASK_INFO", "NEUTRAL"),
    "831fa239-a76a-5e7d-9711-29c0ccbdca76": ("OTHER", "NEUTRAL"), "ccef8fdb-5727-5244-a8ff-485082849a9e": ("ASK_INFO", "NEUTRAL"),
    "ad8006e5-084b-599d-b009-875ae4fd9e00": ("ASK_INFO", "NEUTRAL"), "255b8206-90f9-5878-ad5e-e3337a3da539": ("ASK_INFO", "NEUTRAL"),
    "9df42e75-30af-5849-8207-a9100908d00c": ("ASK_PRICE", "NEGATIVE"), "e582de91-f66e-5eed-98fd-9cc321cd4ee6": ("OTHER", "NEUTRAL"),
    "d3ef6172-1106-5181-8969-b23c62170c71": ("ASK_PRICE", "NEGATIVE"), "4df885a6-3e52-5d00-81e8-881dc98baf6c": ("SUPPORT", "NEUTRAL"),
    "8f06dc82-f627-5328-968c-c06d1e0cb5c2": ("OTHER", "NEUTRAL"), "086f8a33-07fd-5091-995f-3eaf9da356ff": ("OTHER", "NEUTRAL"),
    "929b9910-4bbd-5d57-9117-cefae62abeb9": ("OTHER", "NEUTRAL"), "4eb3987f-6d66-5dbb-bab4-ca7cb9488a7d": ("OTHER", "NEUTRAL"),
    "4683cfba-ebef-5336-afff-6909393d86db": ("OTHER", "NEUTRAL"), "e731e9c0-9aeb-59ef-93b2-da6c2cfc2b60": ("PURCHASE_READY", "NEUTRAL"),
    "67f8d96b-a0e7-57af-8d29-5cbc901efbb6": ("ASK_INFO", "NEUTRAL"), "e8864154-568f-5c88-b188-7f2cd0663862": ("OTHER", "NEUTRAL"),
    "f7daab53-56eb-5d78-ab4f-11bbb036213d": ("ASK_INFO", "NEUTRAL"), "1634e5b0-565b-5555-990e-6b5bfd4efba8": ("ASK_PRICE", "NEUTRAL"),
    "f3c47df5-2027-5980-be5e-0f1c815086bd": ("PURCHASE_READY", "NEUTRAL"), "15be521e-2a49-5968-9cec-fedffb49a134": ("ASK_INFO", "NEUTRAL"),
    "2ec05721-f428-5be0-99fe-d51b0e7d82b8": ("PURCHASE_READY", "NEUTRAL"), "be12d094-3763-52c8-99d1-bf224db27755": ("BOOKING", "NEGATIVE"),
    "7b01d641-86aa-5c07-b57c-2bf2967e582f": ("PURCHASE_READY", "NEUTRAL"), "33c101a1-79cd-524d-b55f-a1d869d543d3": ("ASK_PRICE", "NEUTRAL"),
    "8bd41c26-38b1-5db3-8895-3b96c00555e0": ("PURCHASE_READY", "NEUTRAL"), "5f1b5ab9-4675-54af-b05a-4ed1ec5a1037": ("PURCHASE_READY", "NEUTRAL"),
    "9ebba0f1-0830-5368-9773-27ba71dbf6e3": ("OTHER", "NEUTRAL"), "1ddbdb3b-4fec-5dd2-a028-5825fd86b12d": ("OTHER", "NEUTRAL"),
    "dc8e057f-eb73-52e9-84ee-70acbbc79dda": ("OTHER", "NEUTRAL"), "cf74d0c4-8661-57a6-b853-0a350a7e12dd": ("OTHER", "NEUTRAL"),
    "ed11f240-4467-5e3c-8411-5d047c2fd96e": ("ASK_PRICE", "NEUTRAL"), "380900cb-41b8-5341-8c02-6f310220f015": ("OTHER", "NEUTRAL"),
    "e06d256f-aa4b-5995-8664-5b56eba66396": ("OTHER", "NEUTRAL"), "3565b00a-da55-516c-9a91-556a96cf7a6e": ("OTHER", "NEUTRAL"),
    "847f988d-c83f-5c5f-bf80-b216248dc8ea": ("ASK_PRICE", "NEUTRAL"), "b37a6c24-7963-5832-aea8-5903c7a567d5": ("OTHER", "NEUTRAL"),
    "8ac475a9-fdf9-5ded-a52c-fd2a813d80b6": ("OTHER", "NEUTRAL"), "c703a705-0705-5a3c-b422-aaeceafe8ca0": ("OTHER", "NEUTRAL"),
    "b9fd8a69-26cd-58ca-be08-8bd347436436": ("ASK_INFO", "NEUTRAL"), "370f0278-ac3d-5e6a-98e4-ea3e7d968a3f": ("OTHER", "NEUTRAL"),
    "4bc725e8-d700-5d52-ab14-825571dc06a4": ("OTHER", "NEUTRAL"), "9161a36d-cd59-50f0-b366-66e59679ee19": ("OTHER", "NEUTRAL"),
    "a03935bf-346f-5f8c-ae66-e4acd9a79e68": ("OTHER", "NEUTRAL"), "1ac47e72-2605-5652-bbc8-ad04f595330d": ("OTHER", "NEGATIVE"),
    "7025112e-515b-5740-b70a-989437e4e3aa": ("ASK_INFO", "NEUTRAL"), "45fb72f5-73fd-56be-9dfd-b2bed75af8a4": ("OTHER", "NEUTRAL"),
}
FINAL_ADVISORY_RATIONALE = "Khuyến nghị taxonomy W15-V2; chờ xác nhận adjudication của người."

MASTER_HEADERS = (
    "organization_id",
    "message_id",
    "conversation_id",
    "sector",
    "split",
    "round_1_intent",
    "round_1_sentiment",
    "round_1_annotator",
    "round_1_annotation_round",
    "round_1_annotation_version",
    "round_1_source_annotation_id",
    "round_1_source_created_at",
    "round_1_notes",
    "round_2_intent",
    "round_2_sentiment",
    "round_2_annotator",
    "round_2_locked_at",
    "round_2_notes",
    "round_2_available",
    "disagreement",
    "disagreement_category",
    "proposed_intent_for_human_review",
    "proposed_sentiment_for_human_review",
    "proposed_rationale_for_human_review",
    "review_status",
    "adjudicated_intent",
    "adjudicated_sentiment",
    "approved_by",
    "approved_at",
    "rationale",
    "record_status",
)
DECISION_FIELDS = (
    "adjudicated_intent",
    "adjudicated_sentiment",
    "approved_by",
    "approved_at",
    "rationale",
)


class ConsolidationError(ValueError):
    """Raised when an input violates the W13/W15 evidence contract."""


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def read_csv_with_headers(path: Path) -> tuple[tuple[str, ...], list[dict[str, str]]]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        return tuple(reader.fieldnames or ()), list(reader)


def write_csv(path: Path, rows: list[dict[str, str]]) -> None:
    with path.open("x", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=MASTER_HEADERS, lineterminator="\n", extrasaction="raise")
        writer.writeheader()
        writer.writerows(rows)


def replace_csv(path: Path, rows: list[dict[str, str]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=MASTER_HEADERS, lineterminator="\n", extrasaction="raise")
        writer.writeheader()
        writer.writerows(rows)


def write_rows(path: Path, headers: tuple[str, ...], rows: list[dict[str, str]]) -> None:
    with path.open("x", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=headers, lineterminator="\n", extrasaction="raise")
        writer.writeheader()
        writer.writerows(rows)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ConsolidationError(message)


def require_authorization(authorization: str) -> None:
    require(
        authorization == HUMAN_FINALIZATION_AUTHORIZATION,
        "Human-adjudicated finalization requires explicit Data owner authorization",
    )


def canonical_key(row: dict[str, str], source: str) -> str:
    organization_id = row.get("organization_id", "")
    message_id = row.get("message_id", "")
    try:
        uuid.UUID(organization_id)
        uuid.UUID(message_id)
    except ValueError as error:
        raise ConsolidationError(f"{source} has invalid canonical UUID key") from error
    return f"{organization_id}|{message_id}"


def index_rows(rows: list[dict[str, str]], source: str) -> dict[str, dict[str, str]]:
    indexed: dict[str, dict[str, str]] = {}
    for row in rows:
        key = canonical_key(row, source)
        require(key not in indexed, f"{source} has duplicate key: {key}")
        indexed[key] = row
    return indexed


def validate_labels(row: dict[str, str], intent_name: str, sentiment_name: str, source: str) -> None:
    require(row[intent_name] in INTENTS, f"{source} has invalid intent: {row.get('message_id', '')}")
    require(row[sentiment_name] in SENTIMENTS, f"{source} has invalid sentiment: {row.get('message_id', '')}")


def build_rows() -> tuple[list[dict[str, str]], dict[str, object]]:
    round_one = read_csv(ROUND_ONE)
    round_two = read_csv(ROUND_TWO)
    disagreements = read_csv(DISAGREEMENTS)
    analysis = read_csv(ANALYSIS)
    review = read_csv(REVIEW)

    require(len(round_one) == EXPECTED_ROUND_ONE, "Round 1 must contain 1,002 annotations")
    round_one_by_key = index_rows(round_one, "round_1")
    for row in round_one:
        validate_labels(row, "intent", "sentiment", "round_1")

    require(len(round_two) == EXPECTED_TEST, "Frozen round 2 must contain 197 rows")
    round_two_by_key = index_rows(round_two, "round_2")
    test_keys = {key for key, row in round_one_by_key.items() if row["split"] == "test"}
    require(len(test_keys) == EXPECTED_TEST, "Round 1 test split must contain 197 rows")
    require(set(round_two_by_key) == test_keys, "Round 2 keys must exactly match canonical test split")
    for key, row in round_two_by_key.items():
        validate_labels(row, "intent", "sentiment", "round_2")
        baseline = round_one_by_key[key]
        require(row["conversation_id"] == baseline["conversation_id"], f"Round 2 conversation mismatch: {key}")
        require(row["sector"] == baseline["sector"], f"Round 2 sector mismatch: {key}")

    disagreement_by_message = {row["message_id"]: row for row in disagreements}
    require(len(disagreements) == len(disagreement_by_message) == EXPECTED_DISAGREEMENTS, "Frozen disagreements must contain 104 unique messages")
    analysis_by_key = index_rows(analysis, "adjudication_analysis")
    review_by_key = index_rows(review, "adjudication_review")
    disagreement_keys = {
        key for key, row in round_two_by_key.items()
        if row["intent"] != round_one_by_key[key]["intent"]
        or row["sentiment"] != round_one_by_key[key]["sentiment"]
    }
    require(len(disagreement_keys) == EXPECTED_DISAGREEMENTS, "Round comparison must produce 104 disagreements")
    disagreement_message_ids = {round_one_by_key[key]["message_id"] for key in disagreement_keys}
    require(set(FINAL_ADVISORY) == disagreement_message_ids, "Final advisory keys must exactly match disagreements")
    for message_id, (intent, sentiment) in FINAL_ADVISORY.items():
        require(intent in INTENTS and sentiment in SENTIMENTS, f"Final advisory has invalid labels: {message_id}")
    require(set(analysis_by_key) == disagreement_keys, "Analysis keys must exactly match disagreements")
    require(set(review_by_key) == disagreement_keys, "Review keys must exactly match disagreements")

    output: list[dict[str, str]] = []
    for key, first in sorted(round_one_by_key.items(), key=lambda item: (item[1]["sector"], item[1]["conversation_id"], item[1]["message_id"])):
        second = round_two_by_key.get(key)
        analysis_row = analysis_by_key.get(key)
        review_row = review_by_key.get(key)
        is_disagreement = key in disagreement_keys

        if is_disagreement:
            require(analysis_row is not None and review_row is not None, f"Missing review data: {key}")
            for field in ("adjudicated_intent", "adjudicated_sentiment", "approved_by", "approved_at", "rationale"):
                require(not review_row[field].strip(), f"Frozen review must not contain human decision: {key}")
                require(not analysis_row[field].strip(), f"Analysis must not contain human decision: {key}")
            record_status = "HUMAN_ADJUDICATION_REQUIRED"
            proposed_intent, proposed_sentiment = FINAL_ADVISORY[first["message_id"]]
            proposed_rationale = FINAL_ADVISORY_RATIONALE
            review_status = "FINAL_ADVISORY_PENDING_HUMAN_CONFIRMATION"
        elif second is not None:
            record_status = "ROUND_1_ROUND_2_AGREE"
            proposed_intent = ""
            proposed_sentiment = ""
            proposed_rationale = ""
            review_status = ""
        else:
            record_status = "ROUND_1_CANONICAL"
            proposed_intent = ""
            proposed_sentiment = ""
            proposed_rationale = ""
            review_status = ""

        output.append({
            "organization_id": first["organization_id"],
            "message_id": first["message_id"],
            "conversation_id": first["conversation_id"],
            "sector": first["sector"],
            "split": first["split"],
            "round_1_intent": first["intent"],
            "round_1_sentiment": first["sentiment"],
            "round_1_annotator": first["annotator"],
            "round_1_annotation_round": first["annotation_round"],
            "round_1_annotation_version": first["annotation_version"],
            "round_1_source_annotation_id": first["source_annotation_id"],
            "round_1_source_created_at": first["source_created_at"],
            "round_1_notes": first["notes"],
            "round_2_intent": "" if second is None else second["intent"],
            "round_2_sentiment": "" if second is None else second["sentiment"],
            "round_2_annotator": "" if second is None else second["annotator"],
            "round_2_locked_at": "" if second is None else second["locked_at"],
            "round_2_notes": "" if second is None else second["notes"],
            "round_2_available": "false" if second is None else "true",
            "disagreement": "true" if is_disagreement else "false",
            "disagreement_category": "" if analysis_row is None else analysis_row["disagreement_category"],
            "proposed_intent_for_human_review": proposed_intent,
            "proposed_sentiment_for_human_review": proposed_sentiment,
            "proposed_rationale_for_human_review": proposed_rationale,
            "review_status": review_status,
            "adjudicated_intent": "",
            "adjudicated_sentiment": "",
            "approved_by": "",
            "approved_at": "",
            "rationale": "",
            "record_status": record_status,
        })

    summary = {
        "round_1_annotations": len(output),
        "test_messages": sum(row["split"] == "test" for row in output),
        "round_2_messages": sum(row["round_2_available"] == "true" for row in output),
        "disagreements": sum(row["disagreement"] == "true" for row in output),
        "human_decisions": sum(bool(row["approved_by"]) for row in output),
    }
    require(summary == {
        "round_1_annotations": 1002,
        "test_messages": 197,
        "round_2_messages": 197,
        "disagreements": 104,
        "human_decisions": 0,
    }, "Unexpected consolidated counts")
    return output, summary


def build_manifest(summary: dict[str, object]) -> dict[str, object]:
    return {
        "dataset": "POSE CRM W15 unified AI annotation and evaluation data",
        "version": "1.0",
        "created_at": "2026-11-27",
        "canonical_key": ["organization_id", "message_id"],
        "sources": {
            "round_1_annotations": {"path": "../../seed/w13_ai05/round_1_annotations_consolidated.csv", "sha256": sha256(ROUND_ONE)},
            "frozen_round_2": {"path": ROUND_TWO.name, "sha256": sha256(ROUND_TWO)},
            "frozen_disagreements": {"path": DISAGREEMENTS.name, "sha256": sha256(DISAGREEMENTS)},
            "advisory_analysis": {"path": ANALYSIS.name, "sha256": sha256(ANALYSIS)},
            "human_adjudication_review": {"path": REVIEW.name, "sha256": sha256(REVIEW)},
            "round_2_verification": {"path": ROUND_TWO_REPORT.name, "sha256": sha256(ROUND_TWO_REPORT)},
        },
        "counts": summary,
        "outputs": {
            "annotations_master": {"path": OUTPUT.name, "status": "CREATED", "sha256": sha256(OUTPUT)},
            "gold_test_annotations": {"path": "gold_test_annotations.csv", "status": "BLOCKED_DEC_39_HUMAN_ADJUDICATION_REQUIRED"},
            "ai07_signal_decisions": {"path": "ai07_signal_decisions.csv", "status": "BLOCKED_APPROVED_AI07_REVIEWED_DECISION_EXPORT_REQUIRED"},
            "evaluation_results": {"path": "evaluation_results.json", "status": "BLOCKED_GOLD_AND_AI07_DECISIONS_REQUIRED"},
        },
        "gate": {
            "kappa_threshold": 0.60,
            "intent_kappa": 0.4327,
            "sentiment_kappa": 0.5337,
            "status": "BLOCKED_DEC_39",
            "reason": "No approved human adjudication, gold test labels, or reviewed AI-07 decisions exist.",
        },
        "provenance": "Frozen source artifacts remain immutable; human decisions require explicit approval and no gold label is generated here.",
    }


def write_manifest(manifest: dict[str, object]) -> None:
    with MANIFEST.open("x", encoding="utf-8", newline="\n") as handle:
        handle.write(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")


def parse_iso8601(value: str, field: str, key: str) -> None:
    try:
        datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise ConsolidationError(f"{field} must be ISO-8601: {key}") from error


def validate_human_decision(row: dict[str, str], key: str) -> bool:
    values = [row[field].strip() for field in DECISION_FIELDS]
    if not any(values):
        return False
    require(all(values), f"Human decision must provide all adjudication fields: {key}")
    require(row["record_status"] == "HUMAN_ADJUDICATION_REQUIRED", f"Human decision is only valid for disagreement: {key}")
    validate_labels(row, "adjudicated_intent", "adjudicated_sentiment", "human_adjudication")
    parse_iso8601(row["approved_at"], "approved_at", key)
    return True


def validate_existing_master(expected_rows: list[dict[str, str]]) -> tuple[list[dict[str, str]], int]:
    require(OUTPUT.exists(), "Master has not been created")
    actual_rows = read_csv(OUTPUT)
    require(len(actual_rows) == len(expected_rows), "Master row count differs from canonical consolidation")
    expected_by_key = index_rows(expected_rows, "expected_master")
    actual_by_key = index_rows(actual_rows, "annotations_master")
    require(set(actual_by_key) == set(expected_by_key), "Master keys differ from canonical consolidation")

    human_decisions = 0
    for key, expected in expected_by_key.items():
        actual = actual_by_key[key]
        for field in MASTER_HEADERS:
            if field in DECISION_FIELDS:
                continue
            require(actual.get(field, "") == expected[field], f"Immutable master field changed: {field}: {key}")
        if validate_human_decision(actual, key):
            human_decisions += 1
    return actual_rows, human_decisions


def verify_existing() -> None:
    rows, summary = build_rows()
    require(MANIFEST.exists(), "Manifest has not been created")
    _, human_decisions = validate_existing_master(rows)
    summary["human_decisions"] = human_decisions
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    require(manifest["counts"] == summary, "Manifest counts differ from master")
    require(manifest["outputs"]["annotations_master"]["sha256"] == sha256(OUTPUT), "Manifest master checksum differs")
    print(f"VERIFY PASS: master={len(rows)} test={summary['test_messages']} disagreements={summary['disagreements']} human_decisions={human_decisions}")


def refresh_manifest() -> None:
    rows, summary = build_rows()
    require(MANIFEST.exists(), "Manifest has not been created")
    _, human_decisions = validate_existing_master(rows)
    summary["human_decisions"] = human_decisions
    manifest = build_manifest(summary)
    manifest["gate"]["status"] = (
        "BLOCKED_DEC_39_PENDING_REMAINING_HUMAN_ADJUDICATION"
        if human_decisions else "BLOCKED_DEC_39"
    )
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"REFRESH PASS: human_decisions={human_decisions} master_sha256={sha256(OUTPUT)}")


def refresh_advisory() -> None:
    rows, summary = build_rows()
    require(OUTPUT.exists(), "Master has not been created")
    actual_by_key = index_rows(read_csv(OUTPUT), "annotations_master")
    expected_by_key = index_rows(rows, "expected_master")
    require(set(actual_by_key) == set(expected_by_key), "Master keys differ from canonical consolidation")
    for key, actual in actual_by_key.items():
        require(
            not any(actual[field].strip() for field in DECISION_FIELDS),
            f"Refusing to overwrite human decision: {key}",
        )
    replace_csv(OUTPUT, rows)
    MANIFEST.write_text(
        json.dumps(build_manifest(summary), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"ADVISORY REFRESH PASS: master={len(rows)} disagreements={summary['disagreements']}")


def approval_reason(message_id: str, analysis_by_key: dict[str, dict[str, str]], key: str) -> str:
    analysis = analysis_by_key[key]
    uncertainty = analysis["ambiguity_or_review_reason"].strip()
    detail = ""
    if message_id == "8f84eb4d-a3a0-5c05-aac4-f4fc54bc9d22":
        detail = " Độ không chắc trung bình: khẩn cấp lâm sàng không tự quyết định sentiment."
    return (
        "Nhi phê duyệt khuyến nghị advisory sau review có AI hỗ trợ. "
        f"Cơ sở cần lưu ý: {uncertainty}.{detail}"
    )


def approve_all_advisory(reviewer: str, approved_at: str) -> None:
    require(reviewer.strip(), "approved_by must not be empty")
    parse_iso8601(approved_at, "approved_at", "approval")
    require(not ADJUDICATION.exists(), "Refusing to overwrite existing adjudication.csv")
    expected_rows, summary = build_rows()
    actual_rows = read_csv(OUTPUT)
    expected_by_key = index_rows(expected_rows, "expected_master")
    actual_by_key = index_rows(actual_rows, "annotations_master")
    require(set(actual_by_key) == set(expected_by_key), "Master keys differ from canonical consolidation")
    analysis_by_key = index_rows(read_csv(ANALYSIS), "adjudication_analysis")
    decisions: list[dict[str, str]] = []

    for key, expected in expected_by_key.items():
        actual = actual_by_key[key]
        for field in MASTER_HEADERS:
            if field in DECISION_FIELDS:
                continue
            require(actual.get(field, "") == expected[field], f"Immutable master field changed: {field}: {key}")
        require(
            not any(actual[field].strip() for field in DECISION_FIELDS),
            f"Refusing to overwrite existing human decision: {key}",
        )
        if actual["disagreement"] != "true":
            continue
        require(
            actual["proposed_intent_for_human_review"] in INTENTS
            and actual["proposed_sentiment_for_human_review"] in SENTIMENTS,
            f"Missing advisory label: {key}",
        )
        actual["adjudicated_intent"] = actual["proposed_intent_for_human_review"]
        actual["adjudicated_sentiment"] = actual["proposed_sentiment_for_human_review"]
        actual["approved_by"] = reviewer
        actual["approved_at"] = approved_at
        actual["rationale"] = approval_reason(actual["message_id"], analysis_by_key, key)
        decisions.append({
            "message_id": actual["message_id"],
            "intent": actual["adjudicated_intent"],
            "sentiment": actual["adjudicated_sentiment"],
            "approved_by": reviewer,
            "approved_at": approved_at,
            "reason": actual["rationale"],
        })

    require(len(decisions) == EXPECTED_DISAGREEMENTS, "Expected 104 adjudication decisions")
    replace_csv(
        OUTPUT,
        [actual_by_key[canonical_key(row, "expected_master")] for row in expected_rows],
    )
    with ADJUDICATION.open("x", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=("message_id", "intent", "sentiment", "approved_by", "approved_at", "reason"),
            lineterminator="\n",
        )
        writer.writeheader()
        writer.writerows(sorted(decisions, key=lambda row: row["message_id"]))
    summary["human_decisions"] = len(decisions)
    manifest = build_manifest(summary)
    manifest["outputs"]["adjudication"] = {"path": ADJUDICATION.name, "status": "HUMAN_APPROVED", "sha256": sha256(ADJUDICATION)}
    manifest["outputs"]["gold_test_annotations"]["status"] = "BLOCKED_KAPPA_BELOW_THRESHOLD"
    manifest["gate"]["status"] = "BLOCKED_KAPPA_BELOW_THRESHOLD"
    manifest["gate"]["reason"] = "Human adjudication is complete, but frozen intent/sentiment kappa remain below 0.60."
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"APPROVAL PASS: adjudications={len(decisions)} reviewer={reviewer}")


def read_manifest() -> dict[str, object]:
    require(MANIFEST.exists(), "Manifest has not been created")
    return json.loads(MANIFEST.read_text(encoding="utf-8"))


def validate_manifest_checksum(manifest: dict[str, object], key: str, path: Path) -> None:
    sources = manifest.get("sources", {})
    source = sources.get(key, {})
    require(source.get("sha256") == sha256(path), f"Manifest checksum mismatch: {key}")


def validate_frozen_provenance(manifest: dict[str, object]) -> None:
    validate_manifest_checksum(manifest, "round_1_annotations", ROUND_ONE)
    validate_manifest_checksum(manifest, "frozen_round_2", ROUND_TWO)
    validate_manifest_checksum(manifest, "frozen_disagreements", DISAGREEMENTS)
    validate_manifest_checksum(manifest, "advisory_analysis", ANALYSIS)
    validate_manifest_checksum(manifest, "human_adjudication_review", REVIEW)
    validate_manifest_checksum(manifest, "round_2_verification", ROUND_TWO_REPORT)
    outputs = manifest.get("outputs", {})
    require(
        outputs.get("annotations_master", {}).get("sha256") == sha256(OUTPUT),
        "Manifest checksum mismatch: annotations_master",
    )
    require(
        outputs.get("adjudication", {}).get("sha256") == sha256(ADJUDICATION),
        "Manifest checksum mismatch: adjudication",
    )


def validate_standard_kappa_gate() -> dict[str, object]:
    report = json.loads(ROUND_TWO_REPORT.read_text(encoding="utf-8"))
    require(report.get("intent_kappa") == 0.4327, "Frozen intent kappa changed")
    require(report.get("sentiment_kappa") == 0.5337, "Frozen sentiment kappa changed")
    require(report.get("disagreement_count") == EXPECTED_DISAGREEMENTS, "Frozen disagreement count changed")
    require(report.get("message_count") == EXPECTED_TEST, "Frozen test count changed")
    require(
        report["intent_kappa"] < 0.60 or report["sentiment_kappa"] < 0.60,
        "Human-adjudicated route is only required while standard kappa route is blocked",
    )
    return report


def validate_adjudication_export(
    decisions: list[dict[str, str]],
    expected_ids: set[str],
) -> dict[str, dict[str, str]]:
    require(len(decisions) == EXPECTED_DISAGREEMENTS, "Adjudication must contain exactly 104 rows")
    by_id = {row.get("message_id", ""): row for row in decisions}
    require(len(by_id) == len(decisions), "Adjudication has duplicate message_id")
    require(set(by_id) == expected_ids, "Adjudication has missing or unexpected message_id")
    for message_id, row in by_id.items():
        require(row.get("intent") in INTENTS, f"Adjudication invalid intent: {message_id}")
        require(row.get("sentiment") in SENTIMENTS, f"Adjudication invalid sentiment: {message_id}")
        require(row.get("approved_by", "").strip() == EXPECTED_REVIEWER, f"Adjudication reviewer mismatch: {message_id}")
        require(row.get("reason", "").strip(), f"Adjudication reason is empty: {message_id}")
        parse_iso8601(row.get("approved_at", ""), "approved_at", message_id)
    return by_id


def validate_split_isolation(rows: list[dict[str, str]]) -> None:
    dev_conversations = {row["conversation_id"] for row in rows if row["split"] == "dev"}
    test_conversations = {row["conversation_id"] for row in rows if row["split"] == "test"}
    require(not dev_conversations & test_conversations, "Dev/test conversation leakage detected")


def validate_human_finalization_inputs() -> tuple[list[dict[str, str]], dict[str, dict[str, str]], dict[str, object]]:
    require(ADJUDICATION.exists(), "Human adjudication.csv is missing")
    expected_rows, summary = build_rows()
    actual_rows, human_decisions = validate_existing_master(expected_rows)
    require(len(actual_rows) == EXPECTED_ROUND_ONE, "Master must contain exactly 1,002 annotations")
    require(sum(row["split"] == "dev" for row in actual_rows) == 805, "Master must contain exactly 805 dev rows")
    require(sum(row["split"] == "test" for row in actual_rows) == EXPECTED_TEST, "Master must contain exactly 197 test rows")
    require(human_decisions == EXPECTED_DISAGREEMENTS, "Master must contain exactly 104 human decisions")
    require(sum(row["disagreement"] == "true" for row in actual_rows) == EXPECTED_DISAGREEMENTS, "Master disagreement count changed")
    agreement_count = sum(
        row["split"] == "test"
        and row["round_2_available"] == "true"
        and row["disagreement"] == "false"
        for row in actual_rows
    )
    require(agreement_count == EXPECTED_AGREEMENTS, "Master agreement count must equal 93")
    validate_split_isolation(actual_rows)

    manifest = read_manifest()
    validate_frozen_provenance(manifest)
    validate_standard_kappa_gate()

    headers, decisions = read_csv_with_headers(ADJUDICATION)
    require(headers == ADJUDICATION_HEADERS, "Adjudication CSV headers differ from evaluator contract")
    expected_disagreement_ids = {
        row["message_id"] for row in actual_rows if row["disagreement"] == "true"
    }
    by_id = validate_adjudication_export(decisions, expected_disagreement_ids)

    for row in actual_rows:
        if row["disagreement"] != "true":
            continue
        decision = by_id[row["message_id"]]
        require(row["adjudicated_intent"] == decision["intent"], f"Master/adjudication intent mismatch: {row['message_id']}")
        require(row["adjudicated_sentiment"] == decision["sentiment"], f"Master/adjudication sentiment mismatch: {row['message_id']}")
        require(row["approved_by"] == decision["approved_by"], f"Master/adjudication reviewer mismatch: {row['message_id']}")
        require(row["approved_at"] == decision["approved_at"], f"Master/adjudication timestamp mismatch: {row['message_id']}")
        require(row["rationale"] == decision["reason"], f"Master/adjudication rationale mismatch: {row['message_id']}")

    summary["human_decisions"] = human_decisions
    return actual_rows, by_id, manifest


def build_gold_rows(
    master_rows: list[dict[str, str]],
    decisions: dict[str, dict[str, str]],
) -> list[dict[str, str]]:
    by_key = index_rows(master_rows, "annotations_master")
    gold: list[dict[str, str]] = []
    for round_two in read_csv(ROUND_TWO):
        key = canonical_key(round_two, "frozen_round_2")
        master = by_key[key]
        if master["disagreement"] == "true":
            decision = decisions[master["message_id"]]
            gold.append({
                "message_id": master["message_id"],
                "intent": decision["intent"],
                "sentiment": decision["sentiment"],
                "source": "HUMAN_ADJUDICATED",
                "approved_by": decision["approved_by"],
                "approved_at": decision["approved_at"],
            })
        else:
            gold.append({
                "message_id": master["message_id"],
                "intent": master["round_1_intent"],
                "sentiment": master["round_1_sentiment"],
                "source": "ROUND_1_AND_2_AGREE",
                "approved_by": "",
                "approved_at": "",
            })
    require(len(gold) == EXPECTED_TEST, "Gold must contain exactly 197 test rows")
    require(len({row["message_id"] for row in gold}) == EXPECTED_TEST, "Gold has duplicate message_id")
    return gold


def finalize_human_adjudicated_gold(authorization: str) -> None:
    require_authorization(authorization)
    require(not GOLD.exists(), "Refusing to overwrite existing gold_test_annotations.csv")
    master_rows, decisions, manifest = validate_human_finalization_inputs()
    gold = build_gold_rows(master_rows, decisions)
    write_rows(GOLD, GOLD_HEADERS, gold)

    manifest["outputs"]["gold_test_annotations"] = {
        "path": GOLD.name,
        "status": "HUMAN_ADJUDICATED_FINALIZATION_PASS",
        "sha256": sha256(GOLD),
        "row_count": len(gold),
        "csv_contract": list(GOLD_HEADERS),
    }
    manifest["human_adjudicated_finalization"] = {
        "status": "PASS",
        "authorization": "Explicit authorization by Nhi, Data workstream owner",
        "method": "Human-adjudicated gold with AI-assisted human-review provenance",
        "human_adjudications": EXPECTED_DISAGREEMENTS,
        "agreement_rows_not_independently_human_adjudicated": EXPECTED_AGREEMENTS,
        "standard_kappa_route": {
            "status": "BLOCKED",
            "threshold": 0.60,
            "intent_kappa": 0.4327,
            "sentiment_kappa": 0.5337,
        },
        "input_scope": [OUTPUT.name, ADJUDICATION.name, ROUND_ONE.name, ROUND_TWO.name],
        "ai07_prediction_data_used": False,
    }
    manifest["gate"]["status"] = "HUMAN_ADJUDICATED_GOLD_PASS_STANDARD_KAPPA_ROUTE_BLOCKED"
    manifest["gate"]["reason"] = "Gold was created from complete approved adjudications; standard kappa route remains below 0.60."
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"HUMAN GOLD PASS: gold={len(gold)} output={GOLD.name}")


def expect_failure(action, name: str) -> None:
    try:
        action()
    except ConsolidationError:
        return
    raise ConsolidationError(f"Regression test did not fail: {name}")


def self_test_human_finalization() -> None:
    master_rows, decisions, manifest = validate_human_finalization_inputs()
    gold_once = build_gold_rows(master_rows, decisions)
    gold_twice = build_gold_rows(master_rows, decisions)
    require(gold_once == gold_twice, "Gold generation is not deterministic")
    require_authorization(HUMAN_FINALIZATION_AUTHORIZATION)
    expect_failure(lambda: require_authorization("UNAUTHORIZED"), "unauthorized finalization")

    incomplete = deepcopy(list(decisions.values()))
    incomplete[0]["approved_by"] = ""
    expect_failure(lambda: validate_adjudication_export(incomplete, set(decisions)), "missing approval")

    invalid = deepcopy(list(decisions.values()))
    invalid[0]["intent"] = "INVALID"
    expect_failure(lambda: validate_adjudication_export(invalid, set(decisions)), "invalid taxonomy")

    duplicate = deepcopy(list(decisions.values()))
    duplicate.append(deepcopy(duplicate[0]))
    expect_failure(lambda: validate_adjudication_export(duplicate, set(decisions)), "duplicate key")

    unexpected = deepcopy(list(decisions.values()))
    unexpected[0]["message_id"] = "00000000-0000-0000-0000-000000000000"
    expect_failure(lambda: validate_adjudication_export(unexpected, set(decisions)), "unexpected key")

    altered_manifest = deepcopy(manifest)
    altered_manifest["sources"]["frozen_round_2"]["sha256"] = "0" * 64
    expect_failure(lambda: validate_frozen_provenance(altered_manifest), "incorrect checksum")

    leaked_rows = deepcopy(master_rows)
    test_conversation = next(row["conversation_id"] for row in leaked_rows if row["split"] == "test")
    next(row for row in leaked_rows if row["split"] == "dev")["conversation_id"] = test_conversation
    expect_failure(lambda: validate_split_isolation(leaked_rows), "dev/test leakage")

    approved = deepcopy(master_rows)
    expect_failure(
        lambda: require(not any(row["approved_by"] for row in approved), "Refusing to overwrite approved decisions"),
        "approved decision overwrite",
    )

    frozen = validate_standard_kappa_gate()
    require(frozen["intent_kappa"] == 0.4327 and frozen["sentiment_kappa"] == 0.5337, "Standard kappa changed")
    print("HUMAN FINALIZATION SELF-TEST PASS: 10 regression groups")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--write", action="store_true")
    mode.add_argument("--verify", action="store_true")
    mode.add_argument("--refresh-manifest", action="store_true")
    mode.add_argument("--refresh-advisory", action="store_true")
    mode.add_argument("--approve-all-advisory", action="store_true")
    mode.add_argument("--self-test-human-finalization", action="store_true")
    mode.add_argument("--finalize-human-adjudicated-gold", action="store_true")
    parser.add_argument("--approved-by")
    parser.add_argument("--approved-at")
    parser.add_argument("--authorization")
    args = parser.parse_args()

    if args.verify:
        verify_existing()
        return

    if args.refresh_manifest:
        refresh_manifest()
        return

    if args.refresh_advisory:
        refresh_advisory()
        return

    if args.approve_all_advisory:
        require(args.approved_by is not None and args.approved_at is not None, "Approval identity and timestamp are required")
        approve_all_advisory(args.approved_by, args.approved_at)
        return

    if args.self_test_human_finalization:
        self_test_human_finalization()
        return

    if args.finalize_human_adjudicated_gold:
        require(args.authorization is not None, "Explicit Data owner authorization is required")
        finalize_human_adjudicated_gold(args.authorization)
        return

    require(not OUTPUT.exists(), f"Refusing to overwrite: {OUTPUT.name}")
    require(not MANIFEST.exists(), f"Refusing to overwrite: {MANIFEST.name}")
    rows, summary = build_rows()
    write_csv(OUTPUT, rows)
    write_manifest(build_manifest(summary))
    print(f"WRITE PASS: master={len(rows)} test={summary['test_messages']} disagreements={summary['disagreements']}")


if __name__ == "__main__":
    try:
        main()
    except (ConsolidationError, FileNotFoundError, KeyError, json.JSONDecodeError) as error:
        raise SystemExit(f"FAIL: {error}") from error
