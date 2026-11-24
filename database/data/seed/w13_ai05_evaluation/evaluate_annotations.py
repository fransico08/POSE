#!/usr/bin/env python3
"""Prepare and evaluate the isolated W13 AI-05 DEC-37 annotation round."""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import sys
import tempfile
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path
from typing import Iterable

HERE = Path(__file__).resolve().parent
SEED = HERE.parent / "w13_ai05"
ROUND1 = SEED / "round_1_annotations_consolidated.csv"
MESSAGES = SEED / "conversation_messages.csv"
SEED_MANIFEST = SEED / "manifest.json"
EVALUATION_MANIFEST = HERE / "manifest.json"
TEMPLATE = HERE / "round_2_test_template.csv"

INTENTS = {"ASK_PRICE", "ASK_INFO", "BOOKING", "PURCHASE_READY", "COMPLAINT", "SUPPORT", "OTHER"}
SENTIMENTS = {"POSITIVE", "NEUTRAL", "NEGATIVE"}
TEMPLATE_HEADERS = (
    "message_id", "organization_id", "conversation_id", "sector", "occurred_at",
    "prior_context", "message_text", "intent", "sentiment", "annotator", "locked_at", "notes",
)
ADJUDICATION_HEADERS = ("message_id", "intent", "sentiment", "approved_by", "approved_at", "reason")
EXPECTED_TEST_COUNT = 197
KAPPA_THRESHOLD = 0.60


class EvaluationError(ValueError):
    """Raised when an evaluation artifact violates the DEC-37 protocol."""


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def sha256_text(path: Path) -> str:
    """Hash canonical LF text so Git's Windows CRLF checkout does not look like data drift."""
    content = path.read_bytes().replace(b"\r\n", b"\n").replace(b"\r", b"\n")
    return hashlib.sha256(content).hexdigest().upper()


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def read_csv_with_headers(path: Path) -> tuple[tuple[str, ...], list[dict[str, str]]]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        return tuple(reader.fieldnames or ()), list(reader)


def write_csv(path: Path, headers: Iterable[str], rows: Iterable[dict[str, str]]) -> None:
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(headers), extrasaction="raise")
        writer.writeheader()
        writer.writerows(rows)


def load_manifest() -> dict[str, object]:
    return json.loads(EVALUATION_MANIFEST.read_text(encoding="utf-8"))


def validate_sources() -> None:
    sources = load_manifest()["sources"]
    for key, path in {
        "w13_manifest_sha256": SEED_MANIFEST,
        "conversation_messages_sha256": MESSAGES,
        "round1_annotations_sha256": ROUND1,
    }.items():
        if sha256_text(path) != sources[key]:
            raise EvaluationError(f"Nguồn W13 thay đổi: {path.relative_to(HERE.parents[3])}")


def test_rows() -> tuple[list[dict[str, str]], dict[str, dict[str, str]], dict[str, list[dict[str, str]]]]:
    annotations = read_csv(ROUND1)
    messages = read_csv(MESSAGES)
    message_by_id = {row["id"]: row for row in messages}
    if len(message_by_id) != len(messages):
        raise EvaluationError("conversation_messages.csv có message_id trùng")
    rows = [row for row in annotations if row["split"] == "test"]
    if len(rows) != EXPECTED_TEST_COUNT or len({row["message_id"] for row in rows}) != EXPECTED_TEST_COUNT:
        raise EvaluationError("Round-1 test phải chứa chính xác 197 message_id duy nhất")
    messages_by_conversation: dict[str, list[dict[str, str]]] = defaultdict(list)
    for message in messages:
        messages_by_conversation[message["conversation_id"]].append(message)
    for sequence in messages_by_conversation.values():
        sequence.sort(key=lambda item: (item["occurred_at"], item["id"]))
    for annotation in rows:
        message = message_by_id.get(annotation["message_id"])
        if not message or message["direction"] != "INBOUND":
            raise EvaluationError(f"Annotation test không trỏ đến INBOUND: {annotation['message_id']}")
        if message["conversation_id"] != annotation["conversation_id"] or message["organization_id"] != annotation["organization_id"]:
            raise EvaluationError(f"Conversation hoặc tenant không khớp: {annotation['message_id']}")
    return rows, message_by_id, messages_by_conversation


def prior_context(message: dict[str, str], groups: dict[str, list[dict[str, str]]]) -> str:
    values = []
    for item in groups[message["conversation_id"]]:
        if item["id"] == message["id"]:
            break
        values.append(f"{item['direction']}: {item['content']}")
    return "\n".join(values)


def expected_template_rows() -> list[dict[str, str]]:
    annotations, message_by_id, groups = test_rows()
    result = []
    for annotation in annotations:
        message = message_by_id[annotation["message_id"]]
        result.append({
            "message_id": message["id"], "organization_id": message["organization_id"],
            "conversation_id": message["conversation_id"], "sector": annotation["sector"],
            "occurred_at": message["occurred_at"], "prior_context": prior_context(message, groups),
            "message_text": message["content"], "intent": "", "sentiment": "", "annotator": "",
            "locked_at": "", "notes": "",
        })
    return result


def validate_template(path: Path = TEMPLATE) -> None:
    expected = expected_template_rows()
    actual = read_csv(path)
    if not actual or tuple(actual[0]) != TEMPLATE_HEADERS:
        raise EvaluationError("Header round_2_test_template.csv không đúng")
    if actual != expected:
        raise EvaluationError("Template không khớp chính xác 197 message test và context trước đó")
    if any(row["intent"] or row["sentiment"] for row in actual):
        raise EvaluationError("Template không được chứa nhãn vòng 1 hoặc nhãn vòng 2")


def prepare(write: bool) -> None:
    validate_sources()
    rows = expected_template_rows()
    if write:
        write_csv(TEMPLATE, TEMPLATE_HEADERS, rows)
    validate_template()
    expected_hash = load_manifest()["artifacts"].get("round_2_test_template_sha256")
    if expected_hash not in (None, "TO_BE_GENERATED_BY_PREPARE") and sha256(TEMPLATE) != expected_hash:
        raise EvaluationError("SHA-256 template không khớp manifest evaluation")
    print(f"PREPARE PASS: test_inbound={len(rows)} template_sha256={sha256(TEMPLATE)}")


def parse_iso8601(value: str, field: str, message_id: str) -> None:
    try:
        datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise EvaluationError(f"{field} không phải ISO-8601: {message_id}") from error


def verify_handoff_hash(path: Path, expected_hash: str) -> str:
    normalized = expected_hash.strip().upper()
    if len(normalized) != 64 or any(character not in "0123456789ABCDEF" for character in normalized):
        raise EvaluationError("SHA-256 handoff round-2 không hợp lệ")
    actual = sha256(path)
    if actual != normalized:
        raise EvaluationError("SHA-256 round-2 không khớp giá trị đã khóa khi handoff")
    return actual


def validate_round2(path: Path) -> list[dict[str, str]]:
    validate_sources()
    expected = {row["message_id"]: row for row in expected_template_rows()}
    rows = read_csv(path)
    if not rows or not set(TEMPLATE_HEADERS).issubset(rows[0]):
        raise EvaluationError("Round-2 phải có toàn bộ cột của template")
    actual = {row["message_id"]: row for row in rows}
    if len(rows) != len(actual) or set(actual) != set(expected):
        raise EvaluationError("Round-2 phải bao phủ chính xác 197 message_id test, không thiếu hoặc thừa")
    for message_id, row in actual.items():
        baseline = expected[message_id]
        for field in ("organization_id", "conversation_id", "sector", "occurred_at", "prior_context", "message_text"):
            if row[field] != baseline[field]:
                raise EvaluationError(f"Round-2 thay đổi dữ liệu nguồn {field}: {message_id}")
        if row["intent"] not in INTENTS or row["sentiment"] not in SENTIMENTS:
            raise EvaluationError(f"Nhãn round-2 không hợp lệ: {message_id}")
        if not row["annotator"].strip() or not row["locked_at"].strip():
            raise EvaluationError(f"Round-2 chưa khóa/ghi người gán nhãn: {message_id}")
        parse_iso8601(row["locked_at"], "locked_at", message_id)
    return rows


def cohen_kappa(left: list[str], right: list[str]) -> dict[str, float]:
    if len(left) != len(right) or not left:
        raise EvaluationError("Không thể tính kappa với tập rỗng hoặc không cùng độ dài")
    total = len(left)
    observed = sum(a == b for a, b in zip(left, right)) / total
    first, second = Counter(left), Counter(right)
    expected = sum((first[label] / total) * (second[label] / total) for label in set(first) | set(second))
    kappa = 1.0 if expected == 1.0 and observed == 1.0 else (observed - expected) / (1 - expected)
    return {"observed_agreement": observed, "expected_agreement": expected, "kappa": kappa}


def compare_rounds(round2: list[dict[str, str]]) -> tuple[dict[str, object], list[dict[str, str]]]:
    first = {row["message_id"]: row for row in read_csv(ROUND1) if row["split"] == "test"}
    second = {row["message_id"]: row for row in round2}
    ordered_ids = [row["message_id"] for row in expected_template_rows()]
    intent = cohen_kappa([first[key]["intent"] for key in ordered_ids], [second[key]["intent"] for key in ordered_ids])
    sentiment = cohen_kappa([first[key]["sentiment"] for key in ordered_ids], [second[key]["sentiment"] for key in ordered_ids])
    disagreements = []
    intent_pairs: Counter[tuple[str, str]] = Counter()
    sentiment_pairs: Counter[tuple[str, str]] = Counter()
    for message_id in ordered_ids:
        intent_pair = (first[message_id]["intent"], second[message_id]["intent"])
        sentiment_pair = (first[message_id]["sentiment"], second[message_id]["sentiment"])
        if intent_pair[0] != intent_pair[1]:
            intent_pairs[intent_pair] += 1
        if sentiment_pair[0] != sentiment_pair[1]:
            sentiment_pairs[sentiment_pair] += 1
        if intent_pair[0] != intent_pair[1] or sentiment_pair[0] != sentiment_pair[1]:
            disagreements.append({
                "message_id": message_id, "conversation_id": first[message_id]["conversation_id"],
                "sector": first[message_id]["sector"], "message_text": second[message_id]["message_text"],
                "round_1_intent": first[message_id]["intent"], "round_2_intent": second[message_id]["intent"],
                "round_1_sentiment": first[message_id]["sentiment"], "round_2_sentiment": second[message_id]["sentiment"],
            })
    return {
        "test_inbound": len(ordered_ids), "intent": intent, "sentiment": sentiment,
        "threshold": KAPPA_THRESHOLD,
        "hard_label_pairs": {
            "intent": [
                {"round_1": left, "round_2": right, "count": count}
                for (left, right), count in intent_pairs.most_common()
            ],
            "sentiment": [
                {"round_1": left, "round_2": right, "count": count}
                for (left, right), count in sentiment_pairs.most_common()
            ],
        },
        "round_1_distribution": {"intent": dict(sorted(Counter(first[key]["intent"] for key in ordered_ids).items())), "sentiment": dict(sorted(Counter(first[key]["sentiment"] for key in ordered_ids).items()))},
        "round_2_distribution": {"intent": dict(sorted(Counter(second[key]["intent"] for key in ordered_ids).items())), "sentiment": dict(sorted(Counter(second[key]["sentiment"] for key in ordered_ids).items()))},
        "disagreement_count": len(disagreements),
        "threshold_pass": intent["kappa"] >= KAPPA_THRESHOLD and sentiment["kappa"] >= KAPPA_THRESHOLD,
    }, disagreements


def evaluate(round2_path: Path, handoff_hash: str, disagreement_path: Path | None) -> None:
    locked_hash = verify_handoff_hash(round2_path, handoff_hash)
    report, disagreements = compare_rounds(validate_round2(round2_path))
    if disagreement_path and disagreements:
        write_csv(disagreement_path, tuple(disagreements[0]), disagreements)
    report["disagreement_review"] = str(disagreement_path) if disagreement_path and disagreements else None
    report["round2_handoff_sha256"] = locked_hash
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if not report["threshold_pass"]:
        raise SystemExit("KAPPA BELOW THRESHOLD: dừng trước F1/gold và review các bất đồng.")


def validate_adjudication(
    headers: tuple[str, ...],
    decisions: list[dict[str, str]],
    expected_message_ids: set[str],
) -> dict[str, dict[str, str]]:
    if headers != ADJUDICATION_HEADERS:
        raise EvaluationError("Adjudication phải dùng đúng header đã quy định")
    by_id = {row["message_id"]: row for row in decisions}
    if len(decisions) != len(by_id) or set(by_id) != expected_message_ids:
        raise EvaluationError("Adjudication không được thiếu, trùng hoặc chứa message_id ngoài danh sách bất đồng")
    for message_id, row in by_id.items():
        if row["intent"] not in INTENTS or row["sentiment"] not in SENTIMENTS or not row["approved_by"].strip() or not row["approved_at"].strip():
            raise EvaluationError(f"Quyết định adjudication không hợp lệ: {message_id}")
        parse_iso8601(row["approved_at"], "approved_at", message_id)
    return by_id


def finalize(round2_path: Path, handoff_hash: str, adjudication_path: Path, output: Path) -> None:
    verify_handoff_hash(round2_path, handoff_hash)
    report, disagreements = compare_rounds(validate_round2(round2_path))
    if not report["threshold_pass"]:
        raise EvaluationError("Kappa dưới 0.60; chưa được tạo gold/F1")
    expected = {row["message_id"] for row in disagreements}
    headers, decisions = read_csv_with_headers(adjudication_path)
    by_id = validate_adjudication(headers, decisions, expected)
    first = {row["message_id"]: row for row in read_csv(ROUND1) if row["split"] == "test"}
    gold = []
    for message_id in [row["message_id"] for row in expected_template_rows()]:
        decision = by_id.get(message_id)
        gold.append({
            "message_id": message_id,
            "intent": decision["intent"] if decision else first[message_id]["intent"],
            "sentiment": decision["sentiment"] if decision else first[message_id]["sentiment"],
            "source": "HUMAN_ADJUDICATED" if decision else "ROUND_1_AND_2_AGREE",
            "approved_by": decision["approved_by"] if decision else "",
            "approved_at": decision["approved_at"] if decision else "",
        })
    write_csv(output, ("message_id", "intent", "sentiment", "source", "approved_by", "approved_at"), gold)
    print(f"FINALIZE PASS: gold={len(gold)} output={output}")


def self_test() -> None:
    with tempfile.TemporaryDirectory() as temporary:
        fixture = Path(temporary) / "round_2_annotations.csv"
        valid_rows = []
        for row in expected_template_rows():
            valid_rows.append({
                **row,
                "intent": "ASK_INFO",
                "sentiment": "NEUTRAL",
                "annotator": "Thang-synthetic",
                "locked_at": "2026-11-25T09:00:00Z",
            })

        def expect_failure(action, name: str) -> None:
            try:
                action()
            except EvaluationError:
                return
            raise EvaluationError(f"Synthetic negative test did not fail: {name}")

        write_csv(fixture, TEMPLATE_HEADERS, valid_rows)
        if len(validate_round2(fixture)) != EXPECTED_TEST_COUNT:
            raise EvaluationError("Synthetic fixture coverage failure")
        verify_handoff_hash(fixture, sha256(fixture))
        expect_failure(lambda: verify_handoff_hash(fixture, "0" * 64), "handoff hash")

        missing = Path(temporary) / "missing.csv"
        write_csv(missing, TEMPLATE_HEADERS, valid_rows[:-1])
        expect_failure(lambda: validate_round2(missing), "missing message ID")

        duplicate = Path(temporary) / "duplicate.csv"
        write_csv(duplicate, TEMPLATE_HEADERS, [*valid_rows, dict(valid_rows[0])])
        expect_failure(lambda: validate_round2(duplicate), "duplicate message ID")

        invalid_label = Path(temporary) / "invalid-label.csv"
        invalid_rows = [dict(row) for row in valid_rows]
        invalid_rows[0]["intent"] = "INVALID"
        write_csv(invalid_label, TEMPLATE_HEADERS, invalid_rows)
        expect_failure(lambda: validate_round2(invalid_label), "invalid label")

        unlocked = Path(temporary) / "unlocked.csv"
        unlocked_rows = [dict(row) for row in valid_rows]
        unlocked_rows[0]["locked_at"] = ""
        write_csv(unlocked, TEMPLATE_HEADERS, unlocked_rows)
        expect_failure(lambda: validate_round2(unlocked), "unlocked annotation")

        adjudication = Path(temporary) / "adjudication.csv"
        decision = {
            "message_id": valid_rows[0]["message_id"],
            "intent": "ASK_INFO",
            "sentiment": "NEUTRAL",
            "approved_by": "Van-synthetic",
            "approved_at": "2026-11-25T09:00:00Z",
            "reason": "Synthetic only",
        }
        write_csv(adjudication, ADJUDICATION_HEADERS, [decision])
        headers, decisions = read_csv_with_headers(adjudication)
        empty_adjudication = Path(temporary) / "empty-adjudication.csv"
        write_csv(empty_adjudication, ADJUDICATION_HEADERS, [])
        empty_headers, empty_decisions = read_csv_with_headers(empty_adjudication)
        if validate_adjudication(empty_headers, empty_decisions, set()):
            raise EvaluationError("Synthetic empty adjudication failure")
        expect_failure(lambda: validate_adjudication(headers, decisions, set()), "unexpected adjudication")
        expect_failure(lambda: validate_adjudication(headers, [decision, dict(decision)], {decision["message_id"]}), "duplicate adjudication")
        invalid_decision = dict(decision)
        invalid_decision["intent"] = "INVALID"
        expect_failure(lambda: validate_adjudication(headers, [invalid_decision], {decision["message_id"]}), "invalid adjudication")
    if cohen_kappa(["ASK_INFO", "COMPLAINT"], ["ASK_INFO", "COMPLAINT"])["kappa"] != 1.0:
        raise EvaluationError("Synthetic fixture agreement failure")
    if cohen_kappa(["ASK_INFO", "COMPLAINT"], ["ASK_INFO", "ASK_INFO"])["kappa"] >= KAPPA_THRESHOLD:
        raise EvaluationError("Synthetic fixture threshold failure")
    print("SELF-TEST PASS: hash lock, CSV fixture, negative validation, exact agreement, below-threshold disagreement")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    prepare_parser = commands.add_parser("prepare")
    prepare_parser.add_argument("--write", action="store_true")
    commands.add_parser("validate-template")
    evaluate_parser = commands.add_parser("evaluate")
    evaluate_parser.add_argument("--round2", type=Path, required=True)
    evaluate_parser.add_argument("--round2-sha256", required=True)
    evaluate_parser.add_argument("--write-disagreements", type=Path)
    finalize_parser = commands.add_parser("finalize")
    finalize_parser.add_argument("--round2", type=Path, required=True)
    finalize_parser.add_argument("--round2-sha256", required=True)
    finalize_parser.add_argument("--adjudication", type=Path, required=True)
    finalize_parser.add_argument("--output", type=Path, required=True)
    commands.add_parser("self-test")
    args = parser.parse_args()
    if args.command == "prepare":
        prepare(args.write)
    elif args.command == "validate-template":
        validate_sources(); validate_template(); print("TEMPLATE PASS: exact coverage=197; no round-1 labels exposed")
    elif args.command == "evaluate":
        evaluate(args.round2, args.round2_sha256, args.write_disagreements)
    elif args.command == "finalize":
        finalize(args.round2, args.round2_sha256, args.adjudication, args.output)
    else:
        self_test()


if __name__ == "__main__":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except AttributeError:
        pass
    try:
        main()
    except (EvaluationError, FileNotFoundError) as error:
        print(f"FAIL: {error}")
        raise SystemExit(1) from error
