#!/usr/bin/env python3
"""Build the W13 AI-05 additive seed extension from the approved delivery."""
from __future__ import annotations

import hashlib
import json
import shutil
from pathlib import Path


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
W12 = ROOT / "database" / "data" / "seed" / "multisector_v2"
DELIVERY = ROOT / "database" / "data" / "newitems" / "w13_ai05_task1_delivery"

COPIED_ARTIFACTS = (
    "channel_accounts.csv",
    "conversations.csv",
    "conversation_messages.csv",
    "round_1_annotations_consolidated.csv",
    "labeling-guide.vi.md",
    "message-data-quality-rules.vi.md",
    "sector_statistics.csv",
)
CORE_FILES = COPIED_ARTIFACTS + ("manifest.json", "README.md")


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def copy_artifacts(output: Path) -> None:
    for name in COPIED_ARTIFACTS:
        shutil.copyfile(DELIVERY / name, output / name)


def manifest(output: Path) -> dict[str, object]:
    return {
        "dataset": "POSE CRM W13 AI-05 Task 1 additive extension",
        "version": "1.0",
        "baseSeed": {
            "path": "database/data/seed/multisector_v2",
            "manifestSha256": sha256(W12 / "manifest.json"),
            "immutable": True,
        },
        "sourceDelivery": {
            "path": "database/data/newitems/w13_ai05_task1_delivery",
            "artifactSha256": {name: sha256(DELIVERY / name) for name in COPIED_ARTIFACTS},
        },
        "artifacts": {name: sha256(output / name) for name in COPIED_ARTIFACTS},
        "counts": {
            "channelAccounts": 6,
            "conversations": 300,
            "messages": 1927,
            "round1Annotations": 1002,
            "devConversations": 240,
            "testConversations": 60,
        },
        "loadOrder": [
            "Load and validate multisector_v2 first.",
            "Load channel_accounts.csv.",
            "Load conversations.csv.",
            "Load conversation_messages.csv.",
            "Consume round_1_annotations_consolidated.csv only through an approved AI evaluation pipeline.",
        ],
        "scope": {
            "databaseImport": "NOT_RUN",
            "canonicalW12Mutation": False,
            "v15SignalsSeeded": False,
            "secondRoundLabels": False,
        },
    }


def readme() -> str:
    return """# W13 AI-05 Task 1 - Canonical seed extension

Đây là extension additive của `database/data/seed/multisector_v2`, không phải một canonical seed độc lập và không chứa bản sao của sáu pilot nguồn.

## Nội dung

- 6 `ZALO_SIM` channel accounts.
- 300 conversations: 240 dev, 60 test.
- 1.927 messages và 1.002 round-1 inbound annotations.
- Hướng dẫn gán nhãn, quy tắc Data Quality và thống kê sáu ngành.

## Nguồn và tính bất biến

Các CSV, labeling guide, Data Quality rules và statistics được sao chép byte-for-byte từ `database/data/newitems/w13_ai05_task1_delivery`. `manifest.json` lưu hash của W12 canonical và source delivery. Không sửa, sao chép hoặc nạp lại các thư mục pilot theo ngành.

## Thứ tự nạp khi có pipeline được phê duyệt

1. Nạp và xác minh `multisector_v2`.
2. `channel_accounts.csv`.
3. `conversations.csv`.
4. `conversation_messages.csv`.
5. Dùng `round_1_annotations_consolidated.csv` cho evaluation pipeline AI được phê duyệt.

Không nạp PostgreSQL trong Task 1. Không seed Interaction, Outbox, Task, V15 signals hay nhãn vòng 2.
"""


def write_text(path: Path, value: str, encoding: str = "utf-8") -> None:
    with path.open("w", encoding=encoding, newline="\n") as handle:
        handle.write(value)


def build(output: Path = HERE) -> dict[str, int]:
    output.mkdir(parents=True, exist_ok=True)
    copy_artifacts(output)
    write_text(output / "manifest.json", json.dumps(manifest(output), ensure_ascii=False, indent=2) + "\n")
    write_text(output / "README.md", readme())
    checksums = "".join(f"{sha256(output / name)}  {name}\n" for name in CORE_FILES)
    write_text(output / "SHA256SUMS.txt", checksums, encoding="ascii")
    return {"conversations": 300, "messages": 1927, "annotations": 1002}


if __name__ == "__main__":
    print(json.dumps(build(), ensure_ascii=False))
