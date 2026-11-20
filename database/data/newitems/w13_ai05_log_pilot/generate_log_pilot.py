#!/usr/bin/env python3
"""Generate deterministic W13 AI-05 Logistics CSV artifacts."""
from __future__ import annotations
import argparse, csv, hashlib, re, uuid
from collections import defaultdict
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
CANONICAL = ROOT / "database" / "data" / "seed" / "multisector_v2"
HERE = Path(__file__).resolve().parent
SOURCE = HERE / "LOG_50_hoi_thoai_bien_tap_FINAL.md"
ORG = "a477cbf4-afb4-5960-b165-41537f66ac13"
TEAM = "861e532e-830a-58c4-b1bc-89bf52d5f6f7"
ASSIGNEE = "8e7d17b7-68e6-5a84-b992-a2871949c515"
NAMESPACE = uuid.UUID("e8314200-4e82-5e5c-a9fd-248beb711ce2")
CHANNEL_ID = str(uuid.uuid5(NAMESPACE, "channel-account:zalo-sim-log-ai05"))
START = datetime(2026, 11, 10, 8, 30, tzinfo=timezone.utc)
OUTPUT_FILES = ["channel_accounts.csv", "conversations.csv", "conversation_messages.csv", "round_1_annotations.csv", "log_human_review.txt", "labeling-guide.vi.md"]
INTENTS = {"I":"ASK_INFO", "P":"ASK_PRICE", "B":"BOOKING", "R":"PURCHASE_READY", "C":"COMPLAINT", "S":"SUPPORT", "O":"OTHER"}
SENTIMENTS = {"N":"NEUTRAL", "G":"NEGATIVE", "P":"POSITIVE"}
CODES = {1:"SG SN SN",2:"IN IN ON",3:"IN IN ON",4:"IN IN OG",5:"SG CG SN",6:"IN IN ON",7:"BN IN SN",8:"IN IN IG",9:"IN IN IN",10:"PN SN ON",11:"IN SN SN",12:"IN IN ON",13:"RN IN ON",14:"SG IN SN",15:"IN IG BN",16:"IN IN BN",17:"IN IN IN",18:"PN BN BN",19:"PN IN ON",20:"SG SN SG",21:"PN IN ON",22:"PN IN ON",23:"IN IN BN",24:"SG SG SG",25:"RN IN ON",26:"PN IN PN",27:"IN IN CG",28:"IN IG CG",29:"IN IN SN",30:"RN BN ON",31:"CG CG CG",32:"IN IN IN",33:"IG IN IG",34:"SG SG SN",35:"IN IN IG",36:"BN IN SG",37:"IN PN ON",38:"BN BN SN",39:"PN IN OP",40:"PN BN BN",41:"BN IN SN",42:"SG SN SG",43:"IN IN IG",44:"BN IN OG",45:"BN IN ON",46:"BN IN SN",47:"IN IN ON",48:"IN IN ON",49:"RN IN SN",50:"IN SG ON"}


def rows(path):
    with path.open(encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def uid(kind, *parts):
    return str(uuid.uuid5(NAMESPACE, ":".join([kind, *(str(x) for x in parts)])))


def iso(value):
    return value.strftime("%Y-%m-%dT%H:%M:%SZ")


def parse_source():
    items = []
    for block in SOURCE.read_text(encoding="utf-8").split("### ")[1:]:
        ident = re.match(r"(LOG-\d{3})", block)
        metadata = re.search(r"- Entity: (Customer|Lead)\s*\n- Entity UUID: [\x60]([^\x60]+)[\x60]\s*\n- Entity status: [\x60]([^\x60]+)[\x60][\s\S]*?- Offering: [\x60]([^\x60]+)[\x60]\s*\n- W12 price: [\x60]([^\x60]+)[\x60][\s\S]*?- Split: [\x60](dev|test)[\x60]", block)
        turns = [("INBOUND" if who == "KH" else "OUTBOUND", text.rstrip()) for who, text in re.findall(r"^\*\*(KH|NV):\*\* (.+?)\s*$", block, re.M)]
        if not ident or not metadata or len(turns) != 6 or [turn[0] for turn in turns] != ["INBOUND", "OUTBOUND"] * 3:
            raise RuntimeError(f"Invalid FINAL dialogue: {ident.group(1) if ident else 'unknown'}")
        items.append((ident.group(1), *metadata.groups(), turns))
    if len(items) != 50 or [item[0] for item in items] != [f"LOG-{n:03d}" for n in range(1, 51)]:
        raise RuntimeError("Expected LOG-001..LOG-050")
    return items


def canonical():
    offerings = {r["code"]: r for r in rows(CANONICAL / "offerings.csv") if r["organization_id"] == ORG and r["status"] == "ACTIVE"}
    prices = {r["offering_id"]: r for r in rows(CANONICAL / "offering_prices.csv") if r["organization_id"] == ORG and not r["effective_to"]}
    profiles = {r["id"]: r for name in ("customers.csv", "leads.csv") for r in rows(CANONICAL / name) if r["organization_id"] == ORG}
    return offerings, prices, profiles


def labels():
    table = {}
    for ordinal, encoded in CODES.items():
        for index, token in enumerate(encoded.split(), 1):
            table[(f"LOG-{ordinal:03d}", index)] = (INTENTS[token[0]], SENTIMENTS[token[1]])
    if len(table) != 150:
        raise RuntimeError("Expected 150 explicit annotations")
    return table

ANNOTATION_TABLE = labels()
OPEN = {2, 6, 9, 10, 16, 21, 37, 39, 47}
GUIDE = """# Hướng dẫn gán nhãn LOG W13 AI-05

Chỉ gán nhãn cho tin INBOUND. Annotation là bảng explicit đã xét ngữ cảnh từng hội thoại, không dùng keyword heuristic.

ASK_INFO: hỏi dịch vụ, phạm vi hoặc điều kiện. ASK_PRICE: hỏi giá/cước. BOOKING: đề nghị gửi hoặc triển khai, không đồng nghĩa lịch đã được xác nhận. PURCHASE_READY: ý định dùng dịch vụ rõ ràng. COMPLAINT: phản ánh trải nghiệm hay thông tin không phù hợp. SUPPORT: yêu cầu kiểm tra/xử lý trường hợp cụ thể. OTHER: xác nhận hoặc bổ sung không có yêu cầu mới.

NEGATIVE chỉ dùng khi có lo ngại/bức xúc rõ ràng; POSITIVE khi có hài lòng/cảm ơn thật sự; còn lại là NEUTRAL.
"""


def write_csv(path, data):
    with path.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=list(data[0]), lineterminator="\n")
        writer.writeheader(); writer.writerows(data)


def review(conversations, messages, annotations):
    label_by_message = {row["message_id"]: row for row in annotations}
    grouped = defaultdict(list)
    for message in messages: grouped[message["conversation_id"]].append(message)
    lines = ["W13 AI-05 Logistics pilot - human review", ""]
    for conversation in conversations:
        lines.append(f"[{conversation['external_thread_id']}] {conversation['status']}")
        for message in grouped[conversation["id"]]:
            label = label_by_message.get(message["id"])
            extra = f" [{label['intent']}/{label['sentiment']}]" if label else ""
            lines.append(f"{'KH' if message['direction'] == 'INBOUND' else 'NV'}: {message['content']}{extra}")
        lines.append("")
    return "\n".join(lines)


def generate(output=HERE):
    output.mkdir(parents=True, exist_ok=True)
    offerings, prices, profiles = canonical()
    account = {"id":CHANNEL_ID,"organization_id":ORG,"channel_type":"ZALO_SIM","display_name":"POSE Logistics Zalo Demo","external_account_id":"pose-log-zalo-sim","status":"ACTIVE","secret_ref":"POSE_CHANNEL_ZALO_SIM_WEBHOOK_SECRET","team_id":TEAM,"default_assignee_user_id":ASSIGNEE,"created_at":iso(START),"updated_at":iso(START)}
    conversations=[]; messages=[]; annotations=[]
    for ordinal, (editorial, kind, profile_id, profile_status, code, declared_price, split, turns) in enumerate(parse_source(), 1):
        profile, offering = profiles.get(profile_id), offerings.get(code)
        relation = "related_offering_id" if kind == "Customer" else "offering_id"
        allowed = {"Customer":{"ACTIVE"}, "Lead":{"NEW","QUALIFIED"}}
        if not profile or not offering or offering["id"] not in prices or profile["status"] != profile_status or profile_status not in allowed[kind] or profile.get(relation) != offering["id"]:
            raise RuntimeError(f"Invalid W12 mapping: {editorial}")
        cid=uid("conversation",ORG,editorial); start=START+timedelta(hours=(ordinal-1)*3,minutes=((ordinal-1)%5)*7); moments=[start]
        for seq in range(1,6):
            previous, current = turns[seq-1], turns[seq]
            delay = 1+((ordinal+seq)%5) if previous[0] == "INBOUND" and current[0] == "OUTBOUND" else 5+((ordinal*2+seq)%16)
            moments.append(moments[-1]+timedelta(minutes=delay))
        inbound_moments=[when for turn, when in zip(turns,moments) if turn[0]=="INBOUND"]
        conversations.append({"id":cid,"organization_id":ORG,"channel_account_id":CHANNEL_ID,"external_thread_id":f"zalo-ai05-log-{ordinal:03d}","lead_id":profile_id if kind=="Lead" else "","customer_id":profile_id if kind=="Customer" else "","assigned_user_id":ASSIGNEE,"status":"OPEN" if ordinal in OPEN else "PENDING","consent_status":"UNKNOWN","last_inbound_at":iso(inbound_moments[-1]),"last_message_at":iso(moments[-1]),"created_at":iso(start),"updated_at":iso(moments[-1])})
        inbound_index=0
        for sequence, ((direction, content), when) in enumerate(zip(turns,moments),1):
            mid=uid("message",cid,sequence)
            messages.append({"id":mid,"organization_id":ORG,"conversation_id":cid,"direction":direction,"external_message_id":f"zalo-ai05-log-{ordinal:03d}-{sequence:02d}","content_type":"TEXT","content":content,"sent_by_user_id":ASSIGNEE if direction=="OUTBOUND" else "","delivery_status":"SENT" if direction=="OUTBOUND" else "RECEIVED","occurred_at":iso(when),"interaction_id":"","created_at":iso(when)})
            if direction=="INBOUND":
                inbound_index += 1; intent, sentiment=ANNOTATION_TABLE[(editorial,inbound_index)]
                annotations.append({"id":uid("annotation",mid),"organization_id":ORG,"conversation_id":cid,"message_id":mid,"intent":intent,"sentiment":sentiment,"split":split,"annotator":"Nhi-round-1","annotation_round":"1","created_at":iso(when+timedelta(seconds=1)),"notes":"Explicit context-reviewed annotation from frozen LOG FINAL dialogue."})
    write_csv(output / "channel_accounts.csv", [account]); write_csv(output / "conversations.csv", conversations); write_csv(output / "conversation_messages.csv", messages); write_csv(output / "round_1_annotations.csv", annotations)
    (output / "log_human_review.txt").write_text(review(conversations,messages,annotations),encoding="utf-8")
    (output / "labeling-guide.vi.md").write_text(GUIDE,encoding="utf-8")
    (output / "SHA256SUMS.txt").write_text("\n".join(f"{hashlib.sha256((output/name).read_bytes()).hexdigest()}  {name}" for name in OUTPUT_FILES)+"\n",encoding="ascii")

if __name__ == "__main__":
    parser=argparse.ArgumentParser(); parser.add_argument("--output",type=Path,default=HERE); generate(parser.parse_args().output)
