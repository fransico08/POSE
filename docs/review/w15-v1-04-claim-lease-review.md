# W15-V1-04: Claim/lease review patch

## Trạng thái

`NOT APPLIED`, `NOT TESTED`, cần Tài và schema owner duyệt. Đây là specification reviewable, không phải migration hay source patch đã áp dụng.

## Kết luận

Không có fix an toàn chỉ bằng code hiện tại. V15 không có trạng thái claim/lease; lock transaction xuyên provider giữ connection lâu và không có crash recovery. Fix bền vững cần migration mới sau V15, vì vậy không thuộc thay đổi Nhi tự áp dụng.

## Patch Tài phải review

1. Migration mới sau V15 tạo `signal_extraction_claims` với PK `(organization_id, message_id)`, FK composite tới `conversation_messages`, `claim_token`, `claimed_at`, `expires_at`, check `expires_at > claimed_at` và index `(expires_at)`.
2. Thêm `SignalExtractionClaimRepository`: acquire bằng `UPDATE ... WHERE expires_at <= now` rồi `INSERT`; cả hai thao tác dùng `(organization_id, message_id)`. Trả token ngẫu nhiên cho owner.
3. `CustomerSignalService.extract` chỉ gọi Gateway khi acquire thành công; claim sống không tạo invocation và vẫn được tính `remaining`. Không thêm status API mới khi chưa có contract/frontend review.
4. Release/complete phải `WHERE organization_id=? AND message_id=? AND claim_token=?`; owner cũ không được xóa claim đã reclaim. Lease phải dài hơn provider timeout hoặc có renew.
5. Success vẫn dựa vào unique success V15. Timeout/network/invalid-output release claim sau khi ghi attempt. Crash để lease hết hạn rồi reclaim. Không thể cam kết exact-once provider call qua crash nếu provider không hỗ trợ idempotency key.

## Files Tài phải thay đổi sau approval

- New: `backend/src/main/resources/db/migration/V16__signal_extraction_claims.sql`
- New: `backend/src/main/java/com/pose/backend/ai/SignalExtractionClaimRepository.java`
- Modify: `backend/src/main/java/com/pose/backend/ai/CustomerSignalService.java`
- Modify if needed for token-safe completion: `backend/src/main/java/com/pose/backend/ai/SignalPersistence.java`
- New or modify: `backend/src/test/java/com/pose/backend/auth/CustomerSignalConcurrencyIntegrationTest.java`
- Update versioned verifier only after schema approval; do not weaken V15 verifier.

## Required MockProvider tests

Same message/tenant: one call; successful retry: zero new calls; transient failure: release then retry; expired claim: reclaim; tenant isolation: same message UUID across organizations; stale-token release: no deletion; accept/edit race: one terminal audit.
