"""Offline W06 data-quality validation for interactions, CRM tasks and audit logs.

The validator reads the canonical CSV seed only.  It makes no database
connection and does not modify any data.
"""

from collections import defaultdict
import csv
from datetime import datetime
import json
from pathlib import Path
import re
import sys
import uuid


SEED = Path(__file__).resolve().parents[1] / "data" / "seed" / "multisector_v2"
W06_TABLES = ("interactions", "crm_tasks", "audit_logs")
INTERACTION_VISIBILITY = {"CARE_VISIBLE", "SALES_ONLY"}
TASK_STATUSES = {"OPEN", "IN_PROGRESS", "DONE", "CANCELLED"}
TASK_ORIGINS = {"MANUAL", "WORKFLOW", "IMPORT"}
SENSITIVE_KEY = re.compile(r"password|secret|token|api[_-]?key|authorization", re.IGNORECASE)


def load(table):
    with (SEED / f"{table}.csv").open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def parse_timestamp(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def checked_timestamp(row, field, errors, table):
    try:
        return parse_timestamp(row[field])
    except ValueError:
        add(errors, table, row["id"], f"invalid timestamp {field}")
        return None


def add(errors, table, row_id, message):
    errors.append(f"{table}/{row_id}: {message}")


def check_uuid(value, errors, table, row_id, field):
    try:
        uuid.UUID(value)
    except (ValueError, AttributeError):
        add(errors, table, row_id, f"invalid UUID in {field}")


def check_required_text(row, fields, errors, table):
    for field in fields:
        value = row[field]
        if not value.strip() or value != value.strip() or "\ufffd" in value:
            add(errors, table, row["id"], f"invalid required text {field}")


def check_unique_ids(rows, errors, table):
    seen = set()
    for row in rows:
        check_uuid(row["id"], errors, table, row["id"], "id")
        if row["id"] in seen:
            add(errors, table, row["id"], "duplicate primary key")
        seen.add(row["id"])


def check_scope(row, field, referenced, errors, table):
    identifier = row["id"]
    value = row[field]
    if not value:
        return
    target = referenced.get(value)
    if not target:
        add(errors, table, identifier, f"missing FK {field}")
    elif target["organization_id"] != row["organization_id"]:
        add(errors, table, identifier, f"cross-organization {field}")


def contains_sensitive_key(value):
    if isinstance(value, dict):
        return any(SENSITIVE_KEY.search(key) or contains_sensitive_key(item) for key, item in value.items())
    if isinstance(value, list):
        return any(contains_sensitive_key(item) for item in value)
    return False


def check_natural_duplicates(rows, fields, errors, table):
    grouped = defaultdict(list)
    for row in rows:
        grouped[tuple(row[field] for field in fields)].append(row["id"])
    for key, identifiers in grouped.items():
        if len(identifiers) > 1:
            errors.append(f"{table}: duplicate natural event key {key}: {identifiers}")


def main():
    interactions = load("interactions")
    tasks = load("crm_tasks")
    audits = load("audit_logs")
    manifest = json.loads((SEED / "manifest.json").read_text(encoding="utf-8"))
    organizations = {row["id"] for row in load("organizations")}
    users = {row["id"]: row for row in load("users")}
    customers = {row["id"]: row for row in load("customers")}
    leads = {row["id"]: row for row in load("leads")}
    opportunities = {row["id"]: row for row in load("opportunities")}
    errors = []

    for table, rows in (("interactions", interactions), ("crm_tasks", tasks), ("audit_logs", audits)):
        if len(rows) != manifest["tableCounts"].get(table):
            errors.append(f"{table}: row count {len(rows)} differs from manifest")
        check_unique_ids(rows, errors, table)
        for row in rows:
            check_uuid(row["organization_id"], errors, table, row["id"], "organization_id")
            if row["organization_id"] not in organizations:
                add(errors, table, row["id"], "unknown organization_id")

    for row in interactions:
        check_required_text(row, ("type", "channel", "subject", "content", "visibility"), errors, "interactions")
        if row["visibility"] not in INTERACTION_VISIBILITY:
            add(errors, "interactions", row["id"], "invalid visibility")
        if sum(bool(row[field]) for field in ("customer_id", "lead_id")) != 1:
            add(errors, "interactions", row["id"], "customer/lead XOR violation")
        for field, reference in (("customer_id", customers), ("lead_id", leads), ("opportunity_id", opportunities), ("recorded_by_user_id", users)):
            check_scope(row, field, reference, errors, "interactions")
        occurred = checked_timestamp(row, "occurred_at", errors, "interactions")
        created = checked_timestamp(row, "created_at", errors, "interactions")
        updated = checked_timestamp(row, "updated_at", errors, "interactions")
        if None not in (occurred, created, updated) and not occurred <= created <= updated:
            add(errors, "interactions", row["id"], "occurred/created/updated timeline violation")
    check_natural_duplicates(interactions, ("organization_id", "recorded_by_user_id", "type", "channel", "subject", "occurred_at"), errors, "interactions")

    for row in tasks:
        check_required_text(row, ("title", "priority", "status", "origin"), errors, "crm_tasks")
        if row["status"] not in TASK_STATUSES:
            add(errors, "crm_tasks", row["id"], "invalid status")
        if row["origin"] not in TASK_ORIGINS:
            add(errors, "crm_tasks", row["id"], "invalid origin")
        if sum(bool(row[field]) for field in ("customer_id", "lead_id")) != 1:
            add(errors, "crm_tasks", row["id"], "customer/lead XOR violation")
        for field, reference in (("customer_id", customers), ("lead_id", leads), ("opportunity_id", opportunities), ("owner_user_id", users), ("created_by_user_id", users)):
            check_scope(row, field, reference, errors, "crm_tasks")
        created = checked_timestamp(row, "created_at", errors, "crm_tasks")
        updated = checked_timestamp(row, "updated_at", errors, "crm_tasks")
        due = checked_timestamp(row, "due_at", errors, "crm_tasks")
        completed = checked_timestamp(row, "completed_at", errors, "crm_tasks") if row["completed_at"] else None
        if None not in (created, updated, due) and (updated < created or due < created):
            add(errors, "crm_tasks", row["id"], "created/updated/due timeline violation")
        if None not in (created, updated, completed) and not created <= completed <= updated:
            add(errors, "crm_tasks", row["id"], "completed_at outside task timeline")
        if row["status"] == "DONE" and not completed:
            add(errors, "crm_tasks", row["id"], "DONE task has no completed_at")
    check_natural_duplicates(tasks, ("organization_id", "owner_user_id", "title", "due_at"), errors, "crm_tasks")

    for row in audits:
        check_required_text(row, ("action", "entity_type", "entity_id", "result"), errors, "audit_logs")
        check_uuid(row["actor_user_id"], errors, "audit_logs", row["id"], "actor_user_id")
        check_uuid(row["entity_id"], errors, "audit_logs", row["id"], "entity_id")
        check_scope(row, "actor_user_id", users, errors, "audit_logs")
        checked_timestamp(row, "occurred_at", errors, "audit_logs")
        for field in ("before_data", "after_data", "safe_metadata"):
            try:
                payload = json.loads(row[field])
            except json.JSONDecodeError:
                add(errors, "audit_logs", row["id"], f"invalid JSON {field}")
                continue
            if not isinstance(payload, dict):
                add(errors, "audit_logs", row["id"], f"{field} must be a JSON object")
            elif contains_sensitive_key(payload):
                add(errors, "audit_logs", row["id"], f"sensitive key in {field}")
    check_natural_duplicates(audits, ("organization_id", "actor_user_id", "action", "entity_type", "entity_id", "occurred_at"), errors, "audit_logs")

    if errors:
        print("W06 VALIDATION: FAIL")
        print("\n".join(f"- {error}" for error in errors))
        return 1
    print("W06 VALIDATION: PASS (interactions=156, crm_tasks=84, audit_logs=63)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
