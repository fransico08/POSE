from pathlib import Path
import csv, json, re, sys
from collections import defaultdict, Counter
from datetime import datetime, timezone

ROOT=Path(__file__).resolve().parents[2]
SEED=ROOT/'database'/'data'/'seed'/'multisector_v2'
errors=[]; warnings=[]
def err(msg): errors.append(msg)
def warn(msg): warnings.append(msg)
def load(t):
    p=SEED/f'{t}.csv'
    with p.open(encoding='utf-8-sig',newline='') as f: return list(csv.DictReader(f))
def ix(rows): return {r['id']:r for r in rows if 'id' in r}
def parse(s): return datetime.fromisoformat(s.replace('Z','+00:00')) if s else None
def truth(s): return str(s).lower() in ('true','1','yes','t')

names=['organizations','departments','teams','users','credentials','team_memberships','permissions','roles','user_role_grants','role_permissions','offering_categories','offerings','offering_attribute_definitions','offering_attribute_values','offering_prices','sales_assignments','source_systems','customers','leads','source_record_links','duplicate_review_cases','opportunities','handover_requests','customer_assignments','interactions','feedbacks','crm_tasks','import_jobs','import_rows','import_decisions','audit_logs','outbox_events','workflow_executions','support_requests','support_responses','support_attachments']
D={n:load(n) for n in names}
I={n:ix(D[n]) for n in names if n not in ('credentials','role_permissions')}

# generic ids
for n,rows in D.items():
    if rows and 'id' in rows[0]:
        ids=[r['id'] for r in rows]
        if len(ids)!=len(set(ids)): err(f'{n}: duplicate id')

org=I['organizations']; users=I['users']; roles=I['roles']; perms=I['permissions']; cats=I['offering_categories']; offerings=I['offerings']; customers=I['customers']; leads=I['leads']; opps=I['opportunities']; jobs=I['import_jobs']; supports=I['support_requests']; handovers=I['handover_requests']

# D01 roles and ADMIN per org
expected_roles={'ADMIN','MANAGER','SALES_REP','CUSTOMER_CARE','DATA_STAFF'}
roles_by_org=defaultdict(set)
for r in D['roles']: roles_by_org[r['organization_id']].add(r['code'])
for oid in org:
    if roles_by_org[oid]!=expected_roles: err(f'D01 role codes {oid}: {sorted(roles_by_org[oid])}')

# role grants + multi-role
role_code_by_id={r['id']:r['code'] for r in D['roles']}
roles_for_user=defaultdict(set)
for g in D['user_role_grants']:
    if g['user_id'] not in users: err('user_role_grants unknown user '+g['user_id'])
    if g['role_id'] not in roles: err('user_role_grants unknown role '+g['role_id'])
    if g['user_id'] in users and g['role_id'] in roles:
        if users[g['user_id']]['organization_id']!=g['organization_id'] or roles[g['role_id']]['organization_id']!=g['organization_id']: err('user_role_grants cross-organization '+g['id'])
        if not g['revoked_at']: roles_for_user[g['user_id']].add(role_code_by_id[g['role_id']])
if sum(len(v)>1 for v in roles_for_user.values())<2: err('L06 requires at least 2 multi-role users')
for oid in org:
    admins=[u for u in users.values() if u['organization_id']==oid and 'ADMIN' in roles_for_user[u['id']]]
    if not admins: err('D01 missing ADMIN user for org '+oid)

# D02 permissions
expected_permissions={
'identity.user.manage','identity.user.read','identity.role.manage','organization.team.manage','offering.manage','sales_assignment.manage','customer.read','customer.write','lead.read','lead.write','opportunity.read','opportunity.write','opportunity.reopen','interaction.read','interaction.write','interaction.sensitive.read','feedback.write','task.manage','customer_360.read','import.prepare','import.approve','import.retry','dashboard.read','audit.read','support_request.manage','support_request.escalation.handle','handover.request','handover.approve'}
actual_permissions={r['code'] for r in D['permissions']}
if actual_permissions!=expected_permissions: err('D02 permission code mismatch')
if not D['role_permissions']: err('D02 role_permissions empty')

# D03 user org/email and L05 credentials
seen=set()
for u in D['users']:
    if u['organization_id'] not in org: err('user missing org '+u['id'])
    k=(u['organization_id'],u['normalized_email'])
    if k in seen: err('duplicate email within organization '+str(k))
    seen.add(k)
cred={r['user_id']:r for r in D['credentials']}
if set(cred)!=set(users): err('L05 credentials do not cover every user')
bcrypt_re=re.compile(r'^\$2[aby]\$\d\d\$[./A-Za-z0-9]{53}$')
for uid,c in cred.items():
    if not bcrypt_re.match(c['password_hash']): err('L05 non-BCrypt credential '+uid)
if len({c['password_hash'] for c in cred.values()})<len(cred): err('L05 credential hashes must use distinct salts')

# D04 teams code + active manager MANAGER
seen=set()
for t in D['teams']:
    k=(t['organization_id'],t['code'])
    if k in seen: err('D04 duplicate team code '+str(k))
    seen.add(k)
    m=users.get(t['manager_user_id'])
    if not m or m['organization_id']!=t['organization_id'] or m['status']!='ACTIVE' or 'MANAGER' not in roles_for_user[m['id']]: err('D04 invalid manager '+t['id'])

# offering references, status
for o in D['offerings']:
    if o['category_id'] not in cats or cats[o['category_id']]['organization_id']!=o['organization_id']: err('offering category scope '+o['id'])
    if o['status'] not in {'DRAFT','ACTIVE','INACTIVE'}: err('Offering status invalid '+o['id'])

# sales assignments active/revoked/expired + user role
st=Counter(a['status'] for a in D['sales_assignments'])
if not st['REVOKED'] or not st['EXPIRED']: err('L06 requires REVOKED and EXPIRED Sales Assignment')
for a in D['sales_assignments']:
    if a['offering_id'] not in offerings or offerings[a['offering_id']]['organization_id']!=a['organization_id']: err('assignment offering scope '+a['id'])
    if a['sales_user_id'] not in users or users[a['sales_user_id']]['organization_id']!=a['organization_id'] or users[a['sales_user_id']]['status']!='ACTIVE' or 'SALES_REP' not in roles_for_user[a['sales_user_id']]: err('assignment sales role/scope '+a['id'])
    if a['status']=='REVOKED' and not a['revoked_at']: err('REVOKED assignment missing revoked_at '+a['id'])
    if a['effective_to'] and parse(a['effective_to'])<parse(a['effective_from']): err('assignment invalid effective interval '+a['id'])

def assignment_effective(user_id, offering_id, when):
    for a in D['sales_assignments']:
        if a['sales_user_id']==user_id and a['offering_id']==offering_id:
            start=parse(a['effective_from']); end=parse(a['effective_to'])
            if a['status']=='ACTIVE' and start<=when and (end is None or when<=end): return True
    return False

# L01 lead conversion identity + status
for l in D['leads']:
    if l['owner_user_id'] not in users or users[l['owner_user_id']]['organization_id']!=l['organization_id']: err('lead owner scope '+l['id'])
    if l['offering_id'] not in offerings or offerings[l['offering_id']]['organization_id']!=l['organization_id']: err('lead offering scope '+l['id'])
    elif offerings[l['offering_id']]['status']!='ACTIVE': err('Lead uses non-ACTIVE Offering '+l['id'])
    if l['status']=='CONVERTED':
        c=customers.get(l['converted_customer_id'])
        if not c: err('L01 converted lead missing customer '+l['id']); continue
        if c['organization_id']!=l['organization_id']: err('L01 cross-org converted customer '+l['id'])
        email_match=l['normalized_email'] and l['normalized_email']==c['normalized_email']
        phone_match=l['normalized_phone'] and l['normalized_phone']==c['normalized_phone']
        if not (email_match or phone_match): err('L01 converted lead identity mismatch '+l['id'])
        if not l['converted_at']: err('L01 converted lead missing converted_at '+l['id'])
    elif l['converted_customer_id'] or l['converted_at']:
        err('L01 non-CONVERTED lead has conversion link '+l['id'])

# current customer sales assignment must match owner
assigns_by_customer=defaultdict(list)
for a in D['customer_assignments']: assigns_by_customer[a['customer_id']].append(a)
for c in D['customers']:
    active=[a for a in assigns_by_customer[c['id']] if a['assignment_type']=='SALES' and a['status']=='ACTIVE']
    if len(active)!=1 or active[0]['user_id']!=c['owner_user_id']: err('customer active SALES assignment mismatch '+c['id'])

def customer_scope(user_id, customer_id, when):
    for a in assigns_by_customer.get(customer_id,[]):
        if a['user_id']!=user_id: continue
        start=parse(a['effective_from']); end=parse(a['effective_to'])
        if start<=when and (end is None or when<=end): return True
    return False

# D09 opportunities: one offering, customer xor lead, owner role/assignment, stages
allowed_stage={'NEW','QUALIFIED','PROPOSAL','WON','LOST'}
for o in D['opportunities']:
    if bool(o['customer_id'])==bool(o['lead_id']): err('D09 opportunity subject XOR '+o['id'])
    if o['offering_id'] not in offerings: err('D09 opportunity offering '+o['id'])
    elif offerings[o['offering_id']]['status']!='ACTIVE': err('D09 Opportunity uses non-ACTIVE Offering '+o['id'])
    if o['stage'] not in allowed_stage: err('D06 opportunity stage '+o['id'])
    if o['owner_user_id'] not in users or 'SALES_REP' not in roles_for_user[o['owner_user_id']]: err('opportunity owner role '+o['id'])
    if not assignment_effective(o['owner_user_id'],o['offering_id'],parse(o['created_at'])): err('opportunity owner lacks effective offering assignment '+o['id'])
    if o['stage'] in ('WON','LOST') and (not o['closing_reason'] or not o['closed_at']): err('D06 closed opportunity missing reason/time '+o['id'])
if 'OPPORTUNITY_REOPENED' not in {a['action'] for a in D['audit_logs']}: err('D06 missing reopen audit')

# D05 visibility values both present
vis={r['visibility'] for r in D['interactions']}
if vis != {'CARE_VISIBLE','SALES_ONLY'}: err('D05 interactions need both visibility values')
if not {'CARE_VISIBLE','SALES_ONLY'}.issubset({r['visibility'] for r in D['feedbacks']}): err('D05 feedback visibility coverage missing')

# L03 interaction scope and L04 temporal customer integrity
for r in D['interactions']:
    when=parse(r['occurred_at']); actor=r['recorded_by_user_id']
    if r['customer_id']:
        c=customers.get(r['customer_id'])
        if not c: err('interaction customer FK '+r['id']); continue
        if when<parse(c['created_at']): err('L04 interaction before customer created '+r['id'])
        if not customer_scope(actor,c['id'],when): err('L03 interaction outside customer assignment '+r['id'])
    else:
        l=leads.get(r['lead_id'])
        if not l: err('interaction lead FK '+r['id']); continue
        if when<parse(l['created_at']): err('interaction before lead created '+r['id'])
        if actor!=l['owner_user_id'] or not assignment_effective(actor,l['offering_id'],when): err('L03 interaction outside lead scope '+r['id'])

for r in D['feedbacks']:
    when=parse(r['received_at']); actor=r['recorded_by_user_id']
    if r['customer_id']:
        c=customers.get(r['customer_id'])
        if not c or when<parse(c['created_at']): err('L04 feedback before/missing customer '+r['id'])
        elif not customer_scope(actor,c['id'],when): err('feedback outside assignment '+r['id'])
    else:
        l=leads.get(r['lead_id'])
        if not l or when<parse(l['created_at']): err('feedback before/missing lead '+r['id'])

for s in D['support_requests']:
    c=customers.get(s['customer_id']); when=parse(s['created_at'])
    if not c or when<parse(c['created_at']): err('L04 support before/missing customer '+s['id'])
    elif not customer_scope(s['assigned_to_user_id'],c['id'],when): err('support outside CARE assignment '+s['id'])
    if 'CUSTOMER_CARE' not in roles_for_user[s['assigned_to_user_id']]: err('support assignee not care '+s['id'])

# L02 import COMMITTED valid rows must link to matching profile; approval must be manager/admin
rows_by_job=defaultdict(list)
for r in D['import_rows']: rows_by_job[r['import_job_id']].append(r)
for job in D['import_jobs']:
    if job['status']=='COMMITTED':
        if not job['approved_by_user_id'] or not job['approved_at'] or not job['committed_at']: err('D07 committed job lacks approval '+job['id'])
        approver_roles=roles_for_user[job['approved_by_user_id']]
        if not ({'MANAGER','ADMIN'} & approver_roles): err('D07 invalid import approver '+job['id'])
        for r in rows_by_job[job['id']]:
            if r['status']=='VALID':
                if not r['committed_entity_type'] or not r['committed_entity_id']: err('L02 committed VALID row missing profile '+r['id']); continue
                if r['committed_entity_type']=='CUSTOMER': target=customers.get(r['committed_entity_id'])
                else: target=leads.get(r['committed_entity_id'])
                if not target: err('L02 committed row target missing '+r['id']); continue
                payload=json.loads(r['normalized_payload']) if r['normalized_payload'] else {}
                em=(payload.get('email') or '').strip().lower(); ph=payload.get('phone') or ''
                if em and em!=target['normalized_email'] and ph and ph!=target['normalized_phone']: err('L02 committed row identity mismatch '+r['id'])
    if job['status']=='FAILED' and int(job['retry_count'])>0 and not rows_by_job[job['id']]: err('L06 failed retry job lacks rows '+job['id'])
if not any(j['status']=='FAILED' and int(j['retry_count'])>0 and rows_by_job[j['id']] for j in D['import_jobs']): err('L06 missing failed import retry scenario with data')

# import decisions one per committed
approved_decisions={d['import_job_id']:d for d in D['import_decisions'] if d['decision']=='APPROVED'}
for j in D['import_jobs']:
    if j['status']=='COMMITTED' and j['id'] not in approved_decisions: err('D07 committed job missing ImportDecision '+j['id'])

# D08 outbox/workflow/attributes
for n in ['outbox_events','workflow_executions','offering_attribute_definitions','offering_attribute_values']:
    if not D[n]: err('D08 empty '+n)
# unique idempotency
for n in ['outbox_events','workflow_executions']:
    keys=[(r['organization_id'],r['idempotency_key']) for r in D[n]]
    if len(keys)!=len(set(keys)): err('duplicate idempotency key in '+n)

# L06 audit action coverage
required_actions={'USER_ROLES_CHANGED','SALES_ASSIGNMENT_REVOKED','CUSTOMER_OWNER_CHANGED','OPPORTUNITY_REOPENED','IMPORT_APPROVED','IMPORT_RETRY'}
missing=required_actions-{r['action'] for r in D['audit_logs']}
if missing: err('L06 missing required audit actions '+','.join(sorted(missing)))

# D10 handover types + target roles / one pending per customer
if {r['handover_type'] for r in D['handover_requests']} != {'SALES','CARE'}: err('D10 both handover types required')
pending=Counter(r['customer_id'] for r in D['handover_requests'] if r['status']=='PENDING')
if any(v>1 for v in pending.values()): err('D10 more than one PENDING handover per customer')
for h in D['handover_requests']:
    c=customers.get(h['customer_id'])
    if not c or c['organization_id']!=h['organization_id']: err('handover customer scope '+h['id']); continue
    if 'SALES_REP' not in roles_for_user[h['requested_by_user_id']]: err('handover requester not sales '+h['id'])
    if h['handover_type']=='CARE':
        if not h['target_user_id'] or 'CUSTOMER_CARE' not in roles_for_user[h['target_user_id']] or users[h['target_user_id']]['status']!='ACTIVE': err('D10 CARE target invalid '+h['id'])
        if h['status']=='APPROVED':
            applied=[a for a in assigns_by_customer.get(h['customer_id'],[]) if a['assignment_type']=='CARE' and a['user_id']==h['target_user_id'] and a['status']=='ACTIVE' and a['source']=='HANDOVER' and a['handover_request_id']==h['id'] and parse(a['effective_from'])==parse(h['reviewed_at'])]
            if len(applied)!=1: err('D10 approved CARE handover not materialized as CARE assignment '+h['id'])
    else:
        if not h['target_user_id'] or 'SALES_REP' not in roles_for_user[h['target_user_id']] or users[h['target_user_id']]['status']!='ACTIVE': err('D10 SALES target invalid role '+h['id'])
        # eligibility: target must have an ACTIVE assignment for at least one offering currently related to customer by Opportunity
        related={o['offering_id'] for o in D['opportunities'] if o['customer_id']==c['id']}
        when=parse(h['created_at'])
        if related and not any(assignment_effective(h['target_user_id'],off,when) for off in related): err('D10 SALES target lacks active assignment for related offering '+h['id'])
        if h['status']=='APPROVED':
            if c['owner_user_id']!=h['target_user_id']: err('D10 approved SALES handover did not update Customer owner '+h['id'])
            applied=[a for a in assigns_by_customer.get(h['customer_id'],[]) if a['assignment_type']=='SALES' and a['user_id']==h['target_user_id'] and a['status']=='ACTIVE' and a['source']=='HANDOVER' and a['handover_request_id']==h['id'] and parse(a['effective_from'])==parse(h['reviewed_at'])]
            if len(applied)!=1: err('D10 approved SALES handover not materialized as SALES assignment '+h['id'])

# Current Domain Class Diagram choice for conversion: final-state activity/task/opportunity/source links
# must no longer point at a Lead that is already CONVERTED. This is marked review-ready, not an immutable decision.
converted_ids={l['id'] for l in D['leads'] if l['status']=='CONVERTED'}
for n in ['interactions','crm_tasks','opportunities']:
    for r in D[n]:
        if r.get('lead_id') in converted_ids:
            err(f'conversion rebind: {n} still points to CONVERTED Lead {r["lead_id"]}')
for s in D['source_record_links']:
    if s['subject_type']=='LEAD' and s['subject_id'] in converted_ids:
        err('conversion rebind: SourceRecordLink still points to CONVERTED Lead '+s['subject_id'])

# source links polymorphic and unique
seen=set()
for s in D['source_record_links']:
    k=(s['organization_id'],s['source_system_id'],s['source_entity_type'],s['external_id'])
    if k in seen: err('duplicate SourceRecordLink external key '+str(k))
    seen.add(k)
    target=customers.get(s['subject_id']) if s['subject_type']=='CUSTOMER' else leads.get(s['subject_id'])
    if not target or target['organization_id']!=s['organization_id']: err('source link target/scope '+s['id'])

# Mirror critical PostgreSQL CHECK constraints offline so a PASS is meaningful before psql.
for r in D['offering_prices']:
    if float(r['amount'])<0 or (r['effective_to'] and parse(r['effective_to'])<parse(r['effective_from'])): err('OfferingPrice check violation '+r['id'])
for r in D['customers']:
    if not (r['normalized_email'] or r['normalized_phone']): err('Customer identity check violation '+r['id'])
for r in D['leads']:
    if not (r['normalized_email'] or r['normalized_phone']): err('Lead identity check violation '+r['id'])
    if r['status']=='DISQUALIFIED' and not r['disqualification_reason']: err('DISQUALIFIED Lead missing reason '+r['id'])
for r in D['handover_requests']:
    if r['status']=='PENDING' and (r['reviewed_by_user_id'] or r['reviewed_at']): err('PENDING handover has review fields '+r['id'])
    if r['status']=='APPROVED' and (not r['reviewed_by_user_id'] or not r['reviewed_at'] or not r['target_user_id']): err('APPROVED handover missing review/target '+r['id'])
    if r['status']=='REJECTED' and (not r['reviewed_by_user_id'] or not r['reviewed_at'] or not r['rejection_reason']): err('REJECTED handover missing review/reason '+r['id'])
for r in D['customer_assignments']:
    if r['effective_to'] and parse(r['effective_to'])<parse(r['effective_from']): err('CustomerAssignment interval violation '+r['id'])
active_ca=Counter((r['customer_id'],r['assignment_type']) for r in D['customer_assignments'] if r['status']=='ACTIVE')
if any(v>1 for v in active_ca.values()): err('CustomerAssignment unique active type violation')
for n in ['interactions','feedbacks','crm_tasks']:
    for r in D[n]:
        if bool(r['customer_id'])==bool(r['lead_id']): err(n+' subject XOR violation '+r['id'])
for r in D['crm_tasks']:
    if r['status']=='DONE' and not r['completed_at']: err('DONE Task missing completed_at '+r['id'])
for r in D['import_rows']:
    if bool(r['committed_entity_type']) != bool(r['committed_entity_id']): err('ImportRow committed pair violation '+r['id'])
for r in D['support_requests']:
    if r['status']=='ESCALATED' and not r['escalation_reason']: err('ESCALATED Support missing reason '+r['id'])
    if r['status'] in ('RESOLVED','CLOSED') and not r['resolution']: err('resolved/closed Support missing resolution '+r['id'])
for r in D['support_attachments']:
    if int(r['file_size'])<=0: err('SupportAttachment file_size violation '+r['id'])

# JSON parse checks
for n,cols in {'organizations':['metadata'],'users':['metadata'],'offerings':['metadata'],'offering_attribute_definitions':['options'],'offering_attribute_values':['value_json'],'customers':['metadata'],'import_rows':['raw_payload','normalized_payload','error_codes'],'audit_logs':['before_data','after_data','safe_metadata'],'outbox_events':['payload']}.items():
    for r in D[n]:
        for c in cols:
            if r.get(c):
                try: json.loads(r[c])
                except Exception: err(f'{n}.{c} invalid JSON id={r.get("id")}')

print('POSE synchronized seed validation')
print('Seed path:',SEED)
print('Tables checked:',len(names))
print('Organizations:',len(D['organizations']))
print('Customers:',len(D['customers']),'Leads:',len(D['leads']),'Opportunities:',len(D['opportunities']))
print('Interactions:',len(D['interactions']),'Feedbacks:',len(D['feedbacks']),'Tasks:',len(D['crm_tasks']))
print('Support:',len(D['support_requests']),'Handovers:',len(D['handover_requests']))
print('Multi-role users:',sum(len(v)>1 for v in roles_for_user.values()))
print('Errors:',len(errors),'Warnings:',len(warnings))
for e in errors: print('ERROR:',e)
for w in warnings: print('WARN:',w)
if errors:
    sys.exit(1)
print('PASS - L01-L07 and D01-D10 validation checks passed on CSV seed.')
