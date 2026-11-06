"""Offline integrity checks for the canonical W04 Customer/Lead seed data.

This checker is deliberately CSV-only: it does not connect to PostgreSQL or
modify the dataset.  It complements the broader seed validators by making the
Customer/Lead normalization and lifecycle rules explicit and reusable.
"""

from collections import defaultdict
import csv
from datetime import datetime
from pathlib import Path
import re
import sys


SEED = Path(__file__).resolve().parents[1] / "data" / "seed" / "multisector_v2"
EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
E164_RE = re.compile(r"^\+[1-9][0-9]{7,14}$")
LEAD_STATUSES = {"NEW", "QUALIFIED", "CONVERTED", "DISQUALIFIED"}


def load(name):
    with (SEED / name).open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def timestamp(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00")) if value else None


def collect_normalized_duplicates(rows, field, errors, label):
    grouped = defaultdict(list)
    for row in rows:
        value = row[field]
        if value:
            grouped[(row["organization_id"], value)].append(row["id"])
    for key, identifiers in grouped.items():
        if len(identifiers) > 1:
            errors.append(f"{label}: duplicate {field} within organization {key}: {identifiers}")


def validate_identity(row, errors, label):
    identifier = row["id"]
    email, normalized_email = row["email"], row["normalized_email"]
    phone, normalized_phone = row["phone"], row["normalized_phone"]
    display_name = row["display_name"]

    if not display_name or display_name != display_name.strip() or "\ufffd" in display_name:
        errors.append(f"{label}: invalid display_name {identifier}")
    if not normalized_email and not normalized_phone:
        errors.append(f"{label}: identity is empty {identifier}")
    if email and (email != email.strip() or not EMAIL_RE.fullmatch(email)):
        errors.append(f"{label}: invalid email {identifier}")
    if email and normalized_email != email.lower():
        errors.append(f"{label}: normalized_email must be lowercase email {identifier}")
    if normalized_email and (normalized_email != normalized_email.strip().lower() or not EMAIL_RE.fullmatch(normalized_email)):
        errors.append(f"{label}: invalid normalized_email {identifier}")
    if phone and (phone != phone.strip() or not E164_RE.fullmatch(phone)):
        errors.append(f"{label}: phone must use canonical E.164 display form {identifier}")
    if phone and normalized_phone != phone:
        errors.append(f"{label}: normalized_phone must match canonical E.164 phone {identifier}")
    if normalized_phone and not E164_RE.fullmatch(normalized_phone):
        errors.append(f"{label}: invalid normalized_phone {identifier}")


def validate_timestamps(row, errors, label):
    identifier = row["id"]
    created, updated, archived = map(timestamp, (row["created_at"], row["updated_at"], row["archived_at"]))
    if updated < created:
        errors.append(f"{label}: updated_at before created_at {identifier}")
    if archived and not created <= archived <= updated:
        errors.append(f"{label}: archived_at outside record timeline {identifier}")


def main():
    customers = load("customers.csv")
    leads = load("leads.csv")
    organizations = {row["id"] for row in load("organizations.csv")}
    users = {row["id"]: row for row in load("users.csv")}
    offerings = {row["id"]: row for row in load("offerings.csv")}
    customers_by_id = {row["id"]: row for row in customers}
    errors = []

    if len(customers) != 120:
        errors.append(f"customers: expected 120 rows, found {len(customers)}")
    if len(leads) != 60:
        errors.append(f"leads: expected 60 rows, found {len(leads)}")

    for label, rows in (("customers", customers), ("leads", leads)):
        if len({row["id"] for row in rows}) != len(rows):
            errors.append(f"{label}: duplicate primary key")
        collect_normalized_duplicates(rows, "normalized_email", errors, label)
        collect_normalized_duplicates(rows, "normalized_phone", errors, label)
        for row in rows:
            validate_identity(row, errors, label)
            validate_timestamps(row, errors, label)
            owner = users.get(row["owner_user_id"])
            if row["organization_id"] not in organizations:
                errors.append(f"{label}: unknown organization {row['id']}")
            if not owner or owner["organization_id"] != row["organization_id"]:
                errors.append(f"{label}: owner organization mismatch {row['id']}")

    for row in leads:
        identifier = row["id"]
        offering = offerings.get(row["offering_id"])
        customer = customers_by_id.get(row["converted_customer_id"])
        qualified, converted = timestamp(row["qualified_at"]), timestamp(row["converted_at"])
        created, updated = timestamp(row["created_at"]), timestamp(row["updated_at"])
        if row["status"] not in LEAD_STATUSES:
            errors.append(f"leads: invalid status {identifier}")
        if not offering or offering["organization_id"] != row["organization_id"]:
            errors.append(f"leads: offering organization mismatch {identifier}")
        if row["status"] == "CONVERTED":
            if not customer or customer["organization_id"] != row["organization_id"] or not converted:
                errors.append(f"leads: invalid conversion reference {identifier}")
        elif row["converted_customer_id"] or row["converted_at"]:
            errors.append(f"leads: conversion fields set for non-converted lead {identifier}")
        if row["status"] == "DISQUALIFIED" and not row["disqualification_reason"].strip():
            errors.append(f"leads: missing disqualification reason {identifier}")
        if qualified and not created <= qualified <= updated:
            errors.append(f"leads: qualified_at outside record timeline {identifier}")
        if converted and not created <= converted <= updated:
            errors.append(f"leads: converted_at outside record timeline {identifier}")
        if qualified and converted and qualified > converted:
            errors.append(f"leads: qualified_at after converted_at {identifier}")

    if errors:
        print("W04 VALIDATION: FAIL")
        print("\n".join(f"- {error}" for error in errors))
        return 1
    print(f"W04 VALIDATION: PASS (customers={len(customers)}, leads={len(leads)})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
