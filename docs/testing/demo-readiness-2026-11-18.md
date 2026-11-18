# Rà sẵn sàng demo CRM.AI — 18/11/2026

## Phạm vi và cách kiểm

Rà SRS 2.0 mục 10, 13, 14 cùng DEC-18–DEC-29 tại HEAD `e0f03b8` trên nhánh `Tài`. Trạng thái là kết luận cho **kịch bản demo**, không phải nghiệm thu toàn hệ thống. Các DEC-27, DEC-28 và DEC-29 trong decision log vẫn ghi là đề xuất/chờ duyệt. Không sửa SRS hay code ứng dụng.

Đã chạy một kịch bản Chromium trên **cùng một khách giả**, tenant EDU, qua backend thật, ZALO_SIM và AI mock. Runner dựa trên `scripts/e2e-env.ps1`: clone snapshot `pose_v2_review_*` vào database `pose_e2e_*` riêng trên 127.0.0.1:54329; script xác nhận `SHOW data_directory` là `D:/POSE/backend/target/auth-postgres-test`, tạo credential và secret tạm, xóa clone và dừng dịch vụ sau lượt chạy. Để kiểm workflow trong demo, bản sao tạm của runner thêm `--pose.workflow.worker.enabled=true` và `--pose.workflow.worker.interval-ms=1000`; giữ mặc định hạn Task một ngày. Đây không phải cấu hình mặc định của runner. Đã chạy lại kịch bản trên clone mới, 1/1 đạt (thời gian Playwright 10,3 giây); test quan sát chứ không khẳng định mọi yêu cầu SRS đều đạt. Một lượt trước đó cũng đạt 1/1 (12,8 giây). Lượt đầu bị dừng chủ động khi thao tác thử treo; lượt tiếp theo thất bại do fixture đặt số sát mã UUID làm không tìm được gợi ý số, đã đổi tin giả và chạy lại. Không tính hai lượt đó là đạt.

Tài khoản seed sử dụng: `EDU` Manager `edu.mgr@example.com`, Sales `edu.s01@example.com`, Sales nhận bàn giao `edu.s02@example.com`; Admin `edu.admin@example.com` cấu hình kênh. Mật khẩu/secret sinh riêng trong tiến trình, không ghi ở đây. Chưa chạy các tenant còn lại trong lượt này. Ảnh tại `D:/POSE-data/demo-readiness-review-2026-11-18/`, dùng dữ liệu giả, không chứa mật khẩu hay secret. Thư mục ảnh là local, không nằm trong commit. Chưa chạy toàn bộ suite E2E trong lượt review này.

## Truy vết bảy bước

| Bước | Trạng thái | Người, route/API và dữ liệu | Quyết định, chứng cứ |
|---|---|---|---|
| 1. Offering và phân công | Có điều kiện | Manager EDU: `/app/san-pham`, `/app/phan-cong`, `/api/v1/offerings`, `/api/v1/sales-assignments`; cần team và Sales hiệu lực. Lượt này dùng Offering có sẵn trong snapshot, tạo thêm phân công cho Sales nhận bàn giao khi cần; **chưa thao tác tạo Offering mới**. Ảnh `01-san-pham.png` là danh sách của Sales, không phải ảnh tạo Offering. | `frontend/e2e/vertical-slice.spec.ts:15` kiểm phân công; chưa chứng minh thao tác tạo Offering trong lượt review. |
| 2. Tin mới và Lead | Có điều kiện | Admin tạo account ZALO_SIM có team/owner; Sales EDU mở `/app/hoi-thoai`. Webhook `/api/v1/channels/zalo-sim/webhook`, chủ động tạo bằng `POST /api/v1/conversations/{id}/lead`; cần tên do nhân viên nhập, điện thoại/email được xác nhận và Offering hợp lệ. Ảnh `02-lead-hoi-thoai.png`. | SRS tự tạo Lead khác DEC-23 D2 và DEC-29; UI đã tạo/gắn thành công. Worker bật tạo Task “Phản hồi đầu tiên” đúng owner, hạn 19/11 khi nhận tin 18/11; API `/dashboard/today` không trả Task đó. `frontend/e2e/inbox.spec.ts:51` kiểm tạo Lead. |
| 3. Trích và duyệt tín hiệu | Có điều kiện | Sales trong hộp thư, `POST /api/v1/ai/signals/extract`, sau đó duyệt; phải có hội thoại gắn hồ sơ, tin INBOUND, quyền và AI bật. Đã trích và chấp nhận bằng mock; ảnh `03-tin-hieu.png`. | DEC-24; `SignalOutputFilter.java:11`–`12` chỉ INTENT/SENTIMENT, không có Offering interest; mock trả INTENT `OTHER` (`MockProvider.java:38`), không chứng minh hỏi giá hay cảm xúc trung tính. `frontend/e2e/inbox.spec.ts:42` kiểm trích. |
| 4. Copilot, trả lời, Interaction | Chặn | Sales tại hộp thư: `POST /api/v1/ai/copilot/conversations/{id}/reply-draft`, duyệt, sau đó chủ động `POST /api/v1/conversations/{id}/messages`. Đã dùng bản nháp và gửi; ảnh `04-gui-tra-loi.png`. Sau chuyển Lead, Customer 360 ban đầu đếm một Interaction; gửi thêm tin trong cùng phiên làm nó biến mất khỏi Customer 360. | DEC-25 yêu cầu người dùng tự gửi; `frontend/e2e/inbox.spec.ts:28`–`38` kiểm draft/gửi/E04 riêng. Lỗi liên thông xác nhận tại F-01. Customer 360 chỉ có sau chuyển Lead; chưa kiểm tóm tắt trước khi gửi. |
| 5. Qualify, Task, Cơ hội | Có điều kiện | Sales `/app/lead/{id}` qualify, chuyển thành Customer, rồi `/app/co-hoi/them`; đã thấy Task workflow theo dõi và tạo Cơ hội 12.000.000 VND, ảnh `05-co-hoi.png`. Cần worker bật và owner đủ điều kiện. | DEC-12/13; `frontend/e2e/vertical-slice.spec.ts:15`, `frontend/e2e/manager.spec.ts:6` kiểm từng phần. UI tạo Cơ hội cần Customer; chuyển trước khi tạo trong kịch bản này. F-01 làm Interaction chuyển sai sau tin tiếp theo. |
| 6. Bàn giao | Có điều kiện | Manager mở `/app/khach-hang-360/{id}`; `POST /api/v1/customers/{id}/reassign`, rồi Copilot handover-brief. Phải là Customer có Offering liên quan; người mới đủ điều kiện với mọi Offering của Cơ hội mở. Đã chuyển 1 Cơ hội, 1 Task; tạo brief mock, ảnh `06-ho-so-360.png`. | DEC-20 D1/D2/D6. `frontend/e2e/copilot.spec.ts:33` kiểm một Customer khác. Mock nêu bàn giao, Cơ hội và Task, nhưng không chứng minh đủ nhu cầu/cam kết khi Interaction đã mất; chưa đánh giá chất lượng model thật. Đổi owner Lead không thuộc luồng hiện có. |
| 7. Dashboard | Có điều kiện | Sales/Manager `/app/bao-cao`, `GET /api/v1/dashboard/kpis`, kỳ mặc định; cần `dashboard.read`. Ảnh `07-bao-cao.png`. | DEC-17/21/26; xem bảng số dưới đây. `frontend/e2e/inbox.spec.ts:31`–`38` kiểm E04 trước/sau; E01/E02 cần dữ liệu qua ngưỡng, E05 cần suggestion được duyệt. F-01 khiến E02 sai ngữ cảnh Customer vừa được chăm sóc. |

### Số thực tế trong lượt cùng khách

| Quan sát qua API | Trước | Sau |
|---|---:|---:|
| Sales E04 ZALO_SIM | 0/1, `NOT_AVAILABLE_ZERO_DENOMINATOR` | 1/1, `AVAILABLE`, trung vị 1,551229 giây sau gửi |
| Sales E05 REPLY_DRAFT | 0/0, `NOT_AVAILABLE_ZERO_DENOMINATOR` | 1/1, giá trị 1 sau dùng bản nháp |
| Sales E01 | 0 | 0 sau phản hồi; Lead mới chưa quá SLA |
| Sales E02 VND | 47.745.000, 5 cơ hội trước tạo Cơ hội mới | 59.745.000, 6 cơ hội sau tạo Cơ hội 12.000.000, sau khi Interaction bị trả về Lead |
| Interaction trong Customer 360 | 1 sau convert | 0 sau `POST /conversations/{id}/messages` tiếp theo trả 201 |

Số E02 là tổng scope Sales, không phải số của riêng khách. Mức tăng bằng giá trị Cơ hội mới, nhưng nguyên nhân phụ thuộc trạng thái liên kết đã quan sát; không suy diễn toàn bộ bucket chỉ gồm dữ liệu test. E01 là snapshot SLA 24 giờ, E02 xét khoảng 14 ngày; không kỳ vọng một bản ghi mới tạo vừa được chăm sóc tự trở thành quá hạn. E05 đếm suggestion AI đã duyệt, không phải số tín hiệu được chấp nhận. Manager sau bàn giao thấy E01=1, E02=71.185.000 VND/8, E04 ZALO_SIM=1/1, E05 REPLY_DRAFT=1/1; phạm vi Manager khác Sales.

## Lỗ hổng và việc cần xử lý

Ước lượng dưới đây là sơ bộ theo công phát triển + kiểm thử, chưa bao gồm thời gian chờ quyết định.

| Mức | Bằng chứng và tác động | Hướng xử lý / người / công / cần Tài quyết định |
|---|---|---|
| **F-01 — Đã sửa: Interaction bị kéo về Lead sau convert** | Bằng chứng lịch sử: hội thoại còn `leadId`, Customer 360 giảm 1→0 sau tin mới và E02 tăng 12.000.000 VND. Theo DEC-30, commit `b93bb3e` bảo vệ phiên tin cũ; `82f4626` chuyển hội thoại/tín hiệu trong transaction. H2 462/433 đạt/29 bỏ qua; PostgreSQL 462/449 đạt/13 bỏ qua, Verified. | Hồi quy chính và phạm vi Customer riêng đã được kiểm; chi tiết, E2E và các giới hạn còn lại trong `docs/testing/lead-convert-conversation-2026-11-18.md`. |
| **F-02 — Đã chốt (DEC-31): thiếu Offering interest và mock không hiểu ý định** | SRS `docs/SRS-POSE-v2-CRM-AI.md:269`; chỉ INTENT/SENTIMENT tại `SignalOutputFilter.java:11`–`12`. Mock luôn trả OTHER tại `MockProvider.java:38`; ảnh bước 3 không chứng minh ASK_PRICE hay cảm xúc. | Tài BE/FE + Văn dữ liệu: quyết định cập nhật SRS/demo hay bổ sung loại/nhãn và bộ đánh giá; 0,5–2 ngày sau khi chốt. **Cần quyết định Tài**; không tự mở rộng nhãn. |
| **F-03 — Đã chốt (DEC-31): worker mặc định tắt; Task không nằm trong hôm nay** | `application.yml:80`–`84`: worker mặc định false, chu kỳ 60 giây, hạn +1 ngày; `JdbcConversationMessageProcessor.java:198`. Bật worker trong clone: Task đúng Sales nhưng không có trong `/dashboard/today?limit=50`; không chứng minh danh sách không bị giới hạn bởi 50 mục, tuy nhiên hạn hôm sau cũng nằm ngoài điều kiện hôm nay. | Tài vận hành + Thắng workflow/QA: bật worker trước demo, kiểm outbox, giải thích hạn ngày mai hoặc chốt SLA/thứ tự demo; 0,5–1 ngày. **Cần Tài quyết định** nếu muốn Task hiện hôm nay; không tự đổi SLA. |
| **F-04 — Đã chốt (DEC-31): tóm tắt và bàn giao chưa chứng minh đủ nội dung SRS** | Ảnh `06-ho-so-360.png`: brief mock có thông tin bàn giao, cơ hội và Task, không thể chứng minh nhu cầu/cam kết; F-01 làm thiếu lịch sử. Prompt `handover-brief.v3.txt:1` yêu cầu không bịa dữ liệu. Hộp thư có reply-draft, nhưng chưa chạy tóm tắt trước phản hồi cho khách chưa có Customer. | Tài BE/FE + Văn dữ liệu, Thắng QA: sau F-01 chuẩn bị dữ liệu có nguồn cho nhu cầu/cam kết, kiểm brief/model thật và tóm tắt đúng giai đoạn; 1–2 ngày. **Cần Tài chốt** mức nội dung bắt buộc và chấp nhận diễn giải khi thiếu nguồn. |
| **F-05 — Đã chốt (DEC-31): dự phòng chưa chứng minh trọn vẹn như Telegram/AI thật** | Đã đi các thao tác chính qua ZALO_SIM + mock; đây là webhook mô phỏng, không xác nhận Telegram/tunnel hay model thật; `docs/testing/ai-06-reply-draft-2026-11-17.md:34` và `docs/testing/ai-04-inbox-2026-11-16.md:60` nêu giới hạn. Mock chỉ cho kết quả xác định, không thể chứng minh chất lượng. | Thắng QA chuẩn bị kịch bản dự phòng hiển thị rõ “mô phỏng”, Tài giữ khóa tự smoke model thật, Văn cung cấp bộ mẫu; khoảng 0,5–1 ngày cộng thời gian smoke. **Cần Tài chấp nhận** phương án demo mô phỏng. |
| **F-06 — Đã chốt (DEC-31): SRS nói tự tạo Lead** | SRS `:268` đối lập DEC-23 D2 (`decision-log.md:258`) và DEC-29 (`:334`). Lượt thực tế phải chọn/nhập liên hệ, nhập tên và bấm tạo. | Tài quyết định sửa SRS/kịch bản hay thay quyết định; khoảng 0,25 ngày để đồng bộ tài liệu sau khi chốt. **Cần Tài quyết định**. |
| **F-07 — Đã chốt (DEC-31): chuẩn bị dữ liệu và tên người gửi** | Hội thoại chưa lưu tên người gửi; phải hỏi và nhập tên, không tự suy ra mã kỹ thuật (`docs/testing/inbox-create-lead-2026-11-18.md:7`). | Thắng BE/QA chuẩn bị dữ liệu giả và hướng dẫn; nếu muốn tự điền tên cần xem adapter/migration riêng, 0,25 ngày hướng dẫn hoặc 1–2 ngày tính năng. **Cần Tài quyết định** nếu mở rộng phạm vi. |

## Cách xử lý đã chốt theo DEC-31

Các mô tả trong bảng trên là bằng chứng lịch sử; trạng thái và cách xử lý hiện hành:

- F-02: TLCN chỉ có INTENT/SENTIMENT; Offering interest sang KLTN. Mock nhận diện từ khóa theo DEC-31 và không chứng minh chất lượng AI.
- F-03: giữ hạn Task +1 ngày, bật worker và kiểm outbox; mở Task qua danh sách Nhiệm vụ.
- F-04: chỉ nêu dữ kiện có nguồn, thiếu nguồn ghi “Chưa có dữ liệu”; mock và prompt bàn giao được điều chỉnh.
- F-05: chấp nhận ZALO_SIM + mock là dự phòng chính thức; Tài tự smoke kênh và model thật.
- F-06: giữ DEC-23 D2, SRS bước 2 đổi thành nhân viên tạo hoặc gắn Lead; giữ yêu cầu liên hệ và Offering.
- F-07: không làm tính năng lưu tên người gửi; nhân viên hỏi và nhập tên.

## Các câu hỏi đã được trả lời

1. **Đã trả lời (DEC-30).** Khi chuyển Lead, hội thoại và tín hiệu liên quan có chuyển sang Customer trong cùng giao dịch không; với Interaction phiên đã chuyển, tin mới phải giữ liên kết Customer như thế nào?
2. **Đã trả lời (DEC-31).** Giữ DEC-23 D2 và sửa SRS bước 2 thành tạo/gắn thủ công, hay cần quyết định khác? Quy tắc bắt buộc liên hệ/Offering hiện tại vẫn có hiệu lực.
3. **Đã trả lời (DEC-31).** Bước 3 có giảm SRS về INTENT/SENTIMENT hay cần bổ sung Offering interest, bộ nhãn và đánh giá?
4. **Đã trả lời (DEC-31).** Có cần Task phản hồi đầu tiên xuất hiện trong “Việc hôm nay” ngay, hay giữ hạn một ngày và trình diễn qua danh sách Nhiệm vụ?
5. **Đã trả lời (DEC-31).** Có chấp nhận ZALO_SIM + mock là phương án dự phòng chính thức, và tiêu chí tối thiểu cho brief khi nguồn chưa có cam kết là gì?

## Nháp thao tác demo và dự phòng

1. **Manager EDU:** chuẩn bị Offering đang hoạt động, phân công Sales hiện tại và Sales nhận bàn giao; lượt này dùng Offering seed, chưa chạy tạo mới.
2. **Admin/Thắng chuẩn bị:** account ZALO_SIM có team và owner, bật workflow; gửi webhook giả. **Sales:** mở hộp thư, nhập tên khách mẫu, xác nhận số giả, chọn Offering, bấm tạo Lead.
3. **Sales:** bấm trích, kiểm câu trích và chấp nhận/sửa. Giải thích mock chỉ kiểm cơ chế, không trình bày kết quả như phân loại AI thật.
4. **Sales:** soạn bản nháp, kiểm, dùng và tự bấm gửi; không hứa Customer 360 hoàn chỉnh sau tin tiếp theo cho đến khi F-01 được sửa.
5. **Sales:** qualify, đợi Task workflow, chuyển Lead thành Customer, tạo Cơ hội có giá trị. Gửi thêm tin mô phỏng sau chuyển và kiểm Interaction vẫn gắn Customer theo DEC-30.
6. **Manager:** mở Customer 360, đổi người phụ trách đủ điều kiện, kiểm Cơ hội/Task và đọc brief; giải thích phần thiếu nguồn.
7. **Manager:** mở Báo cáo, giải thích E01/E02 theo SLA/khung thời gian, E04 theo kênh và E05 theo gợi ý đã duyệt. Số tổng có dữ liệu seed khác cùng phạm vi.

Nếu Telegram/tunnel lỗi, chỉ dùng kịch bản ZALO_SIM như trên; nó thay kênh và đường webhook bằng mô phỏng, không chứng minh kết nối Telegram thật. F-01 đã sửa theo DEC-30; kết quả chạy mới được ghi riêng trong evidence diễn tập.

## Giới hạn

Không có Chrome DevTools MCP trong phiên; áp dụng phần phù hợp của skill `browser-testing-with-devtools` bằng Playwright Chromium dự án. Có ảnh trình duyệt nhưng chưa kiểm console/network một cách đầy đủ, bàn phím toàn luồng, trình đọc màn hình hay trình duyệt khác. Không gọi AI hoặc Telegram thật; không kết nối Supabase hay PostgreSQL 5432. Các cổng 8080, 8081, 3010, 3011, 54329 được runner báo đã dừng. Không chạy full regression, không xác nhận độc lập chất lượng dữ liệu sáu tổ chức hoặc bộ nhãn của Văn. Ảnh có số điện thoại giả sinh cho fixture, không phải thông tin khách thật.
