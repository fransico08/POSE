# POSE CRM W12 Data Quality Report

**Phạm vi:** canonical seed `database/data/seed/multisector_v2/` và hai
script `database/scripts/seed_v2.psql`, `database/scripts/verify_seed_v2.psql`.

**Trạng thái:** PASS có điều kiện về lưu vết runtime. Canonical PostgreSQL
verification exit code `0` được xác nhận trong đầu vào Task 12.1; nhiệm vụ này
không chạy lại seed, verifier hoặc kết nối PostgreSQL.

## Phương pháp và nguồn bằng chứng

- Đọc `AI_USAGE_DATA.md`, manifest canonical, `expected_kpis.csv`, loader,
  verifier và `W12_ADDITIVE_INTEGRATION.md`.
- Dùng kết quả static Step 8: 37 checksum CSV manifest, row count, UUID unique,
  schema header so với mỗi `\copy`, KPI, và byte identity với staging đã được
  runtime-verify.
- Dùng kết quả runtime đã được cung cấp: canonical loader và verifier thành
  công với exit code `0`. Không suy diễn thêm số liệu PostgreSQL không có log
  đi kèm trong phạm vi hiện tại.

## Khối lượng và phạm vi tổ chức

| Nhóm | Số lượng |
| --- | ---: |
| Bản ghi gốc của 10 bảng CRM W12 | 826 |
| Bản ghi W12 additive | 122,943 |
| Tổng 10 bảng CRM W12 | 123,769 |
| Tổng seed trước migration-derived rows | 126,002 |
| Tổng dự kiến sau migration-derived rows | 134,073 |

Sáu tổ chức có mặt trong `expected_kpis.csv`: POSE Academy, POSE Realty,
POSE Tech, POSE Retail, POSE Logistics và POSE Clinic. Tổng KPI theo tổ chức
khớp canonical: `customers=10,000`, `leads=15,000`,
`opportunities=8,347`, `interactions=45,000`.

Các bảng CRM W12 canonical: `customers=10,000`, `leads=15,000`,
`opportunities=8,347`, `interactions=45,000`, `crm_tasks=18,000`,
`customer_assignments=13,422`, `feedbacks=3,000`,
`handover_requests=2,000`, `support_requests=3,000`,
`support_responses=6,000`. Các artifact liên quan xác minh lifecycle là
`opportunity_lines=8,347` và `sales_assignments=526`.

## Tính tương thích và toàn vẹn

- **V12 schema:** PASS theo đối chiếu canonical V12 và evidence W12 trước đó;
  Task 12.1 không đổi migration, cột hoặc constraint.
- **Bảo toàn dữ liệu gốc:** PASS theo manifest (`826` bản ghi gốc) và identity
  byte của 13 artifact promote với staging đã runtime-verify. Không sửa CSV gốc
  riêng lẻ trong Task 12.1.
- **PK/UUID:** PASS static. Mười CSV CRM không có UUID rỗng hoặc trùng.
- **FK, tenant và lifecycle:** PASS theo static/runtime evidence Step 7C đã tái
  sử dụng; loader giữ thứ tự FK, transaction và verifier giữ D10 CARE/SALES,
  L03/L04 và Customer 360 assertions. Không có truy vấn DB mới trong nhiệm vụ.
- **Ngoại lệ legacy được chấp nhận:** đúng hai `handover_requests` gốc có
  reviewer cross-tenant: `572470a7-6b5d-520d-a399-542ccd8d36fe` và
  `4427d5b0-e519-511d-b0ad-b0e25e7fa555`. Không có ngoại lệ additive.
- **NULL, Unicode và CSV:** PASS static. CSV UTF-8, header khớp `\copy`;
  loader dùng `NULL ''` và `FORCE_NULL` cho các nullable field cần thiết,
  gồm timestamp nullable của `sales_assignments`.
- **Ngoại lệ historical Task:** 12 Task `CANCELLED` từ baseline
  `933f3c3f` giữ `cancellation_reason` rỗng. Validator chỉ chấp nhận đúng
  UUID, organization, status `CANCELLED` và origin `MANUAL` sau khi đối chiếu
  baseline; mọi Task khác thiếu lý do vẫn fail. Đây là giới hạn dữ liệu CSV
  historical, không phải ngoại lệ cho API V12: API vẫn bắt buộc reason khi hủy.
- **Thời gian/timeline:** PASS theo evidence Step 7C cho D06, L03/L04 và D10.
  Không thực hiện lại kiểm tra runtime trong Task 12.1.

## KPI và runtime

`expected_kpis.csv` có sáu dòng, một dòng cho mỗi tổ chức; tổng
`opportunities_total=8,347` khớp manifest và bảng canonical. Manifest có
37 SHA-256 CSV hợp lệ trong static check Step 8.

Canonical loader/verifier đều chặn database ngoài danh sách isolated V12 test.
Guard đã được đồng bộ để cả hai chấp nhận `pose_w12_v12_final_test`,
`pose_w12_v12_clean_test` và `pose_w12_v12_canonical_test`. Loader vẫn từ chối
database đã có `organizations`.

`database/scripts/import_v2_gui.sql` **cần được giữ** trong deliverable như
hard-stop có chủ đích: GUI import cũ hard-code V2 legacy rows và không phải
đường nạp canonical W12. Đường chính thức là `seed_v2.psql` trên database V12
cô lập, rỗng và đã áp dụng migration.

## E03: Coverage Interaction sau SALES handover

**Định nghĩa áp dụng:** E03 đo tỷ lệ Customer có handover `SALES/APPROVED`
trong 30 ngày trước `demo_as_of=2026-10-11` mà không có Interaction hợp lệ do
owner mới (`target_user_id`) ghi sau `reviewed_at`. Việc xác định ngày dùng
ngữ nghĩa local date `Asia/Ho_Chi_Minh`, đồng nhất với KPI canonical.

**Kết quả CSV:** E03 thay đổi từ `150/150 (100%)` thành `144/150 (96.00%)`
sau khi bổ sung đúng sáu Interaction synthetic, mỗi tổ chức một bản ghi.

| Tổ chức | Eligible handover | Không có Interaction hợp lệ của owner mới | E03 |
| --- | ---: | ---: | ---: |
| POSE Academy | 23 | 22 | 95.65% |
| POSE Clinic | 22 | 21 | 95.45% |
| POSE Logistics | 24 | 23 | 95.83% |
| POSE Realty | 22 | 21 | 95.45% |
| POSE Retail | 36 | 35 | 97.22% |
| POSE Tech | 23 | 22 | 95.65% |
| **Tổng** | **150** | **144** | **96.00%** |

Toàn bộ 150 handover eligible có Customer owner khớp `target_user_id`, đúng
một `customer_assignments` `SALES/ACTIVE/HANDOVER` hiệu lực tại `reviewed_at`,
và quan hệ tenant hợp lệ. Đã bổ sung sáu Interaction synthetic xác định được,
mỗi tổ chức một bản ghi do owner mới ghi sau handover và trước mốc demo. Có bảy
Customer khác có Interaction sau handover do requester trước đó ghi; các bản
ghi này không thỏa điều kiện E03.

Hai handover có timestamp UTC ngày `2026-10-11` thực tế là ngày `2026-10-12`
theo giờ Việt Nam, nên bị loại khỏi mẫu số canonical: một tại POSE Clinic và
một tại POSE Retail. Vì vậy mẫu số theo UTC cuối ngày là 152 không phải cách
diễn giải KPI canonical.

**Quyết định Data cuối cùng:** E03 vẫn không phải lỗi KPI hoặc lỗi quan hệ đã
được chứng minh. Canonical V12 seed bổ sung sáu Interaction synthetic tối
thiểu để demo thể hiện cả trường hợp chưa follow-up và owner mới follow-up
thành công trong cohort E03. Đây là coverage demo có kiểm soát, không phải
bằng chứng rằng nghiệp vụ đã phê duyệt một ngưỡng E03 cụ thể.

**Phạm vi xác minh cho seed đã cập nhật:**

- **PASS — CSV validator:** `0` errors và `9` cảnh báo legacy đã biết.
- **PASS — Manifest:** `37/37` checksum khớp CSV canonical.
- **NOT RUN — PostgreSQL loader/verifier:** chưa nạp seed đã cập nhật; không
  dùng evidence `TASK 5 PASS` lịch sử làm kết quả cho thay đổi E03 này.
- **NOT RUN — PostgreSQL KPI reconciliation:** chưa có runtime database cho
  `expected_kpis.csv` đã cập nhật.
- **NOT RUN — Backend/Frontend integration retest:** ngoài phạm vi Data và
  chưa có runtime seed mới để kiểm thử.

Lý do các hạng mục runtime chưa chạy: các database test V12 được whitelist
đang chứa dữ liệu, trong khi workspace không có reset procedure đã được phê
duyệt để tạo lại database rỗng an toàn. Không reset, loader hoặc verifier nào
được chạy cho seed E03 đã cập nhật.

## Hạn chế và đánh giá cuối

- Raw stdout/timestamp của canonical runtime exit `0` chưa được liên kết trong
  repository; đây là khoảng trống lưu vết, không phải lỗi dữ liệu đã biết.
- Không có database hoặc migration được chạy trong Task 12.1, nên không có
  tuyên bố kiểm tra runtime mới.
- Exception historical Task không thay thế kiểm thử API/integration cho luồng
  hủy Task; cần giữ `cancellation_reason` bắt buộc cho mọi record mới.
- Không có blocker dữ liệu hoặc schema còn lại cho canonical integration theo
  evidence hiện có. Trước commit, lưu log của lần canonical runtime exit `0`
  cùng diff đã được Nhi duyệt.

**Đánh giá:** W12 canonical seed sẵn sàng review/tích hợp, với điều kiện lưu
evidence runtime canonical vào hồ sơ kiểm thử trước commit.
