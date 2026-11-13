# W10 — E2E đa vai trò và rà soát phạm vi truy cập

Ngày kiểm thử: 12/11/2026 (UTC+07:00)
Nhánh: `Thắng`
Commit được kiểm thử: `cd5781363adc4f24a88cdfe58e6bb986daae4d53` (`HEAD` trùng `origin/Thắng`)

## Phạm vi

Kiểm tra quyền theo vai trò, team, phân công, tenant và dữ liệu nhạy cảm trên các luồng CRM hiện có. Dùng các test backend hiện có, runner PostgreSQL có guard và E2E Chromium của repo. Không thay đổi policy hoặc production code trong lượt QA này.

## Kết quả kiểm thử

| Lớp | Cách chạy | Kết quả |
|---|---|---|
| Backend/H2 — nhóm kiểm tra quyền và scope | 10 suite: `CustomerLeadIntegrationTest`, `DashboardIntegrationTest`, `HardeningRbacIntegrationTest`, `IdentityIntegrationTest`, `InteractionTaskAuditIntegrationTest`, `LeadConversionIntegrationTest`, `OfferingIntegrationTest`, `OpportunityIntegrationTest`, `SalesAssignmentIntegrationTest`, `VerticalSliceIntegrationTest` | **161/161 đạt**, 0 lỗi, 0 bỏ qua |
| Backend/PostgreSQL | `backend/scripts/test-postgres-v2.ps1` chạy full `mvn verify` trên database kiểm thử | **291 test**, 0 failure, 0 error; **13 demo acceptance test bị skip** vì không cấu hình `POSE_DEMO_PASSWORD` |
| E2E đa vai trò | `scripts/e2e-env.ps1 -Build -KeepFailureLogs`, Playwright Chromium, 1 worker | **31/31 đạt** |
| Build dùng bởi E2E | `next build`; Maven `package -DskipTests` | Cả hai đạt. Next.js báo thiếu `SITE_URL` và dùng fallback local theo cấu hình hiện có. |

Các nhóm test backend xác nhận quyền tenant và permission, scope khách hàng/Lead/Opportunity, ứng viên phân công theo team, thời hạn và chồng lấn Sales Assignment, phân quyền Interaction/Task/Audit, cùng vertical slice. E2E bao gồm Admin, Manager, Sales, Sales kiêm Customer Care, Customer Care và Data Staff; các ca kiểm gồm menu theo permission, dữ liệu Customer 360 bị che theo role, dữ liệu Sales-only, nhiệm vụ trong phạm vi, hồ sơ ngoài phạm vi, phân công Offering theo team/thời hạn, và smoke test Manager trên cả sáu tổ chức EDU/REA/TECH/RET/LOG/CLI.

## PostgreSQL cô lập và bảo toàn dữ liệu

- Runner xác nhận `SHOW data_directory` trỏ tới `backend/target/auth-postgres-test`; không dùng Supabase hoặc PostgreSQL cổng `5432`.
- PostgreSQL full verify tạo database `pose_foundation_test_20261112185153687` và database restore `pose_v2_review_20261112185153687`. Foundation assertions và verifier dữ liệu V2 đạt.
- Xác nhận bản restore có đủ sáu mã organization và 36 user; fingerprint credential trước/sau trùng nhau. Runner giữ lại hai database kiểm thử như thiết kế.
- E2E dùng database clone tạm, rồi dọn clone, session và tiến trình. Sau khi kết thúc, runner xác nhận các cổng `8080`, `8081`, `3010`, `3011`, `54329` đều đã dừng.

## Vai trò và phạm vi đã kiểm

| Vai trò | Bằng chứng chính |
|---|---|
| Admin | Menu theo quyền; kiểm tra permission/tenant và hành vi truy cập backend. |
| Manager | Menu và dữ liệu theo phạm vi team; quản lý phân công; luồng Lead → Customer → Opportunity → Interaction/Task → Customer 360; smoke test trên sáu tenant. |
| Sales | Chỉ thấy Offering đang được phân công còn hiệu lực; thao tác trong phạm vi; không mở được hồ sơ khách hàng ngoài phạm vi. |
| Sales + Customer Care | Menu được tính theo permission hợp nhất; test permission không mượn quyền ghi từ role khác. |
| Customer Care | Không thấy Interaction `SALES_ONLY`; chỉ thấy task trong phạm vi; dashboard/profile được giới hạn. |
| Data Staff | Đối soát dữ liệu và Customer 360 đã che thông tin nhạy cảm; không có quyền quản lý task. |

## Finding và giới hạn

- **F-W10-01 — Lệch tài liệu quyền Opportunity đã biết:** bảng `roles-authorization-v1.md` mô tả Customer Care đọc được Opportunity, nhưng DEC-12 F4 và permission thực tế không cấp `opportunity.read`; menu Opportunity vì thế bị ẩn. Evidence W10 ngày 05/11 ghi nhận Tài chấp nhận giữ trạng thái theo permission hiện hành. Lượt này xác nhận lại hành vi và không tự sửa policy hoặc tài liệu nghiệp vụ.
- **F-W10-02 — Route Phân công bị từ chối tại giao diện:** E2E kiểm quyền Sales/Care/Data Staff trên màn hình Phân công theo evidence hiện có; trang hiển thị trạng thái không có quyền tại chỗ. Đây là hành vi đã được chấp nhận trong evidence W10 ngày 05/11, không phải lỗi backend scope.
- 13 `DemoAccountAcceptanceTest` không chạy do thiếu `POSE_DEMO_PASSWORD`; không có mật khẩu demo nào được nhập hoặc lưu. Các test quyền còn lại và E2E đã chạy đạt.
- Chrome DevTools MCP không được cấu hình trong phiên; dùng runner Playwright Chromium của repo thay thế. Không chạy kiểm thử thủ công bằng profile trình duyệt cá nhân.

## Kết luận

**W10 E2E đa vai trò và rà soát phạm vi truy cập đạt trong phạm vi đã kiểm:** nhóm H2 161/161, PostgreSQL full verify 291 test không failure/error (13 demo acceptance skip), E2E Chromium 31/31. Không phát hiện regression cần sửa trong lượt này. Giữ lại finding Opportunity đã biết để nhóm đối chiếu khi cập nhật ma trận quyền; không tự đổi quyền Customer Care.

Tham chiếu: [E2E W10 ngày 05/11](e2e-w10-2026-11-05.md), [hardening W10](hardening-w10-2026-11-05.md), [ma trận vai trò/quyền](../analysis/roles-authorization-v1.md), [DEC-12](../meetings/decision-log.md).
