#!/usr/bin/env python3
"""Validate W13 AI-05 Logistics pilot artifacts and reproducibility."""
from __future__ import annotations
import csv, hashlib, tempfile
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from generate_log_pilot import ANNOTATION_TABLE, ASSIGNEE, CANONICAL, CHANNEL_ID, HERE, ORG, OUTPUT_FILES, START, TEAM, canonical, generate, parse_source, review

END = datetime(2026, 11, 17, tzinfo=timezone.utc)
INTENTS = {"ASK_PRICE","ASK_INFO","BOOKING","PURCHASE_READY","COMPLAINT","SUPPORT","OTHER"}
SENTIMENTS = {"POSITIVE","NEUTRAL","NEGATIVE"}


def rows(path):
    with path.open(encoding="utf-8-sig", newline="") as file: return list(csv.DictReader(file))


def instant(value): return datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)


def vn_price(amount, unit): return f"{int(amount):,}".replace(",", ".") + f" VND/{unit.lstrip('/')}"


def validate(output):
    failures=[]; approved=parse_source(); offerings, prices, profiles=canonical()
    accounts=rows(output/"channel_accounts.csv"); conversations=rows(output/"conversations.csv"); messages=rows(output/"conversation_messages.csv"); annotations=rows(output/"round_1_annotations.csv")
    teams={row["id"]:row for row in rows(CANONICAL/"teams.csv")}; users={row["id"]:row for row in rows(CANONICAL/"users.csv")}
    memberships=rows(CANONICAL/"team_memberships.csv")
    if len(ANNOTATION_TABLE)!=150: failures.append("explicit annotation table")
    if len(offerings)!=38: failures.append("ACTIVE Logistics offerings")
    if len(accounts)!=1: failures.append("channel account count")
    if accounts:
        expected={"id":CHANNEL_ID,"organization_id":ORG,"channel_type":"ZALO_SIM","team_id":TEAM,"default_assignee_user_id":ASSIGNEE,"status":"ACTIVE"}
        for key,value in expected.items():
            if accounts[0].get(key)!=value: failures.append(f"channel account {key}")
    if teams.get(TEAM,{}).get("organization_id")!=ORG or teams.get(TEAM,{}).get("status")!="ACTIVE": failures.append("team mapping")
    if users.get(ASSIGNEE,{}).get("organization_id")!=ORG or users.get(ASSIGNEE,{}).get("status")!="ACTIVE": failures.append("assignee mapping")
    if not any(row["team_id"]==TEAM and row["user_id"]==ASSIGNEE and row["status"]=="ACTIVE" for row in memberships): failures.append("assignee membership")
    if len(conversations)!=50 or len({row["id"] for row in conversations})!=50 or len({row["external_thread_id"] for row in conversations})!=50: failures.append("conversation uniqueness")
    by_id={row["id"]:row for row in conversations}; by_message=defaultdict(list); message_by_id={row["id"]:row for row in messages}; inbound=set(); duplicates=Counter(); splits=defaultdict(set); customers=leads=0
    for row in conversations:
        if row["organization_id"]!=ORG or row["channel_account_id"]!=CHANNEL_ID or row["assigned_user_id"]!=ASSIGNEE: failures.append("conversation tenant/FK")
        if row["status"] not in {"OPEN","PENDING","CLOSED"}: failures.append("conversation status")
        if bool(row["customer_id"])==bool(row["lead_id"]): failures.append("profile cardinality")
        profile=profiles.get(row["customer_id"] or row["lead_id"],{})
        if profile.get("organization_id")!=ORG: failures.append("profile tenant")
        if row["customer_id"]:
            customers+=1
            if profile.get("status")!="ACTIVE": failures.append("customer status")
        else:
            leads+=1
            if profile.get("status") not in {"NEW","QUALIFIED"}: failures.append("lead status")
    if (customers,leads)!=(35,15): failures.append("35/15 split")
    for row in messages:
        by_message[row["conversation_id"]].append(row); duplicates[(row["direction"],row["content"])]+=1
        if row["organization_id"]!=ORG or row["conversation_id"] not in by_id or not row["content"].strip() or row["content_type"]!="TEXT": failures.append("message schema/FK")
        if not START<=instant(row["occurred_at"])<END: failures.append("timestamp window")
        if row["direction"]=="INBOUND":
            inbound.add(row["id"])
            if row["sent_by_user_id"] or row["delivery_status"]!="RECEIVED": failures.append("inbound delivery")
        elif row["direction"]=="OUTBOUND":
            if row["sent_by_user_id"]!=ASSIGNEE or row["delivery_status"]!="SENT": failures.append("outbound delivery")
        else: failures.append("message direction")
    if len(messages)!=300 or len(message_by_id)!=300: failures.append("message count/uniqueness")
    if any(count>1 and len(content)>25 for (_,content),count in duplicates.items()): failures.append("duplicate substantive messages")
    labels={row["message_id"]:row for row in annotations}
    if set(labels)!=inbound or len(annotations)!=150: failures.append("annotation coverage")
    for row in annotations:
        message=message_by_id.get(row["message_id"])
        if row["organization_id"]!=ORG or row["intent"] not in INTENTS or row["sentiment"] not in SENTIMENTS: failures.append("annotation schema")
        if not message or message["conversation_id"]!=row["conversation_id"] or message["direction"]!="INBOUND": failures.append("annotation FK")
        splits[row["split"]].add(row["conversation_id"])
    if len(splits["dev"])!=40 or len(splits["test"])!=10 or splits["dev"]&splits["test"]: failures.append("split")
    for ordinal,item in enumerate(approved,1):
        editorial,kind,profile_id,profile_status,code,declared_price,split,turns=item; conversation=conversations[ordinal-1]; offering=offerings.get(code,{})
        if not offering or offering.get("id") not in prices or declared_price!=vn_price(prices[offering["id"]]["amount"],prices[offering["id"]]["pricing_unit"]): failures.append(f"W12 offering/price {editorial}")
        relation="related_offering_id" if kind=="Customer" else "offering_id"; profile=profiles.get(profile_id,{})
        if profile.get(relation)!=offering.get("id") or profile.get("status")!=profile_status or (conversation["customer_id"] or conversation["lead_id"])!=profile_id: failures.append(f"profile relation {editorial}")
        actual=by_message[conversation["id"]]
        if [(row["direction"],row["content"]) for row in actual]!=turns: failures.append(f"exact text {editorial}")
        moments=[instant(row["occurred_at"]) for row in actual]
        inbound_times=[instant(row["occurred_at"]) for row in actual if row["direction"]=="INBOUND"]
        if moments!=sorted(moments) or instant(conversation["created_at"])!=moments[0] or instant(conversation["last_message_at"])!=moments[-1] or instant(conversation["last_inbound_at"])!=inbound_times[-1]: failures.append(f"chronology {editorial}")
    raw=b"".join((output/name).read_bytes() for name in OUTPUT_FILES[:4])
    if b"\xef\xbf\xbd" in raw or not any(ord(char)>127 for row in messages for char in row["content"]): failures.append("Unicode")
    expected={name:hashlib.sha256((output/name).read_bytes()).hexdigest() for name in OUTPUT_FILES}
    actual={line.split("  ",1)[1]:line.split("  ",1)[0] for line in (output/"SHA256SUMS.txt").read_text(encoding="ascii").splitlines() if "  " in line}
    if actual!=expected: failures.append("SHA256SUMS")
    if (output/"log_human_review.txt").read_text(encoding="utf-8")!=review(conversations,messages,annotations): failures.append("human review evidence")
    return failures, Counter(row["intent"] for row in annotations), Counter(row["sentiment"] for row in annotations)


def semantic_regressions():
    expected={
        ("LOG-001",1):("SUPPORT","NEGATIVE"), ("LOG-001",3):("SUPPORT","NEUTRAL"),
        ("LOG-005",3):("SUPPORT","NEUTRAL"), ("LOG-008",3):("ASK_INFO","NEGATIVE"),
        ("LOG-015",2):("ASK_INFO","NEGATIVE"), ("LOG-015",3):("BOOKING","NEUTRAL"),
        ("LOG-023",3):("BOOKING","NEUTRAL"), ("LOG-026",3):("ASK_PRICE","NEUTRAL"),
        ("LOG-028",1):("ASK_INFO","NEUTRAL"), ("LOG-028",2):("ASK_INFO","NEGATIVE"),
        ("LOG-028",3):("COMPLAINT","NEGATIVE"), ("LOG-031",1):("COMPLAINT","NEGATIVE"),
        ("LOG-033",1):("ASK_INFO","NEGATIVE"), ("LOG-033",3):("ASK_INFO","NEGATIVE"),
        ("LOG-038",2):("BOOKING","NEUTRAL"), ("LOG-039",3):("OTHER","POSITIVE"),
        ("LOG-042",1):("SUPPORT","NEGATIVE"), ("LOG-042",3):("SUPPORT","NEGATIVE"),
        ("LOG-049",3):("SUPPORT","NEUTRAL"),
    }
    return [f"semantic {key}" for key,value in expected.items() if ANNOTATION_TABLE.get(key)!=value]


def reproducibility():
    with tempfile.TemporaryDirectory() as directory:
        target=Path(directory); generate(target)
        return [name for name in [*OUTPUT_FILES,"SHA256SUMS.txt"] if (HERE/name).read_bytes()!=(target/name).read_bytes()]

if __name__ == "__main__":
    failures,intents,sentiments=validate(HERE); failures.extend(semantic_regressions()); failures.extend(f"reproducibility {name}" for name in reproducibility())
    print("conversations=50 messages=300 inbound_annotations=150")
    print(f"intent_distribution={dict(sorted(intents.items()))}")
    print(f"sentiment_distribution={dict(sorted(sentiments.items()))}")
    print("semantic_regression_tests=PASS" if not semantic_regressions() else "semantic_regression_tests=FAIL")
    print("PASS" if not failures else "FAIL")
    for failure in failures: print(f"- {failure}")
    raise SystemExit(1 if failures else 0)
