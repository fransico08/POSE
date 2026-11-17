# Evidence AI-11 — Gia cố prompt injection — 17/11/2026

## Tích hợp và E04

Đã merge `origin/Thắng` tại `d0a4709`, giữ bộ kiểm thử phân quyền hộp thư. E2E E04 đọc numerator/denominator của Zalo mô phỏng qua API cùng kỳ của Báo cáo trước và sau gửi; kiểm numerator tăng 1, denominator giữ nguyên và hàng giao diện khớp API. Hai lượt `scripts/e2e-env.ps1 -Build` trước khi sửa AI đều đạt **36/36** (runner báo 1,3 phút và 1,2 phút), không phụ thuộc thứ tự tạo hội thoại.

## Ranh giới và kiểm tra đầu ra

Theo đề xuất DEC-27, chưa thay thế DEC-20/25: ngữ cảnh đã áp scope và che dữ liệu được đặt trong khối dữ liệu không tin cậy; ký tự ranh giới và tiền tố vai trò giả được escape trong JSON. Chỉ dẫn hệ thống yêu cầu không coi dữ liệu là mệnh lệnh. Phiên bản prompt có hậu tố `guard1`; reuse yêu cầu cùng phiên bản guard.

Gateway kiểm đầu ra của mọi use case trước khi lưu: trích chỉ dẫn hệ thống, URL lạ, UUID lạ hoặc điện thoại/email làm invocation `FAILED/INVALID_OUTPUT`, không tạo suggestion. V17 ghi `error_detail` lần lượt `SYSTEM_PROMPT_LEAK`, `UNKNOWN_URL`, `UNKNOWN_UUID`, `CONTACT_INFO` theo thứ tự ưu tiên; API và retry job vẫn chỉ dùng `INVALID_OUTPUT`. Không ghi nội dung vi phạm. Đầu ra không có dấu hiệu trên vẫn đi qua bộ lọc nguồn và tín hiệu hiện có.

| Fixture / kiểm tra | Kỳ vọng |
|---|---|
| Việt/Anh yêu cầu bỏ qua chỉ dẫn, đóng khối, giả `system:`/`assistant:`, tiết lộ khách khác | Dữ liệu nằm trong khối; ranh giới giả được escape; không lấy hồ sơ ngoài scope |
| Trả lời có ý hợp lệ lẫn URL/UUID lạ, trích chỉ dẫn, điện thoại/email | Từ chối toàn bộ, không tạo suggestion; ghi mã loại dấu hiệu |
| URL có nguyên văn trong ngữ cảnh đã che | Không bị chặn bởi bộ kiểm URL |
| Có ý thiếu nguồn xen ý hợp lệ, không có dấu hiệu thao túng | Giữ bộ lọc và đếm ý bị loại theo DEC-20/25 |
| Nhãn tín hiệu ngoài danh mục hoặc INTENT lặp | Chỉ giữ nhãn hợp lệ, tối đa một tín hiệu mỗi loại |
| Bản nháp lỗi hoặc được duyệt | Không tự gửi tin; thao tác CRM không tự thay đổi |

## Kiểm chứng

| Bộ kiểm thử | Tổng | Đạt | Bỏ qua | Lỗi |
|---|---:|---:|---:|---:|
| Backend H2 | 411 | 382 | 29 | 0 |
| Backend PostgreSQL 54329 | 411 | 398 | 13 | 0 |
| Vitest | 370 | 370 | 0 | 0 |
| Playwright E2E Chromium sau gia cố | 36 | 36 | 0 | 0 |

Hai suite backend chạy từ hai bản sao sạch, số đọc từ Surefire XML; PostgreSQL trả `Verified` và `SHOW data_directory` khớp cluster của bản sao. Sau khi bổ sung assertion rằng một gợi ý **hợp lệ** chứa câu yêu cầu thao tác vẫn chỉ có trạng thái `PROPOSED` và không thay đổi owner, trạng thái, Task hay tin nhắn, `AiGatewayIntegrationTest` chạy riêng lại trên cả H2 và PostgreSQL: mỗi nơi 11/11 đạt. Database PostgreSQL bổ sung được tạo riêng rồi xóa sau test; không thay đổi mã production sau lượt suite đầy đủ. Typecheck và lint frontend đạt.

Lệnh xóa `backend/target/classes`, `test-classes`, `surefire-reports` tại checkout bị kiểm duyệt tự động từ chối với lý do `blocked by policy`; đã dùng bản sao sạch thay thế, không chạy `mvn clean`. Kiểm tra offline `get-flyway-migration-versions.ps1` trả đúng `1,2,…,17`.

Lượt E2E sau gia cố chạy riêng bằng `scripts/e2e-env.ps1 -Build`, build đạt và 36/36 đạt (runner báo 1,3 phút). Runner xác nhận các cổng 8080, 8081, 3010, 3011 và 54329 đã dừng.

## Gợi ý cho Thắng — AI-11 QA

- Thử biến thể tiếng Việt/Anh, nhiều lượt hội thoại, ký tự Unicode, đường link không chuẩn và các cách yêu cầu lộ prompt hoặc vượt scope.
- Đánh giá riêng tỷ lệ chặn nhầm và bỏ lọt trên bộ tình huống có nhãn; kiểm cả các use case lẫn provider thực theo quy trình được duyệt.
- Kiểm đường gửi tin, đổi trạng thái/owner và tạo Task vẫn cần thao tác, quyền và scope của API nghiệp vụ.

## Giới hạn và bàn giao

- Bộ lọc theo mẫu không chặn được mọi prompt injection; người dùng luôn phải xem xét gợi ý trước khi hành động. Chưa đánh giá chất lượng phòng thủ với Gemini/Claude thật.
- Đề nghị Nhi/Văn cập nhật Data Dictionary cho V17: `ai_invocations.error_detail` và `ck_ai_invocation_error_detail`; không sửa Data Dictionary trong lượt này. Supabase chỉ nhận V17 khi người giữ credential chạy `setup-supabase-v2.ps1` sau sao lưu và được duyệt.
- Không kết nối Supabase hoặc PostgreSQL 5432, không gọi AI thật và không đặt `POSE_DEMO_PASSWORD` cho kiểm thử.

## Fix-up nhận diện URL — 17/11/2026

URL có scheme `://` hoặc bắt đầu bằng `www.` luôn được xét. Tên miền trần chỉ được xét nếu TLD thuộc danh sách cố định trong DEC-27 và toàn bộ chuỗi viết thường hoặc có đường dẫn `/`. Dấu câu cuối được bỏ; đối chiếu scheme và tên miền không phân biệt hoa/thường, còn đường dẫn, query và fragment phân biệt hoa/thường. Các quy tắc UUID, thông tin liên hệ, lộ prompt và thứ tự ưu tiên giữ nguyên.

| Nhóm fixture | Giá trị kiểm thử |
|---|---|
| Phải qua | “Khách ở TP.HCM cần tư vấn”; “Q.1, P.Bến Nghé”; “Dùng Node.js và ASP.NET”; “Gửi file baogia.pdf”; “Cảm ơn anh.Em sẽ gọi lại”; “Giá 1.500.000đ”; “Ngày 17.11.2026”; “v.v.”; “Gói 1.5 triệu” |
| Phải chặn khi ngoài context | `https://evil.example/x`; `http://bit.ly/abc`; `www.evil.com`; `evil.com/login`; `vao-day.vn`; “lừa đảo tại evil.com.” |
| Phải qua khi có trong context | URL khác hoa/thường ở scheme hoặc tên miền; đường dẫn phải khớp đúng hoa/thường |
| Prompt leak | Năm câu trả lời lịch sự thông thường được đối chiếu với prompt thật của từng use case; đoạn trích chỉ dẫn an toàn vẫn bị chặn |

Giới hạn: tên miền trần có TLD ngoài danh sách, hoặc có chữ hoa mà không có đường dẫn `/`, có thể không được nhận diện. Chữ viết tắt hay tên tệp tình cờ khớp TLD được phép và viết thường vẫn có thể bị xem là tên miền; bộ lọc không bảo đảm chặn mọi liên kết hoặc prompt injection.

| Kiểm chứng fix-up | Tổng | Đạt | Bỏ qua | Lỗi |
|---|---:|---:|---:|---:|
| Backend H2 | 429 | 400 | 29 | 0 |
| Backend PostgreSQL 54329 | 429 | 416 | 13 | 0 |
| Playwright E2E Chromium | 36 | 36 | 0 | 0 |

Hai suite backend chạy trên bản sao sạch riêng, số đọc từ Surefire XML; PostgreSQL trả `Verified`, `SHOW data_directory` khớp cluster test của bản sao. Không đặt `POSE_DEMO_PASSWORD`. `AiOutputSafetyTest` và `AiGatewayIntegrationTest` kiểm các trường hợp nêu trên, kể cả URL có cổng và `://` trong đường dẫn. E2E chạy riêng bằng `scripts/e2e-env.ps1 -Build`, runner báo 1,1 phút và xác nhận các cổng 8080, 8081, 3010, 3011, 54329 đã dừng. Không đổi frontend nên không chạy lại Vitest trong lượt fix-up; không gọi AI thật, Supabase hay PostgreSQL 5432.
