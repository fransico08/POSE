from pathlib import Path
import csv

try:
    import bcrypt
except ImportError:
    raise SystemExit(
        "Missing bcrypt. Run: python -m pip install bcrypt"
    )

BASE = (
    Path(__file__).resolve().parents[1]
    / "data"
    / "seed"
    / "multisector_v2"
)

DEMO_PASSWORD = "PoseDemo@2026"

DEMO_ACCOUNTS = {
    "ADMIN": "edu.admin@example.com",
    "MANAGER": "edu.mgr@example.com",
    "SALES_REP": "edu.s01@example.com",
    "CUSTOMER_CARE": "edu.care@example.com",
    "DATA_STAFF": "edu.data@example.com",
}


def load(name):
    with open(
        BASE / name,
        "r",
        encoding="utf-8-sig",
        newline=""
    ) as f:
        return list(csv.DictReader(f))


users = load("users.csv")
roles = load("roles.csv")
grants = load("user_role_grants.csv")
credentials = load("credentials.csv")

users_by_email = {u["email"]: u for u in users}
roles_by_id = {r["id"]: r for r in roles}

roles_by_user = {}

for g in grants:
    role = roles_by_id[g["role_id"]]["code"]
    roles_by_user.setdefault(g["user_id"], set()).add(role)

credentials_by_user = {
    c["user_id"]: c
    for c in credentials
}

errors = []

print("POSE demo credential verification")
print()

for expected_role, email in DEMO_ACCOUNTS.items():

    user = users_by_email.get(email)

    if not user:
        print("FAIL", expected_role, email, "- missing user")
        errors.append(email)
        continue

    if user["status"] != "ACTIVE":
        print("FAIL", expected_role, email, "- user not ACTIVE")
        errors.append(email)
        continue

    actual_roles = roles_by_user.get(user["id"], set())

    if expected_role not in actual_roles:
        print(
            "FAIL",
            expected_role,
            email,
            "- expected role missing:",
            actual_roles
        )
        errors.append(email)
        continue

    credential = credentials_by_user.get(user["id"])

    if not credential:
        print("FAIL", expected_role, email, "- no credential")
        errors.append(email)
        continue

    password_hash = credential["password_hash"]

    ok = bcrypt.checkpw(
        DEMO_PASSWORD.encode("utf-8"),
        password_hash.encode("utf-8")
    )

    if not ok:
        print("FAIL", expected_role, email, "- password mismatch")
        errors.append(email)
        continue

    print(
        "PASS",
        expected_role.ljust(14),
        email,
        "| ACTIVE | password hash verified"
    )


print()

if errors:
    print("FAILED:", len(errors), "demo account(s)")
    raise SystemExit(1)

print("PASS - all 5 role demo credentials verified.")