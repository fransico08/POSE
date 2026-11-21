# Handoff tích hợp dữ liệu W12 + W13

## Trạng thái và phạm vi

Kết quả local đã xác nhận: `W12_W13_INTEGRATION_VERIFIED|W12=PASS|accounts=6|conversations=300|messages=1927`.

Tài liệu này hướng dẫn **chỉ** rehearsal tích hợp trên PostgreSQL cô lập do repository phê duyệt. Không phải hướng dẫn deployment, không được dùng cho production, database dùng chung, Supabase hoặc PostgreSQL cổng `5432`.

## Vai trò từng thành phần

| Thành phần | Vai trò |
|---|---|
| `database/data/seed/multisector_v2` | Seed W12 canonical: sáu Organization, dữ liệu CRM nền và manifest checksum. Bất biến trong quy trình này. |
| `database/data/seed/w13_ai05` | Extension W13 additive: 6 channel accounts, 300 conversations, 1.927 messages; manifest neo vào hash W12 và delivery W13. |
| Flyway V1–V14 | Dựng schema theo thứ tự: V1 baseline, V2–V12 CRM/AI Gateway, V13 ba bảng hội thoại, V14 ownership channel và liên kết Conversation liên quan. |
| Năm script W12/W13 | Orchestrate, nạp W12, xác minh W12, nạp W13 và xác minh cuối. Chúng không thay thế Flyway, không phải deployment script. |

W13 annotation vòng 1 được giữ trong `database/data/seed/w13_ai05/round_1_annotations_consolidated.csv` để review/evaluation. PostgreSQL loader hiện tại chỉ nạp `channel_accounts.csv`, `conversations.csv` và `conversation_messages.csv`; annotation không được import.

## Script và safety guard

| Script | Vai trò | Guard chính |
|---|---|---|
| `run_w12_w13_integration.ps1` | Orchestrator duy nhất: Flyway V1–V12, W12, verify W12, Flyway V13–V14, W13, verify cuối. | Chỉ `127.0.0.1:54329`, user `pose_auth_test`, database rỗng `pose_w12_w13_integration_test`, PostgreSQL 18, `SHOW data_directory` phải là `backend/target/auth-postgres-test`. |
| `load_w12_w13_integration_w12.ps1` | Xác thực và nạp W12. | Hash manifest W12; hash toàn bộ file; `source_record_links.csv` yêu cầu BOM UTF-8, UTF-8 hợp lệ và normalize CRLF thành LF trước đối chiếu checksum. |
| `verify_w12_w13_integration_w12.ps1` | Chạy verifier W12 với target guard thay thế tạm thời. | Chỉ cluster/database cô lập; temporary `.psql` ngoài repo. |
| `load_w12_w13_integration_w13.ps1` | Nạp W13 trong transaction. | Header/count/hash/UUID; `SET LOCAL client_encoding = 'UTF8'`; PostgreSQL dollar quoting hợp lệ; FK/tenant/Unicode/direction; từ chối partial hoặc dataset khác ở rerun. |
| `verify_w12_w13_integration.ps1` | Xác minh cuối W12 + W13 read-only. | Đếm V1–V14, dữ liệu W12, 6/300/1.927, FK tenant, Unicode và business key không trùng. |

Migration discovery yêu cầu đúng một file cho mỗi version và copy stage phải khớp SHA-256 nguồn. W13 nạp atomically: bất kỳ lỗi nào rollback; rerun chỉ được chấp nhận khi cả ba bảng đã có cùng dataset byte-equivalent theo row values.

## Prerequisite

- Windows PowerShell 5.1 hoặc PowerShell tương thích.
- PostgreSQL **18** và Flyway CLI có trong PATH hoặc truyền rõ `-FlywayPath`.
- `psql.exe` PostgreSQL 18 có trong `PATH`; nếu không có, truyền `-PsqlPath` tới executable của PostgreSQL 18.
- Cluster test đã được backend runner tạo/cấp quyền, lắng nghe duy nhất `127.0.0.1:54329`, có role `pose_auth_test` và thư mục dữ liệu chính xác `backend/target/auth-postgres-test`.
- Database `pose_w12_w13_integration_test` tồn tại nhưng rỗng trước lần chạy đầu. Script không tạo lại, reset, drop, truncate hoặc dùng credential mới.
- File seed giữ UTF-8; riêng `source_record_links.csv` của W12 giữ BOM UTF-8. Không đổi line ending/thay mã hóa bằng editor trước khi chạy.

## Thứ tự phụ thuộc

`V1–V12 → W12 seed → W12 verification → V13–V14 → W13 seed → final verification`

Expected final counts: 6 Organization, 36 User, 12 Team, 36 Team membership, 10.000 Customer, 15.000 Lead, 6 channel account, 300 conversation, 1.927 conversation message. Output cuối hiện hành từ script dùng token `w12=PASS` viết thường:

```text
W12_W13_INTEGRATION_VERIFIED|w12=PASS|accounts=6|conversations=300|messages=1927
```

Kết quả handoff ở trên viết `W12=PASS` cùng ý nghĩa; không dùng so sánh chuỗi case-sensitive ngoài script hiện hành.

## Lệnh tái lập trên cluster cô lập đã duyệt

Từ repository root, dry-run không ghi database:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\database\scripts\run_w12_w13_integration.ps1 `
  -FlywayPath flyway
```

Sau khi người phụ trách database xác nhận cluster/database rỗng đúng prerequisite, chạy rehearsal:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\database\scripts\run_w12_w13_integration.ps1 `
  -FlywayPath flyway `
  -Execute
```

Kiểm lại sau rehearsal:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\database\scripts\verify_w12_w13_integration.ps1 `
  -PsqlPath psql.exe
```

## Phân tách môi trường

- **Repository-approved isolated integration test:** chỉ cluster `127.0.0.1:54329` với `SHOW data_directory` đúng; đây là phạm vi của năm script trên.
- **FE/BE development database:** thiết lập theo `backend/README.md` và `docs/huong-dan-chay-local.md`; không dùng runner này trên database phát triển có dữ liệu vì runner yêu cầu target rỗng.
- **Supabase/shared environment:** chỉ người giữ credential và quy trình được duyệt mới được thao tác. Không chạy các script này, không dùng `5432`, không dùng Supabase cho rehearsal này.

## Phụ thuộc schema liên nhánh

Sáu file này không mang theo migration. Chúng chỉ chạy khi checkout đang có `V13__conversations.sql` và `V14__channel_account_ownership.sql` đã được phê duyệt, khớp schema W13. Tại thời điểm handoff, `origin/Nhi` đã có Task 01 và Task 02 Data Dictionary nhưng chưa có hai migration; vì vậy có thể publish script như tài liệu/rehearsal handoff chỉ sau khi owner phê duyệt dependency liên nhánh, nhưng không được chạy từ checkout đó cho đến khi schema source đã được tích hợp.

## Troubleshooting

- `Dedicated PostgreSQL cluster verification failed`: dừng ngay; kiểm host, cổng, role và `SHOW data_directory`. Không đổi `-Port` để vượt guard.
- `running scripts is disabled`: chỉ dùng `-ExecutionPolicy Bypass` trong process PowerShell của lệnh ở trên; không đổi Execution Policy toàn máy hoặc repository.
- `Target must be empty`: tạo database rehearsal mới theo quy trình được duyệt; không xóa/reset database hiện hữu.
- `W12 canonical manifest checksum mismatch` hoặc lỗi `source_record_links.csv`: khôi phục file canonical từ nguồn đã duyệt; không sửa checksum/line ending để ép chạy.
- Flyway hoặc migration checksum/discovery lỗi: dừng, đối chiếu V1–V14 trong workspace; không rename/sửa migration.
- `W13 partial or foreign dataset state` hoặc rerun mismatch: dừng để owner đánh giá dataset; không tự DELETE/UPDATE để làm sạch.

Không có generic deployment script trong phạm vi này. Mọi target mới chỉ được thêm sau khi schema compatibility, credential handling, safety guard và môi trường đã được phê duyệt rõ ràng.
