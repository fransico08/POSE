from pathlib import Path
import csv, json, re, sys
ROOT=Path(__file__).resolve().parents[2]
MIG=(ROOT/'database/migrations/V1__pose_crm_schema.sql').read_text(encoding='utf-8')
SEED_SQL=(ROOT/'database/scripts/seed_v2.psql').read_text(encoding='utf-8')
SEED=ROOT/'database/data/seed/multisector_v2'
errors=[]

def split_top(s):
    out=[]; buf=[]; depth=0; in_sq=False; i=0
    while i<len(s):
        ch=s[i]
        if ch=="'":
            if in_sq and i+1<len(s) and s[i+1]=="'": buf.extend([ch,s[i+1]]); i+=2; continue
            in_sq=not in_sq
        if not in_sq:
            if ch=='(': depth+=1
            elif ch==')': depth-=1
            elif ch==',' and depth==0:
                out.append(''.join(buf).strip()); buf=[]; i+=1; continue
        buf.append(ch); i+=1
    if ''.join(buf).strip(): out.append(''.join(buf).strip())
    return out

tables={}
for m in re.finditer(r'CREATE TABLE\s+(\w+)\s*\((.*?)\n\);',MIG,re.S|re.I):
    name=m.group(1); defs=split_top(m.group(2)); cols=[]
    for d in defs:
        first=d.strip().split(None,1)[0].strip('"') if d.strip() else ''
        if first.upper() in {'CONSTRAINT','PRIMARY','UNIQUE','CHECK','FOREIGN'}: continue
        cols.append(first)
    tables[name]=cols

copies={}
pat=re.compile(r"\\copy\s+(\w+)\s*\(([^)]*)\)\s+FROM\s+'([^']+)'",re.I)
for m in pat.finditer(SEED_SQL):
    table=m.group(1); cols=[x.strip() for x in m.group(2).split(',')]; path=m.group(3)
    copies[table]=(cols,path)

for table,(cols,rel) in copies.items():
    if table not in tables: errors.append(f'copy table missing in migration: {table}'); continue
    missing=[c for c in cols if c not in tables[table]]
    if missing: errors.append(f'{table}: copy columns missing in migration: {missing}')
    p=ROOT/rel
    if not p.exists(): errors.append(f'{table}: CSV path missing {rel}'); continue
    with p.open(encoding='utf-8-sig',newline='') as f: header=next(csv.reader(f))
    if header!=cols: errors.append(f'{table}: CSV header != \\copy list\n  csv={header}\n  sql={cols}')

manifest=json.loads((SEED/'manifest.json').read_text(encoding='utf-8'))
for table,expected in manifest['tableCounts'].items():
    if table not in copies: errors.append(f'{table}: no seed \\copy statement')
    if table not in tables: errors.append(f'{table}: no migration table')
    with (SEED/f'{table}.csv').open(encoding='utf-8-sig',newline='') as f: actual=sum(1 for _ in csv.DictReader(f))
    if actual!=expected: errors.append(f'{table}: manifest {expected} != CSV {actual}')

if 'opportunity_lines' in tables or 'opportunity_lines' in copies: errors.append('DEC-01 violation: opportunity_lines exists')
if 'customer_360_summary' not in MIG: errors.append('Customer360 view missing')

print(f'Static contract check: tables={len(tables)}, seed copies={len(copies)}, errors={len(errors)}')
for e in errors: print('ERROR:',e)
if errors: sys.exit(1)
print('PASS - migration columns, seed headers, manifest counts and seed script are aligned.')
