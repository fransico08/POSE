"""Offline W08-02 checks for canonical outbox, workflow execution and CRM Task CSVs."""

from collections import defaultdict
from contextlib import redirect_stdout
import csv
from datetime import datetime
import json
from io import StringIO
from pathlib import Path
import sys
import uuid


SEED = Path(__file__).resolve().parents[1] / "data" / "seed" / "multisector_v2"
OUTBOX_STATUS = {"PENDING", "PUBLISHED", "FAILED"}
EXECUTION_STATUS = {"PENDING", "RUNNING", "DONE", "FAILED"}
TASK_ORIGIN = {"MANUAL", "WORKFLOW", "IMPORT"}


def load(table):
    with (SEED / f"{table}.csv").open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def timestamp(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00")) if value else None


def valid_uuid(value):
    try:
        uuid.UUID(value)
        return True
    except (ValueError, TypeError):
        return False


def report(name, violations, errors):
    print(f"{name}: {'PASS' if not violations else 'FAIL'} ({len(violations)})")
    errors.extend(f"{name}: {value}" for value in violations)


def duplicate_keys(rows, fields):
    grouped = defaultdict(list)
    for row in rows:
        grouped[tuple(row[field] for field in fields)].append(row["id"])
    return [f"{key} => {ids}" for key, ids in grouped.items() if len(ids) > 1]


def validate(rows, manifest, organizations, users, leads, tasks):
    outbox, executions = rows["outbox_events"], rows["workflow_executions"]
    errors = []
    for table, values in rows.items():
        report(f"{table}_count", [] if len(values) == manifest[table] else [f"{len(values)} != {manifest[table]}"], errors)
        report(f"{table}_primary_key", [row["id"] for row in values if not valid_uuid(row["id"])], errors)
        report(f"{table}_duplicate_id", duplicate_keys(values, ("id",)), errors)

    report("outbox_unique_tenant_key", duplicate_keys(outbox, ("organization_id", "idempotency_key")), errors)
    report("execution_unique_tenant_key", duplicate_keys(executions, ("organization_id", "idempotency_key")), errors)
    report("task_unique_tenant_key", duplicate_keys([row for row in tasks if row["idempotency_key"]], ("organization_id", "idempotency_key")), errors)

    outbox_by_id = {row["id"]: row for row in outbox}
    task_by_id = {row["id"]: row for row in tasks}
    outbox_issues = []
    for row in outbox:
        if row["organization_id"] not in organizations or row["delivery_status"] not in OUTBOX_STATUS:
            outbox_issues.append(row["id"])
        if not row["idempotency_key"] or int(row["retry_count"]) < 0:
            outbox_issues.append(row["id"])
        occurred, published = timestamp(row["occurred_at"]), timestamp(row["published_at"])
        if not occurred or (row["delivery_status"] == "PUBLISHED" and not published) or (published and published < occurred):
            outbox_issues.append(row["id"])
        if row["event_type"] == "LEAD_QUALIFIED":
            lead = leads.get(row["aggregate_id"])
            if row["aggregate_type"] != "LEAD" or not lead or lead["organization_id"] != row["organization_id"]:
                outbox_issues.append(row["id"])
    report("outbox_status_scope_timeline", sorted(set(outbox_issues)), errors)

    execution_issues = []
    for row in executions:
        outbox_row = outbox_by_id.get(row["outbox_event_id"])
        task = task_by_id.get(row["created_task_id"])
        started, finished = timestamp(row["started_at"]), timestamp(row["finished_at"])
        if row["organization_id"] not in organizations or row["status"] not in EXECUTION_STATUS or int(row["retry_count"]) < 0:
            execution_issues.append(row["id"])
        if not outbox_row or outbox_row["organization_id"] != row["organization_id"]:
            execution_issues.append(row["id"])
        if started and finished and finished < started:
            execution_issues.append(row["id"])
        if row["status"] == "DONE" and not finished:
            execution_issues.append(row["id"])
        if task and (task["organization_id"] != row["organization_id"] or task["origin"] != "WORKFLOW" or task["idempotency_key"] != row["idempotency_key"]):
            execution_issues.append(row["id"])
        if row["workflow_type"] == "LEAD_QUALIFIED_FOLLOW_UP" and row["status"] == "DONE" and not task:
            execution_issues.append(row["id"])
    report("execution_fk_scope_timeline", sorted(set(execution_issues)), errors)

    task_issues = []
    for row in tasks:
        owner, creator = users.get(row["owner_user_id"]), users.get(row["created_by_user_id"])
        lead = leads.get(row["lead_id"])
        if row["origin"] not in TASK_ORIGIN or not owner or not creator:
            task_issues.append(row["id"])
        if owner and owner["organization_id"] != row["organization_id"]:
            task_issues.append(row["id"])
        if creator and creator["organization_id"] != row["organization_id"]:
            task_issues.append(row["id"])
        if lead and lead["organization_id"] != row["organization_id"]:
            task_issues.append(row["id"])
        if row["origin"] == "WORKFLOW" and not row["idempotency_key"]:
            task_issues.append(row["id"])
    report("task_origin_scope_idempotency", sorted(set(task_issues)), errors)
    return errors


def run_negative_fixtures(rows, manifest, organizations, users, leads, tasks):
    duplicate = [dict(row) for row in rows["outbox_events"]]
    duplicate.append(dict(duplicate[0], id="00000000-0000-4000-8000-000000000001"))
    assert duplicate_keys(duplicate, ("organization_id", "idempotency_key"))
    broken = [dict(row) for row in rows["workflow_executions"]]
    broken[0]["organization_id"] = "00000000-0000-4000-8000-000000000099"
    negative_rows = dict(rows, workflow_executions=broken)
    with redirect_stdout(StringIO()):
        assert validate(negative_rows, manifest, organizations, users, leads, tasks)
    print("negative_fixtures: PASS (duplicate key and tenant mismatch detected)")


def main():
    rows = {name: load(name) for name in ("outbox_events", "workflow_executions", "crm_tasks")}
    manifest = json.loads((SEED / "manifest.json").read_text(encoding="utf-8"))["tableCounts"]
    organizations = {row["id"] for row in load("organizations")}
    users = {row["id"]: row for row in load("users")}
    leads = {row["id"]: row for row in load("leads")}
    tasks = rows["crm_tasks"]
    errors = validate(rows, manifest, organizations, users, leads, tasks)
    run_negative_fixtures(rows, manifest, organizations, users, leads, tasks)
    print(f"actual_counts: outbox_events={len(rows['outbox_events'])}, workflow_executions={len(rows['workflow_executions'])}, crm_tasks={len(tasks)}")
    if errors:
        print("W08-02 VALIDATION: FAIL")
        print("\n".join(f"- {error}" for error in errors))
        return 1
    print("W08-02 VALIDATION: PASS (offline only; runtime QA remains NOT RUN)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
