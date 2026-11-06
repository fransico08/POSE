from pathlib import Path
import csv
import json
import hashlib
import re
from datetime import datetime

BASE = Path(__file__).resolve().parents[1] / "data" / "seed" / "multisector_v2"

EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")

errors = []


def load(name):
    with open(BASE / name, "r", encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))


def parse_dt(v):
    if not v:
        return None
    return datetime.fromisoformat(v.replace("Z", "+00:00"))


def err(message):
    errors.append(message)


users = load("users.csv")
tasks = load("crm_tasks.csv")
audits = load("audit_logs.csv")
decisions = load("import_decisions.csv")
import_rows = load("import_rows.csv")
feedbacks = load("feedbacks.csv")
leads = load("leads.csv")
opportunities = load("opportunities.csv")
customers = load("customers.csv")
offerings = load("offerings.csv")
handovers = load("handover_requests.csv")

users_by_id = {r["id"]: r for r in users}
leads_by_id = {r["id"]: r for r in leads}


# ============================================================
# TASK TIMELINE
# ============================================================

for r in tasks:
    created = parse_dt(r["created_at"])
    updated = parse_dt(r["updated_at"])
    completed = parse_dt(r["completed_at"])

    if updated and created and updated < created:
        err("Task updated_at before created_at: " + r["id"])

    if completed and created and completed < created:
        err("Task completed_at before created_at: " + r["id"])

    if completed and updated and updated < completed:
        err("Task updated_at before completed_at: " + r["id"])


# ============================================================
# AUDIT ORGANIZATION SCOPE
# ============================================================

for r in audits:
    actor = users_by_id.get(r["actor_user_id"])

    if actor and actor["organization_id"] != r["organization_id"]:
        err("Cross-org audit actor: " + r["id"])


# ============================================================
# IMPORT APPROVAL 1:1
# ============================================================

approved_audits = {}

for r in audits:
    if r["action"] == "IMPORT_APPROVED":
        approved_audits.setdefault(r["entity_id"], []).append(r)

for d in decisions:
    matches = approved_audits.get(d["import_job_id"], [])

    if len(matches) != 1:
        err(
            "IMPORT_APPROVED audit count != 1 for job "
            + d["import_job_id"]
        )

    elif (
        matches[0]["occurred_at"] != d["decided_at"]
        or matches[0]["actor_user_id"] != d["decided_by_user_id"]
    ):
        err(
            "IMPORT_APPROVED audit does not match decision "
            + d["import_job_id"]
        )


# ============================================================
# IMPORT FINGERPRINT
# ============================================================

for r in import_rows:
    expected = hashlib.sha256(
        r["normalized_payload"].encode("utf-8")
    ).hexdigest()

    if r["row_fingerprint"] != expected:
        err("Bad import fingerprint: " + r["id"])


# ============================================================
# INVALID EMAIL SCENARIO
# ============================================================

for r in import_rows:
    if r["status"] != "INVALID":
        continue

    combined = " ".join(str(v) for v in r.values()).lower()

    if "email" not in combined and "dq-cus-01" not in combined:
        continue

    payload = json.loads(r["normalized_payload"])
    email = str(payload.get("email", "")).strip()

    if email and EMAIL_RE.match(email):
        err("INVALID email row contains valid email: " + r["id"])


# ============================================================
# LEAD FEEDBACK SCOPE
# ============================================================

for r in feedbacks:
    lead_id = r["lead_id"].strip()

    if not lead_id:
        continue

    lead = leads_by_id.get(lead_id)

    if not lead:
        err("Feedback references missing Lead: " + r["id"])
        continue

    if r["recorded_by_user_id"] != lead["owner_user_id"]:
        err("Lead feedback actor != Lead owner: " + r["id"])


# ============================================================
# OPPORTUNITY TIMELINE
# ============================================================

opp_audits = {}

for a in audits:
    if a["entity_type"] == "OPPORTUNITY":
        opp_audits.setdefault(a["entity_id"], []).append(a)

for o in opportunities:
    relevant = opp_audits.get(o["id"], [])

    if not relevant:
        continue

    latest = max(parse_dt(a["occurred_at"]) for a in relevant)

    if latest > parse_dt(o["updated_at"]):
        err("Opportunity audit after updated_at: " + o["id"])


# ============================================================
# CUSTOMER METADATA CONSISTENCY
# ============================================================

for c in customers:
    if c["status"] != "INACTIVE" or not c["metadata"]:
        continue

    metadata = json.loads(c["metadata"])

    if metadata.get("demoSegment") == "Active":
        err("Inactive Customer metadata says Active: " + c["id"])


# ============================================================
# REQUIRED REVIEW COVERAGE
# ============================================================

offering_statuses = {r["status"] for r in offerings}

for required in ("ACTIVE", "DRAFT", "INACTIVE"):
    if required not in offering_statuses:
        err("Missing Offering status scenario: " + required)


if not any(r["status"] == "INACTIVE" for r in users):
    err("Missing INACTIVE User scenario")


care_statuses = {
    r["status"]
    for r in handovers
    if r["handover_type"] == "CARE"
}

for required in ("APPROVED", "PENDING", "REJECTED"):
    if required not in care_statuses:
        err("Missing CARE handover scenario: " + required)


# ============================================================
# RESULT
# ============================================================

print("POSE deep validation")
print("Errors:", len(errors))

for e in errors:
    print("ERROR -", e)

if errors:
    raise SystemExit(1)

print(
    "PASS - deep timeline, scope, audit, import and review-scenario "
    "checks passed."
)