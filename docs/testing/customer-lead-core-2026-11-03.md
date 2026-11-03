# Kiểm thử Customer và Lead core — 03/11/2026

## Mục tiêu và môi trường

Kiểm UC-09/UC-10 theo trích yếu Tài cung cấp và DEC-11: validation, chuẩn hóa, scope theo role/tenant, trùng dữ liệu, trạng thái Lead và rollback audit. Commit nền: `e1a162a`, nhánh `Tài`; JDK 25.0.2 biên dịch target Java 21, Maven 3.9.15, Windows PowerShell 5.1.26100.9711, PostgreSQL 18.3 tại cluster test `127.0.0.1:54329`. H2 dùng `MODE=PostgreSQL`.

Lệnh chạy từ `backend`, sau khi xóa các biến `POSE_TEST_*`, `POSE_V2_SMOKE_DATABASE_URL`, `POSE_DEMO_PASSWORD` trong process:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'
& 'C:\Users\Dell\.maven\maven-3.9.15\bin\mvn.cmd' '-Dlogging.level.root=ERROR' verify
powershell.exe -NoProfile -File .\scripts\test-postgres-v2.ps1 `
  -DataPackagePath 'D:\POSE-data\POSE_CRM_DATA_V2_FINAL' `
  -JdkDirectory 'C:\Program Files\Java\jdk-25.0.2' `
  -MavenPath 'C:\Users\Dell\.maven\maven-3.9.15\bin\mvn.cmd'
```

## Kết quả

Số liệu đọc từ surefire XML sau mỗi lượt; XML PostgreSQL được cập nhật từ 08:45:59 ngày 03/11/2026.

| Bộ kiểm | Tổng | Đạt | Skip | Failure/error | So với mốc |
|---|---:|---:|---:|---:|---|
| H2 | 131 | 115 | 16 | 0 | Tăng 22 đạt so với 93; 13 demo và 3 smoke restore skip |
| PostgreSQL | 131 | 118 | 13 | 0 | Tăng 22 đạt so với 96; 13 demo skip |

`CustomerLeadIntegrationTest`: 21/21; `CustomerLeadProfileMigrationTest`: 1/1 trên H2 trong cả hai lượt. PostgreSQL chạy thêm `RestoredV2SmokeTest` 3/3, gồm assertion 120 Customer và 60 Lead còn nguyên, toàn bộ trường V9 mới null. Runner báo `TASK 5 PASS`, foundation assertions đạt và `Verified.`; cluster tự dừng, sau đó cổng 54329 không còn lắng nghe. Runner giữ lại các database test trong cluster cô lập để truy vết.

## Truy vết ca kiểm

Các method thuộc `CustomerLeadIntegrationTest` trừ khi ghi khác. Các method trong bảng đều đạt trên H2 và PostgreSQL, trừ ca 19 chỉ chạy trên bản restore PostgreSQL; test migration ở ca 18 dùng H2 trong cả hai lượt.

| Ca | Test |
|---|---|
| 1, 10 | `salesCreatesLeadAndInitialCustomerWithAudit` |
| 2 | `salesCannotCreateAgainstUnassignedInactiveOrOtherTeamOffering` |
| 3 | `validationAndPhoneNormalization` |
| 4, 11 | `duplicatesOnlyRevealReadableIdsAndAreNotCrossEntity`, `normalizedPhoneDuplicatesAreBlockedOnCreateAndPatch`, `duplicateCustomerConcurrentCreatesAreSerialized` |
| 5 | `salesLeadScopeRespondsToAssignmentAndMembershipChanges` |
| 6, 7 | `managerScopeCandidatesAndOwnerValidation` |
| 8 | `leadTransitionsRejectClosedAndStaleUpdates` |
| 9 | `permissionsForCareDataAdminAndOtherTenant`, `actualForeignTenantRecordsRemainHiddenFromAdmin`, `roleUnionDoesNotBorrowWritePermissionForAdminScope`, `offeringReadPermissionFromAnotherRoleStillAllowsSalesScope` |
| 12–14 | `customerScopeForSalesCareAndManagerUsesLiveAssignmentsAndMembership` |
| 15 | `customerPatchValidatesVersionOwnerAndDuplicate` |
| 16 | `auditFailureRollsBackCreatesAndUpdates` |
| 17 | `legacyNullFieldsAreReturned` |
| 18 | `CustomerLeadProfileMigrationTest.migrationPreservesLegacyFieldsAndEnforcesEnumsAndTenantOffering` |
| 19 | Các assertion V9 trong `RestoredV2SmokeTest.allOriginalDemoAccountsMapToCurrentTenantRolesAndCredentials` |
| Bổ sung | `changedLeadOfferingRequiresEligibleOwner`, `profileUpdatesPersistWithoutExposingContactOrNotesInAudit`, `archivedRecordsAreHiddenAndOnlyArchivedLeadStopsBlockingDuplicates`, `filtersApplyAfterScopeAndSalesCannotFilterByOwner` |

## FINDING và giới hạn

- `docs/analysis/roles-authorization-v1.md:90` mô tả scope Sales theo owner **và** Offering assignment; DEC-11/D1 quy định riêng Customer là owner **hoặc** SALES customer assignment hiệu lực, không bắt buộc Offering assignment khi đọc Customer. Triển khai theo D1 Tài đã chốt; tài liệu Roles cần được đồng bộ trong việc riêng, không sửa tại đây.
- Chưa đọc trực tiếp Google Doc; dùng trích yếu UC-09/UC-10 do Tài cung cấp. Không làm UI/E2E, chuyển đổi Lead, đổi owner, import/Data Staff scope hay nghiệm thu tài khoản demo.
- Enum `customer_type` và `source` là mặc định chờ Tài duyệt. Các lựa chọn tạm khác: giới hạn notes/reason 4000 ký tự; field tùy chọn không nhận chuỗi rỗng (dùng null hoặc bỏ field); chặn cả detail đã archived; `LEAD_STATE_CONFLICT` khi qualify Lead không còn NEW; status Customer chỉ lọc/đọc trong card này; khi đổi Offering liên quan Customer, owner phải còn đủ điều kiện và actor phải có scope Offering mới. Người nhiều role được tạo theo nhánh Sales cho chính mình khi đủ điều kiện, hoặc theo nhánh Manager với owner được chỉ định.
- Hai request tạo Customer cùng email được test qua API và khóa organization; nhánh bắt lỗi unique constraint ở DB được triển khai, chưa ép riêng race từ writer bên ngoài không dùng khóa organization.
- Chưa áp V9 lên Supabase. Trước lần chạy backend tiếp theo kết nối Supabase, Tài sao lưu database bằng `pg_dump` theo quy trình nhóm, rồi chạy backend để Flyway áp V9; không đưa credential vào repo hoặc evidence. Không kết nối Supabase hay PostgreSQL cổng 5432 trong lượt này.
