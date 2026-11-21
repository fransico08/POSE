# W13 AI-05 REA pilot

Pilot gồm 50 hội thoại Zalo synthetic cho POSE Realty: 35 Customer ACTIVE và 15 Lead NEW/QUALIFIED từ W12 canonical, với một channel account `ZALO_SIM`, 40 hội thoại `dev` và 10 `test`.

Generator chỉ dùng 34 offering REA `ACTIVE`. Lead dùng `offering_id`, Customer dùng `related_offering_id` khi liên kết đó là offering ACTIVE; hồ sơ không liên kết dùng vòng fallback deterministic trong catalog REA ACTIVE. Không ghi database, không sửa W12 hay canonical manifest.

```powershell
python database/data/newitems/w13_ai05_rea_pilot/generate_rea_pilot.py
python database/data/newitems/w13_ai05_rea_pilot/validate_rea_pilot.py
```

Validator kiểm tra schema tĩnh, FK/tenant, catalog ACTIVE, chronology, labels, split, Unicode, privacy, SHA-256, byte-for-byte reproducibility, duplicate substantive messages và claim bất động sản không được phép.
