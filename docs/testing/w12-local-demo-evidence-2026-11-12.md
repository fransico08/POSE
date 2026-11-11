# POSE CRM W12 Local Demo Evidence - 2026-11-12

## Pham vi

Database local: pose_w12_v12_clean_test.

Khong chay lai seed_v2.psql, khong re-import du lieu va khong thay doi database trong lan ghi nhan evidence nay.

## Ket qua runtime da xac nhan

| Hang muc | Ket qua |
| --- | --- |
| Ket noi PostgreSQL local | PASS |
| Public tables | 41 |
| Organizations | 6 |
| Canonical seed verifier | TASK 5 PASS |
| Exit code verifier | 0 |
| Demo credential verifier | 5/5 PASS |
| Exit code credential verifier | 0 |
| Re-import canonical seed | Khong thuc hien |
| Database mutation | Khong thuc hien |

Ket qua TASK 5 PASS la evidence cho database/scripts/verify_seed_v2.psql:
row count canonical, V12 migration replay, tenant/lifecycle checks, Customer
360 va KPI cua sau to chuc.

## Xac minh demo credentials

Credential verifier da PASS cho 5/5 demo accounts. Tat ca account deu ACTIVE
va BCrypt hash da duoc xac minh. Password khong duoc in hoac luu trong evidence.

Lenh tai kiem tra an toan, chi chay khi POSE_DEMO_PASSWORD da duoc cap qua
moi truong:

```powershell
if ([string]::IsNullOrWhiteSpace($env:POSE_DEMO_PASSWORD)) {
    throw 'POSE_DEMO_PASSWORD is required and must not be empty.'
}

python database/scripts/verify_demo_credentials.py
```

Lenh khong in password; ket qua runtime hien tai da ket thuc voi exit code 0.

## Follow-up tach biet

- w12_additive_manifest.json va cac loader additive dang khong dong bo voi
  canonical package; khong dung cho local demo bootstrap nay.
- validate_seed_files.py con gia dinh V1 khong phu hop hoan toan voi V10-V12;
  viec dong bo validator la follow-up rieng, khong phu dinh TASK 5 PASS.

## Ket luan

Local demo verification PASS: canonical seed verifier va credential verifier deu
PASS. Evidence nay khong xac nhan mot lan bootstrap moi.
