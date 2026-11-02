# Integration Sales–Offering scope — 02/11/2026

**Card chưa đạt AC-04/AC-06:** FINDING-1 cho thấy Sales vẫn đọc được Offering sau khi rời team. Hai test kỳ vọng đúng AC được giữ `@Disabled`; suite xanh có skip không có nghĩa FINDING đã được sửa.

Nguồn: [Acceptance Criteria và TC](acceptance-criteria-sales-offering-first-flow-v1.md), [Sales Assignment API](../api/sales-assignment-api-v1.md), Offering API, Business Rules, Roles/Authorization, DEC-08–10. DEC-10 chọn Google Doc làm baseline UC/Class Diagram; lượt này chưa đối chiếu trực tiếp Google Doc, không khẳng định repo đã đồng bộ.

Môi trường: nhánh `Tài` tại `D:\POSE`, commit gốc `e24e5d2`; JDK 25.0.2 target Java 21, Maven 3.9.15, PowerShell 5.1.26100.9711, PostgreSQL 18.3. Test dùng MockMvc/JWT fixture, `MutableClock`, không dùng giờ thật. `V1_1__auth_test_crm_open_work.sql` chỉ ở H2 test baseline cung cấp ba bảng tối thiểu cho open-work; PostgreSQL production-schema dùng các bảng V1 thật. Scheduler được trì hoãn 86400000 ms; expiry test gọi service trực tiếp.

## Kiểm chứng

```powershell
# backend: đặt JAVA_HOME tới JDK 25; xóa các biến test URL/demo kế thừa khỏi process
& 'C:\Users\Dell\.maven\maven-3.9.15\bin\mvn.cmd' '-Dlogging.level.root=ERROR' verify
powershell.exe -NoProfile -File .\scripts\test-postgres-v2.ps1 `
  -DataPackagePath 'D:\POSE-data\POSE_CRM_DATA_V2_FINAL' `
  -JdkDirectory 'C:\Program Files\Java\jdk-25.0.2' `
  -MavenPath 'C:\Users\Dell\.maven\maven-3.9.15\bin\mvn.cmd'
# frontend: npm run lint; npm run typecheck; npm test
```

| Bộ kiểm | Tổng | Đạt | Skip | Failure/error |
|---|---:|---:|---:|---:|
| H2, surefire XML | 95 | 78 | 17 | 0 |
| PostgreSQL production schema, surefire XML | 95 | 80 | 15 | 0 |
| Frontend Vitest/RTL | 133 | 133 | 0 | 0 |

H2 skip: 13 acceptance demo, 2 smoke restore, 2 FINDING-1. PostgreSQL skip: 13 acceptance demo, 2 FINDING-1; 2 migration tests trong suite PostgreSQL dùng H2. So mốc PostgreSQL 61 đạt/13 skip/0 lỗi: +19 đạt, +2 skip FINDING; frontend so mốc 131: +2 đạt. Lint/typecheck đạt. Runner in `Verified.`, TASK 5 PASS, foundation assertions đạt và báo 36 user/credentials không đổi; cluster riêng đã dừng. DB test được runner giữ để truy vết.

## Truy vết TC

Các method sau thuộc `SalesAssignmentIntegrationTest` trừ UI. Đạt là cho kịch bản đã kiểm, không bao hàm FINDING-1.

| TC | Test và kết quả |
|---|---|
| 01 | `tc01ManagerCreatesAndAssignedSalesReads` — đạt |
| 02 | `tc02CrossTeamSaleDeniedEvenWhenManagerManagesBoth` — đạt |
| 03 | `tc03UnassignedOfferingIsHidden` — đạt |
| 04 | `tc04OtherSalesAssignmentIsHidden` — đạt |
| 05 | `tc05FutureAssignmentBecomesVisibleOnStart` — đạt |
| 06 | `tc06ExpiredTimeDeniesAccessBeforeJob` — đạt |
| 06b | `tc06bExpiryJobWritesSystemAuditOnce` — đạt |
| 07 | `tc07RevocationRemovesSalesAccess` — đạt |
| 08 | `tc08InactiveOfferingIsHiddenWithoutRevokingAssignment` — đạt |
| 09 | `tc09ManagerCannotAccessOtherTeamAssignments` — đạt list/detail/create/extend/revoke/candidates |
| 10 | `tc10ForeignTenantAndMissingIdsReturnCommonScopeError` — đạt |
| 11 | `tc11SalesCannotMutateOfferingsOrAssignments` — đạt API; `offering-sales-readonly.test.tsx` — 2 ca RTL đạt |
| 12 | `tc12CreateExtendRevokeEachWritesUserAudit` — đạt |
| 13 | `tc13AuditFailuresRollbackAllMutations` — đạt 409 CONFLICT, rollback create/extend/revoke |

| Ca bổ sung | Test và kết quả |
|---|---|
| Open work Lead/Opportunity/Task, revoke 409 | `openWorkListsLeadOpportunityAndTaskAndPreventsRevocation` — đạt 3 mục |
| Version cũ, rút ngắn, ngày sai, đã thu hồi | `conflictsAndValidationPreserveAssignment` — đạt |
| Candidates đúng team | `candidatesOnlyIncludeEligibleSalesInOfferingTeam` — đạt; teamId thừa bị bỏ qua và không đổi kết quả, không phải 400 |
| Offering legacy team null | `legacyOfferingWithoutTeamIsRejected` — đạt |
| Team INACTIVE | `inactiveTeamCannotReceiveAssignment` — đạt |
| Membership kết thúc sau phân công | `salesLosesOfferingAccessWhenMembershipEnds`, `salesCannotReadOfferingDetailWhenMembershipEnds` — FINDING-1, disabled |

| AC | Truy vết / kết luận |
|---|---|
| 01 | TC-01, TC-12 đạt API/audit; thông báo UI chưa kiểm |
| 02 | TC-02 đạt |
| 03 | TC-09/10 đạt trong phạm vi case |
| 04 | TC-01/03/04/05/06/07/08 đạt; **FINDING-1 chưa đạt điều kiện cùng team sau thay đổi membership** |
| 05 | TC-01 đạt API; TC-11 đạt RTL chỉ đọc |
| 06 | TC-03/04 đạt khi assignment thiếu/thuộc người khác; **FINDING-1 chưa đạt sau khi rời team** |
| 07 | TC-08 đạt |
| 08 | TC-05/06/07 đạt |
| 09 | TC-11 đạt API và RTL Offering |
| 10 | TC-02/09/10/11/13 và validation kiểm row/audit không đổi; không chứng nhận mọi tổ hợp bị từ chối |

## FINDING-1 — Sales vẫn đọc Offering sau khi rời team

- **Mức độ:** cao, ảnh hưởng kiểm soát phạm vi dữ liệu; AC-04/06, liên quan TC-03/04 và thay đổi membership sau TC-01.
- **Kỳ vọng:** `acceptance-criteria-sales-offering-first-flow-v1.md:47` yêu cầu cùng team; dòng 49 yêu cầu 403 ngoài scope. `sales-assignment-api-v1.md` mục 1 yêu cầu membership hiệu lực đúng team.
- **Thực tế H2:** có Offering ACTIVE và assignment ACTIVE, sau đó kết thúc membership Sales A. `GET /api/v1/offerings` vẫn trả item chứa ID/mã/tên; `GET /api/v1/offerings/{id}` trả **200** thay vì **403 FORBIDDEN_SCOPE**. Hai test đã chạy riêng và thất bại trước khi disabled. Chưa bật lại hai test lỗi trên PostgreSQL để xác minh riêng ca này.
- **Dấu vết:** `backend/src/main/java/com/pose/backend/offering/OfferingService.java:58` lọc list theo assignment/thời gian nhưng không kiểm membership; detail tại dòng 278 gọi `OfferingRepository.java:192`, truy vấn cũng không kiểm membership. Kiểm eligibility lúc tạo tại `SalesAssignmentService.java:281` không đảm bảo membership còn hiệu lực sau đó.
- **Đề xuất:** nhóm xác nhận AC-04/06 so với Google Doc DEC-10; sau đó kiểm membership ACTIVE, thời gian, team/tenant trong list và detail hoặc chốt cơ chế thu hồi assignment khi membership kết thúc. Cần kiểm tác động lịch sử; không tự sửa production hoặc contract trong lượt này.

## Giới hạn

Chưa E2E trình duyệt thật; RTL chỉ kiểm UI Offering khi thiếu quyền quản lý. Không kết nối Supabase/5432. Không chạy acceptance tài khoản demo vì không cấp mật khẩu; smoke restore không thay acceptance demo. Chưa đọc trực tiếp Google Doc DEC-10. Chưa kiểm đồng thời hoặc mọi tổ hợp membership/thời điểm/audit; test phải bổ sung sau khi nhóm quyết định FINDING-1. Không thay production, business rule hay contract.
