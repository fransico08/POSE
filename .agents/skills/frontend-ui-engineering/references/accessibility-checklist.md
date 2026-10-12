# Checklist accessibility cho POSE

Reference bổ sung cho bản `frontend-ui-engineering` local, không phải tài liệu gốc của skill. Mục tiêu: WCAG 2.2 AA; checklist này không chứng nhận toàn bộ tiêu chí.

## Khi thiết kế component, form và layout

- Dùng HTML semantic, landmark và heading phù hợp. Button/link/input có tên truy cập được; icon trang trí không bị đọc lặp.
- Tất cả thao tác thực hiện được bằng bàn phím; thứ tự Tab hợp lý, focus nhìn thấy và không bị sidebar/header/modal che hoàn toàn.
- Modal cần quản lý focus và trả focus về phần tử mở khi đóng. Ví dụ `<dialog open>` trong skill chỉ là minh họa, không tự đảm bảo modal/focus trap; kiểm tra component thực tế.
- Label không chỉ là placeholder; liên kết lỗi với input, thông báo trạng thái cho công nghệ hỗ trợ; không chỉ dựa vào màu để báo lỗi/trạng thái.
- Text thông thường tương phản tối thiểu 4.5:1, text lớn 3:1; thành phần UI/focus theo tiêu chí áp dụng tối thiểu 3:1.
- Kiểm tra reflow ở chiều rộng 320 CSS px, zoom 200% và 400%, tên/email dài và tiếng Việt có dấu. Với bảng dữ liệu cần layout hai chiều, cuộn trong vùng bảng thay vì làm toàn trang tràn ngang.
- Kích thước target tối thiểu 24 × 24 CSS px hoặc thỏa ngoại lệ/spacing của WCAG 2.2; ưu tiên vùng thao tác 44 × 44 khi phù hợp. Không coi mọi con số gợi ý của skill là yêu cầu WCAG.
- Tôn trọng `prefers-reduced-motion`; animation không được che nội dung hoặc làm sai trạng thái cuối.
- Login cho phép paste/autofill/password manager; nút hiện/ẩn password có tên và trạng thái phù hợp. Không bắt người dùng giải bài ghi nhớ nếu không có phương án thay thế phù hợp.
- Loading/error/empty/403/404 có nội dung và hành động hợp lý; không tự đưa focus đi chỉ vì render lại.

## Khi nghiệm thu

- Thử Tab, Shift+Tab, Enter, Space và Escape theo component; kiểm tra screen reader khi có môi trường phù hợp.
- Kiểm tra responsive 320, 768, 1024 và 1440 px, zoom và nội dung dài; chụp evidence không có secrets.
- Dùng axe/accessibility tree nếu công cụ sẵn có, nhưng vẫn kiểm tra thủ công. Ghi rõ phần chưa kiểm; không ghi PASS nếu chỉ đọc code.

Nguồn tiêu chí: [W3C WCAG 2.2 Quick Reference](https://www.w3.org/WAI/WCAG22/quickref/).
