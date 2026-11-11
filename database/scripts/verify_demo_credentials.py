from pathlib import Path
import csv
import os

BASE = (
    Path(__file__).resolve().parents[1]
    / "data"
    / "seed"
    / "multisector_v2"
)

DEMO_ACCOUNTS = {
    "ADMIN": "edu.admin@example.com",
    "MANAGER": "edu.mgr@example.com",
    "SALES_REP": "edu.s01@example.com",
    "CUSTOMER_CARE": "edu.care@example.com",
    "DATA_STAFF": "edu.data@example.com",
}


def load_csv(name):
    with open(
        BASE / name,
        "r",
        encoding="utf-8-sig",
        newline="",
    ) as file_handle:
        return list(csv.DictReader(file_handle))


def load_demo_password():
    # Mật khẩu chỉ được cấp qua biến môi trường, không có fallback trong mã nguồn.
    password = os.environ.get("POSE_DEMO_PASSWORD")

    if not password:
        raise SystemExit(
            "POSE_DEMO_PASSWORD is required and must not be empty."
        )

    return password.encode("utf-8")


def main():
    demo_password = load_demo_password()

    try:
        import bcrypt
    except ImportError:
        raise SystemExit(
            "Missing bcrypt. Run: python -m pip install bcrypt"
        )

    users = load_csv("users.csv")
    roles = load_csv("roles.csv")
    grants = load_csv("user_role_grants.csv")
    credentials = load_csv("credentials.csv")

    users_by_email = {user["email"]: user for user in users}
    roles_by_id = {role["id"]: role for role in roles}
    credentials_by_user = {
        credential["user_id"]: credential
        for credential in credentials
    }
    roles_by_user = {}

    for grant in grants:
        role = roles_by_id[grant["role_id"]]["code"]
        roles_by_user.setdefault(grant["user_id"], set()).add(role)

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
                actual_roles,
            )
            errors.append(email)
            continue

        credential = credentials_by_user.get(user["id"])

        if not credential:
            print("FAIL", expected_role, email, "- no credential")
            errors.append(email)
            continue

        password_hash = credential["password_hash"]
        password_matches = bcrypt.checkpw(
            demo_password,
            password_hash.encode("utf-8"),
        )

        if not password_matches:
            print("FAIL", expected_role, email, "- password mismatch")
            errors.append(email)
            continue

        print(
            "PASS",
            expected_role.ljust(14),
            email,
            "| ACTIVE | password hash verified",
        )

    print()

    if errors:
        print("FAILED:", len(errors), "demo account(s)")
        raise SystemExit(1)

    print("PASS - all 5 role demo credentials verified.")


if __name__ == "__main__":
    main()
