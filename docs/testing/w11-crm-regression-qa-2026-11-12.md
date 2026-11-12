# W11 — Regression QA, triage lỗi và cập nhật test case

Ngày kiểm thử: 12/11/2026 (UTC+07:00)
Nhánh: `Thắng`
Commit nền: `5a51b894a1cb0d2d8d9c95ffbce9d3fd6bd36d26`
Phạm vi: regression CRM cho FE/BE, gồm Opportunity UI, Customer 360, Interaction/Task, Sales Assignment và vertical slice Assignment → Customer → Task → Customer 360. Không bao gồm kiểm thử chấp nhận Telegram/Claude hoặc tunnel của CRM.AI. FE có thay đổi cục bộ chưa commit trong lượt QA này.

## Tóm tắt kết quả

| Nhóm | Lệnh / cách chạy | Kết quả |
|---|---|---|
| Backend — H2 | `mvn -pl backend -am verify` | **291 test; 0 failure; 0 error; 28 skipped; BUILD SUCCESS.** Đã chạy trước phần sửa FE; backend không đổi. |
| Backend — PostgreSQL | `backend/scripts/test-postgres-v2.ps1` theo guard trong `backend/README.md`; cluster cô lập `127.0.0.1:54329` | **291 test; 0 failure; 0 error; 13 skipped.** Foundation DB v2 assertions đạt; V4 có đủ 6 mã tổ chức; 36 user và fingerprint thông tin xác thực ban đầu không đổi. Hai database test được giữ lại, cluster đã dừng sau khi chạy. |
| Frontend — Vitest toàn bộ | `npm.cmd run test -- --maxWorkers=1 --no-file-parallelism --reporter=dot` trong `frontend` | **314 test; 314 passed; 27/27 file passed; không lỗi worker.** Sau lượt full này có thêm một test cho trường hợp `useSearchParams()` trả `null`; nhóm regression CRM bên dưới đã chạy lại và bao gồm test mới. |
| Frontend — nhóm regression CRM, lượt cuối | Cùng cấu hình tuần tự với `tests/customer-ui.test.tsx`, `tests/dashboard.test.tsx`, `tests/customer-360-ui.test.tsx`, `tests/customer-lead-ui.test.tsx`, `tests/opportunity-pipeline-ui.test.tsx` | **129/129 test passed; 5/5 file passed.** |
| Frontend — Opportunity Pipeline | `npm.cmd run test -- --maxWorkers=1 --no-file-parallelism --reporter=dot tests/opportunity-pipeline-ui.test.tsx` | **48/48 test passed**, gồm kiểm thử fallback khi `useSearchParams()` không khả dụng. |
| Frontend — lint phần sửa | `eslint` trên `OpportunityPipeline.tsx`, route `co-hoi/page.tsx` và `opportunity-pipeline-ui.test.tsx`, `--max-warnings=0` | **Đạt; 0 lỗi, 0 cảnh báo.** |
| Frontend — typecheck | `npm.cmd run typecheck` sau khi dependency được khôi phục | **Đạt; mã thoát 0, không có lỗi TypeScript.** |
| E2E — trình duyệt thật | `scripts/e2e-env.ps1` với JDK 22, Maven 3.9.15, PostgreSQL 18; cluster cô lập `127.0.0.1:54329` | **31/31 test passed ở lượt đầu và 31/31 ở lượt xác nhận thứ hai.** Cả hai lượt đều dọn database và dừng BE/FE/cluster kiểm thử. |

Các lượt PostgreSQL chạy qua script guarded của repo, kiểm tra `SHOW data_directory`, không dùng Supabase và không chạm PostgreSQL 5432. `POSE_DEMO_PASSWORD` không được cấu hình nên 13 demo acceptance tests bị skip; H2 suite có 28 test bị skip theo điều kiện test. Không có credential nào được ghi vào evidence.

## Lỗi đã xử lý và giới hạn còn lại

| ID | Mức độ | Khu vực / test case | Mong đợi | Kết quả cuối | Trạng thái |
|---|---|---|---|---|---|
| W11-UI-01 | P2 | `frontend/tests/opportunity-pipeline-ui.test.tsx` — `loads six stages from the API and paginates` | Tải cơ hội mẫu, hiển thị đủ sáu giai đoạn và chuyển trang. | Đã khởi tạo filter từ `useSearchParams` ngay lần render đầu, bỏ trạng thái `ready` và timer trì hoãn bật query. Route có Suspense fallback. Sau đó xử lý cả trường hợp Next trả `null`; Opportunity đạt 48/48, nhóm regression CRM đạt 129/129 và E2E đa vai trò đạt 31/31 hai lượt. | Đã xử lý; component test và E2E trình duyệt thật đều đạt. |
| W11-UI-02 | Theo dõi | Customer, Dashboard, Customer 360, Customer/Lead | Tải và xử lý dữ liệu đúng theo test. | Nhóm regression CRM tuần tự đạt 129/129; full FE suite trước test null fallback đạt 314/314. | Đạt ở component tests. |
| W11-ENV-01 | Môi trường test | Khởi chạy Vitest worker | Runner thực thi toàn bộ test. | Một lượt song song trước đó gặp lỗi khởi chạy worker; chạy tuần tự bản cuối đạt đủ 27 file và không có lỗi worker. Chưa kết luận nguyên nhân cụ thể của lỗi ở chế độ song song. | Không tái hiện ở lượt chạy tuần tự hoàn chỉnh. |
| W11-TYPE-01 | Môi trường test | TypeScript | Typecheck FE và E2E không có lỗi. | Sau khi dependency được khôi phục, `npm.cmd run typecheck` thoát mã `0`, không báo lỗi. | Đạt. |
| W11-E2E-01 | Chặn kiểm thử | Playwright E2E | Chạy regression trên browser thật. | Sau khi build FE/BE và cài Chromium, toàn bộ **31/31** test đạt ở hai lượt. Lượt xác nhận thứ hai chạy trên bản sao DB kiểm thử mới. | Đạt; runner dừng các dịch vụ và cluster sau lượt cuối. |
| W11-E2E-02 | Công cụ kiểm thử | `scripts/e2e-env.ps1` trên đường dẫn Windows có khoảng trắng | Khởi chạy đúng JAR và Next CLI; giữ log khi cần chẩn đoán. | Trích dẫn đối số đường dẫn JAR/Next CLI để xử lý workspace `C:\Users\WIN 11\...`; thêm `-KeepFailureLogs` để giữ log khi E2E thất bại. Cú pháp PowerShell đã được kiểm tra. | Đạt ở hai lượt E2E thành công. |
| W11-BE-01 | Đạt | Backend unit/integration và dữ liệu PostgreSQL cô lập | Backend regression đạt; migration và dữ liệu nền giữ nguyên theo guard. | H2 và PostgreSQL đều BUILD SUCCESS, 0 failure, 0 error; assertion dữ liệu đạt. | Đạt trong giới hạn 13 PostgreSQL acceptance tests bị skip do thiếu cấu hình demo password. |

## Cập nhật test case và FE

- Giữ nguyên assertion nghiệp vụ của test Pipeline; không bỏ qua hoặc làm yếu test.
- Filter trang Pipeline giờ lấy trực tiếp từ Next `useSearchParams`, bao gồm kiểm tra UUID khách hàng, trước khi kích hoạt query. Nhờ đó không cần chờ effect và timer 0 ms mới tải danh sách.
- Thêm Suspense fallback ở route Pipeline cho giai đoạn Next đang đọc search params.
- Mock `useSearchParams` trong test để URL kiểm thử khớp với URL thực tế của trình duyệt; thêm test kiểm tra fallback filter mặc định khi hook trả `null`.
- Full FE suite đạt 314/314 trước khi thêm test fallback; sau thay đổi cuối, 129 test trong nhóm regression CRM và 48 test Opportunity đều đạt. Lint phần sửa và typecheck đạt. E2E đa vai trò đạt 31/31 ở hai lượt.

## Kết luận

Lỗi loading của Opportunity Pipeline đã được xử lý và kiểm chứng. Full FE suite đạt **314/314** trước test fallback mới; nhóm regression CRM sau thay đổi cuối đạt **129/129**, Opportunity đạt **48/48**, lint và typecheck đạt. Playwright E2E trên Chromium đạt **31/31 ở hai lượt**. Backend H2/PostgreSQL đạt 0 failure/0 error ở lượt kiểm thử trước trên cùng commit nền; 28 H2 test và 13 PostgreSQL acceptance test bị skip theo điều kiện cấu hình, gồm demo acceptance thiếu `POSE_DEMO_PASSWORD`.

**W11 regression QA đã hoàn tất trong phạm vi đã chạy và ghi nhận tại đây.** Giới hạn còn lại: các test backend bị skip chưa được chạy vì thiếu cấu hình demo password; không có credential nào được ghi vào evidence. Lượt này chưa commit hoặc push. Source FE, runner E2E và evidence QA đang là thay đổi cục bộ trên nhánh `Thắng`.
