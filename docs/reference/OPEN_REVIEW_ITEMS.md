# Open Review Items — do not silently treat as locked

Theo review mới nhất trong repo, Business Rules và Domain Class Diagram vẫn ở trạng thái Review. Vì vậy package này là **review-ready candidate**, không phải bằng chứng rằng nhóm đã khóa mọi enum/schema choice.

## Nhóm enum đang chờ đóng review

Review của Nhi yêu cầu **giữ nguyên/chờ nhóm chốt** cho bộ enum liên quan:

- Lead status
- CRM Task status
- Offering status
- Sales Assignment status

Bản candidate v2 hiện dùng các giá trị đang có trong Business Rules hiện hành để migration/seed có thể chạy đồng bộ:

- Lead: `NEW`, `QUALIFIED`, `CONVERTED`, `DISQUALIFIED`
- Task: `OPEN`, `IN_PROGRESS`, `DONE`, `CANCELLED`
- Offering: `DRAFT`, `ACTIVE`, `INACTIVE`
- Assignment: `ACTIVE`, `EXPIRED`, `REVOKED`

**Không merge như “enum đã chốt” nếu owner Business Rules chưa đóng review.** Khi nhóm chốt khác, sửa đồng thời Data Dictionary + migration + seed + validator + verify.

## Các lựa chọn schema từ Domain Class Diagram đang Review

Candidate v2 hiện dùng lựa chọn đang được sơ đồ thể hiện:

1. Attribute Definition ở cấp Category.
2. Offering Price lưu lịch sử hiệu lực.
3. Lead bắt buộc một Offering.
4. Visibility áp dụng cho Interaction và Feedback.
5. Lead conversion rebind activity/task/opportunity/source link về Customer ở final state.

Các mục này được tách rõ để nhóm có thể đổi mà không nhầm là DEC-01 → DEC-05.
