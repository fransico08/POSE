#!/usr/bin/env python3
"""Static validator for the W13 AI-05 Clinic pilot."""
from __future__ import annotations
import csv, hashlib, re, tempfile
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from generate_cli_pilot import ASSIGNEE, CANONICAL, CHANNEL_ID, ORG, OUTPUT_FILES, TEAM, SOURCE, active_offerings, current_prices, generate, parse_approved, profiles, render_human_review

HERE = Path(__file__).resolve().parent
INTENTS = {"ASK_PRICE", "ASK_INFO", "BOOKING", "PURCHASE_READY", "COMPLAINT", "SUPPORT", "OTHER"}
SENTIMENTS = {"POSITIVE", "NEUTRAL", "NEGATIVE"}
START = datetime(2026, 11, 10, tzinfo=timezone.utc)
END = datetime(2026, 11, 17, tzinfo=timezone.utc)

def rows(path: Path):
    with path.open(encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))

def instant(value: str):
    return datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)

def check(ok, message, failures):
    if not ok: failures.append(message)

def validate(output: Path):
    failures=[]
    approved=parse_approved(); offerings=active_offerings(); prices=current_prices(); profiles_by_id=profiles()
    accounts=rows(output/"channel_accounts.csv"); conversations=rows(output/"conversations.csv"); messages=rows(output/"conversation_messages.csv"); annotations=rows(output/"round_1_annotations.csv")
    check(len(offerings)==34,"expected 34 ACTIVE offerings",failures)
    check(len(accounts)==1,"expected one channel account",failures)
    if accounts:
        expected={"id":CHANNEL_ID,"organization_id":ORG,"channel_type":"ZALO_SIM","team_id":TEAM,"default_assignee_user_id":ASSIGNEE,"status":"ACTIVE","secret_ref":"POSE_CHANNEL_ZALO_SIM_WEBHOOK_SECRET"}
        for key,value in expected.items(): check(accounts[0].get(key)==value,f"invalid channel account {key}",failures)
    check(len(conversations)==50 and len({x["id"] for x in conversations})==50,"expected 50 unique conversations",failures)
    check(len({x["external_thread_id"] for x in conversations})==50,"duplicate external thread ids",failures)
    by_id={x["id"]:x for x in conversations}; by_msg=defaultdict(list); inbound=set(); duplicate=Counter(); split_ids=defaultdict(set)
    customer_count=lead_count=0
    for index,row in enumerate(conversations):
        check(row["organization_id"]==ORG and row["channel_account_id"]==CHANNEL_ID and row["assigned_user_id"]==ASSIGNEE,"conversation tenant/channel/assignee mismatch",failures)
        check(row["status"] in {"OPEN", "PENDING", "CLOSED"},"unsupported conversation status",failures)
        check(bool(row["customer_id"]) != bool(row["lead_id"]),"conversation profile cardinality invalid",failures)
        pid=row["customer_id"] or row["lead_id"]; p=profiles_by_id.get(pid,{})
        check(p.get("organization_id")==ORG,"profile tenant mismatch",failures)
        if row["customer_id"]: customer_count+=1; check(p.get("status")=="ACTIVE" and not p.get("archived_at"),"invalid customer FK",failures)
        else: lead_count+=1; check(p.get("status") in {"NEW","QUALIFIED"} and not p.get("archived_at"),"invalid lead FK",failures)
    check((customer_count,lead_count)==(35,15),f"expected 35 customers and 15 leads, got {customer_count}/{lead_count}",failures)
    for row in messages:
        by_msg[row["conversation_id"]].append(row); duplicate[(row["direction"],row["content"])] += 1
        check(row["organization_id"]==ORG and row["conversation_id"] in by_id,"orphan/foreign message",failures)
        check(row["direction"] in {"INBOUND","OUTBOUND"} and row["content_type"]=="TEXT" and row["content"].strip()!="","invalid message",failures)
        check(START<=instant(row["occurred_at"])<END,"message outside date window",failures)
        if row["direction"]=="INBOUND": inbound.add(row["id"]); check(not row["sent_by_user_id"] and row["delivery_status"]=="RECEIVED","invalid inbound delivery",failures)
        else: check(row["sent_by_user_id"]==ASSIGNEE and row["delivery_status"]=="SENT","invalid outbound delivery",failures)
    internal_customer_terms = re.compile(r"(?i)\b(offering|canonical|w12|nhân viên cskh|bộ phận (tiếp nhận|đặt hẹn|xét nghiệm|chẩn đoán hình ảnh|phụ trách)|mã dịch vụ)\b")
    unverified_named_doctor = re.compile(r"(?i)\bbác sĩ nhi\b(?!\s+khoa)")
    for row in messages:
        if row["direction"] == "OUTBOUND":
            check(not internal_customer_terms.search(row["content"]), "internal customer-facing terminology", failures)
            check(not unverified_named_doctor.search(row["content"]), "unverified named doctor", failures)
    check(len({x["id"] for x in messages})==len(messages),"duplicate message ids",failures)
    check(not any(n>1 and len(text)>20 for (direction,text),n in duplicate.items()),"duplicate substantive messages",failures)
    labels={x["message_id"]:x for x in annotations}; check(set(labels)==inbound and len(labels)==len(annotations),"annotation coverage invalid",failures)
    for row in annotations:
        check(row["organization_id"]==ORG and row["conversation_id"] in by_id,"foreign annotation",failures); check(row["intent"] in INTENTS and row["sentiment"] in SENTIMENTS,"invalid labels",failures); split_ids[row["split"]].add(row["conversation_id"])
    check(len(split_ids["dev"])==40 and len(split_ids["test"])==10 and not split_ids["dev"]&split_ids["test"],"invalid dev/test split",failures)
    for index,item in enumerate(approved):
        conversation=conversations[index]; offering=offerings.get(item.offering_code,{})
        check(offering.get("status")=="ACTIVE" and offering.get("organization_id")==ORG,"inactive/foreign offering",failures)
        check(offering.get("id") in prices,"missing current W12 price",failures)
        actual=by_msg[conversation["id"]]
        check([(x["direction"],x["content"]) for x in actual]==[(x.direction,x.content) for x in item.turns],f"exact-text mismatch {item.editorial_id}",failures)
        check((conversation["customer_id"] or conversation["lead_id"])==item.profile_id,f"profile mismatch {item.editorial_id}",failures)
        moments=[instant(x["occurred_at"]) for x in actual]; check(moments==sorted(moments),f"chronology mismatch {item.editorial_id}",failures);
        for previous, current, previous_turn, current_turn in zip(moments, moments[1:], item.turns, item.turns[1:]):
            delay = int((current - previous).total_seconds() // 60)
            expected_range = (1, 5) if previous_turn.direction == "INBOUND" and current_turn.direction == "OUTBOUND" else (4, 20)
            check(expected_range[0] <= delay <= expected_range[1], f"context-sensitive response delay mismatch {item.editorial_id}", failures)
        check(instant(conversation["created_at"])==moments[0] and instant(conversation["last_message_at"])==moments[-1],f"conversation timestamps mismatch {item.editorial_id}",failures)
        inbound_times=[instant(x["occurred_at"]) for x in actual if x["direction"]=="INBOUND"]; check(instant(conversation["last_inbound_at"])==max(inbound_times),f"last inbound mismatch {item.editorial_id}",failures)
    raw=b"".join((output/f).read_bytes() for f in OUTPUT_FILES[:4]); all_text="\n".join(x["content"] for x in messages)
    check(b"\xef\xbf\xbd" not in raw and any(ord(c)>127 for c in all_text),"Unicode validation failed",failures)
    expected={f:hashlib.sha256((output/f).read_bytes()).hexdigest() for f in [*OUTPUT_FILES,"cli_id_mapping.json"]}; actual={line.split("  ",1)[1]:line.split("  ",1)[0] for line in (output/"SHA256SUMS.txt").read_text(encoding="ascii").splitlines() if "  " in line}; check(actual==expected,"SHA256SUMS mismatch",failures)
    check((output/"cli_human_review.txt").read_text(encoding="utf-8")==render_human_review(conversations,messages,annotations),"human review mismatch",failures)
    return failures, len(messages), len(annotations)

def reproducibility():
    with tempfile.TemporaryDirectory() as directory:
        target=Path(directory); generate(target); return [f for f in [*OUTPUT_FILES,"cli_id_mapping.json","SHA256SUMS.txt"] if (HERE/f).read_bytes() != (target/f).read_bytes()]

if __name__ == "__main__":
    failures,messages,annotations=validate(HERE); failures += [f"reproducibility mismatch: {f}" for f in reproducibility()]
    print(f"conversations=50 messages={messages} inbound_labeled={annotations}")
    print("PASS" if not failures else "FAIL")
    for failure in failures: print(f"- {failure}")
    raise SystemExit(1 if failures else 0)
