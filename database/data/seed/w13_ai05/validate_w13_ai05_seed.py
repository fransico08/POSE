#!/usr/bin/env python3
"""Validate the W13 AI-05 additive seed extension without importing a database."""
from __future__ import annotations

import csv
import hashlib
import json
import subprocess
import sys
import tempfile
from pathlib import Path

from build_w13_ai05_seed import CORE_FILES, COPIED_ARTIFACTS, DELIVERY, HERE, ROOT, W12, build, sha256


def count_rows(path: Path) -> int:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return sum(1 for _ in csv.DictReader(handle))


def run_cross_sector_validator() -> tuple[bool, str]:
    validator = DELIVERY / "validate_task1_delivery.py"
    result = subprocess.run(
        [sys.executable, str(validator)],
        cwd=DELIVERY,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    output = (result.stdout + result.stderr).strip()
    return result.returncode == 0 and output.endswith("PASS"), output


def parse_checksums() -> tuple[dict[str, str], list[str]]:
    hashes: dict[str, str] = {}
    failures: list[str] = []
    for line in (HERE / "SHA256SUMS.txt").read_text(encoding="ascii").splitlines():
        digest, separator, name = line.partition("  ")
        if not separator or name in hashes:
            failures.append(f"invalid checksum entry: {line}")
        else:
            hashes[name] = digest
    return hashes, failures


def validate() -> tuple[list[str], dict[str, object]]:
    failures: list[str] = []
    cross_ok, cross_output = run_cross_sector_validator()
    if not cross_ok:
        failures.append(f"cross-sector validator failed: {cross_output[-1000:]}")

    manifest = json.loads((HERE / "manifest.json").read_text(encoding="utf-8"))
    failures.extend(
        "missing extension artifact: " + name
        for name in CORE_FILES
        if not (HERE / name).is_file()
    )
    expected_w12_hash = manifest["baseSeed"]["manifestSha256"]
    if sha256(W12 / "manifest.json") != expected_w12_hash:
        failures.append("W12 canonical manifest drift")

    source_hashes = manifest["sourceDelivery"]["artifactSha256"]
    extension_hashes = manifest["artifacts"]
    for name in COPIED_ARTIFACTS:
        source = DELIVERY / name
        extension = HERE / name
        if sha256(source) != source_hashes.get(name):
            failures.append(f"source delivery hash drift: {name}")
        if sha256(extension) != extension_hashes.get(name):
            failures.append(f"extension hash drift: {name}")
        if source.read_bytes() != extension.read_bytes():
            failures.append(f"extension differs from delivery: {name}")

    expected_counts = {
        "channel_accounts.csv": 6,
        "conversations.csv": 300,
        "conversation_messages.csv": 1927,
        "round_1_annotations_consolidated.csv": 1002,
    }
    actual_counts = {name: count_rows(HERE / name) for name in expected_counts}
    for name, expected in expected_counts.items():
        if actual_counts[name] != expected:
            failures.append(f"row count {name}: {actual_counts[name]} != {expected}")

    hashes, checksum_failures = parse_checksums()
    failures.extend(checksum_failures)
    if set(hashes) != set(CORE_FILES):
        failures.append("SHA256SUMS artifact set")
    for name, expected in hashes.items():
        if (HERE / name).is_file() and sha256(HERE / name) != expected:
            failures.append(f"SHA256 mismatch: {name}")

    with tempfile.TemporaryDirectory() as temporary:
        generated = Path(temporary)
        build(generated)
        for name in CORE_FILES:
            if (generated / name).read_bytes() != (HERE / name).read_bytes():
                failures.append(f"non-reproducible: {name}")

    evidence = {
        "overall": "PASS" if not failures else "FAIL",
        "counts": actual_counts,
        "crossSectorValidator": "PASS" if cross_ok else "FAIL",
        "checks": {
            "w12Manifest": not any("W12" in item for item in failures),
            "exactDeliveryBytes": not any("delivery" in item or "extension differs" in item for item in failures),
            "checksums": not any("SHA256" in item or "checksum" in item for item in failures),
            "reproducibility": not any("non-reproducible" in item for item in failures),
        },
        "failures": failures,
    }
    (HERE / "validation_evidence.json").write_text(
        json.dumps(evidence, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return failures, evidence


if __name__ == "__main__":
    failures, evidence = validate()
    print(json.dumps(evidence["counts"], ensure_ascii=False))
    print("PASS" if not failures else "FAIL: " + "; ".join(failures))
    raise SystemExit(0 if not failures else 1)
