# F-01 — Chuyển hội thoại khi chuyển Lead

Ngày 18/11/2026; nhánh `Tài`. Theo DEC-30, Lead convert chuyển các hội thoại và tín hiệu cùng tenant trong transaction, ghi audit chỉ gồm ID; không thay người được giao, trạng thái duyệt hay invocation. Gợi ý AI của Lead giữ nguyên. Không thêm migration. API link thủ công vẫn trả 409 khi đổi hồ sơ.

`ConversationInteractionSessionService` thuộc phần AI-02 của Thắng đã được sửa: với hội thoại cũ còn trỏ Lead CONVERTED, dùng Customer đích; khi cập nhật Interaction cũ không xóa Customer đã có. Câu SQL dùng kiểu UUID tường minh cho cả H2 và PostgreSQL. Scope hội thoại giữ DEC-23; Customer 360, tín hiệu và context AI vẫn kiểm scope Customer riêng theo quyết định bổ sung của Tài.

## Hồi quy

| Kịch bản | Test |
|---|---|
| Nhiều hội thoại, tín hiệu PROPOSED/ACCEPTED, Customer 360, trả lời trong phiên và E02 | `LeadConversionIntegrationTest.conversionMovesConversationsSignalsAndPreservesInteractionAfterMoreMessages` |
| Customer đã tồn tại và rollback khi audit chuyển tín hiệu lỗi | `conversionReusesCustomerAndRollsBackWhenSignalAuditFails` |
| Hội thoại cũ trỏ Lead đã chuyển, phiên cũ và phiên mới hơn bốn giờ | `legacyConversationResolvesConvertedLeadForNewSessions` |
| Hội thoại chưa liên kết, tham số Customer null | `unlinkedConversationCanReuseInteractionWithNullCustomer` |
| Lead khác, hội thoại tenant khác không bị chuyển | `conversionLeavesOtherLeadsAndTenantsUntouched` |
| E03/E04 không đổi sau cùng một lượt convert | `conversionDoesNotChangeHandoverOrFirstResponseMetrics` |
| Sales được giao hội thoại nhưng Customer ngoài scope, reply-draft không chứa Customer | `assignedSalesKeepsConversationButCannotReadOtherSalesCustomerOrUseItsContext` |
| Hộp thư không hiển thị Customer ngoài scope nhưng vẫn cho trả lời | `frontend/tests/inbox.test.tsx` |
| Link thủ công không thay hồ sơ | `ConversationApiIntegrationTest.linkRejectsAnyReplacementAndLeavesSystemInteractionUntouched` |
| Hộp thư tạo Lead, convert, gửi tiếp và xem Customer 360 | `frontend/e2e/inbox.spec.ts` |

## Kiểm chứng

- H2: `mvn -pl backend -am -Dlogging.level.root=ERROR verify` trên bản sao sạch `D:/POSE-data/pose-f01-h2-final4-20261118`; Surefire backend 462 tổng, 433 đạt, 29 bỏ qua, 0 failure/error. Không đặt `POSE_DEMO_PASSWORD`.
- PostgreSQL: `backend/scripts/test-postgres-v2.ps1` trên bản sao sạch `D:/POSE-data/pose-f01-pg-final4-20261118`, không đặt mật khẩu demo; Surefire backend 462 tổng, 449 đạt, 13 bỏ qua, 0 failure/error; có `Verified.`. `SHOW data_directory` khớp `D:/POSE-data/pose-f01-pg-final4-20261118/backend/target/auth-postgres-test`; server đã dừng. Lượt ban đầu trước khi thêm ép kiểu UUID thất bại trên PostgreSQL, không dùng làm kết quả cuối.
- Lệnh xóa ba thư mục build trong checkout chính bị chính sách của môi trường từ chối; không chạy `mvn clean`, không xóa cluster. Đã dùng checkout sạch để chạy H2 và PostgreSQL.
- Typecheck và lint đạt; Vitest 375/375. Không gọi AI thật, không đặt mật khẩu demo, không kết nối Supabase hay 5432.
- `scripts/e2e-env.ps1 -Build` chạy riêng: 37/37 đạt, khoảng 1,1 phút; ca hộp thư `fcac93a` và `00e97f6` đã gửi cả OUTBOUND lẫn INBOUND ZALO_SIM sau convert, mở Customer 360 có Interaction và tín hiệu. Runner báo các cổng 8080, 8081, 3010, 3011, 54329 đã dừng.

Fixture tenant khác trong ca cô lập là hội thoại chưa gắn Lead; test phiên cách hơn bốn giờ gọi trực tiếp service attach, không giả lập webhook ở thời điểm đó. E2E có webhook INBOUND sau convert trong phiên hiện tại. Các suite Dashboard/E04 và link thủ công hiện có cũng được chạy cùng regression.

## Hardening thứ tự khóa — 18/11/2026

Khóa các hội thoại của Lead theo ID ngay sau khóa Lead và trước khi chuyển Interaction; cập nhật và audit vẫn dùng danh sách đã khóa. Test `conversionLocksConversationsBeforeMovingInteractions` theo dõi các lệnh JDBC thực chạy và kiểm tra kết quả chuyển cùng audit. Test `convertedConversationStartsNewCustomerSessionAfterStrayLeadInteraction` dựng Interaction SYSTEM còn trỏ Lead sau convert rồi kiểm phiên mới sau hơn bốn giờ gắn Customer; bản ghi lạc cũ không bị sửa.

- H2 trên bản sao sạch `D:/POSE-data/pose-f01-lock-h2-20261118`: 464 test, 435 đạt, 29 bỏ qua, 0 failure/error.
- PostgreSQL 54329 trên bản sao sạch `D:/POSE-data/pose-f01-lock-pg-20261118`: 464 test, 451 đạt, 13 bỏ qua, 0 failure/error; runner báo `Verified.` và dừng cluster. `SHOW data_directory` khớp thư mục `backend/target/auth-postgres-test` của bản sao. Không đặt `POSE_DEMO_PASSWORD`.
- Test kiểm thứ tự lời gọi JDBC và kết quả dữ liệu; chưa chạy stress test webhook đồng thời với convert. Không sửa giao diện, không chạy lại E2E.
