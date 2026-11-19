# W17 — Rà soát ổn định backend, giai đoạn 1

**Ngày thực hiện: 18/11/2026; tên file theo yêu cầu card.** Checkout `Tài` tại `d747f6a`. Chỉ đọc mã và dữ liệu; không thay ứng dụng, schema hay nghiệp vụ. Mức dưới đây là ưu tiên rà soát, không khẳng định lỗi đã xảy ra trong production.

## Phát hiện ưu tiên

| Mã / mức | Bằng chứng và tác động | Đề xuất, công dự kiến, quyết định |
|---|---|---|
| W17-01 — MAJOR | `OpportunityRepository.java:17`–`34`: mapper gọi `lines(...)` cho mỗi dòng của list. H2 synthetic: thêm hai Cơ hội trong scope tăng số câu của Sales/Admin từ 7 lên 9; trang N dòng gây thêm N truy vấn, tối đa 20 với trang mặc định. Chưa đo tải PostgreSQL. | Lấy lines theo batch rồi ghép theo ID; giữ nguyên scope và thứ tự. 0,5–1 ngày, không đổi nghiệp vụ. |
| W17-02 — MAJOR | `ConversationService.java:75`–`80`, `115`–`120`, `FoundationAccess.java:18`–`20`: list/messages là đọc nhưng `lockAndRefresh` khóa `organizations FOR UPDATE`. Polling nhiều người cùng tenant tuần tự hóa với các mutation. JDBC test H2: list Manager 16, Sales 13 câu/request, bao gồm xác thực; chưa đo tải đồng thời. | Đo tranh chấp/độ trễ dưới tải; đề xuất cơ chế refresh quyền không chiếm khóa ghi cho đọc. 1–2 ngày; **cần Tài quyết định** nếu thay bảo đảm đồng bộ quyền. |
| W17-03 — MAJOR | `ConversationService.java:153`–`175` giữ khóa organization và conversation trong lúc `adapter.send` gọi ngoài; `TelegramReplyAdapter.java:37`–`43` đặt timeout 12 giây. Gửi thành công bên ngoài rồi DB rollback có thể tạo gửi lặp khi retry. Chưa tái hiện bằng Telegram thật. | Thiết kế outbox/idempotency, trạng thái gửi và thử lỗi mạng/rollback. 2–4 ngày; **cần Tài quyết định** thay hợp đồng gửi và hành vi retry. |
| W17-04 — MAJOR | Khả năng vòng chờ, chưa tái hiện: convert khóa organization → Lead → conversation (`FoundationAccess.java:19`, `LeadConversionService.java:25`–`28`); webhook khóa conversation rồi insert message (`ChannelWebhookService.java:70`–`91`); message có FK organization (`V13__conversations.sql:48`–`62`). FK có thể cần khóa organization trong khi convert đợi conversation. | Viết test hai transaction PostgreSQL có barrier, đo `pg_locks`/SQLSTATE, thống nhất thứ tự khóa hoặc retry có giới hạn. 1–2 ngày; **cần Tài quyết định** nếu thay hành vi retry. |
| W17-05 — MAJOR | `ApiExceptionHandler.java:107`–`111` ghi nguyên exception. Lượt test PostgreSQL cố ý vi phạm CHECK đã cho thấy driver in chi tiết hàng lỗi trong stack trace; với bảng chứa dữ liệu khách, chuỗi exception có thể chứa dữ liệu nhạy cảm. Chưa chứng minh lộ khóa hoặc mật khẩu. | Kiểm thử bằng dữ liệu giả, giữ traceId và mã SQLSTATE nhưng lọc dữ liệu hàng/exception cause; quy định retention. 0,5–1,5 ngày; **cần Tài quyết định** mức chi tiết log vận hành. |
| W17-06 — MAJOR | `application.yml:4`–`5` mặc định kết nối `localhost:5432/pose` khi thiếu `POSE_DATABASE_URL`; có thể kết nối nhầm database local ngoài ý muốn. README đã cảnh báo, chưa thử kết nối. | Đề xuất fail-fast khi thiếu URL ngoài profile test; 0,5 ngày; **cần Tài quyết định** vì thay cách khởi động. |
| W17-07 — MINOR | `DashboardService.java:78`–`147`, `Customer360Service.java:31`–`130`: nhiều truy vấn tập hợp độc lập. JDBC H2 synthetic: Manager KPI 29, today 27, 360 41 câu; Sales lần lượt 25, 18, 30. Không phải bằng chứng N+1: số phụ thuộc role, dữ liệu và xác thực. | Profile trên PostgreSQL với dữ liệu lớn, gom aggregate phù hợp mà giữ từng scope; 1–3 ngày. |
| W17-08 — MINOR | `ConversationService.java:90`–`100` có hai correlated subquery lấy nội dung và chiều tin mới nhất cho từng hội thoại trong cùng một câu SQL. Không phải N+1 JDBC; có thể hai lần probe index mỗi dòng. Dataset restore không có tin để đo thực tế. | Đo có tin và so `LATERAL` lấy cả hai cột; 0,5–1 ngày. |
| W17-09 — MINOR | `CustomerSignalService.java:30`–`34` list tín hiệu không có phân trang/LIMIT; `:37`–`41` danh sách tin chỉ giới hạn 100. Có thể tăng payload và bộ nhớ khi hồ sơ nhiều tín hiệu. Chưa đo dữ liệu lớn. | Xác định ngưỡng và thiết kế phân trang tương thích; 1–2 ngày; **cần Tài quyết định** nếu đổi API/UI. |

Không ghi nhận BLOCKER đã tái hiện. Các rủi ro chưa tái hiện không được coi là lỗi đã xác nhận. Các thay đổi trên đều là đề xuất, chưa tạo migration V18.

## Khóa và transaction

| Luồng | Thứ tự chính quan sát được |
|---|---|
| Convert | Khóa organization → Lead → các conversation theo ID → ghi Interaction/Task/Opportunity → conversation → khóa/chuyển signal → Lead và audit (`LeadConversionService.java:25`–`74`). |
| Link / tạo Lead trong inbox | Khóa organization → conversation → kiểm/tạo Lead → cập nhật conversation, Interaction và audit (`ConversationService.java:60`–`72`, `185`–`216`). |
| Reassign Customer | Khóa organization → Customer → cập nhật từng Opportunity, Task và audit → handover, assignment, Customer (`CustomerService.java:79`–`133`). |
| Webhook | Tìm account → insert/khóa conversation → insert message → cập nhật conversation → attach Interaction → audit/outbox (`ChannelWebhookService.java:41`–`128`); không qua `lockAndRefresh`. |
| Worker tin nhắn | Outbox `FOR UPDATE SKIP LOCKED` → conversation/channel → execution → Task → outbox (`JdbcConversationMessageProcessor.java:88`–`114`, `165`–`198`, `239`). Worker Lead khóa outbox trước các hàng nghiệp vụ (`JdbcWorkflowProcessor.java:146`–`156`, `221`, `266`). |
| Reply | Organization → conversation → gọi adapter ngoài → message/Interaction → audit (`ConversationService.java:153`–`180`). |

Không có thử nghiệm hai luồng đồng bộ để kết luận deadlock/lost update; W17-04 là giả thuyết cần tái hiện. Đọc danh sách/tin hội thoại cũng khóa organization như W17-02.

## Đo số câu JDBC

Checkout tạm `D:/POSE-data/w17-count`, H2 fixture `DashboardIntegrationTest`; thêm test chỉ trong bản sao, bật `org.springframework.jdbc.core.JdbcTemplate` DEBUG và đếm `Executing prepared SQL statement`/SQL query/update, không ghi tham số. Con số gồm xác thực và kiểm scope, không phải số network round-trip PostgreSQL. Fixture ít dòng và không đại diện sáu tổ chức restore; admin fixture không có đủ quyền nên các 403 không dùng để kết luận hiệu năng admin.

| Endpoint, status 200 | Admin | Manager | Sales |
|---|---:|---:|---:|
| Dashboard KPI | 20 | 29 | 25 |
| Dashboard today | 17 | 27 | 18 |
| Lead list | chưa đo: 403 fixture | 8 | 7 |
| Customer list | chưa đo: 403 fixture | 8 | 7 |
| Opportunity list, fixture rỗng | 7 | 8 | 7 |
| Task list | 8 | 12 | 8 |
| Customer 360 | chưa đo: 403 fixture | 41 | 30 |
| Conversation list | chưa đo: 403 fixture | 16 | 13 |

Chưa đo số câu của messages, signals, mỗi KPI E riêng hay request đầy đủ trên restore PostgreSQL. Cần thiết bị đo test-only với dữ liệu thật giả lập và các role có quyền đúng trước khi dùng số cho SLA.

## EXPLAIN trên bản restore

PostgreSQL 18, `SHOW data_directory` = `D:/POSE-data/w17-review-pg/backend/target/auth-postgres-test`; database `pose_v2_review_20261118223645317`. Chỉ chạy SELECT `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)`. Dataset có 120 Customer, 60 Lead, 72 Opportunity, 156 Interaction, 84 Task và 21 handover cho cả sáu tổ chức. Số dưới là khoảng nhỏ nhất–lớn nhất của sáu lần, đơn vị ms; shared hits là số block trong node gốc, không phải số lần đọc vật lý. Các câu là **truy vấn thăm dò rút gọn**, chưa phải SQL nguyên văn của từng endpoint hay plan theo Admin/Manager/Sales; không dùng số này làm SLA. E04/E05 và hội thoại/tín hiệu chưa có fixture đáng kể nên không đánh giá hiệu năng khi có dữ liệu.

| Nhóm truy vấn đại diện | Thời gian ms | Shared hits | Seq scan đáng chú ý |
|---|---:|---:|---|
| Lead list lọc status, phân trang | 0,097–0,150 | 9 | leads |
| Customer list lọc status, phân trang | 0,104–0,125 | 11 | customers |
| Opportunity list lọc stage, phân trang | 0,091–0,128 | 9 | opportunities |
| Task quá hạn / phần today | 0,090–0,144 | 10 | crm_tasks |
| Customer 360 — interactions | 0,160–0,206 | 15–18 | customers; interactions dùng index ở plan đã đo |
| Customer 360 — tasks | 0,119–0,211 | 13–15 | crm_tasks, customers |
| Nhóm A / B / C / D tổng hợp | 0,108–0,247 | 5–13 | leads / crm_tasks / workflow_executions / interactions |
| E01 | 0,136–0,229 | 12–19 | leads, interactions |
| E02 | 0,218–0,526 | 15–51 | opportunities, interactions |
| E03, thăm dò điều kiện liên hệ | 0,088–0,120 | 5–6 | handover_requests |
| Hội thoại / tin / tín hiệu | 0,069–0,178 | 7–9 | organizations |
| E04 / E05, thăm dò | 0,077–0,136 | 6–9 | organizations |

Có index V1 `ix_leads_org_owner_status`, `ix_opportunities_org_owner_stage`; V11 `ix_interactions_org_{customer,lead,opportunity}_time`, `ix_tasks_org_{owner_status,customer,lead,opportunity}_due`; V13 `ix_conversations_org_updated`, `ix_conversation_messages_org_conversation_time`; V15 `idx_customer_signals_{customer,lead}`. Seq scan trên tập nhỏ không tự chứng minh thiếu index. Cần kiểm thử V18+ trên dữ liệu lớn trước khi đề xuất index cụ thể; xem xét sort list `(organization_id, updated_at, id)`, E03 `(organization_id, handover_type, status, reviewed_at, customer_id)` và truy vấn E04 theo hướng/direction sau khi đo plan chính xác. Không tạo index trong lượt rà soát.

Tái lập: chạy `backend/scripts/test-postgres-v2.ps1` trên bản sao, xác nhận `SHOW data_directory`, chọn database `pose_v2_review_*` vừa tạo; dùng `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT ...` với predicate lấy organization từ `organizations`, đối chiếu SQL thực tại `DashboardRepository`, `ValueLeakageRepository`, `Customer360Service` và các repository list. Bản tóm tắt phép đo thử nằm ngoài repo tại `D:/POSE-data/w17-explain-results.json`; không chứa nội dung khách. Chưa lưu script đầy đủ của phép đo thăm dò, nên cần dựng lại câu SQL trước khi so sánh; phép đo này không thay thế profile endpoint.

Ví dụ tái lập probe list Lead cho tổ chức đầu tiên, trong phiên `psql` đã kết nối **đúng bản restore cô lập**:

```sql
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
SELECT l.id FROM leads l
WHERE l.organization_id=(SELECT id FROM organizations ORDER BY id OFFSET 0 LIMIT 1)
  AND l.archived_at IS NULL AND l.status='NEW'
ORDER BY l.updated_at DESC,l.id LIMIT 20;
```

Thay `OFFSET 0` lần lượt bằng 1–5 để đo sáu tổ chức. SQL này không có predicate role; dùng SQL thực với tham số actor từ test-only instrumentation để hoàn tất phép đo theo vai trò.

## Rà frontend dùng tên role

- **Có kiểm permission kết hợp role:** `navigation.ts:31`–`35`; `lib/crm.ts:22`–`25`; `lib/opportunities.ts:15`–`18`; `lib/offerings.ts:63`–`67`; `lib/sales-assignments.ts:63`; `lib/interactions.ts:23`; `lib/tasks.ts:8`–`9`; `lib/customer360.ts:118`; `components/inbox/Inbox.tsx:36`–`37`; `components/crm/CrmDetail.tsx:81`; `components/customer360/CustomerSignalsSection.tsx:23`; `lib/copilot.ts:67`–`73`. Cần đối chiếu từng trường hợp đa role/permission theo backend; không coi guard UI là bảo mật.
- **Nhánh chỉ kiểm role tại chỗ, thường để chọn form hoặc che dữ liệu:** `CrmForm.tsx:24`, `TaskForm.tsx:30`, `OpportunityForm.tsx:30`, `CreateLeadDialog.tsx:18`, `CrmList.tsx:24`, `OpportunityPipeline.tsx:33`, `Customer360Hub.tsx:18`, `Customer360Profile.tsx:72`, `WorkReferences.tsx:14,24`, `OfferingList.tsx:74`. Một số được bao bởi guard permission ở component/hàm gọi; cần test trạng thái bị gỡ quyền và đa role trước khi coi là lỗi. `Dashboard.tsx:112` kết hợp `mayReadUsers` với role. Không tự đổi hành vi.
- **Chỉ hiển thị/truyền thông tin role:** `UserMenu.tsx`, `AppSidebar.tsx`, `Overview.tsx`, `lib/server/backend.ts`; không phải kiểm quyền. `tasks.ts:21` lọc role của ứng viên, cần đối chiếu API khi thay quy tắc ứng viên.

## Baseline và câu hỏi

- H2 checkout sạch: 471 tổng, 442 đạt, 29 bỏ qua, 0 lỗi; PostgreSQL checkout sạch: 471 tổng, 458 đạt, 13 bỏ qua, 0 lỗi, script `Verified.`; đọc Surefire XML, không đặt `POSE_DEMO_PASSWORD`. Lệnh gốc: `mvn -pl backend -am -Dlogging.level.root=ERROR verify`; PostgreSQL dùng `backend/scripts/test-postgres-v2.ps1 -DataPackagePath D:/POSE-data/POSE_CRM_DATA_V2_FINAL` cùng JDK 25.0.2/Maven 3.9.15.
- Frontend Vitest: 375/375 đạt. E2E mặc định bằng `scripts/e2e-env.ps1 -Build`: 38/38 đạt trong 1,2 phút; E2E `-WorkflowWorker`: 1/1 đạt trong 10,6 giây. Script xác nhận các cổng thử nghiệm đã dừng.
- Cần Tài quyết định: ưu tiên W17-01–09; có chấp nhận thay cơ chế refresh quyền đọc và hành vi gửi/retry không; mức chi tiết log; có fail-fast URL DB; có đổi contract phân trang tín hiệu. Cần thêm benchmark theo từng role và dữ liệu lớn, đo lock hai transaction và kiểm logging bằng dữ liệu giả trước khi chốt index hoặc kết luận lộ dữ liệu.

## Cập nhật giai đoạn 2 (DEC-32)

Chi tiết triển khai, tái hiện và kiểm chứng bản sạch xem tại [w17-stability-fixes-2026-11-18.md](w17-stability-fixes-2026-11-18.md).

| Mã | Trạng thái | Trước sửa (giai đoạn 1) | Sau sửa (giai đoạn 2, DEC-32) |
|---|---|---|---|
| W17-01 | **Đã sửa** | `OpportunityRepository.list` gọi `lines(...)` từng dòng; H2 tăng +N câu SQL theo số Cơ hội (7 → 9 khi thêm 2 Cơ hội) | Tải batch `opportunity_lines` trong 1 câu `WHERE organization_id=? AND opportunity_id IN (...)`; test `OpportunityIntegrationTest` xác nhận số câu SQL cho 5 Cơ hội **bằng** 1 Cơ hội, giữ nguyên thứ tự trang và `position` |
| W17-02 | **Đã sửa** | 5 đường đọc (`ChannelAccountService.list`, `ConversationService.list/messages/draftContext/assignees`) gọi `lockAndRefresh` giữ khóa ghi `organizations` | Chuyển cả 5 đường đọc sang `@Transactional(readOnly=true)` + `FoundationAccess.refresh()`; giữ `lockAndRefresh` cho mọi mutation. Test xác nhận thu hồi quyền → `403` ngay ở request kế tiếp và đọc không bị chặn bởi mutation đang giữ khóa tổ chức |
| W17-03 | **Giữ nguyên (ghi giới hạn)** | Gọi nền tảng ngoài đồng bộ trong transaction giữ khóa | Không đổi luồng gửi theo DEC-32; đã ghi rõ giới hạn gửi lặp khi rollback DB trong `docs/api/conversation-api-v1.md` và `DEC-32`; outbox/idempotency chuyển sang KLTN |
| W17-04 | **Đã sửa** | Giả thuyết vòng chờ chưa tái hiện | Tái hiện được `SQLSTATE 40P01` trên PostgreSQL 18.3 cho cả 2 cặp `(a) reply ↔ webhook` và `(b) convert Lead ↔ webhook` (gồm cả vòng chờ thứ hai trên `leads FOR UPDATE` ↔ FK `interactions.lead_id`). Đổi khóa `organizations` (`FoundationAccess`, `OrganizationRepository`) và `leads` (`LeadConversionService`) sang `FOR NO KEY UPDATE` trên PostgreSQL (giữ `FOR UPDATE` trên H2). Sau sửa: cả hai cặp đạt không `40P01`, hai mutation cùng tenant vẫn tuần tự |
| W17-05 | **Đã sửa** | `ApiExceptionHandler` log nguyên `exception` kèm stack trace có thể lộ dữ liệu dòng | Chỉ log `traceId`, `exception`, `rootException`, `sqlState`, `constraint` hợp lệ (không stack trace/message). Test PostgreSQL với dữ liệu giả vi phạm `CHECK` (`23514`) và `UNIQUE` (`23505`, `uq_customers_org_email`) xác nhận log không chứa giá trị giả; API response giữ nguyên contract |
| W17-06 | **Đã sửa** | `application.yml` mặc định `jdbc:postgresql://localhost:5432/pose` | Bỏ mặc định `localhost:5432`, thêm `DatabaseUrlGuard` fail-fast khi thiếu `POSE_DATABASE_URL` (không in credential); cập nhật `backend/README.md` và `docs/huong-dan-chay-local.md` |
| W17-07 | **Để đo thêm / KLTN** | Dashboard KPI/today và Customer 360 gồm nhiều truy vấn tập hợp theo scope | Không sửa trong W17; để đo thêm trên dữ liệu lớn / KLTN |
| W17-08 | **Để đo thêm / KLTN** | Hai correlated subquery lấy `last_content` và `last_direction` trong `ConversationService.list` | Không sửa trong W17; để đo thêm khi có dữ liệu tin nhắn lớn / KLTN |
| W17-09 | **Để đo thêm / KLTN** | `CustomerSignalService` chưa phân trang danh sách tín hiệu | Không sửa trong W17; để đo thêm / KLTN |
