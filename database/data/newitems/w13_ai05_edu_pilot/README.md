# W13 AI-05 EDU pilot

Pilot tổng hợp gồm 50 hội thoại EDU: 35 liên kết Customer ACTIVE và 15 liên kết Lead NEW/QUALIFIED của seed V2. Có một `ZALO_SIM` channel account pilot, 40 hội thoại `dev`, 10 hội thoại `test`; nhãn lần 1 chỉ áp dụng cho tin `INBOUND`.

Workflow bao gồm admissions (new learner, phụ huynh, quay lại, objection, inquiry bỏ dở), onboarding, academic support, đổi lịch, complaint và renewal/retention. Offering chỉ lấy từ các dòng `ACTIVE` của `database/data/seed/multisector_v2/offerings.csv` có `organization_id` EDU; Lead ưu tiên `offering_id`, Customer ưu tiên `related_offering_id`, hồ sơ chưa liên kết dùng vòng deterministic trong chính catalog ACTIVE này. Nhân viên không nêu học phí, ưu đãi, lịch, chính sách hoặc cam kết học tập khi chưa có fact được xác minh.

Các UUID là UUIDv5 cố định; timestamp UTC nằm trong 10-16/11/2026. Generator chỉ đọc CSV V2 để chọn FK hợp lệ và không ghi database. Đây là artifact chuẩn bị cho integration sau này, không thay đổi manifest, loader hoặc validator canonical.

```powershell
python database/data/newitems/w13_ai05_edu_pilot/generate_edu_pilot.py
python database/data/newitems/w13_ai05_edu_pilot/validate_edu_pilot.py
```

Validator kiểm tra schema CSV, FK/tenant tĩnh, uniqueness nội bộ hội thoại, chronology, nhãn/split, Unicode, privacy, SHA-256, tái lập byte-for-byte và business quality: workflow diversity, claim chưa xác minh, so sánh đối thủ từ staff, response role/context và mismatch nhãn rõ ràng. Không kiểm tra PostgreSQL, webhook runtime hoặc canonical import vì chúng ngoài phạm vi pilot.
