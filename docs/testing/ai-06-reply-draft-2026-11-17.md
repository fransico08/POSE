# AI-06 — Bản nháp trả lời trong hộp thư

Ngày: 17/11/2026 · Nhánh: `Tài` · Chủ trì: Huỳnh Minh Tài · Trạng thái: đã kiểm thử kỹ thuật, chờ Tài review.

## Thiết kế và quyền

Theo DEC-25, V16 mở rộng subject invocation thành `CONVERSATION`; suggestion không có CHECK subject riêng ở V12. Migration Java tra cứu CHECK theo nội dung để tương thích tên ràng buộc phát sinh tự động ở H2 và PostgreSQL 18; không sửa V1–V15. Quyền `ai.assist.use`, `conversation.reply` và scope hội thoại được kiểm trước khi gọi gateway; Data Staff bị từ chối kể cả khi kiêm vai trò. Hội thoại đóng trả `409 CONVERSATION_CLOSED`.

Context gồm tối đa 10 tin mới nhất theo thời gian; profile chỉ được thêm khi actor đọc được. Redactor che giá trị chuỗi, giữ ID nguồn. Gateway lọc nguồn tin, Interaction và Opportunity theo context, từ chối đầu ra không có nguồn hoặc chứa liên hệ/token che. Prompt yêu cầu không cam kết thông tin không có căn cứ. Dùng hoặc sửa nháp chỉ ghi nhận duyệt rồi điền nội dung, không gửi tin tự động.

## Kiểm chứng

- Xóa ba thư mục build trong checkout bị kiểm duyệt tự động từ chối (`blocked by policy`); dùng bản sao sạch từ Git archive, chép đúng các file đang thay đổi, không xóa cluster gốc.
- Không đặt `POSE_DEMO_PASSWORD`, không kết nối Supabase hay PostgreSQL 5432, không gọi Gemini/Claude thật.
- Vitest: 366/366; typecheck và lint đạt.
- H2 bản sao sạch: 387 test, 359 đạt, 28 bỏ qua, 0 lỗi; JDK 25.0.2, Maven 3.9.15; lệnh `mvn -pl backend -am -Dlogging.level.root=ERROR verify`.
- PostgreSQL bản sao sạch: 387 test, 374 đạt, 13 bỏ qua, 0 lỗi; `test-postgres-v2.ps1` báo `Verified`; `SHOW data_directory` khớp cluster cô lập, cổng 54329 đã dừng. Không đặt `POSE_DEMO_PASSWORD`.
- Next.js 16.4.6: typecheck, lint, Vitest 366/366 và `npm run build` đạt. `scripts/e2e-env.ps1 -Build` chạy E2E **35/35**, khoảng 1,2 phút; gồm `font-assets.spec.ts` và `inbox.spec.ts` (soạn nháp, dùng nháp, kiểm tra chưa gửi tin, tự bấm gửi, reflow 375px). Script xác nhận các cổng 8080, 8081, 3010, 3011 và 54329 đã dừng.

### Bản vá bảo mật Next.js

Lượt đầu với Next.js 16.4.3 dừng build trước E2E:

```text
Error [SecurityFatalError]: Your version of Next.js is affected by a known security vulnerability.

**We strongly recommend you upgrade Next.js.**
```

Theo quyết định của Tài, commit `c5b0893` ghim `next` và `eslint-config-next` lên 16.4.6; chỉ các gói `@next/*` đi kèm bắt buộc thay đổi theo. Không chạy `next upgrade`, codemod hay nâng các dependency độc lập khác. `npm audit` trước nâng ghi 9 advisory (1 high, 8 moderate); [GHSA-mfr8-8qww-v76h](https://github.com/advisories/GHSA-mfr8-8qww-v76h) ảnh hưởng `>=16.3.0 <=16.4.4`, nên 16.4.6 nằm ngoài khoảng. Sau nâng, `npm audit` không còn advisory cho Next.js, còn 8 advisory (1 high cho `undici` gián tiếp và 7 moderate); `npm audit --omit=dev` báo 0. Không sửa các dependency còn lại. Rà mã JavaScript/TypeScript trong repo không thấy `use cache` hoặc `unstable_cache`.

## Kiểm tra với MockProvider và giới hạn

Bộ kiểm thử dùng hội thoại giả, kiểm tra nguồn thực và loại nguồn bịa, che số điện thoại/email, không gửi tin khi dùng nháp và chặn output chứa token che. Đây là kiểm tra cơ chế bằng mock, chưa đánh giá chất lượng hay khả năng tránh khẳng định thiếu căn cứ của model thật; chưa kiểm thử Gemini/Claude thật.

Nội dung tin khách đi vào prompt nên vẫn có rủi ro prompt injection; bản nháp luôn cần người dùng duyệt và chỉ được gửi khi người dùng bấm gửi. Chuyển kiểm thử an toàn đối với prompt injection sang AI-11.

Một lượt PostgreSQL đầu thất bại vì PostgreSQL 18 đưa cả NOT NULL vào information_schema CHECK; đã xác minh chỉ đọc trên cluster test và sửa V16 chỉ chọn CHECK chứa CUSTOMER/LEAD. Đã chạy lại trên bản sao sạch và đạt Verified.

Kiểm thủ công MockProvider trên ba context giả: câu hỏi về giá, yêu cầu hỗ trợ và yêu cầu hẹn. Cả ba trả cùng lời cảm ơn trung tính kèm mã nguồn của tin; không thấy khẳng định về giá, khuyến mãi hay thời hạn. Câu mẫu chỉ kiểm cơ chế, không đánh giá mức trả lời đúng câu hỏi của model thật. Lượt đầu câu mock khẳng định đã nhận câu hỏi ngay cả với yêu cầu hỗ trợ; đã bỏ khẳng định đó và kiểm lại ba mẫu.

Khi kiểm PostgreSQL, GET danh sách gợi ý từng lỗi do kiểm scope hội thoại khóa organization trong transaction chỉ đọc. Đã điều chỉnh transaction của danh sách và bổ sung ca lấy suggestion theo người tạo trong `ReplyDraftIntegrationTest`.

## Truy vết kiểm thử

| Kịch bản | Kiểm tra |
|---|---|
| Tạo nháp có nguồn, che liên hệ, tái sử dụng | `ReplyDraftIntegrationTest.createsRedactedSourcedDraftAndReusesOnlyForSameActor` |
| Loại nguồn bịa, từ chối kết quả không nguồn hoặc token che | `removesInventedSourcesAndRejectsAnUnsourcedDraft` |
| Sales/Care ngoài scope, Data Staff kiêm role, hội thoại đóng | `rejectsOtherSalesDataStaffAndClosedConversation` |
| Gợi ý chỉ người tạo được xem và duyệt | `scopeAndCreatorRemainEnforcedWhenConversationIsReassigned` |
| Hồ sơ gắn có/không có scope | `visibleLinkedProfileIsIncluded`, `inaccessibleLinkedProfileIsOmittedFromContext` |
| Thiếu quyền, tenant khác, Manager khác team | `missingPermissionAndForeignTenantAreDenied`, `managerOfAnotherTeamCannotDraftEvenWhenConversationExists` |
| Tắt AI, hết hạn mức, timeout và không tự gửi | `disabledAndBudgetBlockedKeepManualMessagesAvailable`, `providerFailureDoesNotSendAnyMessage` |
| Phân quyền giao diện, sử dụng/sửa/từ chối, hủy khi đổi hội thoại | `frontend/tests/inbox-copilot.test.tsx` |

Các test có kết quả theo số liệu bộ kiểm thử phía trên; không suy diễn chất lượng AI thật từ kết quả mock.

## Fix-up: kiểm lịch sử Flyway cho setup Supabase

`setup-supabase-v2.ps1` dùng chung `Get-PoseMigrationVersions`: lấy version từ migration SQL và Java, thêm baseline 1, sắp xếp theo số và dừng nếu version trùng. Hàm chạy độc lập không kết nối database; PowerShell 5.1 liệt kê `1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16`. Fixture độc lập kiểm `1,2,10,16` và từ chối version 2 trùng giữa SQL và Java. Parser PowerShell 5.1 đạt.

Rà các script trong `scripts/`, `backend/scripts/`, `database/` và tài liệu theo đường dẫn migration, mẫu tên version và `flyway_schema_history`: không tìm thấy script nào khác tự dựng danh sách chỉ từ SQL; `scripts/dev.ps1` chỉ kiểm tra sự tồn tại của bảng history. Đã cập nhật mốc vận hành cũ trong `backend/README.md`, `database/README.md` và `docs/huong-dan-chay-local.md`; các evidence có ngày giữ nguyên làm lịch sử. Data Dictionary chưa được sửa; Nhi/Văn cần ghi V16 và CHECK `ck_ai_invocation_subject_type` bổ sung `CONVERSATION`.

Đã chạy `backend/scripts/test-postgres-v2.ps1` trên PostgreSQL 18 ở cluster `D:/POSE/backend/target/auth-postgres-test`; xác nhận `SHOW data_directory`, không đặt `POSE_DEMO_PASSWORD`. Runner báo `Verified`; Surefire có **387 test, 374 đạt, 13 bỏ qua, 0 lỗi** (49 report đều mới). Trên database regression `pose_foundation_test_20261117143017235`, truy vấn chỉ đọc `SELECT string_agg(version,',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success` trả `1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16`, khớp chính xác kết quả của hàm. Đã dừng cluster sau đối chiếu. Không kết nối Supabase hay cổng 5432; không chạy lại H2/E2E vì không đổi Java hoặc frontend.
