#!/usr/bin/env python3
"""Validate a blind, AI-assisted second annotation pass and report agreement."""
from __future__ import annotations

import csv
import hashlib
from collections import Counter, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
SOURCE = ROOT / "database" / "data" / "newitems" / "w13_ai05_task1_delivery"
MESSAGES = SOURCE / "conversation_messages.csv"
ROUND_1 = SOURCE / "round_1_annotations_consolidated.csv"
ROUND_2 = HERE / "round_2_annotations.csv"
ENRICHED = HERE / "round_2_annotations_enriched.csv"
DISAGREEMENTS = HERE / "disagreements_for_review.csv"
REPORT = HERE / "agreement_report.md"

INTENTS = {
    "ASK_PRICE", "ASK_INFO", "BOOKING", "PURCHASE_READY", "COMPLAINT", "SUPPORT", "OTHER",
}
SENTIMENTS = {"POSITIVE", "NEUTRAL", "NEGATIVE"}
SECTORS = {"EDU", "REA", "CLI", "TECH", "RET", "LOG"}
ANNOTATOR = "THANG_CODEX_ASSISTED"


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def write_csv(path: Path, headers: list[str], rows: list[dict[str, str]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=headers, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def sector_for(conversation: dict[str, str]) -> str:
    parts = conversation["external_thread_id"].split("-")
    if len(parts) < 4 or parts[0:2] != ["zalo", "ai05"]:
        raise ValueError(f"Unexpected synthetic conversation key: {conversation['external_thread_id']}")
    return parts[2].upper()


def cohen_kappa(left: list[str], right: list[str]) -> float | None:
    if len(left) != len(right):
        raise ValueError("Paired labels must have equal length")
    if not left:
        return None
    labels = set(left) | set(right)
    observed = sum(a == b for a, b in zip(left, right, strict=True)) / len(left)
    expected = sum(left.count(label) * right.count(label) for label in labels) / (len(left) ** 2)
    if expected == 1:
        return None
    return (observed - expected) / (1 - expected)


def fmt(value: float | None) -> str:
    return "không xác định" if value is None else f"{value:.4f}"


def distribution(rows: list[dict[str, str]], field: str) -> str:
    counts = Counter(row[field] for row in rows)
    return ", ".join(f"`{label}` {counts[label]}" for label in sorted(counts))


def main() -> None:
    messages = read_csv(MESSAGES)
    conversations = read_csv(SOURCE / "conversations.csv")
    first = read_csv(ROUND_1)
    second = read_csv(ROUND_2)
    message_by_external = {row["external_message_id"]: row for row in messages if row["direction"] == "INBOUND"}
    conversation_by_id = {row["id"]: row for row in conversations}
    first_by_message = {row["message_id"]: row for row in first}

    if len(message_by_external) != sum(row["direction"] == "INBOUND" for row in messages):
        raise ValueError("Inbound external_message_id must be unique")
    if len(first_by_message) != len(first):
        raise ValueError("Round-one message IDs must be unique")

    second_by_message: dict[str, dict[str, str]] = {}
    enriched: list[dict[str, str]] = []
    for row in second:
        if row["external_message_id"] not in message_by_external:
            raise ValueError(f"Unknown or non-inbound message: {row['external_message_id']}")
        if row["intent"] not in INTENTS or row["sentiment"] not in SENTIMENTS:
            raise ValueError(f"Invalid label: {row['external_message_id']}")
        message = message_by_external[row["external_message_id"]]
        conversation = conversation_by_id[message["conversation_id"]]
        sector = sector_for(conversation)
        if sector not in SECTORS:
            raise ValueError(f"Unexpected sector: {sector}")
        first_row = first_by_message.get(message["id"])
        if not first_row or first_row["split"] != "test":
            raise ValueError(f"Second-round labels must be paired test-split inbound messages: {row['external_message_id']}")
        if first_row["conversation_id"] != message["conversation_id"] or first_row["organization_id"] != message["organization_id"]:
            raise ValueError(f"Round-one linkage mismatch: {row['external_message_id']}")
        if first_row["intent"] not in INTENTS or first_row["sentiment"] not in SENTIMENTS:
            raise ValueError(f"Invalid round-one label for {row['external_message_id']}")
        second_by_message[message["id"]] = row
        enriched.append({
            "sample_id": row["external_message_id"],
            "sector": sector,
            "organization_id": message["organization_id"],
            "conversation_id": message["conversation_id"],
            "message_id": message["id"],
            "split": first_row["split"],
            "annotator": ANNOTATOR,
            "intent": row["intent"],
            "sentiment": row["sentiment"],
            "method": "blind_semantic_pass_ai_assisted",
        })

    expected_test_ids = {row["message_id"] for row in first if row["split"] == "test"}
    actual_ids = set(second_by_message)
    if len(second) != len(second_by_message):
        raise ValueError("Round-two sample IDs must be unique")
    if actual_ids != expected_test_ids:
        missing = len(expected_test_ids - actual_ids)
        extra = len(actual_ids - expected_test_ids)
        raise ValueError(f"Round-two coverage differs from test split: missing={missing}, extra={extra}")
    if len(expected_test_ids) != 197:
        raise ValueError(f"Expected 197 held-out test annotations from the source package, got {len(expected_test_ids)}")
    conversation_count = len({row["conversation_id"] for row in enriched})
    sector_count = len({row["sector"] for row in enriched})
    if conversation_count != 60 or sector_count != len(SECTORS):
        raise ValueError(f"Expected test coverage across 60 conversations and 6 sectors, got {conversation_count} / {sector_count}")

    enriched.sort(key=lambda row: (row["sector"], row["sample_id"]))
    write_csv(ENRICHED, list(enriched[0]), enriched)

    paired: list[dict[str, str]] = []
    for row in enriched:
        source_row = first_by_message[row["message_id"]]
        paired.append({**row, "round1_intent": source_row["intent"], "round1_sentiment": source_row["sentiment"]})
    disagreements = [row for row in paired if row["intent"] != row["round1_intent"] or row["sentiment"] != row["round1_sentiment"]]
    write_csv(DISAGREEMENTS, [
        "sample_id", "sector", "message_id", "round1_intent", "round2_intent", "round1_sentiment", "round2_sentiment",
    ], [{
        "sample_id": row["sample_id"], "sector": row["sector"], "message_id": row["message_id"],
        "round1_intent": row["round1_intent"], "round2_intent": row["intent"],
        "round1_sentiment": row["round1_sentiment"], "round2_sentiment": row["sentiment"],
    } for row in disagreements])

    overall_intent_kappa = cohen_kappa([r["round1_intent"] for r in paired], [r["intent"] for r in paired])
    overall_sentiment_kappa = cohen_kappa([r["round1_sentiment"] for r in paired], [r["sentiment"] for r in paired])
    sector_rows: list[str] = []
    for sector in sorted(SECTORS):
        group = [row for row in paired if row["sector"] == sector]
        ik = cohen_kappa([r["round1_intent"] for r in group], [r["intent"] for r in group])
        sk = cohen_kappa([r["round1_sentiment"] for r in group], [r["sentiment"] for r in group])
        ia = sum(r["round1_intent"] == r["intent"] for r in group) / len(group)
        sa = sum(r["round1_sentiment"] == r["sentiment"] for r in group) / len(group)
        sector_rows.append(f"| {sector} | {len(group)} | {ia:.1%} | {fmt(ik)} | {sa:.1%} | {fmt(sk)} |")

    intent_pairs = sum(row["round1_intent"] == row["intent"] for row in paired)
    sentiment_pairs = sum(row["round1_sentiment"] == row["sentiment"] for row in paired)
    report = f"""# W13 AI-05 — lượt gán nhãn vòng hai và độ đồng thuận

## Phạm vi và phương pháp

- Gán độc lập mù trên toàn bộ split `test`: **{len(paired)}** tin inbound từ **{conversation_count}** hội thoại, đủ **{sector_count}** ngành; split `dev` không được đưa vào để giữ riêng cho phát triển prompt.
- Vòng hai được tạo trước khi đọc/đối chiếu nhãn vòng một; sau khi khóa file vòng hai mới tính thống kê.
- Đây là lượt gán ngữ nghĩa do Codex hỗ trợ theo phân công Thắng (`THANG_CODEX_ASSISTED`), không phải lượt gán tay đã được Thắng xác nhận. Vì vậy kappa dưới đây là **đồng thuận giữa vòng một và một lượt AI hỗ trợ**, không được mô tả là kappa giữa hai người cho tới khi Thắng/người gán thứ hai duyệt và xác nhận.
- Không sửa file hội thoại, nhãn vòng một, seed canonical hay Data Dictionary do Văn sở hữu. Artefact mới không lưu nội dung tin nhắn.

## Kết quả tổng thể

| Nhãn | Số cặp | Tỷ lệ trùng | Cohen's kappa |
|---|---:|---:|---:|
| Intent | {len(paired)} | {intent_pairs / len(paired):.1%} | {fmt(overall_intent_kappa)} |
| Sentiment | {len(paired)} | {sentiment_pairs / len(paired):.1%} | {fmt(overall_sentiment_kappa)} |

## Phân bố nhãn trên tập test

| Nhóm | Vòng một | Vòng hai |
|---|---|---|
| Intent | {distribution(paired, 'round1_intent')} | {distribution(paired, 'intent')} |
| Sentiment | {distribution(paired, 'round1_sentiment')} | {distribution(paired, 'sentiment')} |

## Theo ngành

| Ngành | Cặp | Intent trùng | Kappa intent | Sentiment trùng | Kappa sentiment |
|---|---:|---:|---:|---:|---:|
{chr(10).join(sector_rows)}

## Kiểm tra và cách dùng

- Bộ vòng hai phủ đúng toàn bộ **{len(expected_test_ids)}** nhãn inbound thuộc `test`, không có ID trùng/thiếu/thừa; mọi cặp cùng hội thoại, tenant và split.
- Intent chỉ thuộc danh mục bảy nhãn; sentiment chỉ thuộc ba nhãn trong hướng dẫn AI-05.
- Các khác biệt cần người phụ trách dữ liệu xem lại: **{len(disagreements)}** tin; danh sách ID và nhãn hai vòng ở `disagreements_for_review.csv`, không sao chép nội dung hội thoại.
- Hash nguồn tin nhắn: `{sha256(MESSAGES)}`; hash nguồn vòng một: `{sha256(ROUND_1)}`.
- `round_2_annotations_enriched.csv` là nhãn vòng hai đã ghép ID; `build_round2_evidence.py` kiểm tra độ phủ và tái lập báo cáo. Dùng kết quả này làm chỉ số chất lượng chính thức sau khi người gán thứ hai duyệt các khác biệt và xác nhận.

Kappa không đo chất lượng tuyệt đối của nhãn; cần diễn giải cùng phân bố lớp, số lượng cặp và việc adjudication các trường hợp bất đồng.
"""
    REPORT.write_text(report, encoding="utf-8", newline="\n")
    print(f"PASS: {len(paired)} paired test messages / 60 conversations / {len(SECTORS)} sectors")
    print(f"intent agreement={intent_pairs / len(paired):.4f}; kappa={fmt(overall_intent_kappa)}")
    print(f"sentiment agreement={sentiment_pairs / len(paired):.4f}; kappa={fmt(overall_sentiment_kappa)}")
    print(f"disagreements={len(disagreements)}; source files unchanged; report={REPORT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
