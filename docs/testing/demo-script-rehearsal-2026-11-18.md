# Evidence diễn tập kịch bản CRM.AI — 18/11/2026

## Quyết định và phạm vi

DEC-31 chốt tạo/gắn Lead do nhân viên thực hiện, tín hiệu TLCN chỉ INTENT/SENTIMENT, giữ SLA phản hồi đầu tiên một ngày và dùng ZALO_SIM + mock làm dự phòng. Offering interest chuyển sang KLTN. F-01 được xử lý theo DEC-30; sau convert có thể tiếp tục nhắn và kiểm Interaction trên Customer.

Mock chuẩn hóa NFC và chữ thường; cụm nhiều từ so thêm bản bỏ dấu (`đ` thành `d`) theo ranh giới từ. Các từ đơn `giá`, `tệ`, `tốt` phải đúng dấu. Không khớp chỉ trả INTENT OTHER; cảm xúc chỉ được thêm khi có từ khóa, khiếu nại tạo COMPLAINT và NEGATIVE. Khi nhiều từ khóa khớp, ưu tiên COMPLAINT, ASK_PRICE, BOOKING; NEGATIVE ưu tiên POSITIVE. Đây là dữ liệu và kết quả mô phỏng, không đo chất lượng AI thật.

Prompt bàn giao được nâng lên v4 vì mock cũ khẳng định không có Cơ hội hoặc Task dù ngữ cảnh có thể không được phép đọc các mục đó. Khi không có dữ kiện, mock ghi “Chưa có dữ liệu”; không tự bịa hành động. Bộ lọc nguồn không thể chứng minh mọi câu đúng ngữ nghĩa: nhân viên vẫn phải đối chiếu trước khi sử dụng.

## Kiểm thử

- Môi trường backend: hai checkout riêng mới tại `D:/POSE-data/pose-dec31-check` và `D:/POSE-data/pose-dec31-pg`, không đặt `POSE_DEMO_PASSWORD`. PostgreSQL test 127.0.0.1:54329, `SHOW data_directory` xác nhận `D:/POSE-data/pose-dec31-pg/backend/target/auth-postgres-test`.
- H2: 470 tổng, 441 đạt, 29 bỏ qua, 0 lỗi; PostgreSQL: 470 tổng, 457 đạt, 13 bỏ qua, 0 lỗi; script PostgreSQL báo `Verified.` Số đọc từ Surefire XML.
- `MockProviderSignalTest`: kiểm từng nhóm từ khóa, không dấu, ranh giới từ, ký tự đặc biệt, không khớp và brief thiếu dữ liệu. `AiEvaluationIntegrationTest` chạy công cụ AI-10 với fixture tổng hợp, 15 ca đạt trên H2; không phải bộ nhãn AI-05 và không phải đánh giá AI thật.
- Frontend: typecheck, lint, build đạt; Vitest 375/375 đạt. Chạy `scripts/e2e-env.ps1 -Build -KeepFailureLogs`: lượt cuối 38/38 đạt trong 59,7 giây, 0 bỏ qua; 5 cổng 8080, 8081, 3010, 3011, 54329 đã dừng. Kịch bản mới chạy riêng 1/1 đạt trước lượt đầy đủ.
- Lượt E2E đầu có 33/38 đạt: hai ca cũ dùng fixture “Xin hỏi giá sản phẩm” nay tạo ASK_PRICE thay OTHER và gây locator trùng; đã báo Tài và chỉnh kỳ vọng/locator mà không bỏ kiểm tra số lượng. Một ca mới đọc ô nháp trước khi cập nhật, một ca cũ chọn nhầm cả preview và tin; đã sửa cách chờ/chọn. Một webhook theo sau convert thất bại một lần, không thu được mã lỗi cụ thể ở lượt đó; cùng ca đạt trong lượt sau, chưa xác định được nguyên nhân và cần theo dõi nếu tái diễn.

| Bước | Bằng chứng Playwright mới |
|---|---|
| Phân công, nhận tin | Chuẩn bị Offering đủ điều kiện, account ZALO_SIM và tin INBOUND giả. |
| Tạo Lead, trích và duyệt | Tạo Lead có liên hệ do người dùng xác nhận; tin “học phí” cho ASK_PRICE và được chấp nhận. |
| Nháp và chuyển đổi | Dùng nháp rồi tự gửi, qualify và convert; tin mới sau chuyển vẫn hiện trong Customer 360 cùng tín hiệu. |
| Cơ hội và bàn giao | Tạo Cơ hội, đổi owner, kiểm owner Cơ hội và đọc ý có nguồn trong brief. |
| Báo cáo, mobile | E04 tăng numerator đúng một và giữ denominator; E05 tăng numerator và denominator đúng một. Kiểm trang 360 và Báo cáo không cuộn ngang ở 375px. |

## Checklist và giới hạn

Kịch bản chính thức ở `docs/demo/kich-ban-demo-crm-ai-v1.md` yêu cầu bật worker, kiểm outbox, account ZALO_SIM, Offering và phân công trước khi demo. Task phản hồi đầu tiên hạn +1 ngày nên mở trong danh sách Nhiệm vụ. Lượt E2E ở 6219e77 không bật worker và không kiểm hai Task workflow trong cùng lượt; các kiểm thử workflow độc lập nằm trong bộ backend. Chưa chứng minh Telegram/tunnel hay mô hình thật; Tài tự smoke khi có khóa. Không gọi AI thật, không dùng Supabase/5432, không sửa dữ liệu Nhi hoặc migration. Không có ảnh chụp chứa mật khẩu, token hay secret.

## Kiểm lại với workflow worker

Script có chế độ `-WorkflowWorker`, bật worker cách một giây và chỉ chạy test `@workflow`; mặc định worker tắt và các test `@workflow` bị loại. Không thay SLA. Test dùng ZALO_SIM, AI mock, một database test mới trên cluster 54329; kiểm Task phản hồi đầu tiên trong danh sách Nhiệm vụ, owner, hạn sau một ngày, Task theo dõi sau qualify và chuyển Task sang Customer, tin lặp không tạo Task/outbox trùng và cả hai outbox `PUBLISHED`.

Lượt thử ban đầu thất bại trước khi kiểm nghiệp vụ do `psql` trên Windows báo `invalid byte sequence for encoding "UTF8"` khi nhận chuỗi tiếng Việt trong lệnh SQL, không có mã HTTP. Đã truyền chuỗi dưới dạng UTF-8 hex trong test; một lượt thử tiếp theo đã đạt. Các số kiểm chứng cuối được ghi dưới đây.

| Kiểm chứng bản sửa | Kết quả |
|---|---|
| H2 trên checkout mới, không đặt `POSE_DEMO_PASSWORD` | 471 tổng, 442 đạt, 29 bỏ qua, 0 lỗi; số từ Surefire XML. |
| PostgreSQL 54329 trên checkout mới, không đặt `POSE_DEMO_PASSWORD` | 471 tổng, 458 đạt, 13 bỏ qua, 0 lỗi; `Verified.`; `SHOW data_directory` đúng thư mục `D:/POSE-data/pose-worker-pg/backend/target/auth-postgres-test`. |
| Typecheck, lint, Vitest | Đạt; Vitest 375/375. |
| Worker, lượt 1 / 2 | 1/1 đạt trong 8,9 giây / 1/1 đạt trong 8,2 giây. |
| Mặc định, lượt 1 / 2 | 38/38 đạt trong 59,4 giây / 38/38 đạt trong 59,7 giây. |

Task phản hồi đầu tiên gắn hội thoại, không tự gắn Lead; Task theo dõi gắn Lead và chuyển sang Customer khi convert. Chỉ thời điểm hạn theo Asia/Ho_Chi_Minh, owner và quan hệ của hai Task trong fixture được kiểm; không đánh giá kênh hay AI thật.

Sau mỗi lượt E2E, script xác nhận các cổng 8080, 8081, 3010, 3011 và 54329 không còn lắng nghe. Không dùng Supabase, cổng 5432, Telegram thật hay nhà cung cấp AI thật; không sửa migration hoặc dữ liệu Nhi.
