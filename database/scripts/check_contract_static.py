"""Validate the W12 seed contract against the current backend schema sources.

This is a static guard only. It does not execute SQL or alter seed data.
"""

import csv
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BASELINE = ROOT / "backend/src/main/resources/db/baseline/V1__nhi_v2_schema.sql"
MIGRATIONS = sorted(
    (ROOT / "backend/src/main/resources/db/migration").glob("V*.sql"),
    key=lambda path: int(re.match(r"V(\d+)__", path.name).group(1)),
)
SEED_SQL = ROOT / "database/scripts/seed_v2.psql"
SEED = ROOT / "database/data/seed/multisector_v2"
STAGE_TARGETS = {
    "w12_stage_permissions": "permissions",
    "w12_stage_role_permissions": "role_permissions",
    "w12_stage_opportunities": "opportunities",
}


def split_top_level(value):
    """Split SQL definitions on commas outside parentheses and string literals."""
    result = []
    buffer = []
    depth = 0
    in_string = False
    index = 0
    while index < len(value):
        character = value[index]
        if character == "'":
            if in_string and index + 1 < len(value) and value[index + 1] == "'":
                buffer.extend((character, value[index + 1]))
                index += 2
                continue
            in_string = not in_string
        elif not in_string:
            if character == "(":
                depth += 1
            elif character == ")":
                depth -= 1
            elif character == "," and depth == 0:
                result.append("".join(buffer).strip())
                buffer = []
                index += 1
                continue
        buffer.append(character)
        index += 1
    if "".join(buffer).strip():
        result.append("".join(buffer).strip())
    return result


def parse_create_tables(sql):
    tables = {}
    pattern = re.compile(
        r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(?:public\.)?(\w+)\s*\((.*?)\n\);",
        re.IGNORECASE | re.DOTALL,
    )
    constraints = {"CONSTRAINT", "PRIMARY", "UNIQUE", "CHECK", "FOREIGN"}
    for match in pattern.finditer(sql):
        columns = []
        for definition in split_top_level(match.group(2)):
            tokens = definition.strip().split(None, 1)
            if not tokens:
                continue
            column = tokens[0].strip('"')
            if column.upper() not in constraints:
                columns.append(column)
        tables[match.group(1)] = set(columns)
    return tables


def load_schema():
    tables = parse_create_tables(BASELINE.read_text(encoding="utf-8"))
    sources = [BASELINE]
    for migration in MIGRATIONS:
        sql = migration.read_text(encoding="utf-8")
        sources.append(migration)
        tables.update(parse_create_tables(sql))
        for match in re.finditer(
            r"ALTER\s+TABLE\s+(?:ONLY\s+)?(?:public\.)?(\w+)\s+ADD\s+COLUMN\s+"
            r"(?:IF\s+NOT\s+EXISTS\s+)?(\w+)",
            sql,
            re.IGNORECASE,
        ):
            tables.setdefault(match.group(1), set()).add(match.group(2))
    return tables, "\n".join(path.read_text(encoding="utf-8") for path in sources)


def parse_copies(seed_sql):
    copies = {}
    pattern = re.compile(
        r"\\copy\s+(\w+)\s*\(([^)]*)\)\s+FROM\s+'([^']+)'",
        re.IGNORECASE,
    )
    for match in pattern.finditer(seed_sql):
        table = match.group(1)
        columns = [column.strip() for column in match.group(2).split(",")]
        copies[table] = (columns, match.group(3))
    return copies


def main():
    errors = []
    schema, schema_sql = load_schema()
    seed_sql = SEED_SQL.read_text(encoding="utf-8")
    copies = parse_copies(seed_sql)
    logical_copies = {}

    for copy_table, (columns, relative_path) in copies.items():
        target_table = STAGE_TARGETS.get(copy_table, copy_table)
        logical_copies[target_table] = copy_table
        if target_table not in schema:
            errors.append(f"{copy_table}: target table missing from backend schema: {target_table}")
            continue
        missing_columns = [column for column in columns if column not in schema[target_table]]
        if missing_columns:
            errors.append(
                f"{copy_table}: columns missing from backend schema {target_table}: {missing_columns}"
            )
        csv_path = ROOT / relative_path
        if not csv_path.exists():
            errors.append(f"{copy_table}: CSV path missing {relative_path}")
            continue
        with csv_path.open(encoding="utf-8-sig", newline="") as handle:
            header = next(csv.reader(handle))
        if header != columns:
            errors.append(
                f"{copy_table}: CSV header != \\copy list\n  csv={header}\n  sql={columns}"
            )

    manifest = json.loads((SEED / "manifest.json").read_text(encoding="utf-8"))
    for table, expected in manifest["tableCounts"].items():
        if table not in logical_copies:
            errors.append(f"{table}: no direct or approved staging \\copy statement")
        if table not in schema:
            errors.append(f"{table}: missing from current backend schema")
        csv_path = SEED / f"{table}.csv"
        with csv_path.open(encoding="utf-8-sig", newline="") as handle:
            actual = sum(1 for _ in csv.DictReader(handle))
        if actual != expected:
            errors.append(f"{table}: manifest {expected} != CSV {actual}")

    if "opportunity_lines" not in schema:
        errors.append("V10 opportunity_lines table missing from current backend schema")
    if "INSERT INTO public.opportunity_lines" not in seed_sql:
        errors.append("seed does not replay deterministic opportunity_lines derivation")
    if not re.search(r"CREATE\s+VIEW\s+(?:public\.)?customer_360_summary", schema_sql, re.IGNORECASE):
        errors.append("customer_360_summary view missing from backend baseline")

    print(
        "Static contract check: "
        f"schema_tables={len(schema)}, migrations={len(MIGRATIONS)}, "
        f"seed_copies={len(copies)}, errors={len(errors)}"
    )
    for error in errors:
        print("ERROR:", error)
    if errors:
        return 1
    print("PASS - current backend schema, W12 seed headers, staging mappings and manifest counts are aligned.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
