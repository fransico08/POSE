# Hướng dẫn agent cho POSE CRM

## Phạm vi và nguồn quyết định

- Đây là POSE CRM đa ngành, không phải LogiRoute hoặc storefront E-Commerce.
- Thực hiện đúng yêu cầu hiện tại của Tài, từng bước; không tự mở rộng card hoặc coi gợi ý của skill là yêu cầu nghiệp vụ.
- Đọc `readme.md`, README của module và tài liệu liên quan trước khi làm. Với frontend foundation, đọc `frontend/README.md`, `docs/api/api-contract-foundation-v1.md`, `docs/analysis/roles-authorization-v1.md`; đối chiếu `docs/analysis/business-rules-v1.md` khi có nghiệp vụ.
- Yêu cầu trực tiếp của người dùng và quyết định đã chốt của POSE ưu tiên hơn gợi ý thiết kế trong skill. Nếu nghiệp vụ, contract và code mâu thuẫn, báo bằng chứng và hỏi trực tiếp Tài; không tự sửa nghiệp vụ hoặc tạo tài liệu câu hỏi thay cho việc hỏi.
- Tài liệu POSE viết tiếng Việt có dấu; code, tên biến và commit message viết tiếng Anh. Tài liệu skill bên thứ ba giữ ngôn ngữ nguồn.

## Cách chọn skill

Skill nằm ở `.agents/skills/`. Không đọc toàn bộ catalogue mỗi lần và không chạy mọi skill cùng lúc. Đọc đầy đủ `SKILL.md` của skill được chọn, sau đó chỉ đọc reference cần cho công việc; thông báo ngắn cho Tài biết skill được dùng và lý do.

| Công việc | Skill | Cách áp dụng |
|---|---|---|
| Chọn màu, font, spacing, phong cách, đề xuất wireframe/design system hoặc review thị giác | `ui-ux-pro-max` | Tra cứu local; kiểm tra kết quả phù hợp CRM nội bộ, không áp dụng layout landing page vào app một cách máy móc. |
| Xây component, routing/layout, state, responsive, form và accessibility | `frontend-ui-engineering` | Nền tảng triển khai; dùng design system đã chốt, không tự tạo bộ màu khác cho mỗi trang. |
| Kiểm tra giao diện chạy thật, console/network, bàn phím và screenshot | `browser-testing-with-devtools` | Chỉ dùng khi có Chrome DevTools MCP tương ứng; nếu không có, dùng công cụ kiểm thử trình duyệt được phiên cung cấp theo hướng dẫn riêng của công cụ đó và báo phần chưa kiểm. |

- Khi vừa thiết kế vừa code: dùng UI UX Pro Max cho quyết định thiết kế, Frontend UI Engineering cho triển khai; browser testing chỉ khi đã có giao diện chạy được.
- Không áp dụng skill UI cho công việc thuần backend/database. Quy tắc ưu tiên trong bảng chỉ định nguồn tham khảo, không vô hiệu hóa trigger bắt buộc của môi trường agent.
- Không tự cài package, MCP, extension hoặc sửa cấu hình toàn máy chỉ vì skill có ví dụ cài đặt. Nêu nhu cầu trước nếu task cần công cụ chưa có.
- Skill có thể được nhận diện ở lượt/phiên tiếp theo tùy client. Nếu catalogue không hiển thị, đọc file `SKILL.md` theo đường dẫn trong bảng; không tuyên bố đã dùng skill mà chưa đọc.

## Quy tắc frontend foundation

- Stack đã chốt: Next.js, TypeScript, Tailwind CSS, TanStack Query; Recharts chỉ khi cần biểu đồ. Không tự đổi stack hoặc thêm thư viện UI/animation chỉ vì kết quả tra cứu gợi ý.
- Card hiện tại là routing, login và layout khung. Chốt user flow/wireframe trước khi code; không dựng toàn bộ module CRM hoặc dashboard ngoài yêu cầu.
- Login nhận `organizationCode`, email, password. Sáu mã demo: EDU, REA, TECH, RET, LOG, CLI; backend không phân biệt hoa/thường. Không hardcode tài khoản/mật khẩu vào frontend hoặc skill output.
- Quyền và phạm vi dữ liệu do backend kiểm soát. Dùng `permissions` của `/auth/me` để điều hướng và ẩn/hiện thao tác; không chỉ kiểm tên role, không coi route guard là bảo mật backend.
- Hiển thị tenant/user hiện tại. Login lỗi dùng thông báo chung theo contract, không tiết lộ tài khoản INACTIVE hoặc sự tồn tại của tenant/email.
- Refresh token xoay vòng: chỉ một request refresh đang chạy trong một phiên, không retry bằng token đã tiêu thụ. Logout/hết phiên phải dọn state/cache liên quan tenant và user. Cách lưu token phải được quyết định phù hợp kiến trúc, không lấy ví dụ lưu token từ skill làm mặc định.
- Có trạng thái kiểm tra phiên, loading, lỗi mạng, empty, 403 và 404. Thông báo lỗi field theo contract; không để lộ password, token hoặc dữ liệu nhạy cảm trong console, screenshot và evidence.
- Mục tiêu accessibility của POSE: WCAG 2.2 AA; skill nguồn có thể còn ghi 2.1 AA. Kiểm tra bàn phím/focus, label, tương phản, reflow/zoom, giảm chuyển động và hỗ trợ password manager/paste. Không tuyên bố đạt toàn chuẩn chỉ dựa vào checklist hoặc axe.
- Màu/font/spacing dùng token nhất quán, nội dung tiếng Việt có dấu và dữ liệu demo phù hợp. Ưu tiên đọc và thao tác dữ liệu CRM; không mặc định Glassmorphism hoặc mobile-only từ dự án nguồn.
- Với UI UX Pro Max, xác định gốc repo hiện tại rồi gọi script bằng đường dẫn tuyệt đối tới `.agents/skills/ui-ux-pro-max/scripts/search.py`. Tra cứu thử không dùng `--persist`; chỉ lưu design system khi task cần, sau khi kiểm tra kết quả. Không dùng `--force` nếu chưa được cho phép.

## Git và dữ liệu

- Mỗi phiên agent có thay đổi phải thêm một dòng vào `docs/AI-USAGE.md` trước khi commit; không ghi secrets hoặc credentials.
- Công việc của Tài thực hiện trên nhánh `Tài` ngay tại checkout `D:\POSE`. Worktree cũ `C:\Users\Dell\.codex\worktrees\pose-tai-auth\POSE` chỉ còn là bản sao detached HEAD để tham chiếu, không làm việc hay commit ở đó. Kiểm tra `git status` và branch trước khi sửa; không tự tạo nhánh mới hoặc chuyển sang `main` khi chưa được yêu cầu.
- Giữ thay đổi sẵn có của người dùng. Commit cục bộ theo từng ý khi đã được cho phép; không push, merge, mở PR hay đổi Trello nếu chưa có yêu cầu mới cho phép hành động đó.
- Commit message tiếng Anh. Với công việc của Tài, giữ author/committer là tài khoản Git đã cấu hình của Tài (`fransico08`); không thêm trailer `Co-authored-by` cho Codex hoặc agent. Không đổi thông tin tác giả của thành viên khác. Viết lại commit đã tồn tại hoặc force-push chỉ khi Tài xác nhận rõ phạm vi.
- Không chỉnh gói final của Nhi, backup, CSV, Data Dictionary hoặc credentials demo; không commit ZIP, backup, secrets hay `AGENT_HANDOFF.md`.
- Nếu công việc cần PostgreSQL test, chỉ dùng cluster cô lập `127.0.0.1:54329`, xác nhận `SHOW data_directory`, tuân guard/script trong `backend/README.md`; không chạm server thật ở 5432. Supabase dùng chung (DEC-08) chỉ nạp/migrate bằng `backend/scripts/setup-supabase-v2.ps1` hoặc app đã được duyệt, do người giữ credential chạy; agent không giữ, nhập hay in mật khẩu Supabase và không chạy test làm đổi dữ liệu trên đó. Không sửa migrations V1–V4 đã chốt; thay đổi database qua migration mới.
- Chỉ báo hoàn thành phần đã kiểm chứng; nêu riêng giới hạn công cụ và các bước chưa chạy.

## Nguồn skill và cập nhật

- `frontend-ui-engineering` và `browser-testing-with-devtools`: lấy từ `D:\New-Tech\.agents\skills` ngày 12/10/2026; tại thời điểm lấy giống bản ở `D:\Individual_Project`. Không copy `AGENTS.md` của LogiRoute. Checklist accessibility bổ sung riêng cho POSE vì reference nguồn bị thiếu. Chưa tìm thấy thông tin license cho hai skill local; không tự gán license của UI UX Pro Max cho chúng.
- `ui-ux-pro-max`: `https://github.com/nextlevelbuilder/ui-ux-pro-max-skill`, revision `026b6e172c47fa09246b89b6e1d414b8e15f1057`, thư mục nguồn `.claude/skills/ui-ux-pro-max`. Giữ data/scripts/references và MIT license; sửa ví dụ đường dẫn cho cài đặt `.agents` của POSE, dọn trailing whitespace ở dòng trống của `design_system.py`, không đổi thuật toán/dữ liệu.
- Không tự cập nhật skill theo upstream mỗi lần chạy. Khi có yêu cầu cập nhật, so sánh revision, giữ phần thích nghi của POSE và kiểm tra lại script/reference trước khi thay thế.
