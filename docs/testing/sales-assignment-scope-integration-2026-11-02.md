# Integration Sales–Offering scope — 02/11/2026

**Trạng thái sau khắc phục:** FINDING-1 và FINDING-2 đã được sửa theo quyết định của Tài; kết quả chạy lại và giới hạn được ghi ở mục Khắc phục bên dưới. Phần số liệu và FINDING ban đầu được giữ làm lịch sử.

Nguồn: [Acceptance Criteria và TC](acceptance-criteria-sales-offering-first-flow-v1.md), [Sales Assignment API](../api/sales-assignment-api-v1.md), Offering API, Business Rules, Roles/Authorization, DEC-08–10. DEC-10 chọn Google Doc làm baseline UC/Class Diagram; lượt này chưa đối chiếu trực tiếp Google Doc, không khẳng định repo đã đồng bộ.

Môi trường: nhánh `Tài` tại `D:\POSE`, commit gốc `e24e5d2`; JDK 25.0.2 target Java 21, Maven 3.9.15, PowerShell 5.1.26100.9711, PostgreSQL 18.3. Test dùng MockMvc/JWT fixture, `MutableClock`, không dùng giờ thật. `V1_1__auth_test_crm_open_work.sql` chỉ ở H2 test baseline cung cấp ba bảng tối thiểu cho open-work; PostgreSQL production-schema dùng các bảng V1 thật. Scheduler được trì hoãn 86400000 ms; expiry test gọi service trực tiếp.

## Kiểm chứng ban đầu (lịch sử)

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

Các method sau thuộc `SalesAssignmentIntegrationTest` trừ UI. Kết quả gốc được giữ để truy vết; các test FINDING-1 đã bật và chạy lại theo mục Khắc phục.

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
| Membership kết thúc sau phân công | `salesLosesOfferingAccessWhenMembershipEnds`, `salesCannotReadOfferingDetailWhenMembershipEnds` — đã bật và kiểm lại sau sửa FINDING-1 |

| AC | Truy vết / kết luận |
|---|---|
| 01 | TC-01, TC-12 đạt API/audit; thông báo UI chưa kiểm |
| 02 | TC-02 đạt |
| 03 | TC-09/10 đạt trong phạm vi case |
| 04 | TC-01/03/04/05/06/07/08 và test membership/team ở mục Khắc phục đạt trong phạm vi kiểm |
| 05 | TC-01 đạt API; TC-11 đạt RTL chỉ đọc |
| 06 | TC-03/04 và các test membership/team ở mục Khắc phục đạt trong phạm vi kiểm |
| 07 | TC-08 đạt |
| 08 | TC-05/06/07 đạt |
| 09 | TC-11 đạt API và RTL Offering |
| 10 | TC-02/09/10/11/13 và validation kiểm row/audit không đổi; không chứng nhận mọi tổ hợp bị từ chối |

## FINDING-1 — Sales vẫn đọc Offering sau khi rời team (lịch sử trước sửa)

- **Mức độ:** cao, ảnh hưởng kiểm soát phạm vi dữ liệu; AC-04/06, liên quan TC-03/04 và thay đổi membership sau TC-01.
- **Kỳ vọng:** `acceptance-criteria-sales-offering-first-flow-v1.md:47` yêu cầu cùng team; dòng 49 yêu cầu 403 ngoài scope. `sales-assignment-api-v1.md` mục 1 yêu cầu membership hiệu lực đúng team.
- **Thực tế H2:** có Offering ACTIVE và assignment ACTIVE, sau đó kết thúc membership Sales A. `GET /api/v1/offerings` vẫn trả item chứa ID/mã/tên; `GET /api/v1/offerings/{id}` trả **200** thay vì **403 FORBIDDEN_SCOPE**. Hai test đã chạy riêng và thất bại trước khi disabled. Chưa bật lại hai test lỗi trên PostgreSQL để xác minh riêng ca này.
- **Dấu vết:** `backend/src/main/java/com/pose/backend/offering/OfferingService.java:58` lọc list theo assignment/thời gian nhưng không kiểm membership; detail tại dòng 278 gọi `OfferingRepository.java:192`, truy vấn cũng không kiểm membership. Kiểm eligibility lúc tạo tại `SalesAssignmentService.java:281` không đảm bảo membership còn hiệu lực sau đó.
- **Đề xuất:** nhóm xác nhận AC-04/06 so với Google Doc DEC-10; sau đó kiểm membership ACTIVE, thời gian, team/tenant trong list và detail hoặc chốt cơ chế thu hồi assignment khi membership kết thúc. Cần kiểm tác động lịch sử; không tự sửa production hoặc contract trong lượt này.

## FINDING-2 — Assignment ACTIVE chồng khoảng (review)

Review phát hiện tạo/gia hạn cho phép nhiều assignment ACTIVE chưa thu hồi có khoảng giao nhau cùng organization, Offering và Sales. D2 của Tài quy định kiểm khoảng đóng, `effectiveTo=null` là vô hạn; bản ghi EXPIRED/REVOKED không chặn. Nếu giao, API trả `409 SALES_ASSIGNMENT_OVERLAP` và không ghi row hoặc audit. Không trả ID assignment kia.

## Khắc phục FINDING-1 và FINDING-2

- **D1:** `OfferingRepository.java:16` định nghĩa điều kiện SQL dùng chung cho list (`OfferingService.java:59`) và detail (`OfferingRepository.java:207`): assignment ACTIVE chưa thu hồi, đúng Sale và thời gian; membership ACTIVE đúng team/tenant, đã bắt đầu và chưa kết thúc; team và Offering ACTIVE. Team null không thỏa. Không thu hồi assignment tự động.
- **D2:** `SalesAssignmentRepository.java:122` kiểm khoảng giao; `SalesAssignmentService.java:97` và `:133` dùng khi tạo/gia hạn. Cả hai gọi `FoundationAccess.lockAndRefresh` trước khi kiểm; khóa row organization được giữ đến hết transaction. Test đồng thời kiểm đúng một request 201, một 409, một row và một audit. Frontend ánh xạ mã lỗi sang thông báo tiếng Việt.
- Không thêm migration; lần chạy backend kế tiếp trên Supabase không làm thay đổi schema **do hai fix này**. Lượt này không kết nối Supabase.

| Kiểm thử mới hoặc bật lại | Nội dung |
|---|---|
| `salesLosesOfferingAccessWhenMembershipEnds`, `salesCannotReadOfferingDetailWhenMembershipEnds` | Bật lại hai test FINDING-1 |
| `salesMembershipLeftAtBoundaryRemovesReadAccess`, `futureMembershipGrantsAccessAtJoinedAt` | Biên `leftAt` và `joinedAt` với `MutableClock` |
| `inactiveOwningTeamRemovesReadAccessWithoutRevocation`, `salesMovingToAnotherTeamLosesOldOfferingAccess`, `legacyOfferingWithoutTeamIsHiddenFromSales` | Team inactive/chuyển team/team null; giữ lịch sử assignment |
| `overlappingCreateRejectsClosedBoundaryAndOpenEndedIntervals`, `upcomingAssignmentBlocksOverlappingCreate` | Chặn tạo chồng, chạm biên, vô hạn và phân công tương lai |
| `nonOverlappingCreateAfterEndSucceeds`, `revokedAndExpiredAssignmentsDoNotBlockCreate`, `sameOfferingCanBeAssignedToDifferentSales` | Tạo thành công khi không giao, trạng thái cũ không chặn hoặc Sales khác |
| `extensionRejectsOverlapButAllowsNonOverlappingEnd` | Gia hạn giao bị từ chối, không giao được phép |
| `organizationLockSerializesConcurrentOverlappingCreates` | Hai request đồng thời: một 201, một 409 cùng mã overlap, một row/audit |
| `tc13AuditFailuresRollbackAllMutations` | Fixture create dùng Offering khác, chứng minh lỗi audit vẫn rollback |

`OfferingIntegrationTest` được bổ sung membership hợp lệ cho fixture Sales cũ theo D1, không đổi kỳ vọng. Các kiểm thử này dùng test fixture, không phải tài khoản demo thật.

Chạy lại ngày 02/11/2026 bằng JDK 25.0.2 target 21, Maven 3.9.15, Windows PowerShell 5.1 và PostgreSQL 18.3; commit nền `daa3a41`. H2 và PostgreSQL dùng lệnh ở phần Kiểm chứng ban đầu, với các biến `POSE_TEST_*`, `POSE_V2_SMOKE_DATABASE_URL`, `POSE_DEMO_PASSWORD` đã xóa khỏi process; runner PostgreSQL không dùng `-RequireAcceptance`.

| Bộ kiểm sau sửa | Tổng | Đạt | Skip | Failure/error | So với mốc |
|---|---:|---:|---:|---:|---|
| H2, surefire XML | 107 | 92 | 15 | 0 | +14 đạt so với 78; 13 demo và 2 restore smoke skip |
| PostgreSQL production schema, surefire XML | 107 | 94 | 13 | 0 | +14 đạt so với 80; 13 demo skip |
| Frontend Vitest | 133 | 133 | 0 | 0 | Bằng mốc 133; không thêm test frontend trong phạm vi file được giao |

`npm run lint`, `npm run typecheck`, `npm test` đạt; frontend chỉ bổ sung ánh xạ thông báo. Runner PostgreSQL in `Verified.` và đã dừng cluster, cổng 54329 không còn lắng nghe. Không còn test `@Disabled` do FINDING. AC-04/AC-06 đạt trong các trường hợp được kiểm H2 và PostgreSQL; chưa thay cho E2E hoặc đối chiếu Google Doc.

## Kiểm chứng bổ sung sau review (02/11/2026)

| Hạng mục | Kết quả |
|---|---|
| Acceptance tài khoản demo (Tài nhập mật khẩu trong terminal, runner `-RequireAcceptance`) | `Verified.`; PostgreSQL 121/121 đạt, 0 skip; `DemoAccountAcceptanceTest` 27/27 |
| Đối chiếu Google Doc UC-04 (DEC-10) | Không mâu thuẫn với D1/D2; khác biệt thiết kế ghi trong decision log |
| Supabase | Tài sao lưu bằng `pg_dump`, chạy backend: Flyway áp V5–V7 |
| V8 gán Offering cũ vào team `SALES` | H2 109 test: 93 đạt/16 skip/0 lỗi; PostgreSQL 109 test: 96 đạt/13 skip/0 lỗi; trên bản restore 26/26 Offering thuộc team `SALES` đúng tổ chức |

## Giới hạn

Chưa E2E trình duyệt thật; RTL đã có chỉ kiểm UI Offering khi thiếu quyền quản lý. Agent không kết nối Supabase/5432; acceptance demo và migration Supabase do Tài chạy (mục Kiểm chứng bổ sung). Không chứng nhận mọi tổ hợp membership/thời điểm/audit; ca đồng thời đã kiểm hai request create cùng cặp/khoảng. Business rule và AC không bị sửa trong lượt khắc phục.
