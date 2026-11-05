from pathlib import Path
import csv, json, collections, hashlib, re
from datetime import datetime, timezone, timedelta, date

ROOT = Path(__file__).resolve().parents[1]
SEED = ROOT / "data" / "seed" / "multisector_v2"


def load(name):
    with open(SEED / name, "r", encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))


def parse_dt(v):
    return datetime.fromisoformat(v.replace("Z", "+00:00")) if v else None

orgs = load("organizations.csv")
users = load("users.csv")
customers = load("customers.csv")
leads = load("leads.csv")
opportunities = load("opportunities.csv")
tasks = load("crm_tasks.csv")
imports = load("import_rows.csv")
decisions = load("import_decisions.csv")
audits = load("audit_logs.csv")
feedbacks = load("feedbacks.csv")
handovers = load("handover_requests.csv")
offerings = load("offerings.csv")
grants = load("user_role_grants.csv")
manifest = json.loads((SEED / "manifest.json").read_text(encoding="utf-8-sig"))

results = {}
results["tables"] = len(manifest["tableCounts"])
results["seed_rows"] = sum(manifest["tableCounts"].values())
results["organizations"] = len(orgs)
results["users"] = len(users)
results["customers"] = len(customers)
results["leads"] = len(leads)
results["opportunities"] = len(opportunities)
results["interactions"] = len(load("interactions.csv"))
results["feedbacks"] = len(feedbacks)
results["tasks"] = len(tasks)
results["offerings"] = len(offerings)
results["handovers"] = len(handovers)
results["support_requests"] = len(load("support_requests.csv"))
results["audit_logs"] = len(audits)
results["customer_missing_identity"] = sum(1 for r in customers if not r["normalized_email"].strip() and not r["normalized_phone"].strip())
results["lead_missing_identity"] = sum(1 for r in leads if not r["normalized_email"].strip() and not r["normalized_phone"].strip())

for rows, prefix in [(customers, "customer"), (leads, "lead")]:
    for field in ["normalized_email", "normalized_phone"]:
        c = collections.Counter((r["organization_id"], r[field].strip()) for r in rows if r[field].strip())
        results[f"{prefix}_{field}_duplicate_groups"] = sum(1 for v in c.values() if v > 1)

results["opportunity_customer_subjects"] = sum(1 for r in opportunities if r["customer_id"])
results["opportunity_lead_subjects"] = sum(1 for r in opportunities if r["lead_id"])
results["opportunity_xor_invalid"] = sum(1 for r in opportunities if int(bool(r["customer_id"])) + int(bool(r["lead_id"])) != 1)
results["task_chronology_errors"] = sum(1 for r in tasks if parse_dt(r["updated_at"]) < parse_dt(r["created_at"]) or (r["completed_at"] and parse_dt(r["completed_at"]) < parse_dt(r["created_at"])) or (r["completed_at"] and parse_dt(r["updated_at"]) < parse_dt(r["completed_at"])))
results["multi_role_users"] = sum(1 for v in collections.Counter(r["user_id"] for r in grants).values() if v > 1)
results["fingerprint_errors"] = sum(1 for r in imports if hashlib.sha256(r["normalized_payload"].encode("utf-8")).hexdigest() != r["row_fingerprint"])
email_re = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
results["invalid_email_rows_still_valid"] = sum(1 for r in imports if r["status"] == "INVALID" and email_re.match(json.loads(r["normalized_payload"]).get("email", "") or ""))
approved_audits = collections.Counter(r["entity_id"] for r in audits if r["action"] == "IMPORT_APPROVED")
results["import_decisions"] = len(decisions)
results["import_approved_audits"] = sum(1 for r in audits if r["action"] == "IMPORT_APPROVED")
results["import_approval_count_errors"] = sum(1 for d in decisions if approved_audits[d["import_job_id"]] != 1)
user_by_id = {r["id"]: r for r in users}
results["cross_org_audit_errors"] = sum(1 for r in audits if r["actor_user_id"] in user_by_id and user_by_id[r["actor_user_id"]]["organization_id"] != r["organization_id"])
lead_by_id = {r["id"]: r for r in leads}
results["lead_feedback_scope_errors"] = sum(1 for r in feedbacks if r["lead_id"] and r["recorded_by_user_id"] != lead_by_id[r["lead_id"]]["owner_user_id"])
opp_audits = collections.defaultdict(list)
for a in audits:
    if a["entity_type"] == "OPPORTUNITY":
        opp_audits[a["entity_id"]].append(parse_dt(a["occurred_at"]))
results["opportunity_timeline_errors"] = sum(1 for o in opportunities if o["id"] in opp_audits and max(opp_audits[o["id"]]) > parse_dt(o["updated_at"]))
results["inactive_customer_metadata_errors"] = sum(1 for c in customers if c["status"] == "INACTIVE" and c["metadata"] and json.loads(c["metadata"]).get("demoSegment") == "Active")
results["user_status"] = dict(collections.Counter(r["status"] for r in users))
results["offering_status"] = dict(collections.Counter(r["status"] for r in offerings))
results["handover_status"] = dict(collections.Counter(r["status"] for r in handovers))
results["care_handover_status"] = dict(collections.Counter(r["status"] for r in handovers if r["handover_type"] == "CARE"))
results["import_job_status"] = dict(collections.Counter(r["status"] for r in load("import_jobs.csv")))
results["sales_assignment_status"] = dict(collections.Counter(r["status"] for r in load("sales_assignments.csv")))

as_of = date.fromisoformat(manifest["demoAsOf"])
tz = timezone(timedelta(hours=7))
active = [r for r in tasks if r["status"] in ("OPEN", "IN_PROGRESS")]
results["active_tasks"] = len(active)
results["active_tasks_overdue"] = sum(1 for r in active if parse_dt(r["due_at"]).astimezone(tz).date() < as_of)
results["active_tasks_due_today"] = sum(1 for r in active if parse_dt(r["due_at"]).astimezone(tz).date() == as_of)
results["active_tasks_future"] = sum(1 for r in active if parse_dt(r["due_at"]).astimezone(tz).date() > as_of)
results["demo_as_of"] = manifest["demoAsOf"]

print(json.dumps(results, ensure_ascii=False, indent=2))
